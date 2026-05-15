package com.aacbridge.inference

import com.aacbridge.IntentPayload

/**
 * A lightweight, rule-based generator for intent classification.
 * Used for Phase 1 testing and downstream component verification before the
 * full JNI llama.cpp InferenceEngine integration is complete.
 *
 * It uses stochastic random selection across the 5 supported system intents.
 */
class MockIntentGenerator {

    private val supportedIntents = listOf(
        "confirm",
        "reject",
        "scroll",
        "select",
        "call-help"
    )

    /**
     * Generates a mock IntentPayload.
     * 
     * @return IntentPayload containing a randomly selected intent, 
     *         full confidence (1.0f), and the current system timestamp.
     */
    fun generate(): IntentPayload {
        val randomIntent = supportedIntents.random()
        return IntentPayload(
            intentLabel = randomIntent,
            confidence = 1.0f,
            timestamp = System.currentTimeMillis()
        )
    }
}
