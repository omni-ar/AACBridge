package com.aacbridge

import android.util.Log
import com.aacbridge.fallback.InMemoryFallbackRepository
import com.aacbridge.inference.MockIntentGenerator

/**
 * Staged app-layer fallback routing.
 *
 * Current flow (Phase 1):
 * MockIntentGenerator -> IntentPayload -> FallbackRouter
 *
 * PURPOSE:
 * Provides safe offline/testing AAC interaction before
 * full InferenceEngine integration is complete.
 *
 * Responsibilities:
 * - generate temporary AAC responses via mock intents
 * - support staged UI interaction
 * - reduce JNI dependency for every interaction
 * - allow UI iteration before final inference integration
 *
 * This router is intentionally lightweight and deterministic.
 */
class FallbackRouter {

    companion object {
        private const val TAG = "FallbackRouter"
    }

    private val mockIntentGenerator = MockIntentGenerator()
    private val fallbackRepository = InMemoryFallbackRepository()

    /**
     * Generates a mock intent and resolves a fallback response.
     *
     * @return Pair of (intentLabel, responseText) for UI display.
     */
    suspend fun generateFallbackResponse(): Pair<String, String> {
        val payload = mockIntentGenerator.generate()
        Log.d(TAG, "Generated mock intent: ${payload.intentLabel} (confidence=${payload.confidence})")

        val response = fallbackRepository.getFallbackResponse(
            intent = payload.intentLabel,
            stateId = null
        ) ?: "Please wait a moment."

        return Pair(payload.intentLabel, response)
    }

    /**
     * Resolves a fallback response for a specific intent label.
     *
     * Used when intent source is gaze tracking or manual UI trigger
     * rather than MockIntentGenerator.
     *
     * @param intentLabel one of: confirm, reject, scroll, select, call-help
     * @return fallback response text
     */
    suspend fun resolveResponse(intentLabel: String): String {
        return fallbackRepository.getFallbackResponse(
            intent = intentLabel,
            stateId = null
        ) ?: "Please wait a moment."
    }
}
