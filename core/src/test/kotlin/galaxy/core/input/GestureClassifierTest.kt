package galaxy.core.input

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GestureClassifierTest {
    private val w = 2000f
    private val h = 1000f
    private lateinit var g: GestureClassifier

    @BeforeTest
    fun setUp() {
        g = GestureClassifier()
    }

    private fun tap(x: Float, y: Float, at: Long): List<Gesture> {
        g.onDown(x, y, at, w, h)
        return g.onUp(x, y, at + 80)
    }

    @Test
    fun singleTap() {
        assertEquals(listOf(Gesture.TAP), tap(500f, 500f, 0))
    }

    @Test
    fun doubleTap() {
        tap(500f, 500f, 0)
        assertEquals(listOf(Gesture.TAP, Gesture.DOUBLE_TAP), tap(510f, 505f, 250))
    }

    @Test
    fun slowSecondTapIsNotDouble() {
        tap(500f, 500f, 0)
        assertEquals(listOf(Gesture.TAP), tap(500f, 500f, 600))
    }

    @Test
    fun tripleTapGivesOnlyOneDouble() {
        tap(500f, 500f, 0)
        tap(500f, 500f, 200)
        assertEquals(listOf(Gesture.TAP), tap(500f, 500f, 400))
    }

    @Test
    fun longPressFiresWhileHolding() {
        g.onDown(500f, 500f, 0, w, h)
        assertTrue(g.onHold(300).isEmpty())
        assertEquals(listOf(Gesture.LONG_PRESS), g.onHold(650))
        assertTrue(g.onHold(900).isEmpty())
        assertTrue(g.onUp(500f, 500f, 1_000).isEmpty())
    }

    @Test
    fun backAndForthIsPetting() {
        g.onDown(800f, 500f, 0, w, h)
        val out = mutableListOf<Gesture>()
        var t = 0L
        var x = 800f
        repeat(4) { stroke ->
            val dir = if (stroke % 2 == 0) 1 else -1
            repeat(10) {
                x += dir * 15f
                t += 16
                out += g.onMove(x, 500f, t)
            }
        }
        out += g.onUp(x, 500f, t + 16)
        assertEquals(3, out.count { it == Gesture.PET })
        assertTrue(Gesture.TAP !in out)
    }

    @Test
    fun singleSwipeIsNotPetting() {
        g.onDown(200f, 500f, 0, w, h)
        val out = (1..20).flatMap { g.onMove(200f + it * 30f, 500f, it * 16L) }
        assertTrue(out.isEmpty())
    }

    @Test
    fun swipeUpOpensMenu() {
        g.onDown(1000f, 900f, 0, w, h)
        (1..10).forEach { g.onMove(1000f + it, 900f - it * 50f, it * 16L) }
        assertEquals(listOf(Gesture.SWIPE_UP), g.onUp(1010f, 400f, 200))
    }

    @Test
    fun shortSwipeUpIsNothing() {
        g.onDown(1000f, 900f, 0, w, h)
        assertTrue(g.onUp(1000f, 800f, 100).isEmpty())
    }

    @Test
    fun twoFingerHoldOpensHiddenMenuAndIsNotATap() {
        g.onDown(500f, 500f, 0, w, h)
        g.onPointerCount(2, 50)
        assertTrue(g.onHold(500).isEmpty())
        assertEquals(listOf(Gesture.TWO_FINGER_HOLD), g.onHold(900))
        assertTrue(g.onHold(1_200).isEmpty())
        assertTrue(g.onUp(500f, 500f, 1_300).isEmpty())
    }

    @Test
    fun briefSecondFingerDoesNothing() {
        g.onDown(500f, 500f, 0, w, h)
        g.onPointerCount(2, 50)
        g.onPointerCount(1, 200)
        assertTrue(g.onHold(1_000).isEmpty())
        assertTrue(g.onUp(500f, 500f, 1_100).isEmpty())
    }
}
