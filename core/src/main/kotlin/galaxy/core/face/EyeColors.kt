package galaxy.core.face

/** Colors the user can give Galaxy's eyes, by voice or from the menu. */
object EyeColors {
    data class Named(val name: String, val color: Long)

    val all = listOf(
        Named("Cyan", Expressions.CYAN),
        Named("Bleu", 0xFF4A7BFFL),
        Named("Violet", 0xFFB06CFFL),
        Named("Rose", 0xFFFF6FB5L),
        Named("Rouge", 0xFFFF4A4AL),
        Named("Orange", 0xFFFF9F43L),
        Named("Jaune", 0xFFFFE15CL),
        Named("Vert", 0xFF4AFF7AL),
        Named("Blanc", 0xFFFFFFFFL),
    )

    /** The color after [current] in [all], to cycle through them. */
    fun next(current: Long?): Long {
        val index = all.indexOfFirst { it.color == (current ?: Expressions.CYAN) }
        return all[(index + 1) % all.size].color
    }
}
