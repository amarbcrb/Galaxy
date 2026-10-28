package galaxy.core.brain

import galaxy.core.Emotion
import galaxy.core.face.EyeColors
import java.text.Normalizer

/** Things you can ask Galaxy to do, by voice or from the hidden menu. */
sealed interface Command {
    data class Act(val emotion: Emotion) : Command
    data object Sleep : Command
    data object Wake : Command
    data object TellTime : Command
    data object Stop : Command
    data class Timer(val minutes: Int) : Command
    data object HandsUp : Command
    data object Shoot : Command

    /** New eye color; the app saves it. A null color means "the next one". */
    data class EyeColor(val color: Long?) : Command

    /** What Galaxy's brain does. For [EyeColor] the app also changes the color. */
    fun toEvent(): Event = when (this) {
        is Act -> Event.Act(emotion)
        Sleep -> Event.Sleep
        Wake -> Event.Wake
        TellTime -> Event.TellTime
        Stop -> Event.CancelTimer
        is Timer -> Event.StartTimer(minutes * 60_000L)
        HandsUp -> Event.HandsUp
        Shoot -> Event.Shoot()
        is EyeColor -> Event.NewColor
    }
}

/** Understands simple French voice commands like "dors", "rigole" or "minuteur 5 minutes". */
object VoiceCommands {
    private val numberWords = mapOf(
        "un" to 1, "une" to 1, "deux" to 2, "trois" to 3, "quatre" to 4, "cinq" to 5,
        "six" to 6, "sept" to 7, "huit" to 8, "neuf" to 9, "dix" to 10, "quinze" to 15,
        "vingt" to 20, "trente" to 30, "quarante" to 40, "soixante" to 60,
    )

    private val moods = listOf(
        Emotion.LAUGHING to Regex("""\b(rigole\w*|ris|rire|marrant|blague)\b"""),
        Emotion.LOVE to Regex("""\b(amoureux|bisous?|coeurs?|calins?)\b|je t aime"""),
        Emotion.HAPPY to Regex("""\b(content|heureux|joyeux|souri\w*)\b"""),
        Emotion.ANGRY to Regex("""\b(fache\w*|colere|enerve\w*)\b"""),
        Emotion.SAD to Regex("""\b(triste|pleure\w*)\b"""),
        Emotion.SURPRISED to Regex("""\b(surpris\w*)\b"""),
        Emotion.CURIOUS to Regex("""\b(curieux|regarde)\b"""),
        Emotion.DIZZY to Regex("""\b(tournis|tourne|vertige)\b"""),
        Emotion.HUNGRY to Regex("""\b(faim|mange\w*)\b"""),
        Emotion.SLEEPY to Regex("""\b(fatigue\w*|baille\w*)\b"""),
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()

    fun parse(text: String): Command? {
        val t = normalize(text)
        if (t.isEmpty()) return null

        if (Regex("""\b(minuteur|minuterie|chrono)\b""").containsMatchIn(t)) {
            // "trois minutes": the word just before "minute" wins over an earlier "un".
            val beforeMinutes = Regex("""\b(\w+) minutes?\b""").find(t)?.groupValues?.get(1)
            val minutes = beforeMinutes?.let { it.toIntOrNull() ?: numberWords[it] }
                ?: Regex("""\b(\d{1,3})\b""").find(t)?.groupValues?.get(1)?.toInt()
            if (minutes != null && minutes in 1..600) return Command.Timer(minutes)
        }
        if (Regex("""\bmains? en l air\b|\bhaut les mains\b""").containsMatchIn(t)) return Command.HandsUp
        if (Regex("""\b(pan|pam|bang|boum|tire|feu)\b""").containsMatchIn(t)) return Command.Shoot
        if (Regex("""\b(couleurs?|yeux)\b""").containsMatchIn(t)) {
            val named = EyeColors.all.firstOrNull { Regex("""\b${normalize(it.name)}s?\b""").containsMatchIn(t) }
            if (named != null) return Command.EyeColor(named.color)
            if (Regex("""\bchange\w*\b""").containsMatchIn(t)) return Command.EyeColor(null)
        }
        if (Regex("""\b(arrete\w*|stop)\b""").containsMatchIn(t)) return Command.Stop
        if (Regex("""\bheures?\b""").containsMatchIn(t)) return Command.TellTime
        if (Regex("""\b(reveille\w*|debout)\b""").containsMatchIn(t)) return Command.Wake
        if (Regex("""\b(dors|dort|dodo|endors|dormir)\b|bonne nuit""").containsMatchIn(t)) return Command.Sleep
        for ((emotion, regex) in moods) {
            if (regex.containsMatchIn(t)) return Command.Act(emotion)
        }
        return null
    }
}
