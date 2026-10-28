package galaxy.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Galaxy's voice: the phone's French text-to-speech, pitched up a little. */
class Speaker(context: Context) {
    private var ready = false
    private var counter = 0
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        if (status == TextToSpeech.SUCCESS) onReady()
    }

    private fun onReady() {
        val result = tts.setLanguage(Locale.FRANCE)
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        tts.setPitch(1.4f)
        tts.setSpeechRate(1.05f)
    }

    fun speak(text: String) {
        if (!ready) return
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "galaxy-${counter++}")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.shutdown()
    }
}
