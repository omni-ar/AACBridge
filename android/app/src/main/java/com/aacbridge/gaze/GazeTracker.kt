package com.aacbridge.gaze

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import java.util.concurrent.Executors
import com.aacbridge.BuildConfig

/**
 * MediaPipe Face Mesh gaze tracking for AAC interaction.
 *
 * Responsibilities:
 * - Face Mesh landmark detection
 * - 400ms dwell threshold enforcement
 * - fixation target mapping to AAC intents
 *
 * Targets:
 * - confirm
 * - reject
 * - scroll
 * - select
 * - call-help
 *
 * IMPORTANT:
 * This layer emits ONLY UI-level intent events.
 * It does NOT invoke JNI, KV cache, or backend routing.
 * Gaze logic is fully isolated from native systems.
 */
class GazeTracker(
    private val context: Context,
    private val onGazeIntent: (String) -> Unit
) {

    companion object {
        private const val TAG = "GazeTracker"
        private const val DWELL_THRESHOLD_MS = 400L

        // Hardcoded thresholds mapped to AAC intent areas
        private const val THRESHOLD_X = 0.02f
        private const val THRESHOLD_Y = 0.01f
    }

    private var faceLandmarker: FaceLandmarker? = null
    private var currentTarget: String? = null
    private var dwellStartTime: Long = 0L
    private var hasFired: Boolean = false

    private val dwellExecutor = Executors.newSingleThreadExecutor()

    init {
        initializeFaceLandmarker()
    }

    private fun initializeFaceLandmarker() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinFaceDetectionConfidence(0.5f)
                .setMinFacePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: FaceLandmarkerResult, _ -> processResult(result) }
                .setErrorListener { e: RuntimeException -> Log.e(TAG, "FaceLandmarker error: ${e.message}") }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(TAG, "FaceLandmarker initialized")
        } catch (e: Exception) {
            Log.w(TAG, "FaceLandmarker init failed (model asset may be missing). " +
                       "Gaze tracking will use manual/simulated input only.", e)
        }
    }

    /**
     * Processes live camera frames from CameraX ImageAnalysis.
     */
    fun processImageProxy(imageProxy: ImageProxy) {
        if (faceLandmarker == null) {
            imageProxy.close()
            return
        }

        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Camera frame received")
            }
            // CameraX 1.3.0+ supports direct toBitmap() with rotation applied
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000
            
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "detectAsync invoked")
            }
            faceLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image proxy", e)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Processes FaceLandmarker results to determine gaze target.
     *
     * Uses eye center vs nose tip displacement to resolve
     * horizontal and vertical gaze direction.
     */
    private fun processResult(result: FaceLandmarkerResult) {
        val faces = result.faceLandmarks()
        if (faces.isNullOrEmpty()) {
            resetDwell()
            return
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Face landmarks detected")
        }

        val face = faces[0]

        // MediaPipe landmark indices:
        // 4 = nose tip, 33 = left eye inner, 362 = right eye inner
        val noseTip = face[4]
        val leftEye = face[33]
        val rightEye = face[362]

        val eyeCenterX = (leftEye.x() + rightEye.x()) / 2f
        val eyeCenterY = (leftEye.y() + rightEye.y()) / 2f

        val deltaX = noseTip.x() - eyeCenterX
        val deltaY = noseTip.y() - eyeCenterY

        val target = mapGazeToTarget(deltaX, deltaY)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Resolved gaze target: $target (deltaX=$deltaX, deltaY=$deltaY)")
        }
        dwellExecutor.execute {
            updateDwell(target)
        }
    }

    /**
     * Maps gaze displacement to one of 5 AAC intent targets.
     *
     * Layout:
     *   top-left=confirm    top-right=reject
     *   bottom-left=scroll  bottom-right=select
     *   center=call-help
     */
    private fun mapGazeToTarget(deltaX: Float, deltaY: Float): String {
        return when {
            deltaX < -THRESHOLD_X && deltaY < -THRESHOLD_Y -> "confirm"
            deltaX > THRESHOLD_X && deltaY < -THRESHOLD_Y  -> "reject"
            deltaX < -THRESHOLD_X && deltaY > THRESHOLD_Y   -> "scroll"
            deltaX > THRESHOLD_X && deltaY > THRESHOLD_Y    -> "select"
            else -> "call-help"
        }
    }

    /**
     * Public method for manual/simulated gaze input.
     *
     * Allows UI buttons or test harnesses to trigger
     * dwell-based intent without real camera input.
     */
    fun simulateGaze(target: String) {
        dwellExecutor.execute {
            updateDwell(target)
        }
    }

    private fun updateDwell(target: String) {
        val now = System.currentTimeMillis()

        if (target == currentTarget) {
            if (now - dwellStartTime >= DWELL_THRESHOLD_MS) {
                if (!hasFired) {
                    Log.d(TAG, "Dwell threshold reached: $target")
                    onGazeIntent(target)
                    hasFired = true
                }
            }
        } else {
            currentTarget = target
            dwellStartTime = now
            hasFired = false
        }
    }

    fun resetDwell() {
        dwellExecutor.execute {
            currentTarget = null
            dwellStartTime = 0L
            hasFired = false
        }
    }

    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
        dwellExecutor.shutdown()
    }
}
