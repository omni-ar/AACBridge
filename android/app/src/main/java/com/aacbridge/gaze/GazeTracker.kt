package com.aacbridge.gaze

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

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
    }

    private var faceLandmarker: FaceLandmarker? = null
    private var currentTarget: String? = null
    private var dwellStartTime: Long = 0L

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
                .setMinFaceTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ -> processResult(result) }
                .setErrorListener { e -> Log.e(TAG, "FaceLandmarker error: ${e.message}") }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(TAG, "FaceLandmarker initialized")
        } catch (e: Exception) {
            Log.w(TAG, "FaceLandmarker init failed (model asset may be missing). " +
                       "Gaze tracking will use manual/simulated input only.", e)
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
        updateDwell(target)
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
            deltaX < -0.02f && deltaY < -0.01f -> "confirm"
            deltaX > 0.02f && deltaY < -0.01f  -> "reject"
            deltaX < -0.02f && deltaY > 0.01f   -> "scroll"
            deltaX > 0.02f && deltaY > 0.01f    -> "select"
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
        updateDwell(target)
    }

    private fun updateDwell(target: String) {
        val now = System.currentTimeMillis()

        if (target == currentTarget) {
            if (now - dwellStartTime >= DWELL_THRESHOLD_MS) {
                Log.d(TAG, "Dwell threshold reached: $target")
                onGazeIntent(target)
                dwellStartTime = now // prevent rapid re-firing
            }
        } else {
            currentTarget = target
            dwellStartTime = now
        }
    }

    fun resetDwell() {
        currentTarget = null
        dwellStartTime = 0L
    }

    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }
}
