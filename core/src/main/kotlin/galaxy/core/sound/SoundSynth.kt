package galaxy.core.sound

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class SoundCue {
    TAP, SURPRISE, HAPPY, LAUGH, LOVE, ANGRY, SAD, DIZZY, YAWN, WAKE, EAT, HUNGRY, ALARM, CURIOUS,
    SCARED, BANG, GLASS,
}

/** A tone gliding from [startHz] to [endHz], or a burst of [noise]. A volume of 0 is a pause. */
data class Tone(
    val startHz: Float,
    val endHz: Float,
    val millis: Int,
    val volume: Float = 0.6f,
    val noise: Boolean = false,
)

/** Generates Galaxy's little robot sounds as 16-bit mono PCM, so the app ships no audio files. */
object SoundSynth {
    const val SAMPLE_RATE = 22_050

    private fun t(start: Number, end: Number, millis: Int, volume: Float = 0.6f) =
        Tone(start.toFloat(), end.toFloat(), millis, volume)

    private fun pause(millis: Int) = Tone(0f, 0f, millis, 0f)

    private fun noise(millis: Int, volume: Float) = Tone(0f, 0f, millis, volume, noise = true)

    fun tones(cue: SoundCue): List<Tone> = when (cue) {
        SoundCue.TAP -> listOf(t(900, 1300, 60, 0.4f))
        SoundCue.SURPRISE -> listOf(t(600, 1500, 120))
        SoundCue.HAPPY -> listOf(t(880, 880, 80), t(1175, 1175, 80), t(1568, 1568, 120))
        SoundCue.LAUGH -> List(5) { listOf(t(1200, 900, 70), pause(40)) }.flatten()
        SoundCue.LOVE -> listOf(t(700, 1000, 200, 0.45f), t(1000, 700, 250, 0.45f))
        SoundCue.ANGRY -> listOf(t(300, 220, 150, 0.7f), pause(40), t(300, 200, 200, 0.7f))
        SoundCue.SAD -> listOf(t(600, 400, 400, 0.45f))
        SoundCue.DIZZY -> listOf(t(600, 1200, 120), t(1200, 600, 120), t(600, 1200, 120), t(1200, 500, 160))
        SoundCue.YAWN -> listOf(t(700, 300, 700, 0.35f))
        SoundCue.WAKE -> listOf(t(500, 900, 150, 0.5f), t(900, 1300, 150, 0.5f))
        SoundCue.EAT -> List(3) { listOf(t(500, 700, 90), pause(60)) }.flatten() + t(1400, 1400, 150)
        SoundCue.HUNGRY -> listOf(t(500, 350, 300, 0.45f), pause(80), t(450, 300, 400, 0.45f))
        SoundCue.ALARM -> listOf(t(1300, 1300, 120, 0.8f), pause(60), t(1300, 1300, 120, 0.8f), pause(60), t(1700, 1700, 200, 0.8f))
        SoundCue.CURIOUS -> listOf(t(700, 1100, 150, 0.45f))
        SoundCue.SCARED -> List(6) { listOf(t(950, 900, 60, 0.45f), t(880, 840, 60, 0.45f)) }.flatten()
        SoundCue.BANG -> listOf(noise(140, 0.9f), t(140, 50, 220, 0.8f))
        SoundCue.GLASS -> listOf(
            noise(60, 0.5f), t(3200, 3100, 40, 0.3f), pause(30), t(2600, 2500, 40, 0.3f), pause(20),
            t(3800, 3700, 40, 0.25f), pause(40), t(2900, 2800, 50, 0.25f), noise(120, 0.25f),
        )
    }

    fun render(cue: SoundCue, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val tones = tones(cue)
        val total = tones.sumOf { samplesFor(it, sampleRate) }
        val out = ShortArray(total)
        var index = 0
        var phase = 0.0
        val random = Random(cue.ordinal)
        for (tone in tones) {
            val n = samplesFor(tone, sampleRate)
            val attack = min(n / 4, sampleRate * 5 / 1000).coerceAtLeast(1)
            val release = min(n / 3, sampleRate * 30 / 1000).coerceAtLeast(1)
            for (i in 0 until n) {
                if (tone.volume == 0f) {
                    out[index++] = 0
                    continue
                }
                val progress = i.toDouble() / n
                val freq = tone.startHz + (tone.endHz - tone.startHz) * progress
                phase += 2 * PI * freq / sampleRate
                val envelope = when {
                    i < attack -> i.toDouble() / attack
                    i > n - release -> (n - i).toDouble() / release
                    else -> 1.0
                }
                // A touch of the second harmonic makes the beep sound rounder and cuter.
                val wave = if (tone.noise) random.nextDouble(-1.0, 1.0) else (sin(phase) + 0.25 * sin(2 * phase)) / 1.25
                out[index++] = (wave * envelope * tone.volume * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }

    private fun samplesFor(tone: Tone, sampleRate: Int) = tone.millis * sampleRate / 1000
}
