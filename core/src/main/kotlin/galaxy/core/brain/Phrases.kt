package galaxy.core.brain

/** Everything Galaxy says out loud, in French. */
object Phrases {
    fun time(hour: Int, minute: Int, second: Int): String = buildString {
        append("Il est ")
        append(hour)
        append(if (hour <= 1) " heure" else " heures")
        if (minute > 0) append(" ").append(minute)
        if (second > 0) {
            append(" et ").append(second)
            append(if (second == 1) " seconde" else " secondes")
        }
    }

    fun goodMorning(name: String) = if (name.isBlank()) "Bonjour !" else "Bonjour ${name.trim()} !"

    fun goodNight(name: String) = if (name.isBlank()) "Bonne nuit." else "Bonne nuit ${name.trim()}."

    const val HUNGRY = "J'ai faim..."
    const val THANKS_FOOD = "Miam, merci !"
    const val TIMER_DONE = "C'est l'heure !"
    const val HANDS_UP = "Ne tire pas !"
    const val SHOT = "Aïe !"
    const val NEW_COLOR = "Tadaa !"
    const val THINKING = "Hmm... Je vois... je vois..."
    const val NO_PREDICTION = "Hmm... Je ne vois rien pour l'instant."
}
