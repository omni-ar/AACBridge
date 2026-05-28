package com.aacbridge.fusion

/**
 * Lightweight Android integration placeholder for
 * cross-attention fusion model outputs.
 *
 * Represents the unified intent classification
 * after EMG x gaze fusion.
 *
 * 5 intent classes: confirm, reject, scroll, select, call-help
 */
data class FusionOutput(
    val intentLabel: String,
    val confidence: Float,
    val timestampMs: Long
)
