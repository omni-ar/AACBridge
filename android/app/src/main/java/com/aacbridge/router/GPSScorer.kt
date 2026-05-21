package com.aacbridge.router

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure mathematical scorer for spatial context similarity
 * and GPS hardware reliability.
 *
 * Decouples:
 *
 * 1. Spatial similarity score:
 *      S_gps = exp(-d / lambda)
 *
 * 2. GPS hardware reliability:
 *      w_g = exp(-accuracyMeters / lambdaAccuracyMeters)
 *
 * where:
 * - d is Haversine distance in kilometers
 * - accuracyMeters is Android fused-location uncertainty
 *
 * Clinical reasoning:
 *
 * lambdaKm = 0.1 (100 meters)
 *
 * AAC context is highly localized:
 * - patient room
 * - hospital ward
 * - cafeteria
 * - therapy area
 *
 * are semantically distinct environments even within
 * the same physical facility.
 *
 * Therefore:
 * - nearby contexts degrade rapidly
 * - distant contexts mathematically collapse
 */
class GPSScorer(

    /**
     * Spatial decay parameter in kilometers.
     */
    private val lambdaKm: Double = 0.1,

    /**
     * Reliability decay parameter for GPS accuracy.
     *
     * lambda = 50m chosen to preserve weak but useful
     * indoor building-level localization while still
     * suppressing highly unreliable cellular fixes.
     */
    private val lambdaAccuracyMeters: Double = 50.0
) {

    init {

        require(lambdaKm > 0.0) {
            "lambdaKm must be > 0, received: $lambdaKm"
        }

        require(lambdaAccuracyMeters > 0.0) {
            "lambdaAccuracyMeters must be > 0, received: $lambdaAccuracyMeters"
        }
    }

    /**
     * Calculates spatial similarity score between the
     * current GPS location and a state's anchor location.
     *
     * S_gps = exp(-d / lambda)
     *
     * where:
     * - d is Haversine distance in kilometers
     *
     * @param current Pre-validated current GPS location.
     * Contract:
     * Coordinates and accuracy are guaranteed valid by
     * GpsLocation.init.
     *
     * @param anchorLat Anchor latitude from ContextState.
     * @param anchorLng Anchor longitude from ContextState.
     *
     * @return Spatial similarity score bounded in [0,1].
     */
    fun score(
        current: GpsLocation,
        anchorLat: Double,
        anchorLng: Double
    ): Double {

        val distanceKm = calculateHaversineDistance(
            lat1 = current.lat,
            lon1 = current.lng,
            lat2 = anchorLat,
            lon2 = anchorLng
        )

        val exponent = -(distanceKm / lambdaKm)

        return exp(exponent)
            .coerceIn(0.0, 1.0)
    }

    /**
     * Calculates GPS hardware reliability weight.
     *
     * w_g = exp(-accuracyMeters / lambdaAccuracyMeters)
     *
     * This is NOT the spatial similarity score.
     *
     * Reliability only represents confidence in the
     * hardware signal itself.
     *
     * Example:
     * - 5m accuracy   -> high trust
     * - 50m accuracy  -> weak but usable indoor trust
     * - 100m accuracy -> heavily suppressed
     *
     * @param accuracyMeters Pre-validated fused-location
     * accuracy radius from GpsLocation.
     *
     * @return Reliability weight bounded in [0,1].
     */
    fun calculateReliability(
        accuracyMeters: Float
    ): Double {

        val exponent =
            -(accuracyMeters.toDouble() / lambdaAccuracyMeters)

        return exp(exponent)
            .coerceIn(0.0, 1.0)
    }

    /**
     * Computes great-circle distance using the
     * Haversine formula.
     *
     * @return Distance in kilometers.
     */
    internal fun calculateHaversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {

        val earthRadiusKm = 6371.0

        val dLat =
            Math.toRadians(lat2 - lat1)

        val dLon =
            Math.toRadians(lon2 - lon1)

        val originLat =
            Math.toRadians(lat1)

        val destinationLat =
            Math.toRadians(lat2)

        val a =
            sin(dLat / 2).pow(2) +
                    sin(dLon / 2).pow(2) *
                    cos(originLat) *
                    cos(destinationLat)

        val c =
            2.0 * asin(sqrt(a))

        return earthRadiusKm * c
    }
}