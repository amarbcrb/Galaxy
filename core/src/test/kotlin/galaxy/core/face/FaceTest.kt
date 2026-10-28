package galaxy.core.face

import galaxy.core.Emotion
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceTest {
    @Test
    fun everyEmotionHasDistinctLook() {
        val specs = Emotion.entries.map { Expressions.of(it) }
        assertEquals(specs.size, specs.toSet().size)
    }

    @Test
    fun calmIsCyan() {
        assertEquals(Expressions.CYAN, Expressions.of(Emotion.CALM).color)
    }

    @Test
    fun lerpEndpointsAndMiddle() {
        val a = Expressions.of(Emotion.CALM)
        val b = Expressions.of(Emotion.LOVE)
        assertEquals(a, FaceSpec.lerp(a, b, 0f))
        assertEquals(b, FaceSpec.lerp(a, b, 1f))
        val mid = FaceSpec.lerp(a, b, 0.5f)
        assertEquals(0.5f, mid.left.heart, 1e-6f)
        assertEquals((a.left.width + b.left.width) / 2, mid.left.width, 1e-6f)
    }

    @Test
    fun lerpColorPerChannel() {
        assertEquals(0xFF808080L, lerpColor(0xFF000000L, 0xFFFFFFFFL, 0.5f))
        assertEquals(0xFFFF0000L, lerpColor(0xFFFF0000L, 0xFF00FF00L, 0f))
        assertEquals(0xFF00FF00L, lerpColor(0xFFFF0000L, 0xFF00FF00L, 1f))
    }

    @Test
    fun blinksRegularlyAndBriefly() {
        val blinker = Blinker(Random(3))
        var closedFrames = 0
        var blinks = 0
        var wasClosed = false
        for (t in 0L..60_000L step 10) {
            val open = blinker.openness(t)
            assertTrue(open in 0f..1f)
            val closed = open < 0.5f
            if (closed) closedFrames++
            if (closed && !wasClosed) blinks++
            wasClosed = closed
        }
        assertTrue(blinks in 8..40, "blinks=$blinks")
        // Eyes are open the vast majority of the time.
        assertTrue(closedFrames < 6_000 / 10, "closed=$closedFrames")
    }

    @Test
    fun gazeFollowsFingerThenTiltThenWanders() {
        val gaze = Gaze(Random(5))
        assertEquals(Look(0.5f, -1f), gaze.target(0, Look(0.5f, -2f), Look(0.1f, 0.1f), 0f, awake = true))
        assertEquals(Look(0.1f, 0.1f), gaze.target(0, null, Look(0.1f, 0.1f), -0.8f, awake = true))
        assertEquals(Look(-0.8f, 0f), gaze.target(0, null, null, -0.8f, awake = true))
        assertEquals(Look.CENTER, gaze.target(0, Look(1f, 1f), null, 0f, awake = false))
        val looks = (0L..60_000L step 500).map { gaze.target(it, null, null, 0f, awake = true) }.toSet()
        assertTrue(looks.size > 3)
        assertTrue(looks.all { it.x in -1f..1f && it.y in -0.6f..0.6f })
    }

    @Test
    fun facePresenceSmoothsAndAnnouncesArrivals() {
        val presence = FacePresence(absenceMillis = 20_000, lostMillis = 1_500)
        assertTrue(presence.onFrame(Look(0.4f, 0f), 0))
        assertEquals(Look(0.4f, 0f), presence.look)
        assertTrue(!presence.onFrame(Look(0f, 0f), 200))
        assertEquals(0.2f, presence.look!!.x, 1e-6f)
        presence.onFrame(null, 1_000)
        assertTrue(presence.look != null)
        presence.onFrame(null, 2_000)
        assertEquals(null, presence.look)
        assertTrue(!presence.onFrame(Look(0f, 0f), 10_000))
        assertTrue(presence.onFrame(Look(0f, 0f), 40_000))
    }

    @Test
    fun chosenEyeColorTintsCalmLooksOnly() {
        val red = 0xFFFF4A4AL
        assertEquals(red, Expressions.of(Emotion.CALM, red).color)
        assertEquals(red, Expressions.of(Emotion.CURIOUS, red).color)
        assertEquals(Expressions.of(Emotion.LOVE).color, Expressions.of(Emotion.LOVE, red).color)
    }

    @Test
    fun eyeColorsCycle() {
        assertEquals(EyeColors.all[1].color, EyeColors.next(null))
        assertEquals(EyeColors.all[0].color, EyeColors.next(EyeColors.all.last().color))
    }

    @Test
    fun crackPatternStartsAtImpactAndIsStable() {
        val a = CrackPattern.generate(seed = 7, impactX = 300f, impactY = 500f, width = 1080f, height = 2400f)
        val b = CrackPattern.generate(seed = 7, impactX = 300f, impactY = 500f, width = 1080f, height = 2400f)
        assertEquals(a, b)
        assertTrue(a.size > 30, "segments=${a.size}")
        assertTrue(a.count { it.x1 == 300f && it.y1 == 500f } >= 9)
        assertTrue(a.all { it.width in 1f..4f })
    }
}
