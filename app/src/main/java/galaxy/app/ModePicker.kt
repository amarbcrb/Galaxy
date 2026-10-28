package galaxy.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import galaxy.core.Emotion
import galaxy.core.face.Look
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** One entry of the double-tap carousel: a face to wear, or the clock when [emotion] is null. */
data class Mode(val label: String, val emotion: Emotion?)

val MODES = listOf(
    Mode("Calme", Emotion.CALM),
    Mode("Content", Emotion.HAPPY),
    Mode("Rieur", Emotion.LAUGHING),
    Mode("Amoureux", Emotion.LOVE),
    Mode("Bisou", Emotion.KISS),
    Mode("Curieux", Emotion.CURIOUS),
    Mode("Surpris", Emotion.SURPRISED),
    Mode("Fâché", Emotion.ANGRY),
    Mode("Triste", Emotion.SAD),
    Mode("Fatigué", Emotion.SLEEPY),
    Mode("Faim", Emotion.HUNGRY),
    Mode("Tournis", Emotion.DIZZY),
    Mode("Peur", Emotion.SCARED),
    Mode("Heure", null),
)

/**
 * Double-tap carousel: swipe sideways through Galaxy's faces; the one in the middle is applied
 * right away. Touching anywhere else closes it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModePicker(current: Emotion, eyeColor: Long?, onSelect: (Mode) -> Unit, onClose: () -> Unit) {
    val start = MODES.indexOfFirst { it.emotion == current }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start) { MODES.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { onSelect(MODES[it]) }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        val itemWidth = 120.dp
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .padding(top = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = (maxWidth - itemWidth) / 2),
                pageSize = PageSize.Fixed(itemWidth),
                pageSpacing = 12.dp,
            ) { page ->
                val mode = MODES[page]
                val selected = page == pager.currentPage
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier
                        .width(itemWidth)
                        .clickable { scope.launch { pager.animateScrollToPage(page) } },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(110.dp, 72.dp)
                            .clip(shape)
                            .background(Color.Black)
                            .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color.DarkGray, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (mode.emotion != null) {
                            Face(mode.emotion, Look.CENTER, head = { Look.CENTER }, eyeColor = eyeColor)
                        } else {
                            Text(
                                "12:34",
                                color = Emotion.CALM.color(eyeColor),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                            )
                        }
                    }
                    Text(
                        mode.label,
                        color = if (selected) Color.White else Color.Gray,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Text(
                "Glisse pour changer · touche le visage pour fermer",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
