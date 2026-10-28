package galaxy.core.brain

import kotlin.test.Test
import kotlin.test.assertEquals

class PhrasesTest {
    @Test
    fun time() {
        assertEquals("Il est 14 heures 32 et 7 secondes", Phrases.time(14, 32, 7))
        assertEquals("Il est 1 heure 5 et 1 seconde", Phrases.time(1, 5, 1))
        assertEquals("Il est 9 heures", Phrases.time(9, 0, 0))
        assertEquals("Il est 9 heures et 30 secondes", Phrases.time(9, 0, 30))
        assertEquals("Il est 0 heure 15", Phrases.time(0, 15, 0))
    }

    @Test
    fun greetings() {
        assertEquals("Bonjour Amar !", Phrases.goodMorning(" Amar "))
        assertEquals("Bonjour !", Phrases.goodMorning(""))
        assertEquals("Bonne nuit Amar.", Phrases.goodNight("Amar"))
    }
}
