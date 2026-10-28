package galaxy.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import galaxy.core.brain.Brain
import galaxy.core.brain.Crack
import galaxy.core.face.CrackPattern
import galaxy.core.face.Segment

private const val FADE_MILLIS = 800L
private const val FLASH_MILLIS = 120L

/** A fake broken screen over everything, after Galaxy gets "shot". Fades away by itself. */
@Composable
fun CrackOverlay(crack: Crack, modifier: Modifier = Modifier) {
    val shownAt by produceState(0L, crack) { withFrameMillis { value = it } }
    val now by produceState(0L, crack) {
        while (true) withFrameMillis { value = it }
    }
    val cache = remember(crack) { arrayOfNulls<List<Segment>>(1) }

    Canvas(modifier.fillMaxSize()) {
        val segments = cache[0] ?: CrackPattern.generate(
            seed = crack.seed,
            impactX = (crack.x + 1f) / 2f * size.width,
            impactY = (crack.y + 1f) / 2f * size.height,
            width = size.width,
            height = size.height,
        ).also { cache[0] = it }

        val age = if (shownAt == 0L) 0L else now - shownAt
        val remaining = Brain.CRACK_MILLIS - age
        val alpha = (remaining.toFloat() / FADE_MILLIS).coerceIn(0f, 1f)
        if (age < FLASH_MILLIS) drawRect(Color.White.copy(alpha = 0.6f * (1f - age.toFloat() / FLASH_MILLIS)))

        val impact = Offset((crack.x + 1f) / 2f * size.width, (crack.y + 1f) / 2f * size.height)
        // A small shattered spot where the "bullet" hit.
        drawCircle(Color.White.copy(alpha = 0.25f * alpha), radius = 0.03f * size.minDimension, center = impact)
        for (s in segments) {
            val a = Offset(s.x1, s.y1)
            val b = Offset(s.x2, s.y2)
            // A dark edge under each white line gives the glass some depth.
            drawLine(Color.Black.copy(alpha = 0.6f * alpha), a + Offset(1.5f, 1.5f), b + Offset(1.5f, 1.5f), s.width + 1f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.85f * alpha), a, b, s.width, StrokeCap.Round)
        }
    }
}
