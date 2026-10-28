package galaxy.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadMotionTest {
    @Test
    fun quickTurnRightMakesTheFaceLagLeftThenRecenter() {
        val head = HeadMotion()
        var t = 0L
        repeat(15) { head.onGyro(yawRate = -4f, pitchRate = 0f, timeMillis = t); t += 20 }
        assertTrue(head.yaw < -0.3f, "yaw=${head.yaw}")
        repeat(100) { head.onGyro(0f, 0f, t); t += 20 }
        assertEquals(0f, head.yaw, 0.01f)
    }

    @Test
    fun tippingTheTopAwayLooksDown() {
        val head = HeadMotion()
        var t = 0L
        repeat(15) { head.onGyro(0f, pitchRate = -4f, timeMillis = t); t += 20 }
        assertTrue(head.pitch > 0.3f, "pitch=${head.pitch}")
    }

    @Test
    fun handTremorIsIgnored() {
        val head = HeadMotion()
        var t = 0L
        repeat(200) { head.onGyro(if (it % 2 == 0) 0.4f else -0.4f, 0.3f, t); t += 20 }
        assertEquals(0f, head.yaw, 1e-6f)
        assertEquals(0f, head.pitch, 1e-6f)
    }

    @Test
    fun spinningAroundIsDetectedOnce() {
        val head = HeadMotion()
        var t = 0L
        var spins = 0
        repeat(60) { if (head.onGyro(8f, 0f, t)) spins++; t += 20 }
        assertEquals(1, spins)
    }

    @Test
    fun screenAxesFollowDisplayRotation() {
        assertEquals(1f to 2f, ScreenAxes.remap(1f, 2f, 0))
        assertEquals(-2f to 1f, ScreenAxes.remap(1f, 2f, 1))
        assertEquals(-1f to -2f, ScreenAxes.remap(1f, 2f, 2))
        assertEquals(2f to -1f, ScreenAxes.remap(1f, 2f, 3))
    }
}
