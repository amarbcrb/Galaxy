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
}

enum class VibrationPattern { SHORT, ALARM }

sealed interface Action {
    data class Speak(val text: String) : Action
    data class PlaySound(val cue: SoundCue) : Action
    data class Vibrate(val pattern: VibrationPattern) : Action
}

enum class Overlay { NONE, CLOCK, TIMER }

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

        val busy = ringingSince != null || timerDeadline != null
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
            Event.DoubleTap -> {
                clockUntil = ms + CLOCK_MILLIS
                val t = now.dateTime
                out += Action.Speak(Phrases.time(t.hour, t.minute, t.second))
            }
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
        )
    }

    companion object {
        const val HUNGRY_LEVEL = 20
        const val YAWN_AFTER_MILLIS = 2 * 60_000L
        const val SLEEP_AFTER_MILLIS = 5 * 60_000L
        const val NIGHT_AWAKE_MILLIS = 30_000L
        const val HUNGRY_REMINDER_MILLIS = 10 * 60_000L
        const val CLOCK_MILLIS = 5_000L
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
