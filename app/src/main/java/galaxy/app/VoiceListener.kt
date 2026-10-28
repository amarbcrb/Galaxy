package galaxy.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
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
    /** The last recognizer error code, for the menu, or null once it hears again. */
    private val onErrorCode: (Int?) -> Unit,
) : RecognitionListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var failures = 0

    /** Recognition services to try, Google's first: some phones' default one never hears anything. */
    private var services: List<ComponentName?> = emptyList()
    private var serviceIndex = 0

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStatus(VoiceStatus.UNAVAILABLE)
            return
        }
        active = true
        failures = 0
        services = recognitionServices(context)
        serviceIndex = 0
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
        val service = services.getOrNull(serviceIndex)
        recognizer = try {
            if (service != null) {
                SpeechRecognizer.createSpeechRecognizer(context, service)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: RuntimeException) {
            SpeechRecognizer.createSpeechRecognizer(context)
        }.also { it.setRecognitionListener(this) }
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
        // After a few failures, try the next recognition service.
        if (failures % FAILURES_PER_SERVICE == 0 && serviceIndex < services.lastIndex) serviceIndex++
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

    override fun onBeginningOfSpeech() {
        onErrorCode(null)
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
        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) onErrorCode(error)
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

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    companion object {
        private const val NEXT_MILLIS = 300L
        private const val RETRY_MILLIS = 1_000L
        private const val MAX_FAILURES = 9
        private const val FAILURES_PER_SERVICE = 3
        private val PREFERRED_PACKAGES = listOf(
            "com.google.android.googlequicksearchbox",
            "com.google.android.tts",
            "com.google.android.as",
        )

        /** Google's recognition services found on the phone, in order of preference, then the default (null). */
        fun recognitionServices(context: Context): List<ComponentName?> {
            @Suppress("DEPRECATION")
            val installed = context.packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
            val google = PREFERRED_PACKAGES.mapNotNull { pkg ->
                installed.firstOrNull { it.serviceInfo.packageName == pkg }
                    ?.let { ComponentName(it.serviceInfo.packageName, it.serviceInfo.name) }
            }
            return google + listOf(null)
        }

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
