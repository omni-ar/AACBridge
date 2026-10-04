package com.aacbridge.router

import android.location.Location
import android.os.Build
import android.os.SystemClock

/**
 * Validates Android Location objects before they enter
 * the routing pipeline as GpsLocation snapshots.
 *
 * Three independent rejection criteria:
 *
 * 1. MOCK DETECTION
 *    Rejects locations from mock providers.
 *    Uses Location.isMock() on API 31+ and
 *    Location.isFromMockProvider() on older APIs.
 *
 * 2. STALENESS
 *    Rejects locations older than a configurable threshold.
 *    Prevents stale cached fixes from influencing routing
 *    after prolonged indoor periods or GPS outages.
 *
 * 3. ACCURACY SANITY
 *    Rejects locations with suspiciously high accuracy
 *    (< MIN_PLAUSIBLE_ACCURACY_METERS) that may indicate
 *    spoofed or synthetic fixes.
 *    Also rejects extremely poor fixes above the maximum.
 *
 * Design rationale:
 * Validation is centralized here rather than scattered
 * across ActiveSweep and DriftDetector to ensure
 * consistent rejection behavior.
 *
 * Security context:
 * These checks raise the bar against casual mock-location
 * attacks but do NOT provide cryptographic GPS provenance.
 * A rooted device with modified system frameworks can
 * bypass all application-level mock detection.
 */
class LocationValidator(

    /**
     * Maximum acceptable age of a location fix in milliseconds.
     *
     * Default: 120_000 (2 minutes).
     *
     * Rationale:
     * The ContextDaemon sweeps every 60 seconds. A 2-minute
     * threshold allows one missed sweep cycle while rejecting
     * fixes from previous sessions or prolonged GPS outages.
     *
     * Sensitivity analysis values: 30s, 60s, 120s, 300s.
     * 120s was selected as a conservative default balancing
     * indoor GPS recovery time against staleness risk.
     */
    private val maxAgeMs: Long = 120_000L,

    /**
     * Minimum plausible GPS accuracy in meters.
     *
     * Accuracy values below this threshold are suspicious
     * because consumer-grade GPS/GNSS typically achieves
     * 3-5m at best in open sky conditions.
     *
     * A reported accuracy of 0.1m or 1.0m on a mobile
     * device likely indicates a mock or synthetic fix.
     */
    private val minPlausibleAccuracyMeters: Float = 2.0f,

    /**
     * Maximum acceptable GPS accuracy in meters.
     *
     * Fixes with accuracy worse than this are too imprecise
     * for meaningful spatial routing at the 100m scale
     * (GPSScorer lambda = 0.1 km).
     */
    private val maxAcceptableAccuracyMeters: Float = 500.0f
) {

    init {
        require(maxAgeMs > 0) {
            "maxAgeMs must be > 0, received: $maxAgeMs"
        }
        require(minPlausibleAccuracyMeters > 0f) {
            "minPlausibleAccuracyMeters must be > 0"
        }
        require(maxAcceptableAccuracyMeters > minPlausibleAccuracyMeters) {
            "maxAcceptableAccuracyMeters must exceed minPlausibleAccuracyMeters"
        }
    }

    /**
     * Validates an Android Location object.
     *
     * @return A validated GpsLocation if all checks pass,
     *         null if the location is rejected.
     */
    fun validate(location: Location): GpsLocation? {

        // 1. Mock detection
        if (isMockLocation(location)) {
            return null
        }

        // 2. Staleness check
        if (isStale(location)) {
            return null
        }

        // 3. Accuracy sanity
        if (!isAccuracyPlausible(location)) {
            return null
        }

        // 4. Coordinate validity (delegated to GpsLocation init)
        return try {
            GpsLocation(
                lat = location.latitude,
                lng = location.longitude,
                accuracyMeters = location.accuracy
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Detects mock/simulated location providers.
     *
     * API 31+ (Android 12): Location.isMock()
     * API 18-30: Location.isFromMockProvider()
     *
     * Limitation:
     * A rooted device with modified system frameworks
     * can bypass this check by patching the Location
     * object before delivery to the application.
     */
    internal fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    /**
     * Checks whether the location fix is older than maxAgeMs.
     *
     * Uses elapsedRealtimeNanos (monotonic clock) to avoid
     * issues with system clock adjustments.
     */
    internal fun isStale(location: Location): Boolean {
        val fixElapsedMs =
            location.elapsedRealtimeNanos / 1_000_000L

        val currentElapsedMs =
            SystemClock.elapsedRealtime()

        val ageMs = currentElapsedMs - fixElapsedMs

        return ageMs > maxAgeMs
    }

    /**
     * Validates that reported accuracy falls within
     * a physically plausible range.
     *
     * Rejects:
     * - Suspiciously precise fixes (< minPlausibleAccuracyMeters)
     * - Extremely imprecise fixes (> maxAcceptableAccuracyMeters)
     * - Non-finite accuracy values
     */
    internal fun isAccuracyPlausible(location: Location): Boolean {
        val accuracy = location.accuracy

        if (!accuracy.isFinite()) return false
        if (accuracy < minPlausibleAccuracyMeters) return false
        if (accuracy > maxAcceptableAccuracyMeters) return false

        return true
    }
}
