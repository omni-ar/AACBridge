package com.aacbridge.router

/**
 * Accept/reject decision for a single location fix.
 *
 * Pure function over the fields LocationValidator reads
 * from android.location.Location, so it is unit-testable
 * on the JVM.
 *
 * Checks, in order:
 * 1. MOCK: fix flagged by the OS as coming from a mock
 *    provider (Location.isMock / isFromMockProvider).
 * 2. NO_TIMESTAMP / FUTURE_TIMESTAMP / STALE: age from
 *    elapsedRealtimeNanos against SystemClock.elapsedRealtime.
 * 3. NO_ACCURACY / ACCURACY_TOO_FINE / ACCURACY_TOO_COARSE.
 * 4. INVALID_COORDINATES: GpsLocation invariants.
 *
 * Limits of these checks:
 * - The mock flag is set by the OS. A rooted device can
 *   deliver forged fixes without it.
 * - The accuracy floor only stops injectors that report
 *   implausibly exact fixes; a spoofer reporting 5 m passes.
 */
object LocationPolicy {

    /**
     * Maximum fix age.
     *
     * Two ContextDaemon sweep periods (2 x 60 s): a fix
     * from the previous sweep is still accepted, one from
     * before that is not. Engineering choice, not tuned on
     * data.
     */
    const val MAX_FIX_AGE_MS: Long = 120_000L

    /**
     * Allowed clock skew for fixes timestamped slightly
     * after "now" (provider and app read the clock at
     * different instants).
     */
    const val MAX_FUTURE_SKEW_MS: Long = 1_000L

    /**
     * Reported accuracy below this is treated as synthetic.
     *
     * Android reports a 68% horizontal radius; phone GNSS
     * rarely reports below a few metres. Dual-frequency
     * receivers in open sky can approach 1-2 m, so this
     * floor can reject a small number of genuine fixes.
     */
    const val MIN_PLAUSIBLE_ACCURACY_M: Float = 2.0f

    /**
     * Fixes coarser than this carry no usable signal at the
     * GPSScorer's 0.1 km decay scale.
     */
    const val MAX_USABLE_ACCURACY_M: Float = 500.0f

    enum class Rejection {
        MOCK,
        NO_TIMESTAMP,
        FUTURE_TIMESTAMP,
        STALE,
        NO_ACCURACY,
        ACCURACY_TOO_FINE,
        ACCURACY_TOO_COARSE,
        INVALID_COORDINATES
    }

    sealed class Decision {
        data class Accepted(val location: GpsLocation, val ageMs: Long) : Decision()
        data class Rejected(val reason: Rejection) : Decision()
    }

    /**
     * @param fixElapsedRealtimeNanos Location.elapsedRealtimeNanos
     *        (0 when the provider did not set it).
     * @param nowElapsedRealtimeMs SystemClock.elapsedRealtime().
     * @param hasAccuracy Location.hasAccuracy().
     */
    fun evaluate(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        hasAccuracy: Boolean,
        isMock: Boolean,
        fixElapsedRealtimeNanos: Long,
        nowElapsedRealtimeMs: Long,
        maxAgeMs: Long = MAX_FIX_AGE_MS,
        minAccuracyM: Float = MIN_PLAUSIBLE_ACCURACY_M,
        maxAccuracyM: Float = MAX_USABLE_ACCURACY_M
    ): Decision {

        if (isMock) return Decision.Rejected(Rejection.MOCK)

        if (fixElapsedRealtimeNanos <= 0L) {
            return Decision.Rejected(Rejection.NO_TIMESTAMP)
        }

        val ageMs = nowElapsedRealtimeMs - fixElapsedRealtimeNanos / 1_000_000L

        if (ageMs < -MAX_FUTURE_SKEW_MS) {
            return Decision.Rejected(Rejection.FUTURE_TIMESTAMP)
        }
        if (ageMs > maxAgeMs) {
            return Decision.Rejected(Rejection.STALE)
        }

        if (!hasAccuracy || !accuracyMeters.isFinite()) {
            return Decision.Rejected(Rejection.NO_ACCURACY)
        }
        if (accuracyMeters < minAccuracyM) {
            return Decision.Rejected(Rejection.ACCURACY_TOO_FINE)
        }
        if (accuracyMeters > maxAccuracyM) {
            return Decision.Rejected(Rejection.ACCURACY_TOO_COARSE)
        }

        return try {
            Decision.Accepted(
                location = GpsLocation(
                    lat = latitude,
                    lng = longitude,
                    accuracyMeters = accuracyMeters
                ),
                ageMs = ageMs.coerceAtLeast(0L)
            )
        } catch (_: IllegalArgumentException) {
            Decision.Rejected(Rejection.INVALID_COORDINATES)
        }
    }
}
