package galaxy.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sin

/** Little bouncing sound bars: Galaxy is listening to you. */
@Composable
fun ListeningBars(color: Color, modifier: Modifier = Modifier) {
    val t by produceState(0L) {
        while (true) withFrameMillis { value = it }
    }
    Canvas(modifier.size(72.dp, 36.dp)) {
        val bars = 5
        val gap = size.width / bars
        for (i in 0 until bars) {
            val level = 0.25f + 0.75f * abs(sin(t / 140.0 + i * 0.9)).toFloat()
            val x = gap * (i + 0.5f)
            val half = size.height * level / 2
            drawLine(
                color,
                Offset(x, size.height / 2 - half),
                Offset(x, size.height / 2 + half),
                strokeWidth = gap * 0.45f,
                cap = StrokeCap.Round,
            )
        }
    }
}
