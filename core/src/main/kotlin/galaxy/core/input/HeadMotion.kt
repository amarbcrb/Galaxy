package galaxy.core.input

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/**
 * Turns the phone's rotation speed (gyroscope) into a head turn: when the phone turns quickly,
 * the face lags behind and keeps looking at you, then catches up. Spinning the phone around
 * makes Galaxy dizzy.
 *
 * Rates are in rad/s, already expressed in screen axes (see [ScreenAxes]).
 */
class HeadMotion {
    /** -1 (turned left) .. 1 (turned right). */
    var yaw = 0f
        private set

    /** -1 (looking up) .. 1 (looking down). */
    var pitch = 0f
        private set

    private var lastTime = -1L
    private val turns = ArrayDeque<Pair<Long, Float>>()
    private var lastSpin = Long.MIN_VALUE / 2

    /**
     * @param yawRate rotation around the screen's vertical axis, counterclockwise seen from above.
     * @param pitchRate rotation around the screen's horizontal axis, positive tips the top towards you.
     * @return true when the phone was just spun around.
     */
    fun onGyro(yawRate: Float, pitchRate: Float, timeMillis: Long): Boolean {
        val dt = if (lastTime < 0) 0f else ((timeMillis - lastTime) / 1000f).coerceIn(0f, 0.1f)
        lastTime = timeMillis

        // Ease back to the center, like a head catching up with the body.
        val decay = exp(-dt / SETTLE_SECONDS)
        yaw *= decay
        pitch *= decay
        // Turning right makes the world (and you) slide left on screen: the eyes follow you there.
        if (abs(yawRate) > DEAD_ZONE) yaw += yawRate * dt * GAIN
        if (abs(pitchRate) > DEAD_ZONE) pitch -= pitchRate * dt * GAIN
        yaw = yaw.coerceIn(-1f, 1f)
        pitch = pitch.coerceIn(-1f, 1f)

        turns.addLast(timeMillis to abs(yawRate) * dt)
        while (turns.isNotEmpty() && timeMillis - turns.first().first > SPIN_WINDOW_MILLIS) turns.removeFirst()
        if (turns.sumOf { it.second.toDouble() } > SPIN_ANGLE && timeMillis - lastSpin > 2_000) {
            lastSpin = timeMillis
            turns.clear()
            return true
        }
        return false
    }

    companion object {
        private const val DEAD_ZONE = 0.6f
        private const val GAIN = 0.8f
        private const val SETTLE_SECONDS = 0.35f
        private const val SPIN_WINDOW_MILLIS = 1_500L
        private const val SPIN_ANGLE = 1.6 * PI
    }
}

/** Converts a vector from device axes to screen axes (x right, y up) for the current display rotation. */
object ScreenAxes {
    /** [quarterTurns] is the display rotation: 0, 1 (90°), 2 (180°) or 3 (270°). */
    fun remap(x: Float, y: Float, quarterTurns: Int): Pair<Float, Float> = when (quarterTurns and 3) {
        1 -> -y to x
        2 -> -x to -y
        3 -> y to -x
        else -> x to y
    }
}
