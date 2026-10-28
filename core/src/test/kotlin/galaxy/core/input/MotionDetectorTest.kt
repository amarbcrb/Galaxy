package galaxy.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionDetectorTest {
    private val g = MotionDetector.GRAVITY

    /** Feeds samples every 20 ms (50 Hz) and collects detected motions. */
    private fun MotionDetector.feed(
        fromMillis: Long,
        durationMillis: Long,
        sample: (Long) -> Triple<Float, Float, Float>,
    ): List<Motion> {
        val out = mutableListOf<Motion>()
        var t = fromMillis
        while (t < fromMillis + durationMillis) {
            val (x, y, z) = sample(t)
            out += onSample(x, y, z, t)
            t += 20
        }
        return out
    }

    private val upright = Triple(0f, g, 0f)
    private val flat = Triple(0f, 0f, g)
    private val faceDown = Triple(0f, 0f, -g)

    @Test
    fun shakeIsDetectedOnce() {
        val d = MotionDetector()
        d.feed(0, 1_000) { upright }
        val out = d.feed(1_000, 600) { t -> if ((t / 100) % 2 == 0L) Triple(15f, g, 0f) else Triple(-15f, g, 0f) }
        assertEquals(listOf(Motion.SHAKE), out)
    }

    @Test
    fun gentleHandlingIsNotAShake() {
        val d = MotionDetector()
        val out = d.feed(0, 3_000) { t -> Triple(if ((t / 200) % 2 == 0L) 2f else -2f, g, 0f) }
        assertTrue(out.isEmpty())
    }

    @Test
    fun flatAndStillForAMinuteThenPickedUp() {
        val d = MotionDetector(flatStillMillis = 60_000)
        assertTrue(d.feed(0, 59_000) { flat }.isEmpty())
        assertEquals(listOf(Motion.FLAT_STILL), d.feed(59_000, 3_000) { flat })
        assertTrue(d.feed(62_000, 5_000) { flat }.isEmpty())
        assertEquals(listOf(Motion.PICKED_UP), d.feed(67_000, 200) { Triple(3f, 4f, g + 3f) })
    }

    @Test
    fun faceDownThenPickedUp() {
        val d = MotionDetector()
        d.feed(0, 500) { upright }
        val down = d.feed(500, 3_000) { faceDown }
        assertEquals(listOf(Motion.FACE_DOWN), down)
        val up = d.feed(3_500, 2_000) { upright }
        assertEquals(listOf(Motion.PICKED_UP), up)
    }

    @Test
    fun holdingUprightNeverRests() {
        val d = MotionDetector(flatStillMillis = 5_000)
        assertTrue(d.feed(0, 20_000) { upright }.isEmpty())
    }
}
