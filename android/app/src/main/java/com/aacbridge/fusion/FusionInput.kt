package com.aacbridge.fusion

/**
 * Lightweight Android integration for
 * late-fusion model inputs.
 *
 * Represents a single cross-modal observation.
 *
 * `emgEmbedding`: Expected shape (64). FusionInference reshapes to `(1,1,64)` for ONNX.
 * `gazeVector`: Expected shape 5-dim `[deltaX, deltaY, abs(deltaX), abs(deltaY), magnitude]`.
 * `gazeTarget`: String label intent emitted by traditional GazeTracker logic (fallback).
 *
 * Contract defined by Medha (EMG) and Heer (gaze).
 *
 * EMG embedding spec: shape (1,64), float32, L2 normalized
 */
data class FusionInput(
    val emgEmbedding: FloatArray,
    val gazeVector: FloatArray,
    val gazeTarget: String,
    val timestampMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FusionInput) return false
        return emgEmbedding.contentEquals(other.emgEmbedding) &&
               gazeVector.contentEquals(other.gazeVector) &&
               gazeTarget == other.gazeTarget &&
               timestampMs == other.timestampMs
    }

    override fun hashCode(): Int {
        var result = emgEmbedding.contentHashCode()
        result = 31 * result + gazeVector.contentHashCode()
        result = 31 * result + gazeTarget.hashCode()
        result = 31 * result + timestampMs.hashCode()
        return result
    }
}
