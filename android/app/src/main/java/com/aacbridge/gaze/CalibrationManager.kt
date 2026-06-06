package com.aacbridge.gaze

import android.content.Context

/**
 * Minimal SharedPreferences wrapper for gaze tracking thresholds.
 *
 * Provides persistence for user-specific calibration values.
 * A full guided calibration UI flow is deferred to future clinical
 * deployment iterations.
 */
class CalibrationManager(context: Context) {
    private val prefs = context.getSharedPreferences("aacbridge_calibration", Context.MODE_PRIVATE)

    /** Returns calibrated X threshold, or default 0.02f if uncalibrated. */
    fun getThresholdX(): Float = prefs.getFloat("threshold_x", 0.02f)

    /** Returns calibrated Y threshold, or default 0.01f if uncalibrated. */
    fun getThresholdY(): Float = prefs.getFloat("threshold_y", 0.01f)

    /** Persists user-specific thresholds. Called externally by future calibration UI. */
    fun saveCalibration(thresholdX: Float, thresholdY: Float) {
        prefs.edit()
            .putFloat("threshold_x", thresholdX)
            .putFloat("threshold_y", thresholdY)
            .apply()
    }
}
