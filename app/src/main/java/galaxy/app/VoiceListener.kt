package galaxy.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

enum class VoiceStatus { OFF, STARTING, LISTENING, UNAVAILABLE, NO_PERMISSION }

/**
 * Listens continuously for French speech with the phone's speech recognizer and hands every
 * phrase heard (with its alternatives, partial or final) to [onPhrases]. Main thread only.
 */
class VoiceListener(
    private val context: Context,
    private val onPhrases: (phrases: List<String>, final: Boolean) -> Unit,
    private val onStatus: (VoiceStatus) -> Unit,
) : RecognitionListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var failures = 0

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStatus(VoiceStatus.UNAVAILABLE)
            return
        }
        active = true
        failures = 0
        onStatus(VoiceStatus.STARTING)
        createRecognizer()
        listen()
    }

    fun stop() {
        if (!active) return
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        onStatus(VoiceStatus.OFF)
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(this) }
    }

    private fun listen() {
        if (!active) return
        try {
            recognizer?.startListening(recognizerIntent(context))
        } catch (e: RuntimeException) {
            recover()
        }
    }

    private fun restart(delayMillis: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, delayMillis)
    }

    /** A broken recognizer often stays broken: start over with a fresh one, more slowly each time. */
    private fun recover() {
        failures++
        if (failures >= MAX_FAILURES) {
            stop()
            onStatus(VoiceStatus.UNAVAILABLE)
            return
        }
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (!active) return@postDelayed
            createRecognizer()
            listen()
        }, RETRY_MILLIS * failures)
    }

    override fun onReadyForSpeech(params: Bundle?) {
        onStatus(VoiceStatus.LISTENING)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { onPhrases(it, false) }
    }

    override fun onResults(results: Bundle?) {
        failures = 0
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { onPhrases(it, true) }
        restart(NEXT_MILLIS)
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                stop()
                onStatus(VoiceStatus.NO_PERMISSION)
            }
            // Nobody spoke, or nothing understood: just listen again.
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                failures = 0
                restart(NEXT_MILLIS)
            }
            else -> recover()
        }
    }

    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    companion object {
        private const val NEXT_MILLIS = 300L
        private const val RETRY_MILLIS = 1_000L
        private const val MAX_FAILURES = 8

        /** French free speech, with partial results so commands react quickly. */
        fun recognizerIntent(context: Context) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }
}
