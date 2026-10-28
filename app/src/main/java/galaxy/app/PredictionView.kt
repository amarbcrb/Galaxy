package galaxy.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import galaxy.core.brain.Prediction
import kotlin.math.min

private val CARD_RED = Color(0xFFD32F2F)

/** The magic trick's reveal: a playing card (or a number) popping up on screen. */
@Composable
fun PredictionView(prediction: Prediction, glow: Color) {
    val appear = remember(prediction) { Animatable(0f) }
    LaunchedEffect(prediction) {
        appear.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow))
    }
    val pop = Modifier.graphicsLayer {
        val v = appear.value
        scaleX = 0.3f + 0.7f * v
        scaleY = 0.3f + 0.7f * v
        rotationY = (1f - v) * 180f
        alpha = v.coerceIn(0f, 1f)
    }
    when (prediction) {
        is Prediction.Card -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val height = min(maxHeight.value * 0.8f, maxWidth.value * 0.8f * 3.5f / 2.5f).dp
            PlayingCard(prediction, height, glow, pop)
        }
        is Prediction.Number -> Box(pop) { Digits(prediction.value.toString(), glow) }
    }
}

@Composable
private fun PlayingCard(card: Prediction.Card, height: Dp, glow: Color, modifier: Modifier) {
    val width = height * 2.5f / 3.5f
    val ink = if (card.suit.red) CARD_RED else Color.Black
    val fontScale = with(LocalDensity.current) { 1.dp.toSp() }
    val shape = RoundedCornerShape(height * 0.06f)
    Box(
        modifier
            .size(width, height)
            .shadow(24.dp, shape, ambientColor = glow, spotColor = glow)
            .background(Color.White, shape)
            .border(3.dp, glow, shape),
    ) {
        Corner(card, ink, fontScale * height.value * 0.1f, Modifier.align(Alignment.TopStart).padding(height * 0.04f))
        Corner(
            card,
            ink,
            fontScale * height.value * 0.1f,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(height * 0.04f)
                .graphicsLayer { rotationZ = 180f },
        )
        Text(
            card.suit.symbol,
            color = ink,
            fontSize = fontScale * height.value * 0.4f,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun Corner(card: Prediction.Card, ink: Color, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(card.rankLabel, color = ink, fontSize = size, fontWeight = FontWeight.Bold)
        Text(card.suit.symbol, color = ink, fontSize = size)
    }
}
