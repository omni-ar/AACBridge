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
import kotlin.math.abs
import kotlin.math.hypot

/**
 * MediaPipe Face Mesh gaze tracking for AAC interaction.
 *
 * Geometry
 * --------
 * Displacement between nose tip (landmark 4) and eye
 * centre (midpoint of inner corners 33 and 362), divided
 * by interocular distance to remove face-scale
 * dependence, then offset by a per-user neutral baseline
 * captured through CalibrationManager.
 *
 * After baseline subtraction:
 *   gy < 0  => looking up
 *   gy > 0  => looking down
 *   gx < 0  => looking one way, gx > 0 the other
 *              (see MIRROR_X below)
 *
 * Targets
 * -------
 *   top-left     = confirm
 *   top-right    = reject
 *   bottom-left  = scroll
 *   bottom-right = select
 *   centre       = NO INTENT (long dwell => call-help)
 *
 * call-help is deliberately NOT the fallthrough branch.
 * When it was, every ambiguous frame -- face partly out of
 * frame, mid-saccade, user simply reading the screen --
 * resolved to a request for assistance. For an AAC device
 * that is the worst available failure mode.
 *
 * Thread safety
 * -------------
 * Dwell evaluation is serialized through a single-thread
 * executor so asynchronous MediaPipe callbacks cannot
 * interleave FSM transitions.
 *
 * This layer emits ONLY UI-level intent events. It does
 * not invoke JNI, KV cache, or backend routing.
 */
class GazeTracker(
    private val context: Context,
    private val onGazeIntent: (String) -> Unit,
    private val onGazeVector: ((FloatArray) -> Unit)? = null,
    private val onDwellProgress: ((Float) -> Unit)? = null,
    private val onOcclusionStateChanged: ((Boolean) -> Unit)? = null,
    private val calibrationManager: CalibrationManager? = null,
    private val onCalibrationNeeded: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "GazeTracker"

        private const val DWELL_THRESHOLD_MS = 400L

        /** Sustained centre fixation that deliberately requests help. */
        private const val HELP_DWELL_MS = 1500L

        private const val OCCLUSION_THRESHOLD_FRAMES = 10

        /**
         * CameraX front-camera ImageAnalysis frames are NOT
         * mirrored, but the user's mental model is. Verify
         * once on device: look hard at the top-left target
         * and read gx in logcat. If gx is positive, flip
         * this to true.
         */
        private const val MIRROR_X = false

        /** Rate limit for the calibration-needed callback. */
        private const val CALIB_PROMPT_INTERVAL_MS = 5000L
    }

    private var faceLandmarker: FaceLandmarker? = null

    private var currentTarget: String? = null
    private var dwellStartTime: Long = 0L
    private var hasFired: Boolean = false

    private var centerDwellStart: Long = 0L
    private var helpFired: Boolean = false

    private var emptyFramesCount = 0
    private var lastCalibPrompt = 0L

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
                .setErrorListener { e: RuntimeException ->
                    Log.e(TAG, "FaceLandmarker error: ${e.message}")
                }
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(TAG, "FaceLandmarker initialized")
        } catch (e: Exception) {
            Log.w(TAG, "FaceLandmarker init failed (model asset may be missing). " +
                       "Gaze tracking will use manual/simulated input only.", e)
        }
    }

    fun processImageProxy(imageProxy: ImageProxy) {
        if (faceLandmarker == null) {
            imageProxy.close()
            return
        }

        try {
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = imageProxy.imageInfo.timestamp / 1_000_000
            faceLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image proxy", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun processResult(result: FaceLandmarkerResult) {
        val faces = result.faceLandmarks()

        if (faces.isNullOrEmpty()) {
            emptyFramesCount++
            if (emptyFramesCount == OCCLUSION_THRESHOLD_FRAMES) {
                onOcclusionStateChanged?.invoke(true)
                resetDwell()
            }
            return
        }

        if (emptyFramesCount >= OCCLUSION_THRESHOLD_FRAMES) {
            onOcclusionStateChanged?.invoke(false)
        }
        emptyFramesCount = 0

        val face = faces[0]

        // 4 = nose tip
        // 33 / 362 = eye inner corners  (used for the centre)
        // 130 / 359 = eye OUTER corners (wider, more stable scale ref)
        val noseTip = face[4]
        val leftInner = face[33]
        val rightInner = face[362]
        val leftOuter = face[130]
        val rightOuter = face[359]

        val eyeCenterX = (leftInner.x() + rightInner.x()) / 2f
        val eyeCenterY = (leftInner.y() + rightInner.y()) / 2f

        val rawDx = noseTip.x() - eyeCenterX
        val rawDy = noseTip.y() - eyeCenterY

        val iod = CalibrationManager.interocular(
            leftOuter.x(), leftOuter.y(),
            rightOuter.x(), rightOuter.y()
        )

        val (nx, ny) = CalibrationManager.normalize(rawDx, rawDy, iod)

        val calib = calibrationManager

        // Feed the baseline capture instead of classifying.
        if (calib != null && calib.isCalibrating) {
            calib.addSample(nx, ny)
            return
        }

        // Without a baseline every reading is meaningless.
        if (calib == null || !calib.isCalibrated) {
            val now = System.currentTimeMillis()
            if (now - lastCalibPrompt > CALIB_PROMPT_INTERVAL_MS) {
                lastCalibPrompt = now
                onCalibrationNeeded?.invoke()
            }
            return
        }

        var gx = nx - calib.neutralX
        val gy = ny - calib.neutralY

        if (MIRROR_X) gx = -gx

        val target = mapGazeToTarget(gx, gy, calib)

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "gaze gx=%.3f gy=%.3f iod=%.3f -> %s"
                .format(gx, gy, iod, target ?: "center"))
        }

        // 5-dim feature vector for the fusion model, built
        // from the SAME baseline-corrected, scale-normalized
        // values the classifier uses.
        // NOTE: the deployed fusion ONNX was trained on the
        // old raw-delta distribution. Retrain or re-export
        // against these values before trusting its output.
        val magnitude = hypot(gx, gy)
        val gazeFeatures = floatArrayOf(gx, gy, abs(gx), abs(gy), magnitude)

        dwellExecutor.execute {
            onGazeVector?.invoke(gazeFeatures)
            updateDwell(target)
        }
    }

    /**
     * Maps baseline-corrected gaze to a quadrant target.
     *
     * Returns null for the centre dead zone and for
     * readings that clear the dead zone on only one axis.
     * A null result must NOT be coerced into an intent.
     */
    private fun mapGazeToTarget(
        gx: Float,
        gy: Float,
        calib: CalibrationManager
    ): String? {

        val threshX = calib.getThresholdX()
        val threshY = calib.getThresholdY()

        if (abs(gx) < CalibrationManager.DEADBAND &&
            abs(gy) < CalibrationManager.DEADBAND) {
            return null
        }

        if (abs(gx) < threshX || abs(gy) < threshY) {
            return null
        }

        return when {
            gx < 0 && gy < 0 -> "confirm"
            gx > 0 && gy < 0 -> "reject"
            gx < 0 && gy > 0 -> "scroll"
            else             -> "select"
        }
    }

    /**
     * Manual/simulated gaze input for UI buttons and test
     * harnesses.
     */
    fun simulateGaze(target: String) {
        dwellExecutor.execute { updateDwell(target) }
    }

    private fun updateDwell(target: String?) {
        val now = System.currentTimeMillis()

        // Centre: no quadrant intent. A long, deliberate
        // fixation here is the explicit call-help gesture.
        if (target == null) {
            if (currentTarget != null) {
                currentTarget = null
                dwellStartTime = 0L
                hasFired = false
                onDwellProgress?.invoke(0f)
            }

            if (centerDwellStart == 0L) {
                centerDwellStart = now
                helpFired = false
                return
            }

            val held = now - centerDwellStart
            onDwellProgress?.invoke(
                (held.toFloat() / HELP_DWELL_MS).coerceIn(0f, 1f)
            )

            if (held >= HELP_DWELL_MS && !helpFired) {
                Log.d(TAG, "Centre dwell threshold reached: call-help")
                onGazeIntent("call-help")
                helpFired = true
            }
            return
        }

        centerDwellStart = 0L
        helpFired = false

        if (target == currentTarget) {
            val progress =
                ((now - dwellStartTime).toFloat() / DWELL_THRESHOLD_MS)
                    .coerceIn(0f, 1f)
            onDwellProgress?.invoke(progress)

            if (now - dwellStartTime >= DWELL_THRESHOLD_MS && !hasFired) {
                Log.d(TAG, "Dwell threshold reached: $target")
                onGazeIntent(target)
                hasFired = true
            }
        } else {
            currentTarget = target
            dwellStartTime = now
            hasFired = false
            onDwellProgress?.invoke(0f)
        }
    }

    fun resetDwell() {
        dwellExecutor.execute {
            currentTarget = null
            dwellStartTime = 0L
            hasFired = false
            centerDwellStart = 0L
            helpFired = false
            onDwellProgress?.invoke(0f)
        }
    }

    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
        dwellExecutor.shutdown()
    }
}