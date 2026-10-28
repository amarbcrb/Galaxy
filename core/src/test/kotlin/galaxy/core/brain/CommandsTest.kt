package galaxy.core.brain

import galaxy.core.Emotion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommandsTest {
    private fun parse(text: String) = VoiceCommands.parse(text)

    @Test
    fun sleepAndWake() {
        assertEquals(Command.Sleep, parse("Galaxy, dors !"))
        assertEquals(Command.Sleep, parse("va dormir"))
        assertEquals(Command.Sleep, parse("Bonne nuit Galaxy"))
        assertEquals(Command.Wake, parse("Réveille-toi"))
        assertEquals(Command.Wake, parse("debout"))
    }

    @Test
    fun moods() {
        assertEquals(Command.Act(Emotion.LAUGHING), parse("Rigole"))
        assertEquals(Command.Act(Emotion.LAUGHING), parse("fais-moi rire"))
        assertEquals(Command.Act(Emotion.LOVE), parse("je t'aime"))
        assertEquals(Command.Act(Emotion.LOVE), parse("fais un câlin"))
        assertEquals(Command.Act(Emotion.ANGRY), parse("Fâche-toi"))
        assertEquals(Command.Act(Emotion.SAD), parse("tu es triste ?"))
        assertEquals(Command.Act(Emotion.HAPPY), parse("souris"))
        assertEquals(Command.Act(Emotion.SURPRISED), parse("surprise"))
        assertEquals(Command.Act(Emotion.DIZZY), parse("tourne"))
        assertEquals(Command.Act(Emotion.HUNGRY), parse("tu as faim"))
    }

    @Test
    fun timeTimerAndStop() {
        assertEquals(Command.TellTime, parse("Quelle heure est-il ?"))
        assertEquals(Command.Timer(5), parse("minuteur 5 minutes"))
        assertEquals(Command.Timer(3), parse("lance un minuteur de trois minutes"))
        assertEquals(Command.Timer(1), parse("minuteur une minute"))
        assertEquals(Command.Timer(10), parse("minuteur de 10"))
        assertEquals(Command.Stop, parse("arrête"))
        assertEquals(Command.Stop, parse("stop"))
    }

    @Test
    fun unknownOrEmpty() {
        assertNull(parse(""))
        assertNull(parse("la voiture est garée dehors"))
        assertNull(parse("minuteur"))
    }

    @Test
    fun commandsMapToEvents() {
        assertEquals(Event.Act(Emotion.SAD), Command.Act(Emotion.SAD).toEvent())
        assertEquals(Event.StartTimer(120_000), Command.Timer(2).toEvent())
        assertEquals(Event.CancelTimer, Command.Stop.toEvent())
    }

    @Test
    fun handsUpAndShoot() {
        assertEquals(Command.HandsUp, parse("Mains en l'air !"))
        assertEquals(Command.HandsUp, parse("haut les mains"))
        assertEquals(Command.Shoot, parse("Pan !"))
        assertEquals(Command.Shoot, parse("pan pan"))
        assertEquals(Command.Shoot, parse("bang"))
        assertEquals(Event.Shoot(), Command.Shoot.toEvent())
    }

    @Test
    fun eyeColors() {
        assertEquals(Command.EyeColor(0xFFFF4A4AL), parse("change la couleur de tes yeux en rouge"))
        assertEquals(Command.EyeColor(0xFF4AFF7AL), parse("yeux verts"))
        assertEquals(Command.EyeColor(0xFFB06CFFL), parse("Mets tes yeux en violet"))
        assertEquals(Command.EyeColor(null), parse("change de couleur"))
        assertNull(parse("rouge"))
        assertEquals(Event.NewColor, Command.EyeColor(null).toEvent())
    }
}
