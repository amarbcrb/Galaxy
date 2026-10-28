package galaxy.core.input

import kotlin.math.abs
import kotlin.math.sqrt

enum class Motion { SHAKE, FACE_DOWN, FLAT_STILL, PICKED_UP }

/**
 * Reads accelerometer samples (m/s², device axes, as Android reports them) and detects
 * shakes, the phone resting flat or face down, and being picked up again.
 */
class MotionDetector(
    private val flatStillMillis: Long = 60_000,
    private val faceDownMillis: Long = 1_000,
) {
    private enum class Pose { FACE_UP, FACE_DOWN, OTHER }
    private enum class Rest { NONE, FLAT, FACE_DOWN }

    var gravityX = 0f
        private set
    var gravityY = 0f
        private set
    var gravityZ = 0f
        private set
    private var initialized = false

    private val spikes = ArrayDeque<Long>()
    private var lastSpike = Long.MIN_VALUE / 2
    private var lastShake = Long.MIN_VALUE / 2

    private var pose = Pose.OTHER
    private var poseSince = 0L
    private var stillSince = 0L
    private var rest = Rest.NONE

    fun onSample(x: Float, y: Float, z: Float, timeMillis: Long): List<Motion> {
        if (!initialized) {
            gravityX = x
            gravityY = y
            gravityZ = z
            poseSince = timeMillis
            stillSince = timeMillis
            initialized = true
        } else {
            gravityX += (x - gravityX) * ALPHA
            gravityY += (y - gravityY) * ALPHA
            gravityZ += (z - gravityZ) * ALPHA
        }

        val out = mutableListOf<Motion>()
        val deviation = abs(sqrt(x * x + y * y + z * z) - GRAVITY)

        // Shake: several strong jolts within a second.
        if (deviation > SHAKE_JOLT && timeMillis - lastSpike > 80) {
            spikes.addLast(timeMillis)
            lastSpike = timeMillis
        }
        while (spikes.isNotEmpty() && timeMillis - spikes.first() > 1_000) spikes.removeFirst()
        if (spikes.size >= 3 && timeMillis - lastShake > 2_000) {
            out += Motion.SHAKE
            lastShake = timeMillis
            spikes.clear()
        }

        val newPose = when {
            gravityZ > FLAT_Z -> Pose.FACE_UP
            gravityZ < -FLAT_Z -> Pose.FACE_DOWN
            else -> Pose.OTHER
        }
        if (newPose != pose) {
            pose = newPose
            poseSince = timeMillis
        }
        if (deviation > STILL_DEVIATION) stillSince = timeMillis

        if (rest != Rest.NONE) {
            val lifted = deviation > LIFT_DEVIATION ||
                (rest == Rest.FLAT && pose != Pose.FACE_UP) ||
                (rest == Rest.FACE_DOWN && pose != Pose.FACE_DOWN)
            if (lifted) {
                rest = Rest.NONE
                stillSince = timeMillis
                out += Motion.PICKED_UP
            }
        } else if (pose == Pose.FACE_DOWN && timeMillis - poseSince >= faceDownMillis) {
            rest = Rest.FACE_DOWN
            out += Motion.FACE_DOWN
        } else if (pose == Pose.FACE_UP &&
            timeMillis - maxOf(poseSince, stillSince) >= flatStillMillis
        ) {
            rest = Rest.FLAT
            out += Motion.FLAT_STILL
        }
        return out
    }

    companion object {
        const val GRAVITY = 9.81f
        private const val ALPHA = 0.1f
        private const val SHAKE_JOLT = 7f
        private const val STILL_DEVIATION = 0.6f
        private const val LIFT_DEVIATION = 2f
        private const val FLAT_Z = 9.0f
    }
}
