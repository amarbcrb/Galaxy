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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

/** How many face units must fit across the screen, so both eyes fit in portrait too. */
private const val FACE_WIDTH = 1.3f

fun Emotion.color(eyeColor: Long? = null): Color = Color(Expressions.of(this, eyeColor).color.toInt())

/**
 * Galaxy's animated face. Morphs smoothly between emotions, blinks, looks at [look] and turns
 * its head by [head] (read at draw time, so fast sensor updates only redraw).
 */
@Composable
fun Face(
    emotion: Emotion,
    look: Look,
    head: () -> Look,
    eyeColor: Long?,
    modifier: Modifier = Modifier,
) {
    val target = Expressions.of(emotion, eyeColor)
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
        val openness = if (emotion == Emotion.ASLEEP || emotion == Emotion.KO) 1f else blinker.openness(t)
        drawFace(spec, emotion, gazeX, gazeY, head(), openness, t, painter)
    }
}

private fun DrawScope.drawFace(
    spec: FaceSpec,
    emotion: Emotion,
    gazeX: Float,
    gazeY: Float,
    head: Look,
    openness: Float,
    t: Long,
    painter: GlowPainter,
) {
    val u = min(size.height, size.width / FACE_WIDTH)
    val color = Color(spec.color.toInt())
    // A head turn moves the whole face, brings the eyes closer and makes the far eye narrower.
    val gaze = Offset(gazeX * 0.08f * u + head.x * 0.22f * u, gazeY * 0.06f * u + head.y * 0.12f * u)
    val spacing = EYE_SPACING * (1f - 0.25f * abs(head.x))
    val bounce = if (emotion == Emotion.LAUGHING) -abs(sin(t / 120.0)).toFloat() * 0.03f * u else 0f
    val pulse = if (emotion == Emotion.LOVE) 1f + 0.06f * sin(t * 2 * PI / 600).toFloat() else 1f
    // Scared: the whole face trembles. Dizzy: it sways from side to side.
    val shake = when (emotion) {
        Emotion.SCARED -> Offset(sin(t / 23.0).toFloat(), cos(t / 31.0).toFloat()) * (0.006f * u)
        Emotion.DIZZY -> Offset(sin(t / 260.0).toFloat() * 0.04f * u, cos(t / 520.0).toFloat() * 0.01f * u)
        else -> Offset.Zero
    }

    var rightEyeTop = Offset.Zero
    val eyes = mutableListOf<Triple<Offset, Float, Float>>() // center, half width, half height
    for (side in intArrayOf(-1, 1)) {
        val eye = if (side < 0) spec.left else spec.right
        var center = Offset(
            size.width / 2 + side * spacing * u + eye.offsetX * u,
            size.height / 2 + eye.offsetY * u + bounce,
        ) + gaze + shake
        val scale = Size(1f - 0.3f * head.x * side, 1f - 0.15f * abs(head.y))
        drawEye(center, eye, side, u, color, openness, pulse, scale, t, painter)
        eyes += Triple(center, eye.width * u * scale.width / 2, eye.height * u * scale.height / 2)
        if (side > 0) rightEyeTop = center + Offset(eye.width * u / 2, -eye.height * u / 2)
    }
    when (emotion) {
        Emotion.ASLEEP -> drawSnore(rightEyeTop, u, color, t, painter)
        Emotion.DIZZY -> drawOrbitingStars(eyes, u, t, painter)
        Emotion.SAD -> drawTears(eyes, u, t, painter)
        else -> Unit
    }
}

private val STAR_COLOR = Color(0xFFFFE15C)
private val TEAR_COLOR = Color(0xFF6EC6FF)

/** Cartoon dizziness: little stars circling above the head. */
private fun DrawScope.drawOrbitingStars(eyes: List<Triple<Offset, Float, Float>>, u: Float, t: Long, painter: GlowPainter) {
    val left = eyes.first()
    val right = eyes.last()
    val center = Offset((left.first.x + right.first.x) / 2, minOf(left.first.y - left.third, right.first.y - right.third) - 0.1f * u)
    val radiusX = (right.first.x - left.first.x) / 2 + 0.12f * u
    val radiusY = 0.07f * u
    val count = 5
    for (i in 0 until count) {
        val angle = t / 1_100.0 * 2 * PI + i * 2 * PI / count
        // Stars in front (lower on the ellipse) look bigger and brighter.
        val depth = ((sin(angle) + 1) / 2).toFloat()
        val position = center + Offset(cos(angle).toFloat() * radiusX, sin(angle).toFloat() * radiusY)
        val path = starPath(position, 0.045f * u * (0.6f + 0.4f * depth), t / 300f + i)
        val alpha = 0.5f + 0.5f * depth
        painter.glow(this, path, STAR_COLOR, alpha, u)
        drawPath(path, STAR_COLOR, alpha = alpha)
    }
}

private fun starPath(c: Offset, radius: Float, rotation: Float): Path = Path().apply {
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) radius else radius * 0.45f
        val a = rotation + k * PI.toFloat() / 5 - PI.toFloat() / 2
        val x = c.x + cos(a) * r
        val y = c.y + sin(a) * r
        if (k == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Sad: tears roll down from the outer corner of each eye. */
private fun DrawScope.drawTears(eyes: List<Triple<Offset, Float, Float>>, u: Float, t: Long, painter: GlowPainter) {
    eyes.forEachIndexed { index, (center, halfWidth, halfHeight) ->
        val side = if (index == 0) -1 else 1
        for (k in 0 until 2) {
            val p = ((t / 1_600f) + k * 0.5f + index * 0.23f) % 1f
            val x = center.x + side * halfWidth * 0.55f
            val y = center.y + halfHeight + p * p * 0.45f * u
            val radius = 0.026f * u * (0.6f + 0.4f * (p * 4f).coerceAtMost(1f))
            val alpha = if (p > 0.8f) (1f - p) / 0.2f else 1f
            val path = Path().apply {
                moveTo(x, y - radius * 2.4f)
                cubicTo(x + radius * 0.4f, y - radius * 1.4f, x + radius, y - radius * 0.6f, x + radius, y)
                cubicTo(x + radius, y + radius * 1.3f, x - radius, y + radius * 1.3f, x - radius, y)
                cubicTo(x - radius, y - radius * 0.6f, x - radius * 0.4f, y - radius * 1.4f, x, y - radius * 2.4f)
                close()
            }
            painter.glow(this, path, TEAR_COLOR, alpha, u)
            drawPath(path, TEAR_COLOR, alpha = alpha)
        }
    }
}

private fun DrawScope.drawEye(
    c: Offset,
    eye: EyeShape,
    side: Int,
    u: Float,
    color: Color,
    openness: Float,
    pulse: Float,
    scale: Size,
    t: Long,
    painter: GlowPainter,
) {
    val w = eye.width * u * pulse * scale.width
    val h = eye.height * u * pulse * scale.height * openness.coerceAtLeast(0.08f)
    val left = c.x - w / 2
    val top = c.y - h / 2
    val margin = 0.08f * u

    // Each eye stays inside its own half so the lids never cover the other eye.
    clipRect(left = left - margin, top = 0f, right = left + w + margin, bottom = size.height) {
        val bodyAlpha = (1f - eye.heart - eye.cross - eye.spiral).coerceAtLeast(0f)
        if (bodyAlpha > 0f) {
            val path = eyePath(left, top, w, h, eye)
            painter.glow(this, path, color, bodyAlpha, u)
            drawPath(path, color, alpha = bodyAlpha)
        }
        if (eye.cross > 0f) {
            // Knocked out: an X instead of the eye.
            val arm = min(w, h) * 0.45f
            val stroke = 0.06f * u
            val crossColor = color.copy(alpha = color.alpha * eye.cross)
            drawLine(crossColor, c + Offset(-arm, -arm), c + Offset(arm, arm), stroke, StrokeCap.Round)
            drawLine(crossColor, c + Offset(-arm, arm), c + Offset(arm, -arm), stroke, StrokeCap.Round)
        }
        if (eye.spiral > 0f) {
            // Dizzy: spirals turning in opposite directions.
            val path = spiralPath(c, min(w, h) * 0.5f, side * t / 400f)
            drawPath(path, color, alpha = eye.spiral, style = Stroke(width = 0.035f * u, cap = StrokeCap.Round))
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

private fun spiralPath(c: Offset, radius: Float, rotation: Float): Path = Path().apply {
    val turns = 3f
    val steps = 90
    for (i in 0..steps) {
        val f = i / steps.toFloat()
        val a = rotation + f * turns * 2 * PI.toFloat()
        val r = radius * f
        val x = c.x + cos(a) * r
        val y = c.y + sin(a) * r
        if (i == 0) moveTo(x, y) else lineTo(x, y)
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
