package com.aacbridge.router

import android.location.Location
import android.os.Build
import android.os.SystemClock

/**
 * Validates Android Location objects before they enter
 * the routing pipeline as GpsLocation snapshots.
 *
 * Reads the relevant fields from android.location.Location
 * and delegates the decision to LocationPolicy (pure,
 * unit-tested). See LocationPolicy for the checks and
 * their limits.
 *
 * Security context:
 * These checks raise the bar against non-root mock-location
 * apps and stale fixes. They do NOT provide GPS provenance:
 * a rooted device with modified system frameworks can
 * deliver forged fixes without the mock flag.
 */
class LocationValidator(
    private val maxAgeMs: Long = LocationPolicy.MAX_FIX_AGE_MS,
    private val minPlausibleAccuracyMeters: Float = LocationPolicy.MIN_PLAUSIBLE_ACCURACY_M,
    private val maxAcceptableAccuracyMeters: Float = LocationPolicy.MAX_USABLE_ACCURACY_M
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

    fun evaluate(location: Location): LocationPolicy.Decision =
        LocationPolicy.evaluate(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy,
            hasAccuracy = location.hasAccuracy(),
            isMock = isMockLocation(location),
            fixElapsedRealtimeNanos = location.elapsedRealtimeNanos,
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            maxAgeMs = maxAgeMs,
            minAccuracyM = minPlausibleAccuracyMeters,
            maxAccuracyM = maxAcceptableAccuracyMeters
        )

    /**
     * @return A validated GpsLocation, or null if rejected.
     */
    fun validate(location: Location): GpsLocation? =
        (evaluate(location) as? LocationPolicy.Decision.Accepted)?.location

    /**
     * API 31+ (Android 12): Location.isMock()
     * API 18-30: Location.isFromMockProvider()
     */
    internal fun isMockLocation(location: Location): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }
}
