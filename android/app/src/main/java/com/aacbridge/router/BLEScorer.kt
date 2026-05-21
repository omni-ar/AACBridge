package com.aacbridge.router

import kotlin.math.exp

/**
 * Pure mathematical scorer for BLE spatial context
 * and BLE hardware reliability.
 *
 * Decouples:
 *
 * 1. Topological BLE match score:
 *      S_ble
 *
 * 2. BLE hardware reliability:
 *      R_ble (gamma)
 *
 * IMPORTANT:
 * These are NOT the same concept.
 *
 * - S_ble measures semantic/contextual overlap
 * - R_ble measures trust in the live BLE environment
 *
 * This separation prevents weak RSSI signals from being
 * incorrectly interpreted as semantic mismatch.
 */
class BLEScorer(

    /**
     * RSSI midpoint for sigmoid confidence.
     *
     * At:
     * rssi = r0
     *
     * sigmoid(rssi) = 0.5
     */
    private val r0: Double = -70.0,

    /**
     * Sigmoid steepness parameter.
     */
    private val k: Double = 0.1
) {

    init {

        require(k > 0.0) {
            "k must be > 0, received: $k"
        }
    }

    /**
     * Calculates topological BLE match score.
     *
     * Semantic interpretation:
     * "How well does the current BLE environment match
     * the expected BLE anchors for this context state?"
     *
     * Missing devices:
     * - contribute 0 to numerator
     * - still contribute static weight to denominator
     *
     * This properly penalizes partial context matches.
     *
     * Example:
     * If a patient's room normally contains:
     * - bedside tablet
     * - caregiver phone
     * - smart speaker
     *
     * but only one is currently visible,
     * the context similarity degrades proportionally.
     *
     * @param detected Currently visible BLE devices mapped
     * to their RSSI readings.
     *
     * @param registered Pre-validated expected BLE anchors
     * mapped to static importance weights.
     *
     * Contract:
     * All static weights MUST be strictly > 0.0.
     * Enforcement belongs to the database ingestion layer,
     * not the scorer.
     *
     * @return Match score bounded in [0,1].
     */
    fun score(
        detected: Map<String, Rssi>,
        registered: Map<String, Double>
    ): Double {

        // State has no BLE anchors
        if (registered.isEmpty()) {
            return 0.0
        }

        var matchedWeight = 0.0
        var totalWeight = 0.0

        for ((mac, staticWeight) in registered) {

            totalWeight += staticWeight

            // xi = 1 if detected, 0 otherwise
            if (detected.containsKey(mac)) {
                matchedWeight += staticWeight
            }
        }

        return if (totalWeight > 0.0) {
            (matchedWeight / totalWeight)
                .coerceIn(0.0, 1.0)
        } else {
            0.0
        }
    }

    /**
     * Calculates BLE hardware reliability weight.
     *
     * R_ble = average(sigmoid(rssi))
     *
     * IMPORTANT:
     * This is NOT the semantic BLE match score.
     *
     * Reliability only measures:
     * - signal quality
     * - environmental BLE confidence
     * - scan trustworthiness
     *
     * Semantic matching is handled separately by score().
     *
     * M = number of currently detected BLE devices.
     *
     * Critical edge case:
     * If M = 0:
     *
     * BLE modality is considered unavailable,
     * not semantically incorrect.
     *
     * Therefore:
     * R_ble = 0.0
     *
     * which allows graceful fallback to:
     * - temporal routing
     * - GPS routing
     *
     * without divide-by-zero failures.
     *
     * @param detected Currently visible BLE devices.
     *
     * @return Reliability weight bounded in [0,1].
     */
    fun calculateReliability(
        detected: Map<String, Rssi>
    ): Double {

        val m = detected.size

        // BLE modality unavailable
        if (m == 0) {
            return 0.0
        }

        var sumSigmoid = 0.0

        for ((_, rssi) in detected) {

            /*
             * sigmoid(rssi) =
             * 1 / (1 + exp(-k * (rssi - r0)))
             */
            val exponent =
                -k * (rssi.value - r0)

            val sigmoid =
                1.0 / (1.0 + exp(exponent))

            sumSigmoid += sigmoid
        }

        return (sumSigmoid / m)
            .coerceIn(0.0, 1.0)
    }
}