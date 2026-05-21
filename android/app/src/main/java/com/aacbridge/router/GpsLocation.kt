package com.aacbridge.router

/**
 * Immutable validated GPS snapshot.
 *
 * Represents a single fused-location reading from the Android
 * location subsystem.
 *
 * This is a trusted domain boundary type.
 *
 * IMPORTANT:
 * Coordinate validation is enforced HERE instead of inside
 * GPSScorer. Any instantiated GpsLocation is therefore
 * guaranteed mathematically valid throughout the routing layer.
 *
 * This prevents duplicated defensive validation across:
 * - GPSScorer
 * - StateRouter
 * - logging
 * - persistence
 * - cache orchestration
 *
 * Accuracy semantics:
 * accuracyMeters represents the Android fused-location
 * 68th percentile confidence radius.
 */
data class GpsLocation(

    /**
     * Latitude in decimal degrees.
     *
     * Valid range:
     * [-90, 90]
     */
    val lat: Double,

    /**
     * Longitude in decimal degrees.
     *
     * Valid range:
     * [-180, 180]
     */
    val lng: Double,

    /**
     * Estimated horizontal accuracy radius in meters.
     *
     * Must be:
     * - finite
     * - non-negative
     */
    val accuracyMeters: Float
) {

    init {

        require(lat.isFinite()) {
            "Latitude must be finite, received: $lat"
        }

        require(lng.isFinite()) {
            "Longitude must be finite, received: $lng"
        }

        require(lat in -90.0..90.0) {
            "Latitude must be within [-90, 90], received: $lat"
        }

        require(lng in -180.0..180.0) {
            "Longitude must be within [-180, 180], received: $lng"
        }

        require(accuracyMeters.isFinite()) {
            "Accuracy must be finite, received: $accuracyMeters"
        }

        require(accuracyMeters >= 0f) {
            "Accuracy must be non-negative, received: $accuracyMeters"
        }
    }
}