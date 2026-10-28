package galaxy.core.sound

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoundSynthTest {
    @Test
    fun everyCueRendersAudibleSoundOfTheRightLength() {
        for (cue in SoundCue.entries) {
            val pcm = SoundSynth.render(cue)
            val expected = SoundSynth.tones(cue).sumOf { it.millis * SoundSynth.SAMPLE_RATE / 1000 }
            assertEquals(expected, pcm.size, "$cue length")
            assertTrue(pcm.maxOf { abs(it.toInt()) } > 3_000, "$cue is too quiet")
        }
    }

    @Test
    fun startsAndEndsSilentlyToAvoidClicks() {
        for (cue in SoundCue.entries) {
            val pcm = SoundSynth.render(cue)
            assertTrue(abs(pcm.first().toInt()) < 500, "$cue start")
            assertTrue(abs(pcm.last().toInt()) < 2_000, "$cue end")
        }
    }
}
