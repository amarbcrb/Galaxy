package galaxy.app

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import galaxy.core.Emotion
import galaxy.core.face.Blinker
import galaxy.core.face.Expressions
import galaxy.core.face.EyeShape
import galaxy.core.face.FaceSpec
import galaxy.core.face.Look
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Distance of each eye's center from the middle of the screen, in face units. */
private const val EYE_SPACING = 0.34f

fun Emotion.color(): Color = Color(Expressions.of(this).color.toInt())

/** Galaxy's animated face. Morphs smoothly between emotions, blinks, and looks at [look]. */
@Composable
fun Face(emotion: Emotion, look: Look, dim: Boolean, modifier: Modifier = Modifier) {
    val target = Expressions.of(emotion)
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(target) {
        from = FaceSpec.lerp(from, to, progress.value)
        to = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
    }

    val gazeSpring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
    val gazeX by animateFloatAsState(look.x, gazeSpring, label = "gazeX")
    val gazeY by animateFloatAsState(look.y, gazeSpring, label = "gazeY")

    val blinker = remember { Blinker() }
    val frameMillis by produceState(0L) {
        while (true) withFrameMillis { value = it }
    }
    val painter = remember { GlowPainter() }

    Canvas(modifier.fillMaxSize()) {
        val spec = FaceSpec.lerp(from, to, progress.value)
        val t = frameMillis
        val openness = if (emotion == Emotion.ASLEEP) 1f else blinker.openness(t)
        drawFace(spec, emotion, gazeX, gazeY, openness, t, dim, painter)
    }
}

private fun DrawScope.drawFace(
    spec: FaceSpec,
    emotion: Emotion,
    gazeX: Float,
    gazeY: Float,
    openness: Float,
    t: Long,
    dim: Boolean,
    painter: GlowPainter,
) {
    val u = size.minDimension
    val color = Color(spec.color.toInt()).copy(alpha = if (dim) 0.35f else 1f)
    val gaze = Offset(gazeX * 0.08f * u, gazeY * 0.06f * u)
    val bounce = if (emotion == Emotion.LAUGHING) -abs(sin(t / 120.0)).toFloat() * 0.03f * u else 0f
    val pulse = if (emotion == Emotion.LOVE) 1f + 0.06f * sin(t * 2 * PI / 600).toFloat() else 1f
    val spin = if (emotion == Emotion.DIZZY) (t % 700) / 700.0 * 2 * PI else null

    var rightEyeTop = Offset.Zero
    for (side in intArrayOf(-1, 1)) {
        val eye = if (side < 0) spec.left else spec.right
        var center = Offset(
            size.width / 2 + side * EYE_SPACING * u + eye.offsetX * u,
            size.height / 2 + eye.offsetY * u + bounce,
        ) + gaze
        if (spin != null) {
            // Eyes circle in opposite directions.
            center += Offset(cos(side * spin).toFloat(), sin(side * spin).toFloat()) * (0.05f * u)
        }
        drawEye(center, eye, side, u, color, openness, pulse, painter)
        if (side > 0) rightEyeTop = center + Offset(eye.width * u / 2, -eye.height * u / 2)
    }
    if (emotion == Emotion.ASLEEP) drawSnore(rightEyeTop, u, color, t, painter)
}

private fun DrawScope.drawEye(
    c: Offset,
    eye: EyeShape,
    side: Int,
    u: Float,
    color: Color,
    openness: Float,
    pulse: Float,
    painter: GlowPainter,
) {
    val w = eye.width * u * pulse
    val h = eye.height * u * pulse * openness.coerceAtLeast(0.08f)
    val left = c.x - w / 2
    val top = c.y - h / 2
    val margin = 0.08f * u

    // Each eye stays inside its own half so the lids never cover the other eye.
    clipRect(left = left - margin, top = 0f, right = left + w + margin, bottom = size.height) {
        val bodyAlpha = 1f - eye.heart
        if (bodyAlpha > 0f) {
            val path = eyePath(left, top, w, h, eye)
            painter.glow(this, path, color, bodyAlpha, u)
            drawPath(path, color, alpha = bodyAlpha)
        }
        if (eye.heart > 0f) {
            val path = heartPath(c, min(w, h) * 1.05f)
            painter.glow(this, path, color, eye.heart, u)
            drawPath(path, color, alpha = eye.heart)
        }
        if (eye.lidTop > 0f) {
            val lidY = top + eye.lidTop * h
            // Positive angle lowers the inner corner: clockwise for the left eye, the other way for the right.
            rotate(degrees = eye.lidAngle * -side, pivot = Offset(c.x, lidY)) {
                drawRect(Color.Black, topLeft = Offset(c.x - w, lidY - u), size = Size(w * 2, u))
            }
        }
        if (eye.lidBottom > 0f) {
            val arcTop = top + h - eye.lidBottom * h
            drawOval(Color.Black, topLeft = Offset(c.x - w * 0.75f, arcTop), size = Size(w * 1.5f, h * 1.2f + 0.1f * u))
        }
    }
}

private fun eyePath(left: Float, top: Float, w: Float, h: Float, eye: EyeShape): Path {
    var rTop = eye.topRound * min(w / 2, h)
    var rBottom = eye.bottomRound * min(w / 2, h)
    if (rTop + rBottom > h) {
        val scale = h / (rTop + rBottom)
        rTop *= scale
        rBottom *= scale
    }
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = left + w,
                bottom = top + h,
                topLeftCornerRadius = CornerRadius(rTop),
                topRightCornerRadius = CornerRadius(rTop),
                bottomRightCornerRadius = CornerRadius(rBottom),
                bottomLeftCornerRadius = CornerRadius(rBottom),
            ),
        )
    }
}

private fun heartPath(c: Offset, s: Float): Path = Path().apply {
    val x = c.x
    val y = c.y - s * 0.1f
    moveTo(x, y + s * 0.45f)
    cubicTo(x - s * 0.6f, y + s * 0.05f, x - s * 0.55f, y - s * 0.45f, x, y - s * 0.2f)
    cubicTo(x + s * 0.55f, y - s * 0.45f, x + s * 0.6f, y + s * 0.05f, x, y + s * 0.45f)
    close()
}

/** Three little "z" floating up next to the right eye while Galaxy sleeps. */
private fun DrawScope.drawSnore(from: Offset, u: Float, color: Color, t: Long, painter: GlowPainter) {
    for (i in 0 until 3) {
        val p = ((t / 2_400f) + i / 3f) % 1f
        val position = from + Offset(p * 0.15f * u, -p * 0.25f * u)
        painter.text(this, "z", position, (0.06f + p * 0.05f) * u, color.copy(alpha = color.alpha * (1f - p)))
    }
}

/** Native paints for the soft glow and text, reused across frames. */
private class GlowPainter {
    private val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
    }
    private var blurRadius = -1f

    fun glow(scope: DrawScope, path: Path, color: Color, alpha: Float, u: Float) {
        val radius = 0.05f * u
        if (radius != blurRadius) {
            blurRadius = radius
            glowPaint.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
        }
        glowPaint.color = color.copy(alpha = 0.55f * alpha * color.alpha).toArgb()
        scope.drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), glowPaint) }
    }

    fun text(scope: DrawScope, text: String, position: Offset, size: Float, color: Color) {
        textPaint.textSize = size
        textPaint.color = color.toArgb()
        scope.drawIntoCanvas { it.nativeCanvas.drawText(text, position.x, position.y, textPaint) }
    }
}
