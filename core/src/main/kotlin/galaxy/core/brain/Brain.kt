package galaxy.core.brain

import galaxy.core.Emotion
import galaxy.core.sound.SoundCue
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/** A point in time: [millis] is a monotonic clock, [dateTime] the wall clock. */
data class Moment(val millis: Long, val dateTime: LocalDateTime)

sealed interface Event {
    data object Tap : Event
    data object DoubleTap : Event
    data object LongPress : Event
    data object Pet : Event
    data object MenuOpened : Event
    data object Shake : Event
    data object PickedUp : Event
    data object FaceDown : Event
    data object FlatStill : Event
    data class Battery(val level: Int, val charging: Boolean) : Event
    data class StartTimer(val durationMillis: Long) : Event
    data object CancelTimer : Event

    /** Show an emotion on request (voice command or hidden menu). */
    data class Act(val emotion: Emotion) : Event
    data object Sleep : Event
    data object Wake : Event
    data object TellTime : Event

    /** Someone's face appeared in front of the camera after being away. */
    data object FaceSeen : Event

    /** "Mains en l'air !": Galaxy gets scared. */
    data object HandsUp : Event

    /** "Pan !": the screen cracks where [x], [y] point (-1..1), or somewhere random if null. */
    data class Shoot(val x: Float? = null, val y: Float? = null) : Event

    /** The eye color just changed. */
    data object NewColor : Event

    /** Magic trick: think for a moment, then reveal [prediction] (or admit seeing nothing). */
    data class Reveal(val prediction: Prediction?) : Event
}

enum class VibrationPattern { SHORT, ALARM, SHOT }

/** A cracked screen, centered on [x], [y] in -1..1 screen coordinates. */
data class Crack(val x: Float, val y: Float, val seed: Int, val startMillis: Long)

sealed interface Action {
    data class Speak(val text: String) : Action
    data class PlaySound(val cue: SoundCue) : Action
    data class Vibrate(val pattern: VibrationPattern) : Action
}

enum class Overlay { NONE, CLOCK, TIMER, PREDICTION }

data class BrainSettings(
    val name: String = "",
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 7,
)

data class BrainState(
    val emotion: Emotion = Emotion.CALM,
    val overlay: Overlay = Overlay.NONE,
    /** Time left on the timer, or null when no timer runs. */
    val timerRemainingMillis: Long? = null,
    val timerRinging: Boolean = false,
    val asleep: Boolean = false,
    val night: Boolean = false,
    val charging: Boolean = false,
    val crack: Crack? = null,
    /** The prediction shown on screen while [overlay] is [Overlay.PREDICTION]. */
    val revealed: Prediction? = null,
) {
    val keepScreenOn: Boolean get() = charging || timerRemainingMillis != null || timerRinging
}

/**
 * Galaxy's behavior. It knows nothing about Android: it receives [Event]s and regular [tick]s,
 * keeps its [state] up to date and returns the [Action]s (speech, sounds, vibrations) to perform.
 */
class Brain(
    var settings: BrainSettings = BrainSettings(),
    private val random: Random = Random.Default,
) {
    private enum class SleepReason { IDLE, NIGHT, FLAT, FACE_DOWN }

    var state = BrainState()
        private set

    private var started = false
    private var asleep = false
    private var sleepReason = SleepReason.IDLE
    private var night = false
    private var nightAwakeUntil = 0L
    private var lastInteraction = 0L
    private var yawned = false
    private var nextCuriousAt = 0L

    private val moods = ArrayDeque<Pair<Emotion, Long>>()
    private var mood: Emotion? = null
    private var moodUntil = 0L

    private val taps = ArrayDeque<Long>()
    private val pets = ArrayDeque<Long>()
    private var lastPetSound = Long.MIN_VALUE / 2

    private var clockUntil = 0L

    private var timerStart = 0L
    private var timerDeadline: Long? = null
    private var ringingSince: Long? = null
    private var lastAlarm = Long.MIN_VALUE / 2

    private var batteryLevel: Int? = null
    private var charging = false
    private var lastHungryCall = 0L

    private var greetedOn: LocalDate? = null
    private var crack: Crack? = null
    private var pendingReveal: Prediction? = null
    private var revealAt = 0L
    private var revealed: Prediction? = null
    private var revealedUntil = 0L

    private val hungry get() = batteryLevel.let { it != null && it <= HUNGRY_LEVEL } && !charging

    fun onEvent(event: Event, now: Moment): List<Action> {
        ensureStarted(now)
        val out = mutableListOf<Action>()
        when (event) {
            is Event.Battery -> onBattery(event, now, out)
            is Event.StartTimer -> {
                interact(now, out)
                timerStart = now.millis
                timerDeadline = now.millis + event.durationMillis
                ringingSince = null
                setMood(now, Emotion.HAPPY to 1_500)
                out += Action.PlaySound(SoundCue.HAPPY)
            }
            Event.CancelTimer -> {
                timerDeadline = null
                ringingSince = null
            }
            is Event.Act -> {
                ringingSince = null
                interact(now, out)
                setMood(now, event.emotion to 3_000)
                soundOf(event.emotion)?.let { out += Action.PlaySound(it) }
            }
            Event.Sleep -> {
                ringingSince = null
                if (!asleep) out += Action.PlaySound(SoundCue.YAWN)
                sleep(SleepReason.IDLE)
            }
            Event.Wake -> {
                val wasAsleep = asleep
                interact(now, out)
                if (!wasAsleep) {
                    setMood(now, Emotion.HAPPY to 2_000)
                    out += Action.PlaySound(SoundCue.HAPPY)
                }
            }
            Event.TellTime -> {
                interact(now, out)
                tellTime(now, out)
            }
            Event.FaceSeen -> if (!night) {
                val wasAsleep = asleep
                interact(now, out)
                if (!wasAsleep) {
                    setMood(now, Emotion.HAPPY to 2_000)
                    out += Action.PlaySound(SoundCue.HAPPY)
                }
            }
            Event.HandsUp -> {
                ringingSince = null
                interact(now, out)
                setMood(now, Emotion.SCARED to 8_000)
                out += Action.Speak(Phrases.HANDS_UP)
                out += Action.PlaySound(SoundCue.SCARED)
            }
            is Event.Shoot -> {
                ringingSince = null
                interact(now, out)
                crack = Crack(
                    x = event.x ?: random.nextFloat() * 1.2f - 0.6f,
                    y = event.y ?: random.nextFloat() * 1.2f - 0.6f,
                    seed = random.nextInt(),
                    startMillis = now.millis,
                )
                setMood(now, Emotion.KO to 3_500, Emotion.DIZZY to 2_000)
                out += Action.PlaySound(SoundCue.BANG)
                out += Action.PlaySound(SoundCue.GLASS)
                out += Action.Vibrate(VibrationPattern.SHOT)
                out += Action.Speak(Phrases.SHOT)
            }
            is Event.Reveal -> {
                interact(now, out)
                val prediction = event.prediction
                if (prediction == null) {
                    setMood(now, Emotion.CURIOUS to 2_500)
                    out += Action.Speak(Phrases.NO_PREDICTION)
                } else {
                    // A little suspense before the reveal.
                    pendingReveal = prediction
                    revealAt = now.millis + THINK_MILLIS
                    revealed = null
                    setMood(now, Emotion.CURIOUS to THINK_MILLIS.toInt())
                    out += Action.Speak(Phrases.THINKING)
                    out += Action.PlaySound(SoundCue.DRUMROLL)
                }
            }
            Event.NewColor -> {
                interact(now, out)
                setMood(now, Emotion.HAPPY to 1_500)
                out += Action.PlaySound(SoundCue.HAPPY)
                out += Action.Speak(Phrases.NEW_COLOR)
            }
            Event.FaceDown -> sleep(SleepReason.FACE_DOWN)
            Event.FlatStill -> if (!asleep) sleep(SleepReason.FLAT)
            else -> onTouch(event, now, out)
        }
        refresh(now)
        return out
    }

    fun tick(now: Moment): List<Action> {
        ensureStarted(now)
        val out = mutableListOf<Action>()
        val ms = now.millis

        val nowNight = isNight(now.dateTime)
        if (nowNight && !night) {
            night = true
            if (!asleep) {
                out += Action.Speak(Phrases.goodNight(settings.name))
                out += Action.PlaySound(SoundCue.YAWN)
            }
            sleep(SleepReason.NIGHT)
        } else if (!nowNight && night) {
            night = false
            if (asleep && sleepReason == SleepReason.NIGHT) {
                asleep = false
                lastInteraction = ms
                setMood(now, Emotion.SLEEPY to 3_000)
                out += Action.PlaySound(SoundCue.YAWN)
            }
        }

        timerDeadline?.let { deadline ->
            if (ms >= deadline) {
                timerDeadline = null
                ringingSince = ms
                lastAlarm = Long.MIN_VALUE / 2
                asleep = false
                lastInteraction = ms
                if (night) nightAwakeUntil = ms + NIGHT_AWAKE_MILLIS
                out += Action.Speak(Phrases.TIMER_DONE)
            }
        }
        ringingSince?.let { since ->
            if (ms - since > RING_MAX_MILLIS) {
                ringingSince = null
            } else if (ms - lastAlarm >= RING_INTERVAL_MILLIS) {
                lastAlarm = ms
                out += Action.PlaySound(SoundCue.ALARM)
                out += Action.Vibrate(VibrationPattern.ALARM)
            }
        }

        pendingReveal?.let { prediction ->
            if (ms >= revealAt) {
                pendingReveal = null
                revealed = prediction
                revealedUntil = ms + REVEAL_MILLIS
                lastInteraction = ms
                setMood(now, Emotion.LAUGHING to 2_500, Emotion.HAPPY to 2_000)
                out += Action.PlaySound(SoundCue.TADA)
                out += Action.Speak(prediction.sentence)
            }
        }

        val busy = ringingSince != null || timerDeadline != null || pendingReveal != null
        if (night && !asleep && !busy && ms >= nightAwakeUntil) sleep(SleepReason.NIGHT)

        if (!night && !asleep && !busy) {
            val idle = ms - lastInteraction
            if (idle >= SLEEP_AFTER_MILLIS) {
                sleep(SleepReason.IDLE)
            } else if (idle >= YAWN_AFTER_MILLIS && !yawned) {
                yawned = true
                setMood(now, Emotion.SLEEPY to 2_500)
                out += Action.PlaySound(SoundCue.YAWN)
            } else if (idle < YAWN_AFTER_MILLIS && mood == null && ms >= nextCuriousAt) {
                setMood(now, Emotion.CURIOUS to 1_800)
                nextCuriousAt = ms + random.nextLong(25_000, 60_000)
            }
        }

        if (hungry && !asleep && ms - lastHungryCall >= HUNGRY_REMINDER_MILLIS) {
            lastHungryCall = ms
            out += Action.Speak(Phrases.HUNGRY)
            out += Action.PlaySound(SoundCue.HUNGRY)
        }

        advanceMood(ms)
        refresh(now)
        return out
    }

    private fun onTouch(event: Event, now: Moment, out: MutableList<Action>) {
        val ms = now.millis
        if (ringingSince != null) {
            ringingSince = null
            interact(now, out)
            setMood(now, Emotion.HAPPY to 2_000)
            return
        }
        val wasAsleep = asleep
        interact(now, out)
        // The touch that wakes Galaxy up is only a wake-up, except a shake which also makes him dizzy.
        if (wasAsleep && event != Event.Shake) return

        when (event) {
            Event.Tap -> {
                taps.addLast(ms)
                while (taps.isNotEmpty() && ms - taps.first() > ANGRY_TAP_WINDOW_MILLIS) taps.removeFirst()
                if (taps.size >= ANGRY_TAPS) {
                    taps.clear()
                    setMood(now, Emotion.ANGRY to 2_500)
                    out += Action.PlaySound(SoundCue.ANGRY)
                    out += Action.Vibrate(VibrationPattern.SHORT)
                } else {
                    setMood(now, Emotion.SURPRISED to 900)
                    out += Action.PlaySound(SoundCue.SURPRISE)
                }
            }
            Event.DoubleTap -> tellTime(now, out)
            Event.LongPress -> {
                setMood(now, Emotion.LAUGHING to 2_500)
                out += Action.PlaySound(SoundCue.LAUGH)
            }
            Event.Pet -> {
                pets.addLast(ms)
                while (pets.isNotEmpty() && ms - pets.first() > PET_WINDOW_MILLIS) pets.removeFirst()
                val inLove = pets.size >= LOVE_PETS
                setMood(now, if (inLove) Emotion.LOVE to 3_000 else Emotion.HAPPY to 2_000)
                if (ms - lastPetSound >= PET_SOUND_INTERVAL_MILLIS) {
                    lastPetSound = ms
                    out += Action.PlaySound(if (inLove) SoundCue.LOVE else SoundCue.HAPPY)
                }
            }
            Event.Shake -> {
                setMood(now, Emotion.DIZZY to 3_000)
                out += Action.PlaySound(SoundCue.DIZZY)
                out += Action.Vibrate(VibrationPattern.SHORT)
            }
            else -> Unit
        }
    }

    private fun onBattery(event: Event.Battery, now: Moment, out: MutableList<Action>) {
        val first = batteryLevel == null
        val wasHungry = hungry
        val wasCharging = charging
        batteryLevel = event.level
        charging = event.charging
        if (first) {
            lastHungryCall = now.millis
            return
        }
        if (charging && !wasCharging && !(asleep && night)) {
            interact(now, out)
            setMood(now, Emotion.HAPPY to 3_000)
            out += Action.PlaySound(SoundCue.EAT)
            out += Action.Speak(Phrases.THANKS_FOOD)
        } else if (hungry && !wasHungry && !asleep) {
            lastHungryCall = now.millis
            out += Action.Speak(Phrases.HUNGRY)
            out += Action.PlaySound(SoundCue.HUNGRY)
        }
    }

    private fun tellTime(now: Moment, out: MutableList<Action>) {
        clockUntil = now.millis + CLOCK_MILLIS
        val t = now.dateTime
        out += Action.Speak(Phrases.time(t.hour, t.minute, t.second))
    }

    private fun soundOf(emotion: Emotion): SoundCue? = when (emotion) {
        Emotion.HAPPY -> SoundCue.HAPPY
        Emotion.LAUGHING -> SoundCue.LAUGH
        Emotion.SURPRISED -> SoundCue.SURPRISE
        Emotion.CURIOUS -> SoundCue.CURIOUS
        Emotion.ANGRY -> SoundCue.ANGRY
        Emotion.SAD -> SoundCue.SAD
        Emotion.LOVE -> SoundCue.LOVE
        Emotion.DIZZY -> SoundCue.DIZZY
        Emotion.SLEEPY -> SoundCue.YAWN
        Emotion.HUNGRY -> SoundCue.HUNGRY
        Emotion.SCARED -> SoundCue.SCARED
        Emotion.CALM, Emotion.ASLEEP, Emotion.KO -> null
    }

    /** Someone paid attention to Galaxy: wake up if needed and say good morning once a day. */
    private fun interact(now: Moment, out: MutableList<Action>) {
        lastInteraction = now.millis
        yawned = false
        if (night) nightAwakeUntil = now.millis + NIGHT_AWAKE_MILLIS
        if (asleep) {
            asleep = false
            if (night) {
                // Woken gently at night: stays sleepy and quiet.
                moods.clear()
                mood = null
            } else {
                setMood(now, Emotion.SURPRISED to 1_000, Emotion.HAPPY to 2_000)
                out += Action.PlaySound(SoundCue.WAKE)
            }
        }
        val today = now.dateTime.toLocalDate()
        if (!night && now.dateTime.hour in MORNING_HOURS && greetedOn != today) {
            greetedOn = today
            out += Action.Speak(Phrases.goodMorning(settings.name))
        }
    }

    private fun sleep(reason: SleepReason) {
        asleep = true
        sleepReason = reason
        moods.clear()
        mood = null
    }

    private fun setMood(now: Moment, vararg steps: Pair<Emotion, Int>) {
        moods.clear()
        steps.forEach { (emotion, millis) -> moods.addLast(emotion to millis.toLong()) }
        mood = null
        advanceMood(now.millis)
    }

    private fun advanceMood(ms: Long) {
        if (mood != null && ms < moodUntil) return
        val next = moods.removeFirstOrNull()
        if (next == null) {
            mood = null
            return
        }
        mood = next.first
        moodUntil = ms + next.second
    }

    private fun ensureStarted(now: Moment) {
        if (started) return
        started = true
        lastInteraction = now.millis
        nextCuriousAt = now.millis + random.nextLong(20_000, 40_000)
        night = isNight(now.dateTime)
        if (night) sleep(SleepReason.NIGHT)
    }

    fun isNight(dateTime: LocalDateTime): Boolean {
        val start = settings.nightStartHour
        val end = settings.nightEndHour
        val hour = dateTime.hour
        return when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }

    private fun refresh(now: Moment) {
        val ms = now.millis
        advanceMood(ms)
        if (crack.let { it != null && ms - it.startMillis > CRACK_MILLIS }) crack = null
        if (revealed != null && ms > revealedUntil) revealed = null
        val emotion = when {
            ringingSince != null -> Emotion.LAUGHING
            asleep -> Emotion.ASLEEP
            mood != null -> mood!!
            night -> Emotion.SLEEPY
            hungry -> Emotion.HUNGRY
            else -> Emotion.CALM
        }
        val deadline = timerDeadline
        val glancing = (ms - timerStart) % TIMER_GLANCE_CYCLE_MILLIS >= TIMER_GLANCE_CYCLE_MILLIS - TIMER_GLANCE_MILLIS
        val overlay = when {
            revealed != null -> Overlay.PREDICTION
            ms < clockUntil -> Overlay.CLOCK
            deadline != null && !glancing -> Overlay.TIMER
            else -> Overlay.NONE
        }
        state = BrainState(
            emotion = emotion,
            overlay = overlay,
            timerRemainingMillis = deadline?.let { (it - ms).coerceAtLeast(0) },
            timerRinging = ringingSince != null,
            asleep = asleep,
            night = night,
            charging = charging,
            crack = crack,
            revealed = revealed,
        )
    }

    companion object {
        const val HUNGRY_LEVEL = 20
        const val YAWN_AFTER_MILLIS = 2 * 60_000L
        const val SLEEP_AFTER_MILLIS = 5 * 60_000L
        const val NIGHT_AWAKE_MILLIS = 30_000L
        const val HUNGRY_REMINDER_MILLIS = 10 * 60_000L
        const val CLOCK_MILLIS = 5_000L
        const val CRACK_MILLIS = 5_000L
        const val THINK_MILLIS = 3_000L
        const val REVEAL_MILLIS = 8_000L
        const val RING_INTERVAL_MILLIS = 2_000L
        const val RING_MAX_MILLIS = 2 * 60_000L
        const val TIMER_GLANCE_CYCLE_MILLIS = 20_000L
        const val TIMER_GLANCE_MILLIS = 3_000L
        const val ANGRY_TAPS = 4
        const val ANGRY_TAP_WINDOW_MILLIS = 1_500L
        const val LOVE_PETS = 8
        const val PET_WINDOW_MILLIS = 4_000L
        const val PET_SOUND_INTERVAL_MILLIS = 1_500L
        val MORNING_HOURS = 5..11
    }
}
