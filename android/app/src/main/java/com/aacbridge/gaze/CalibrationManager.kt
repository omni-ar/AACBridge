package com.aacbridge.gaze

import android.content.Context
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Gaze calibration: neutral baseline capture plus threshold persistence.
 *
 * WHY A NEUTRAL BASELINE IS REQUIRED
 * ----------------------------------
 * The previous implementation compared raw
 * (noseTip - eyeCenter) displacement against absolute
 * thresholds. That cannot work, because in human anatomy
 * the nose tip sits BELOW the eye center, so deltaY is
 * always positive (~+0.06 to +0.08 in normalized image
 * coordinates), and holding a phone naturally pitches the
 * camera upward, pushing it further positive.
 *
 * With THRESHOLD_Y = 0.01f, the predicate deltaY < -0.01f
 * was unsatisfiable, so "confirm" and "reject" could never
 * fire and the classifier locked onto "scroll".
 *
 * The fix is to subtract a per-user resting displacement
 * captured while the user looks at screen center, so that
 * looking up yields a negative value and looking down a
 * positive one.
 *
 * WHY SCALE NORMALIZATION IS REQUIRED
 * -----------------------------------
 * MediaPipe landmarks are normalized to the image, not to
 * the face, so displacement grows as the face fills more
 * of the frame. A baseline captured at arm's length would
 * be wrong at 20 cm. All values handled here are divided
 * by interocular distance, making them face-relative
 * ratios rather than image-relative offsets.
 */
class CalibrationManager(context: Context) {

    companion object {
        private const val PREFS = "aacbridge_calibration"

        private const val KEY_NEUTRAL_X = "neutral_x"
        private const val KEY_NEUTRAL_Y = "neutral_y"
        private const val KEY_THRESHOLD_X = "threshold_x"
        private const val KEY_THRESHOLD_Y = "threshold_y"

        /**
         * Defaults expressed as a FRACTION OF INTEROCULAR
         * DISTANCE, not as raw normalized coordinates.
         * The old 0.02f / 0.01f values are not comparable
         * and must not be carried over.
         */
        const val DEFAULT_THRESHOLD_X = 0.06f
        const val DEFAULT_THRESHOLD_Y = 0.05f

        /** Below this on both axes the gaze counts as centered. */
        const val DEADBAND = 0.04f

        /** Minimum samples for a usable calibration (~0.7 s at 30 fps). */
        private const val MIN_SAMPLES = 20

        /** Reject calibration if the user moved this much during capture. */
        private const val MAX_SPREAD = 0.15f

        /**
         * Scale-normalized displacement. Divides the raw
         * nose-to-eye-center offset by interocular distance
         * so the result is invariant to face size.
         */
        fun normalize(
            deltaX: Float,
            deltaY: Float,
            interocular: Float
        ): Pair<Float, Float> {
            val d = if (interocular < 1e-4f) 1e-4f else interocular
            return Pair(deltaX / d, deltaY / d)
        }

        /** Euclidean distance between the two outer eye corners. */
        fun interocular(
            leftX: Float, leftY: Float,
            rightX: Float, rightY: Float
        ): Float = hypot(rightX - leftX, rightY - leftY)
    }

    private val prefs =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    var neutralX: Float = prefs.getFloat(KEY_NEUTRAL_X, Float.NaN)
        private set

    @Volatile
    var neutralY: Float = prefs.getFloat(KEY_NEUTRAL_Y, Float.NaN)
        private set

    /**
     * True once a neutral baseline exists. Gaze intents
     * MUST NOT fire before this is true -- without a
     * baseline every reading is meaningless.
     */
    val isCalibrated: Boolean
        get() = !neutralX.isNaN() && !neutralY.isNaN()

    @Volatile
    var isCalibrating: Boolean = false
        private set

    /** Samples collected so far in the current capture. */
    val sampleCount: Int
        @Synchronized get() = samplesX.size

    private val samplesX = ArrayList<Float>()
    private val samplesY = ArrayList<Float>()

    fun getThresholdX(): Float =
        prefs.getFloat(KEY_THRESHOLD_X, DEFAULT_THRESHOLD_X)

    fun getThresholdY(): Float =
        prefs.getFloat(KEY_THRESHOLD_Y, DEFAULT_THRESHOLD_Y)

    fun saveThresholds(thresholdX: Float, thresholdY: Float) {
        prefs.edit()
            .putFloat(KEY_THRESHOLD_X, thresholdX)
            .putFloat(KEY_THRESHOLD_Y, thresholdY)
            .apply()
    }

    /**
     * Starts baseline capture. The UI must show a fixation
     * target at screen centre and call finishCalibration()
     * after roughly two seconds.
     */
    @Synchronized
    fun beginCalibration() {
        samplesX.clear()
        samplesY.clear()
        isCalibrating = true
    }

    /**
     * Feeds one scale-normalized sample. Called from
     * GazeTracker while isCalibrating is true.
     */
    @Synchronized
    fun addSample(nx: Float, ny: Float) {
        if (!isCalibrating) return
        samplesX.add(nx)
        samplesY.add(ny)
    }

    /**
     * Commits the baseline.
     *
     * Uses the MEDIAN, not the mean: a handful of frames
     * where MediaPipe loses or mis-fits the face produce
     * outliers that would drag an average far off.
     *
     * @return false if too few samples or the face moved
     *         too much during capture -- the caller should
     *         prompt the user to try again.
     */
    @Synchronized
    fun finishCalibration(): Boolean {
        isCalibrating = false

        if (samplesX.size < MIN_SAMPLES) return false

        val mx = median(samplesX)
        val my = median(samplesY)

        val spread = samplesY.map { abs(it - my) }.average().toFloat()
        if (spread > MAX_SPREAD) return false

        neutralX = mx
        neutralY = my

        prefs.edit()
            .putFloat(KEY_NEUTRAL_X, mx)
            .putFloat(KEY_NEUTRAL_Y, my)
            .apply()

        return true
    }

    @Synchronized
    fun reset() {
        isCalibrating = false
        neutralX = Float.NaN
        neutralY = Float.NaN
        samplesX.clear()
        samplesY.clear()
        prefs.edit()
            .remove(KEY_NEUTRAL_X)
            .remove(KEY_NEUTRAL_Y)
            .apply()
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }
}