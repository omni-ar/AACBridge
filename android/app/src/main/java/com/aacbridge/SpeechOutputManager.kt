package com.aacbridge

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Android TextToSpeech wrapper for AAC voice output.
 *
 * Converts LLM responses and fallback text into
 * spoken audio for the AAC user.
 *
 * Lifecycle:
 * - initialized in AppContainer / MainActivity
 * - shutdown() must be called in onDestroy()
 */
class SpeechOutputManager(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "SpeechOutputManager"
    }

    private val tts: TextToSpeech = TextToSpeech(context, this)
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            isReady = result != TextToSpeech.LANG_MISSING_DATA &&
                      result != TextToSpeech.LANG_NOT_SUPPORTED
            if (isReady) {
                Log.d(TAG, "TTS initialized successfully")
            } else {
                Log.e(TAG, "TTS language not supported")
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    /**
     * Speaks the given text immediately.
     *
     * Flushes any queued utterances to ensure
     * low-latency response for AAC users.
     */
    fun speak(text: String) {
        if (isReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            Log.w(TAG, "TTS not ready, skipping: $text")
        }
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
