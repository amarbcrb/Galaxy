package galaxy.core.brain

import galaxy.core.Emotion
import galaxy.core.sound.SoundCue
import java.time.LocalDateTime
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrainTest {
    private lateinit var brain: Brain
    private var millis = 0L
    private var wall = LocalDateTime.of(2026, 10, 28, 14, 0, 0)

    private fun now() = Moment(millis, wall)

    private fun advance(ms: Long) {
        // Tick like the app does, so timed behaviors happen along the way.
        var left = ms
        while (left > 0) {
            val step = minOf(left, 100L)
            millis += step
            wall = wall.plusNanos(step * 1_000_000)
            left -= step
            brain.tick(now())
        }
    }

    private fun send(event: Event) = brain.onEvent(event, now())

    @BeforeTest
    fun setUp() {
        brain = Brain(BrainSettings(name = "Amar"), Random(1))
        brain.tick(now())
    }

    @Test
    fun startsCalm() {
        assertEquals(Emotion.CALM, brain.state.emotion)
    }

    @Test
    fun tapSurprisesThenCalms() {
        val actions = send(Event.Tap)
        assertEquals(Emotion.SURPRISED, brain.state.emotion)
        assertTrue(Action.PlaySound(SoundCue.SURPRISE) in actions)
        advance(1_000)
        assertEquals(Emotion.CALM, brain.state.emotion)
    }

    @Test
    fun manyQuickTapsMakeHimAngry() {
        repeat(3) { send(Event.Tap); advance(200) }
        val actions = send(Event.Tap)
        assertEquals(Emotion.ANGRY, brain.state.emotion)
        assertTrue(Action.PlaySound(SoundCue.ANGRY) in actions)
    }

    @Test
    fun slowTapsDoNotMakeHimAngry() {
        repeat(4) { send(Event.Tap); advance(1_000) }
        send(Event.Tap)
        assertEquals(Emotion.SURPRISED, brain.state.emotion)
    }

    @Test
    fun pettingMakesHappyThenInLove() {
        send(Event.Pet)
        assertEquals(Emotion.HAPPY, brain.state.emotion)
        repeat(8) { advance(200); send(Event.Pet) }
        assertEquals(Emotion.LOVE, brain.state.emotion)
    }

    @Test
    fun longPressMakesHimLaugh() {
        send(Event.LongPress)
        assertEquals(Emotion.LAUGHING, brain.state.emotion)
    }

    @Test
    fun shakeMakesHimDizzy() {
        send(Event.Shake)
        assertEquals(Emotion.DIZZY, brain.state.emotion)
    }

    @Test
    fun doubleTapShowsAndSaysTheTime() {
        wall = LocalDateTime.of(2026, 10, 28, 14, 32, 7)
        val actions = send(Event.DoubleTap)
        assertEquals(Overlay.CLOCK, brain.state.overlay)
        assertTrue(Action.Speak("Il est 14 heures 32 et 7 secondes") in actions)
        advance(Brain.CLOCK_MILLIS + 100)
        assertEquals(Overlay.NONE, brain.state.overlay)
    }

    @Test
    fun getsBoredThenFallsAsleep() {
        advance(Brain.YAWN_AFTER_MILLIS + 100)
        assertEquals(Emotion.SLEEPY, brain.state.emotion)
        advance(Brain.SLEEP_AFTER_MILLIS - Brain.YAWN_AFTER_MILLIS)
        assertTrue(brain.state.asleep)
        assertEquals(Emotion.ASLEEP, brain.state.emotion)
    }

    @Test
    fun touchWakesHimUpSurprisedThenHappy() {
        advance(Brain.SLEEP_AFTER_MILLIS + 100)
        val actions = send(Event.Tap)
        assertFalse(brain.state.asleep)
        assertEquals(Emotion.SURPRISED, brain.state.emotion)
        assertTrue(Action.PlaySound(SoundCue.WAKE) in actions)
        advance(1_100)
        assertEquals(Emotion.HAPPY, brain.state.emotion)
    }

    @Test
    fun faceDownSleepsUntilPickedUp() {
        send(Event.FaceDown)
        assertTrue(brain.state.asleep)
        advance(60_000)
        assertTrue(brain.state.asleep)
        send(Event.PickedUp)
        assertFalse(brain.state.asleep)
        assertEquals(Emotion.SURPRISED, brain.state.emotion)
    }

    @Test
    fun flatStillSleeps() {
        send(Event.FlatStill)
        assertTrue(brain.state.asleep)
    }

    @Test
    fun saysGoodNightAndSleepsAtNight() {
        wall = LocalDateTime.of(2026, 10, 28, 21, 59, 59)
        send(Event.Tap)
        val actions = mutableListOf<Action>()
        repeat(20) {
            millis += 100
            wall = wall.plusNanos(100_000_000)
            actions += brain.tick(now())
        }
        assertTrue(Action.Speak("Bonne nuit Amar.") in actions)
        assertTrue(brain.state.night)
        assertTrue(brain.state.asleep)
    }

    @Test
    fun nightTouchWakesGentlyThenSleepsAgain() {
        brain = Brain(BrainSettings(name = "Amar"), Random(1))
        wall = LocalDateTime.of(2026, 10, 28, 23, 0, 0)
        brain.tick(now())
        assertTrue(brain.state.asleep)
        val actions = send(Event.Tap)
        assertFalse(brain.state.asleep)
        assertEquals(Emotion.SLEEPY, brain.state.emotion)
        assertTrue(actions.none { it is Action.PlaySound })
        advance(Brain.NIGHT_AWAKE_MILLIS + 200)
        assertTrue(brain.state.asleep)
    }

    @Test
    fun wakesUpAtTheEndOfTheNight() {
        brain = Brain(BrainSettings(), Random(1))
        wall = LocalDateTime.of(2026, 10, 29, 6, 59, 59)
        brain.tick(now())
        assertTrue(brain.state.asleep)
        advance(1_500)
        assertFalse(brain.state.night)
        assertFalse(brain.state.asleep)
    }

    @Test
    fun greetsOncePerMorning() {
        brain = Brain(BrainSettings(name = "Amar"), Random(1))
        wall = LocalDateTime.of(2026, 10, 28, 8, 0, 0)
        brain.tick(now())
        val first = send(Event.Tap)
        assertTrue(Action.Speak("Bonjour Amar !") in first)
        advance(2_000)
        val second = send(Event.Tap)
        assertTrue(second.none { it is Action.Speak })
    }

    @Test
    fun noGreetingInTheAfternoon() {
        val actions = send(Event.Tap)
        assertTrue(actions.none { it is Action.Speak })
    }

    @Test
    fun hungryWhenBatteryLowAndHappyWhenPlugged() {
        send(Event.Battery(level = 50, charging = false))
        val low = send(Event.Battery(level = 15, charging = false))
        assertTrue(Action.Speak(Phrases.HUNGRY) in low)
        assertEquals(Emotion.HUNGRY, brain.state.emotion)

        val plugged = send(Event.Battery(level = 15, charging = true))
        assertTrue(Action.PlaySound(SoundCue.EAT) in plugged)
        assertEquals(Emotion.HAPPY, brain.state.emotion)
        assertTrue(brain.state.charging)
        assertTrue(brain.state.keepScreenOn)
    }

    @Test
    fun timerCountsDownRingsAndStopsOnTouch() {
        send(Event.StartTimer(60_000))
        assertEquals(Overlay.TIMER, brain.state.overlay)
        assertEquals(60_000L, brain.state.timerRemainingMillis)
        assertTrue(brain.state.keepScreenOn)

        advance(10_000)
        assertEquals(50_000L, brain.state.timerRemainingMillis)

        advance(50_000)
        assertTrue(brain.state.timerRinging)
        assertNull(brain.state.timerRemainingMillis)
        assertEquals(Emotion.LAUGHING, brain.state.emotion)

        send(Event.Tap)
        assertFalse(brain.state.timerRinging)
        assertEquals(Emotion.HAPPY, brain.state.emotion)
    }

    @Test
    fun timerGlancesBackAtTheFaceRegularly() {
        send(Event.StartTimer(5 * 60_000L))
        advance(Brain.TIMER_GLANCE_CYCLE_MILLIS - Brain.TIMER_GLANCE_MILLIS + 500)
        assertEquals(Overlay.NONE, brain.state.overlay)
        advance(Brain.TIMER_GLANCE_MILLIS)
        assertEquals(Overlay.TIMER, brain.state.overlay)
    }

    @Test
    fun timerKeepsHimAwake() {
        send(Event.StartTimer(10 * 60_000L))
        advance(Brain.SLEEP_AFTER_MILLIS + 1_000)
        assertFalse(brain.state.asleep)
    }

    @Test
    fun timerWakesHimUpWhenDone() {
        send(Event.StartTimer(1_000))
        send(Event.FaceDown)
        advance(1_100)
        assertFalse(brain.state.asleep)
        assertTrue(brain.state.timerRinging)
    }

    @Test
    fun cancelTimer() {
        send(Event.StartTimer(60_000))
        send(Event.CancelTimer)
        assertNull(brain.state.timerRemainingMillis)
        assertEquals(Overlay.NONE, brain.state.overlay)
    }

    @Test
    fun nightWindowCanWrapOrNot() {
        brain.settings = BrainSettings(nightStartHour = 22, nightEndHour = 7)
        assertTrue(brain.isNight(LocalDateTime.of(2026, 1, 1, 23, 0)))
        assertTrue(brain.isNight(LocalDateTime.of(2026, 1, 1, 3, 0)))
        assertFalse(brain.isNight(LocalDateTime.of(2026, 1, 1, 7, 0)))
        brain.settings = BrainSettings(nightStartHour = 1, nightEndHour = 6)
        assertTrue(brain.isNight(LocalDateTime.of(2026, 1, 1, 2, 0)))
        assertFalse(brain.isNight(LocalDateTime.of(2026, 1, 1, 23, 0)))
        brain.settings = BrainSettings(nightStartHour = 0, nightEndHour = 0)
        assertFalse(brain.isNight(LocalDateTime.of(2026, 1, 1, 2, 0)))
    }

    @Test
    fun actOnCommand() {
        val actions = send(Event.Act(Emotion.SAD))
        assertEquals(Emotion.SAD, brain.state.emotion)
        assertTrue(Action.PlaySound(SoundCue.SAD) in actions)
        advance(3_100)
        assertEquals(Emotion.CALM, brain.state.emotion)
    }

    @Test
    fun commandWakesHimUpToAct() {
        send(Event.Sleep)
        assertTrue(brain.state.asleep)
        send(Event.Act(Emotion.LAUGHING))
        assertFalse(brain.state.asleep)
        assertEquals(Emotion.LAUGHING, brain.state.emotion)
    }

    @Test
    fun sleepAndWakeCommands() {
        send(Event.Sleep)
        assertTrue(brain.state.asleep)
        advance(60_000)
        assertTrue(brain.state.asleep)
        send(Event.Wake)
        assertFalse(brain.state.asleep)
        send(Event.Wake)
        assertEquals(Emotion.HAPPY, brain.state.emotion)
    }

    @Test
    fun tellTimeCommand() {
        wall = LocalDateTime.of(2026, 10, 28, 9, 5, 0)
        val actions = send(Event.TellTime)
        assertEquals(Overlay.CLOCK, brain.state.overlay)
        assertTrue(Action.Speak("Il est 9 heures 5") in actions)
    }

    @Test
    fun happyToSeeYou() {
        val actions = send(Event.FaceSeen)
        assertEquals(Emotion.HAPPY, brain.state.emotion)
        assertTrue(Action.PlaySound(SoundCue.HAPPY) in actions)
    }

    @Test
    fun faceWakesHimDuringTheDayButNotAtNight() {
        advance(Brain.SLEEP_AFTER_MILLIS + 100)
        send(Event.FaceSeen)
        assertFalse(brain.state.asleep)

        brain = Brain(BrainSettings(), Random(1))
        wall = LocalDateTime.of(2026, 10, 28, 23, 0, 0)
        brain.tick(now())
        send(Event.FaceSeen)
        assertTrue(brain.state.asleep)
    }

    @Test
    fun handsUpThenShotCracksTheScreen() {
        val up = send(Event.HandsUp)
        assertEquals(Emotion.SCARED, brain.state.emotion)
        assertTrue(Action.Speak(Phrases.HANDS_UP) in up)

        val shot = send(Event.Shoot(0.2f, -0.3f))
        assertEquals(Emotion.KO, brain.state.emotion)
        val crack = brain.state.crack!!
        assertEquals(0.2f, crack.x)
        assertEquals(-0.3f, crack.y)
        assertTrue(Action.PlaySound(SoundCue.GLASS) in shot)
        assertTrue(Action.Vibrate(VibrationPattern.SHOT) in shot)

        advance(3_600)
        assertEquals(Emotion.DIZZY, brain.state.emotion)
        advance(Brain.CRACK_MILLIS)
        assertNull(brain.state.crack)
        assertEquals(Emotion.CALM, brain.state.emotion)
    }

    @Test
    fun shotWithoutAimPicksASpotOnScreen() {
        send(Event.Shoot())
        val crack = brain.state.crack!!
        assertTrue(crack.x in -1f..1f && crack.y in -1f..1f)
    }

    @Test
    fun newColorMakesHimHappy() {
        send(Event.NewColor)
        assertEquals(Emotion.HAPPY, brain.state.emotion)
    }

    @Test
    fun magicRevealAfterSuspense() {
        val card = Prediction.Card(12, Suit.HEARTS)
        val start = send(Event.Reveal(card))
        assertTrue(Action.Speak(Phrases.THINKING) in start)
        assertTrue(Action.PlaySound(SoundCue.DRUMROLL) in start)
        assertEquals(Emotion.CURIOUS, brain.state.emotion)
        assertEquals(Overlay.NONE, brain.state.overlay)

        val spoken = mutableListOf<Action>()
        repeat(31) {
            millis += 100
            wall = wall.plusNanos(100_000_000)
            spoken += brain.tick(now())
        }
        assertTrue(Action.Speak("Ta carte est la Dame de cœur !") in spoken)
        assertEquals(Overlay.PREDICTION, brain.state.overlay)
        assertEquals(card, brain.state.revealed)

        advance(Brain.REVEAL_MILLIS + 100)
        assertEquals(Overlay.NONE, brain.state.overlay)
        assertNull(brain.state.revealed)
    }

    @Test
    fun revealWithoutPredictionGivesNothingAway() {
        val actions = send(Event.Reveal(null))
        assertTrue(Action.Speak(Phrases.NO_PREDICTION) in actions)
        advance(4_000)
        assertNull(brain.state.revealed)
    }
}
