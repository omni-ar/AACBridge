package com.aacbridge.fusion

/**
 * Lightweight Android integration placeholder for
 * cross-attention fusion model inputs.
 *
 * Represents combined sensor signals:
 * - EMG embedding from CNN-LSTM classifier
 * - gaze fixation vector from MediaPipe
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
