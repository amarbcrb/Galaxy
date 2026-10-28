package galaxy.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import galaxy.core.sound.SoundCue
import galaxy.core.sound.SoundSynth
import java.util.concurrent.Executors

/** Plays the synthesized beeps one after the other on a background thread. */
class SoundPlayer {
    private val executor = Executors.newSingleThreadExecutor()
    private val cache = HashMap<SoundCue, ShortArray>()
    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SoundSynth.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(
            AudioTrack.getMinBufferSize(
                SoundSynth.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4096),
        )
        .build()

    fun play(cue: SoundCue) {
        executor.execute {
            val pcm = cache.getOrPut(cue) { SoundSynth.render(cue) }
            if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
            track.write(pcm, 0, pcm.size)
        }
    }

    fun pause() {
        executor.execute {
            track.pause()
            track.flush()
        }
    }

    fun release() {
        executor.execute { track.release() }
        executor.shutdown()
    }
}
