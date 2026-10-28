package galaxy.core.face

import galaxy.core.Emotion

object Expressions {
    const val CYAN = 0xFF5CD3FFL

    /** Galaxy's resting eye: flat-ish top, round bottom. */
    private val base = EyeShape(width = 0.44f, height = 0.32f, topRound = 0.35f, bottomRound = 1f)

    private fun both(eye: EyeShape, color: Long) = FaceSpec(eye, eye, color)

    fun of(emotion: Emotion): FaceSpec = when (emotion) {
        Emotion.CALM -> both(base, CYAN)
        Emotion.HAPPY -> both(base.copy(height = 0.30f, lidBottom = 0.45f), 0xFF7FE6FFL)
        Emotion.LAUGHING -> both(base.copy(height = 0.28f, lidBottom = 0.6f), 0xFF6FFFC8L)
        Emotion.SURPRISED -> both(
            base.copy(width = 0.40f, height = 0.42f, topRound = 1f, bottomRound = 1f),
            0xFFFFE15CL,
        )
        Emotion.CURIOUS -> FaceSpec(
            left = base.copy(width = 0.40f, height = 0.26f, lidTop = 0.15f, offsetY = 0.02f),
            right = base.copy(width = 0.48f, height = 0.38f, offsetY = -0.02f),
            color = CYAN,
        )
        Emotion.ANGRY -> both(base.copy(lidTop = 0.40f, lidAngle = 22f), 0xFFFF4A4AL)
        Emotion.SAD -> both(base.copy(lidTop = 0.30f, lidAngle = -18f, offsetY = 0.04f), 0xFF3D6BFFL)
        Emotion.LOVE -> both(base.copy(width = 0.40f, height = 0.38f, heart = 1f), 0xFFFF6FB5L)
        Emotion.DIZZY -> both(
            base.copy(width = 0.30f, height = 0.30f, topRound = 1f, bottomRound = 1f),
            0xFFFF9F43L,
        )
        Emotion.SLEEPY -> both(base.copy(lidTop = 0.55f), 0xFF8A6CFFL)
        Emotion.ASLEEP -> both(base.copy(lidTop = 0.9f, offsetY = 0.03f), 0xFF6A4FD9L)
        Emotion.HUNGRY -> both(base.copy(height = 0.30f, lidTop = 0.45f, lidAngle = -10f), 0xFFFFB347L)
    }
}
