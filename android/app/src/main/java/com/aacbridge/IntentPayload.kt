package com.aacbridge

/**
 * A lightweight data class representing the result of an intent classification.
 * This payload acts as the unified contract between the intent generators
 * (MockIntentGenerator / InferenceEngine) and the downstream routing systems.
 */
data class IntentPayload(
    val intentLabel: String,
    val confidence: Float,
    val timestamp: Long
)
