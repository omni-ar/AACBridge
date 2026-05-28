package com.aacbridge.fusion

import android.util.Log

/**
 * Lightweight Android integration shell for
 * cross-attention / late fusion inference.
 *
 * Current state:
 * Placeholder pass-through that returns gaze target
 * as the fused intent. Actual ONNX model integration
 * will replace this once Heer exports the fusion model.
 *
 * Decision point: June 7
 * Cross-attention vs late fusion based on F1 gap.
 *
 * DO NOT implement heavy inference logic here yet.
 */
class FusionInference {

    companion object {
        private const val TAG = "FusionInference"
    }

    /**
     * Placeholder fusion pass-through.
     *
     * Currently returns the gaze target directly
     * as the fused intent label. This will be replaced
     * with actual cross-attention or late fusion inference
     * once the ONNX model is exported.
     */
    fun fuse(input: FusionInput): FusionOutput {
        Log.d(TAG, "Fusion placeholder: passing through gaze target '${input.gazeTarget}'")

        return FusionOutput(
            intentLabel = input.gazeTarget,
            confidence = 1.0f,
            timestampMs = System.currentTimeMillis()
        )
    }
}
