package galaxy.core.face

import kotlin.math.abs
import kotlin.random.Random

/** Decides when Galaxy blinks. Returns how open the eyes are, 1 = open, 0 = closed. */
class Blinker(private val random: Random = Random.Default) {
    private var nextBlinkAt = -1L
    private var blinkStart = -1L

    fun openness(nowMillis: Long): Float {
        if (nextBlinkAt < 0) nextBlinkAt = nowMillis + random.nextLong(2_000, 5_000)

        if (blinkStart < 0 && nowMillis >= nextBlinkAt) blinkStart = nowMillis

        if (blinkStart >= 0) {
            val phase = nowMillis - blinkStart
            if (phase < BLINK_MILLIS) {
                val half = BLINK_MILLIS / 2f
                return abs(phase - half) / half
            }
            blinkStart = -1
            // Sometimes a quick double blink, otherwise a pause.
            nextBlinkAt = if (random.nextFloat() < 0.2f) {
                nowMillis + 120
            } else {
                nowMillis + random.nextLong(2_000, 6_000)
            }
        }
        return 1f
    }

    companion object {
        const val BLINK_MILLIS = 160L
    }
}
