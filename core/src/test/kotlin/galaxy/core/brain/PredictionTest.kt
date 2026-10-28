package galaxy.core.brain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PredictionTest {
    @Test
    fun sentences() {
        assertEquals("Ta carte est l'As de pique !", Prediction.Card(1, Suit.SPADES).sentence)
        assertEquals("Ta carte est le 7 de carreau !", Prediction.Card(7, Suit.DIAMONDS).sentence)
        assertEquals("Ta carte est le Roi de trèfle !", Prediction.Card(13, Suit.CLUBS).sentence)
        assertEquals("Ton nombre est 42 !", Prediction.Number(42).sentence)
    }

    @Test
    fun encodeDecode() {
        val card = Prediction.Card(11, Suit.HEARTS)
        assertEquals(card, Prediction.decode(card.encode()))
        assertEquals(Prediction.Number(7), Prediction.decode("N:7"))
        assertNull(Prediction.decode(null))
        assertNull(Prediction.decode("C:14:HEARTS"))
        assertNull(Prediction.decode("nonsense"))
    }
}
