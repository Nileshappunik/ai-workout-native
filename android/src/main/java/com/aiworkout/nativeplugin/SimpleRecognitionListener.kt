package com.aiworkout.nativeplugin

import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

class SimpleRecognitionListener(
    private val onResultCallback: (String) -> Unit,
    private val onErrorCallback: (Int) -> Unit,
    private val onEndCallback: () -> Unit
) : RecognitionListener {

    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        onEndCallback()
    }

    override fun onError(error: Int) {
        onErrorCallback(error)   // ✅ fixed
    }

    override fun onResults(results: Bundle) {
        val matches =
            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull().orEmpty()

        if (text.isNotBlank()) {
            onResultCallback(text)
        }
    }

    override fun onPartialResults(partialResults: Bundle) {
        val matches =
            partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull().orEmpty()

        if (text.isNotBlank()) {
            onResultCallback(text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
