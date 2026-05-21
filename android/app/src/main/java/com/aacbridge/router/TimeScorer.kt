package com.aacbridge.router

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Pure mathematical scorer for temporal context similarity.
 *
 * Uses circular time distance on a 24-hour clock:
 *
 * d = min(|t1 - t2|, 24 - |t1 - t2|)
 *
 * Then applies Gaussian decay:
 *
 * S_time = exp(-(d^2) / (2 * sigma^2))
 *
 * Properties:
 * - Output bounded in [0,1]
 * - Deterministic
 * - No Android dependencies
 * - Unit testable
 *
 * Clinical reasoning:
 * sigma = 2.0 hours allows graceful degradation
 * for normal AAC routine deviations:
 * - delayed caregiver interaction
 * - late meals
 * - schedule drift
 *
 * Design philosophy:
 * This scorer is fault-tolerant rather than fail-fast.
 *
 * Finite malformed inputs are normalized onto the circular
 * 24-hour domain instead of crashing the routing pipeline.
 *
 * Example:
 * 25.5 -> 1.5
 * -0.5 -> 23.5
 */
class TimeScorer(
    private val sigmaHours: Double = 2.0
) {

    init {

        require(sigmaHours > 0.0) {
            "sigmaHours must be > 0, received: $sigmaHours"
        }
    }

    /**
     * Calculates temporal similarity between the current time
     * and a state's anchor time.
     *
     * @param currentHourDecimal Current local time in decimal hours.
     * Example:
     * 13.5 = 1:30 PM
     *
     * @param anchorHourDecimal State anchor time in decimal hours.
     *
     * @return Similarity score bounded in [0,1].
     */
    fun score(
        currentHourDecimal: Double,
        anchorHourDecimal: Double
    ): Double {

        validateHour(currentHourDecimal)
        validateHour(anchorHourDecimal)

        val normalizedCurrent =
            normalizeHour(currentHourDecimal)

        val normalizedAnchor =
            normalizeHour(anchorHourDecimal)

        val rawDistance =
            abs(normalizedCurrent - normalizedAnchor)

        val circularDistance =
            min(rawDistance, 24.0 - rawDistance)

        val exponent =
            -(circularDistance * circularDistance) /
                    (2.0 * sigmaHours * sigmaHours)

        return exp(exponent)
            .coerceIn(0.0, 1.0)
    }

    /**
     * Rejects mathematically invalid floating-point states.
     *
     * Range validation is intentionally NOT enforced because
     * finite malformed inputs are recoverable via modulo
     * normalization on the circular clock domain.
     */
    private fun validateHour(hour: Double) {

        require(hour.isFinite()) {
            "Hour must be finite, received: $hour"
        }
    }

    /**
     * Maps arbitrary finite values onto the circular
     * 24-hour clock domain.
     *
     * Examples:
     * 25.5 -> 1.5
     * -0.5 -> 23.5
     * 24.0 -> 0.0
     */
    private fun normalizeHour(hour: Double): Double {

        return ((hour % 24.0) + 24.0) % 24.0
    }
}