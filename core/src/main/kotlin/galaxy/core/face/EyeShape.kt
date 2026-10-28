package galaxy.core.face

/**
 * One eye, described in "face units" where 1.0 is the shorter side of the screen.
 *
 * The eye body is a rounded rectangle. Lids are drawn in the background color on top of it,
 * which is how expressions like crescents (happy) or slanted brows (angry) are made.
 */
data class EyeShape(
    val width: Float,
    val height: Float,
    /** Rounding of the top corners, 0 = square, 1 = fully round. */
    val topRound: Float,
    /** Rounding of the bottom corners, 0 = square, 1 = fully round. */
    val bottomRound: Float,
    /** Fraction of the eye hidden by the upper lid, 0..1. */
    val lidTop: Float = 0f,
    /** Upper lid angle in degrees. Positive lowers the inner corner (towards the nose). */
    val lidAngle: Float = 0f,
    /** Fraction of the eye hidden by an arched lower lid, 0..1. Makes the happy crescent. */
    val lidBottom: Float = 0f,
    /** 0 = normal eye, 1 = heart. Values between cross-fade. */
    val heart: Float = 0f,
    /** 0 = normal eye, 1 = an X (knocked out). Values between cross-fade. */
    val cross: Float = 0f,
    /** 0 = normal eye, 1 = a turning spiral (dizzy). Values between cross-fade. */
    val spiral: Float = 0f,
    /** 0 = open eye, 1 = a peaceful closed arc (sleeping). Values between cross-fade. */
    val closed: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    companion object {
        fun lerp(a: EyeShape, b: EyeShape, t: Float): EyeShape = EyeShape(
            width = lerp(a.width, b.width, t),
            height = lerp(a.height, b.height, t),
            topRound = lerp(a.topRound, b.topRound, t),
            bottomRound = lerp(a.bottomRound, b.bottomRound, t),
            lidTop = lerp(a.lidTop, b.lidTop, t),
            lidAngle = lerp(a.lidAngle, b.lidAngle, t),
            lidBottom = lerp(a.lidBottom, b.lidBottom, t),
            heart = lerp(a.heart, b.heart, t),
            cross = lerp(a.cross, b.cross, t),
            spiral = lerp(a.spiral, b.spiral, t),
            closed = lerp(a.closed, b.closed, t),
            offsetX = lerp(a.offsetX, b.offsetX, t),
            offsetY = lerp(a.offsetY, b.offsetY, t),
        )
    }
}

/** Both eyes plus their color (ARGB). The right eye's lid angle is mirrored by the renderer. */
data class FaceSpec(
    val left: EyeShape,
    val right: EyeShape,
    val color: Long,
) {
    companion object {
        fun lerp(a: FaceSpec, b: FaceSpec, t: Float): FaceSpec = FaceSpec(
            left = EyeShape.lerp(a.left, b.left, t),
            right = EyeShape.lerp(a.right, b.right, t),
            color = lerpColor(a.color, b.color, t),
        )
    }
}

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun lerpColor(a: Long, b: Long, t: Float): Long {
    fun channel(shift: Int): Long {
        val ca = (a shr shift) and 0xFF
        val cb = (b shr shift) and 0xFF
        return (lerp(ca.toFloat(), cb.toFloat(), t) + 0.5f).toLong().coerceIn(0, 255) shl shift
    }
    return channel(24) or channel(16) or channel(8) or channel(0)
}
