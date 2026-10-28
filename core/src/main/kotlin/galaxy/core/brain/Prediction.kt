package galaxy.core.brain

enum class Suit(val label: String, val symbol: String, val red: Boolean) {
    HEARTS("cœur", "♥", true),
    DIAMONDS("carreau", "♦", true),
    CLUBS("trèfle", "♣", false),
    SPADES("pique", "♠", false),
}

/**
 * What Galaxy "guesses" during a magic trick. The magician sets it secretly beforehand
 * (for example the card he will force), then asks Galaxy to reveal it.
 */
sealed interface Prediction {
    /** What Galaxy says, e.g. "Ta carte est la Dame de cœur !". */
    val sentence: String

    fun encode(): String

    data class Card(val rank: Int, val suit: Suit) : Prediction {
        init {
            require(rank in 1..13) { "rank must be 1..13" }
        }

        /** Short label printed on the card: A, 2..10, V, D, R. */
        val rankLabel: String
            get() = when (rank) {
                1 -> "A"
                11 -> "V"
                12 -> "D"
                13 -> "R"
                else -> rank.toString()
            }

        val name: String
            get() = when (rank) {
                1 -> "l'As"
                11 -> "le Valet"
                12 -> "la Dame"
                13 -> "le Roi"
                else -> "le $rank"
            } + " de ${suit.label}"

        override val sentence get() = "Ta carte est $name !"

        override fun encode() = "C:$rank:${suit.name}"
    }

    data class Number(val value: Int) : Prediction {
        override val sentence get() = "Ton nombre est $value !"

        override fun encode() = "N:$value"
    }

    companion object {
        fun decode(text: String?): Prediction? {
            val parts = text?.split(":") ?: return null
            return when (parts.firstOrNull()) {
                "C" -> {
                    val rank = parts.getOrNull(1)?.toIntOrNull()
                    val suit = Suit.entries.firstOrNull { it.name == parts.getOrNull(2) }
                    if (rank != null && rank in 1..13 && suit != null) Card(rank, suit) else null
                }
                "N" -> parts.getOrNull(1)?.toIntOrNull()?.let(::Number)
                else -> null
            }
        }
    }
}
