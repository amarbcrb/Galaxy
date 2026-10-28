package galaxy.core.input

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign

enum class Gesture { TAP, DOUBLE_TAP, LONG_PRESS, PET, SWIPE_UP }

/**
 * Turns raw touch points of a single finger into gestures.
 *
 * Positions are in pixels, times in milliseconds. Call [onHold] regularly while a finger
 * is down so a long press fires without waiting for the finger to lift.
 */
class GestureClassifier(
    private val longPressMillis: Long = 600,
    private val doubleTapMillis: Long = 300,
) {
    private var width = 1f
    private var height = 1f
    private val minDim get() = min(width, height)

    private var down = false
    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var longPressFired = false
    private val strokeX = Stroke()
    private val strokeY = Stroke()

    private var lastTapTime = -1L
    private var lastTapX = 0f
    private var lastTapY = 0f

    fun onDown(x: Float, y: Float, timeMillis: Long, width: Float, height: Float): List<Gesture> {
        this.width = width
        this.height = height
        down = true
        startX = x
        startY = y
        startTime = timeMillis
        lastX = x
        lastY = y
        moved = false
        longPressFired = false
        strokeX.reset()
        strokeY.reset()
        return emptyList()
    }

    fun onMove(x: Float, y: Float, timeMillis: Long): List<Gesture> {
        if (!down) return emptyList()
        val out = mutableListOf<Gesture>()
        if (hypot(x - startX, y - startY) > SLOP * minDim) moved = true
        val threshold = PET_STROKE * minDim
        if (strokeX.add(x - lastX, threshold)) out += Gesture.PET
        if (strokeY.add(y - lastY, threshold)) out += Gesture.PET
        lastX = x
        lastY = y
        return out.distinct()
    }

    fun onHold(timeMillis: Long): List<Gesture> {
        if (!down || moved || longPressFired) return emptyList()
        if (timeMillis - startTime < longPressMillis) return emptyList()
        longPressFired = true
        lastTapTime = -1
        return listOf(Gesture.LONG_PRESS)
    }

    fun onUp(x: Float, y: Float, timeMillis: Long): List<Gesture> {
        if (!down) return emptyList()
        val out = onMove(x, y, timeMillis).toMutableList()
        down = false

        val rise = startY - y
        if (rise > SWIPE_UP * height && abs(x - startX) < rise && strokeY.reversals == 0) {
            return listOf(Gesture.SWIPE_UP)
        }
        if (moved || longPressFired) return out
        if (timeMillis - startTime >= longPressMillis) return listOf(Gesture.LONG_PRESS)

        out += Gesture.TAP
        val isDouble = lastTapTime >= 0 &&
            timeMillis - lastTapTime <= doubleTapMillis &&
            hypot(x - lastTapX, y - lastTapY) < DOUBLE_TAP_DISTANCE * minDim
        if (isDouble) {
            out += Gesture.DOUBLE_TAP
            lastTapTime = -1
        } else {
            lastTapTime = timeMillis
            lastTapX = x
            lastTapY = y
        }
        return out
    }

    /**
     * One axis of a back-and-forth motion. A pet is counted for each stroke after the first
     * direction change, so a single straight swipe is not a pet.
     */
    private class Stroke {
        var reversals = 0
        private var direction = 0f
        private var length = 0f
        private var counted = false

        fun reset() {
            reversals = 0
            direction = 0f
            length = 0f
            counted = false
        }

        fun add(delta: Float, threshold: Float): Boolean {
            val dir = sign(delta)
            if (dir == 0f) return false
            if (dir != direction) {
                // A tiny wobble in the other direction is not a new stroke.
                if (direction != 0f && length >= threshold) reversals++
                if (direction == 0f || length >= threshold) {
                    direction = dir
                    length = 0f
                    counted = false
                }
            }
            length += abs(delta)
            if (!counted && reversals > 0 && length >= threshold) {
                counted = true
                return true
            }
            return false
        }
    }

    companion object {
        /** Movement beyond this fraction of the short screen side is no longer a tap. */
        const val SLOP = 0.03f
        const val PET_STROKE = 0.06f
        const val SWIPE_UP = 0.35f
        const val DOUBLE_TAP_DISTANCE = 0.1f
    }
}
