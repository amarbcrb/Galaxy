package galaxy.core.face

import kotlin.math.abs
import kotlin.random.Random

/** A direction to look at. x and y are in -1..1, positive x is right, positive y is down. */
data class Look(val x: Float, val y: Float) {
    companion object {
        val CENTER = Look(0f, 0f)
    }
}

/**
 * Where the eyes look: at the finger when touched, down the slope when the phone is tilted,
 * otherwise wandering around on their own.
 */
class Gaze(private val random: Random = Random.Default) {
    private var wander = Look.CENTER
    private var nextMoveAt = -1L

    fun target(nowMillis: Long, finger: Look?, tiltX: Float, awake: Boolean): Look {
        if (!awake) return Look.CENTER
        if (finger != null) return Look(finger.x.coerceIn(-1f, 1f), finger.y.coerceIn(-1f, 1f))
        if (abs(tiltX) > TILT_THRESHOLD) return Look(tiltX.coerceIn(-1f, 1f), 0f)

        if (nowMillis >= nextMoveAt) {
            wander = if (random.nextFloat() < 0.5f) {
                Look.CENTER
            } else {
                Look(random.nextFloat() * 2f - 1f, (random.nextFloat() * 2f - 1f) * 0.6f)
            }
            nextMoveAt = nowMillis + random.nextLong(1_200, 4_000)
        }
        return wander
    }

    companion object {
        const val TILT_THRESHOLD = 0.25f
    }
}
