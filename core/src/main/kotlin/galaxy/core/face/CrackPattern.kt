package galaxy.core.face

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/** One line of a crack, in pixels. */
data class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val width: Float)

/** Draws a "broken screen": jagged cracks radiating from an impact point, with rings around it. */
object CrackPattern {
    fun generate(seed: Int, impactX: Float, impactY: Float, width: Float, height: Float): List<Segment> {
        val random = Random(seed)
        val diagonal = hypot(width, height)
        val out = mutableListOf<Segment>()
        val rays = 9 + random.nextInt(5)
        val ringPoints = List(3) { mutableListOf<Pair<Float, Float>>() }
        val ringRadii = listOf(0.04f, 0.09f, 0.16f).map { it * diagonal * (0.8f + random.nextFloat() * 0.4f) }

        for (i in 0 until rays) {
            var angle = (2 * PI * i / rays + random.nextDouble(-0.25, 0.25)).toFloat()
            var x = impactX
            var y = impactY
            var travelled = 0f
            val maxLength = diagonal * (0.35f + random.nextFloat() * 0.6f)
            var ring = 0
            while (travelled < maxLength && x in -50f..width + 50f && y in -50f..height + 50f) {
                val step = diagonal * (0.03f + random.nextFloat() * 0.05f)
                angle += random.nextDouble(-0.35, 0.35).toFloat()
                val nx = x + cos(angle) * step
                val ny = y + sin(angle) * step
                // Cracks get thinner away from the impact.
                val lineWidth = (4f * (1f - travelled / maxLength)).coerceAtLeast(1f)
                out += Segment(x, y, nx, ny, lineWidth)
                travelled += step
                while (ring < ringRadii.size && travelled >= ringRadii[ring]) {
                    ringPoints[ring] += nx to ny
                    ring++
                }
                if (random.nextFloat() < 0.25f) out += branch(random, nx, ny, angle, diagonal)
                x = nx
                y = ny
            }
        }
        // Rings join neighboring cracks around the impact, like shattered glass.
        for (points in ringPoints) {
            for (j in points.indices) {
                if (random.nextFloat() < 0.2f) continue
                val (ax, ay) = points[j]
                val (bx, by) = points[(j + 1) % points.size]
                out += Segment(ax, ay, bx, by, 1.5f)
            }
        }
        return out
    }

    private fun branch(random: Random, startX: Float, startY: Float, parentAngle: Float, diagonal: Float): List<Segment> {
        var angle = parentAngle + (if (random.nextBoolean()) 1 else -1) * (0.5f + random.nextFloat() * 0.6f)
        var x = startX
        var y = startY
        return List(2 + random.nextInt(3)) {
            val step = diagonal * (0.015f + random.nextFloat() * 0.03f)
            angle += random.nextDouble(-0.3, 0.3).toFloat()
            val nx = x + cos(angle) * step
            val ny = y + sin(angle) * step
            Segment(x, y, nx, ny, 1f).also {
                x = nx
                y = ny
            }
        }
    }
}
