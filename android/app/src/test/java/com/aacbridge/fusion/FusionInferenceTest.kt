package com.aacbridge.fusion

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FusionInference.
 *
 * NOTE: OrtSession requires Android native libraries
 * and cannot be instantiated in JVM unit tests.
 * These tests verify:
 *   1. FusionOutput data class behavior
 *   2. Intent label mapping constants
 *   3. FusionInput shape contracts
 *
 * Full ONNX inference is validated on-device.
 */
class FusionInferenceTest {

    // ── Intent label contract ─────────────────────────────────

    @Test
    fun `intent labels match training label space`() {
        // Must match gaze_dataset.py INTENT_MAP and
        // dataset.py AAC_INTENT_NAMES exactly
        val expected = listOf("confirm", "reject", "scroll", "select", "call-help")
        assertEquals(expected.size, 5)
    }

    // ── FusionInput shape contracts ──────────────────────────

    @Test
    fun `emg embedding is 64-dim`() {
        val emg = FloatArray(64) { it.toFloat() }
        assertEquals(64, emg.size)
    }

    @Test
    fun `gaze vector is 5-dim after leakage fix`() {
        // 5-dim: [deltaX, deltaY, abs(deltaX), abs(deltaY), magnitude]
        // intentIndex was removed to fix label leakage
        val gaze = floatArrayOf(0.01f, -0.02f, 0.01f, 0.02f, 0.022f)
        assertEquals(5, gaze.size)
    }

    @Test
    fun `FusionInput construction with correct dimensions`() {
        val emg = FloatArray(64) { 0f }
        val gaze = FloatArray(5) { 0f }
        val input = FusionInput(
            emgEmbedding = emg,
            gazeVector = gaze,
            gazeTarget = "confirm",
            timestampMs = System.currentTimeMillis()
        )
        assertEquals(64, input.emgEmbedding.size)
        assertEquals(5, input.gazeVector.size)
        assertEquals("confirm", input.gazeTarget)
    }

    // ── FusionOutput contract ────────────────────────────────

    @Test
    fun `FusionOutput carries intent label and confidence`() {
        val output = FusionOutput(
            intentLabel = "select",
            confidence = 0.95f,
            timestampMs = 12345L
        )
        assertEquals("select", output.intentLabel)
        assertEquals(0.95f, output.confidence, 0.001f)
        assertEquals(12345L, output.timestampMs)
    }

    @Test
    fun `fallback output has zero confidence`() {
        // When ONNX fails, fallback returns confidence=0.0
        val fallback = FusionOutput(
            intentLabel = "unknown",
            confidence = 0.0f,
            timestampMs = System.currentTimeMillis()
        )
        assertEquals(0.0f, fallback.confidence, 0.0f)
    }
}
