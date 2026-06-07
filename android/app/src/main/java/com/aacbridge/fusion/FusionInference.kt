package com.aacbridge.fusion

import android.content.Context
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Late-fusion inference engine using ONNX Runtime.
 *
 * Loads `gaze_emg_fusion.onnx` from assets/ and runs
 * inference on paired EMG embedding + gaze vectors
 * to produce a unified intent classification.
 *
 * Architecture (late fusion):
 *   EMG embedding (1,1,64) → squeeze → (1,64)
 *   Gaze vector   (1,5)    → project → (1,64)
 *   Concatenate → (1,128) → MLP → logits (1,5)
 *
 * Lifecycle:
 *   - Created at application scope via AppContainer
 *   - Lazy-loaded to avoid startup overhead
 *   - Must call [close] in Application.onTerminate
 *
 * Fallback:
 *   If ONNX Runtime fails (load or inference), falls
 *   back to returning [FusionInput.gazeTarget] and
 *   logs the error. Never crashes.
 */
class FusionInference(context: Context) {

    companion object {
        private const val TAG = "FusionInference"
        private const val MODEL_ASSET = "gaze_emg_fusion.onnx"

        /**
         * Minimum interval between log emissions.
         *
         * Previous behavior: Log.d on every fuse() call (~30Hz)
         * caused logcat ring buffer saturation, rotating out
         * all diagnostic logs from other components.
         */
        private const val LOG_THROTTLE_MS = 1000L

        /**
         * Intent label mapping.
         *
         * Must match the training label space in
         * gaze_dataset.py / dataset.py AAC_INTENT_NAMES.
         */
        private val INTENT_LABELS = arrayOf(
            "confirm",   // 0
            "reject",    // 1
            "scroll",    // 2
            "select",    // 3
            "call-help"  // 4
        )

        private const val NUM_CLASSES = 5
        private const val EMG_DIM = 64
        private const val GAZE_DIM = 5
    }

    private var lastLogTime = 0L

    /**
     * ONNX Runtime environment and session.
     *
     * Nullable — if model loading fails, inference
     * falls back to gaze target pass-through.
     */
    private val env: OrtEnvironment?
    private val session: OrtSession?

    /**
     * Whether the ONNX model loaded successfully.
     * Used to avoid repeated error logging on every fuse() call.
     */
    private val modelLoaded: Boolean

    init {
        var tempEnv: OrtEnvironment? = null
        var tempSession: OrtSession? = null
        var loaded = false

        try {
            tempEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            tempSession = tempEnv.createSession(modelBytes)
            loaded = true
            Log.i(TAG, "ONNX model loaded: $MODEL_ASSET (${modelBytes.size} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ONNX model — falling back to gaze target", e)
        }

        env = tempEnv
        session = tempSession
        modelLoaded = loaded
    }

    /**
     * Fuse EMG embedding and gaze vector into a
     * unified intent classification.
     *
     * If ONNX inference succeeds:
     *   - Returns argmax intent label
     *   - Returns softmax confidence
     *
     * If ONNX inference fails:
     *   - Returns [FusionInput.gazeTarget] as intent
     *   - Returns confidence = 0.0
     *   - Logs error (throttled)
     */
    fun fuse(input: FusionInput): FusionOutput {
        val now = System.currentTimeMillis()

        if (!modelLoaded || session == null || env == null) {
            return fallback(input, now)
        }

        return try {
            runInference(input, now)
        } catch (e: Exception) {
            if (now - lastLogTime > LOG_THROTTLE_MS) {
                Log.e(TAG, "Inference failed — fallback to gazeTarget", e)
                lastLogTime = now
            }
            fallback(input, now)
        }
    }

    /**
     * Run ONNX Runtime inference.
     *
     * Input tensors:
     *   emg_embedding: shape (1, 1, 64) float32
     *   gaze_vector:   shape (1, 5) float32
     *
     * Output tensor:
     *   logits: shape (1, 5) float32
     */
    private fun runInference(input: FusionInput, now: Long): FusionOutput {
        val env = this.env!!

        /*
         * Build EMG tensor: (1, 1, 64)
         *
         * FusionInput.emgEmbedding is a flat FloatArray(64).
         * The ONNX model expects shape (batch, 1, 64).
         */
        val emgBuffer = FloatBuffer.wrap(input.emgEmbedding)
        val emgTensor = OnnxTensor.createTensor(
            env, emgBuffer, longArrayOf(1, 1, EMG_DIM.toLong())
        )

        /*
         * Build Gaze tensor: (1, 5)
         *
         * FusionInput.gazeVector is a FloatArray(5).
         * The ONNX model expects shape (batch, 5).
         */
        val gazeBuffer = FloatBuffer.wrap(input.gazeVector)
        val gazeTensor = OnnxTensor.createTensor(
            env, gazeBuffer, longArrayOf(1, GAZE_DIM.toLong())
        )

        val inputs = mapOf(
            "emg_embedding" to emgTensor,
            "gaze_vector" to gazeTensor
        )

        val result = session!!.run(inputs)

        /*
         * Extract logits: shape (1, 5)
         */
        @Suppress("UNCHECKED_CAST")
        val logits = (result[0].value as Array<FloatArray>)[0]

        // Clean up native tensors
        emgTensor.close()
        gazeTensor.close()
        result.close()

        /*
         * Argmax → predicted class index
         */
        var maxIdx = 0
        var maxVal = logits[0]
        for (i in 1 until logits.size) {
            if (logits[i] > maxVal) {
                maxVal = logits[i]
                maxIdx = i
            }
        }

        /*
         * Softmax → confidence for the winning class
         */
        val confidence = softmax(logits, maxIdx)

        val label = if (maxIdx < INTENT_LABELS.size) {
            INTENT_LABELS[maxIdx]
        } else {
            "unknown"
        }

        if (now - lastLogTime > LOG_THROTTLE_MS) {
            Log.d(TAG, "Fusion: intent='$label' conf=${String.format("%.3f", confidence)}")
            lastLogTime = now
        }

        return FusionOutput(
            intentLabel = label,
            confidence = confidence,
            timestampMs = now
        )
    }

    /**
     * Fallback when ONNX is unavailable or fails.
     * Returns the gaze target directly.
     */
    private fun fallback(input: FusionInput, now: Long): FusionOutput {
        if (now - lastLogTime > LOG_THROTTLE_MS) {
            Log.d(TAG, "Fallback: gazeTarget='${input.gazeTarget}'")
            lastLogTime = now
        }
        return FusionOutput(
            intentLabel = input.gazeTarget,
            confidence = 0.0f,
            timestampMs = now
        )
    }

    /**
     * Numerically stable softmax for a single class.
     */
    private fun softmax(logits: FloatArray, idx: Int): Float {
        val maxLogit = logits.max()
        var sumExp = 0.0f
        for (l in logits) {
            sumExp += Math.exp((l - maxLogit).toDouble()).toFloat()
        }
        return Math.exp((logits[idx] - maxLogit).toDouble()).toFloat() / sumExp
    }

    /**
     * Release ONNX Runtime resources.
     *
     * Call from Application.onTerminate or when the
     * inference engine is no longer needed.
     */
    fun close() {
        try {
            session?.close()
            env?.close()
            Log.i(TAG, "ONNX session closed")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ONNX session", e)
        }
    }
}
