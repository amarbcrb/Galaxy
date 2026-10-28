package galaxy.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlin.math.min

/** Big glowing digits shown in place of the eyes. */
@Composable
fun Digits(text: String, color: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val heightDp = min(maxHeight.value * 0.32f, maxWidth.value / (text.length * 0.65f))
        val fontSize = with(LocalDensity.current) { heightDp.dp.toSp() }
        Text(
            text = text,
            color = color,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                shadow = Shadow(color = color, blurRadius = 40f),
            ),
        )
    }
}

@Composable
fun ClockDigits(color: Color) {
    val time by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(200)
        }
    }
    Digits("%02d:%02d:%02d".format(time.hour, time.minute, time.second), color)
}

fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
