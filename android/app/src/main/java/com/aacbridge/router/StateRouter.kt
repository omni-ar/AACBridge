package com.aacbridge.router

/**
 * Deterministic context-state ranking engine.
 *
 * Responsibilities:
 * - Evaluate semantic relevance of all candidate states
 * - Dynamically combine:
 *      - temporal similarity
 *      - spatial similarity
 *      - BLE topology similarity
 * - Return highest-relevance context state IDs
 *
 * IMPORTANT:
 * This class is intentionally:
 * - synchronous
 * - deterministic
 * - side-effect free
 * - Android-independent
 *
 * It does NOT:
 * - load KV caches
 * - access JNI
 * - access Room/SQLite
 * - trigger BLE scans
 * - perform async operations
 */
class StateRouter(
    private val timeScorer: TimeScorer,
    private val gpsScorer: GPSScorer,
    private val bleScorer: BLEScorer
) {

    companion object {

        /**
         * Context states below this threshold are treated
         * as mathematically dead.
         */
        private const val DEAD_STATE_THRESHOLD = 0.01
    }

    /**
     * Exposes raw mathematical relevance scores for all
     * candidate states surviving the dead-state threshold.
     *
     * Required by:
     * - DriftDetector
     * - hysteresis margin evaluation
     * - telemetry/debugging
     *
     * IMPORTANT:
     * This method intentionally does NOT apply top-N limiting.
     *
     * @return Ordered list of:
     * (stateId to relevanceScore)
     */
    fun getScoredStates(
        snapshot: SensorSnapshot,
        states: List<ContextState>
    ): List<Pair<String, Double>> {

        return states
            .asSequence()

            .map { state ->

                state.stateId to calculateStateScore(
                    snapshot = snapshot,
                    state = state
                )
            }

            .filter { (_, score) ->
                score >= DEAD_STATE_THRESHOLD
            }

            .sortedByDescending { (_, score) ->
                score
            }

            .toList()
    }

    /**
     * Convenience wrapper for predictive cache loading.
     *
     * Returns only the highest-ranked semantic state IDs.
     *
     * Internally delegates to:
     * getScoredStates()
     *
     * This preserves backward compatibility with:
     * - ActiveSweep
     * - KVCacheManager orchestration
     */
    fun getTopContextIds(
        snapshot: SensorSnapshot,
        states: List<ContextState>,
        limit: Int = HardwareConfig.MAX_ACTIVE_KV_STATES
    ): List<String> {

        require(limit > 0) {
            "limit must be > 0, received: $limit"
        }

        return getScoredStates(
            snapshot = snapshot,
            states = states
        )
            .take(limit)

            .map { (stateId, _) ->
                stateId
            }
    }

    /**
     * Evaluates semantic relevance of a single context state.
     *
     * Final convex combination:
     *
     * S(ci) =
     *      alpha * S_time +
     *      beta  * S_gps  +
     *      gamma * S_ble
     *
     * Dynamic reliability behavior:
     *
     * - Time acts as stable fallback baseline
     * - GPS reliability decays with poor accuracy
     * - BLE reliability decays with weak/no signals
     * - GPS null -> wg = 0.0
     * - M=0 BLE -> wb = 0.0
     *
     * @return Final semantic relevance score bounded in [0,1].
     */
    internal fun calculateStateScore(
        snapshot: SensorSnapshot,
        state: ContextState
    ): Double {

        // -----------------------------------------
        // 1. Temporal scoring
        // -----------------------------------------

        val wt = HardwareConfig.TIME_BASELINE_WEIGHT

        val sTime = timeScorer.score(
            currentHourDecimal = snapshot.currentHourDecimal,
            anchorHourDecimal = state.expectedTime
        )

        // -----------------------------------------
        // 2. GPS scoring
        // -----------------------------------------

        val wg: Double
        val sGps: Double

        if (snapshot.location != null) {

            wg = gpsScorer.calculateReliability(
                snapshot.location.accuracyMeters
            )

            sGps = gpsScorer.score(
                current = snapshot.location,
                anchorLat = state.lat,
                anchorLng = state.lng
            )

        } else {

            wg = 0.0
            sGps = 0.0
        }

        // -----------------------------------------
        // 3. BLE scoring
        // -----------------------------------------

        val wb = bleScorer.calculateReliability(
            snapshot.detectedBleDevices
        )

        val sBle = bleScorer.score(
            detected = snapshot.detectedBleDevices,
            registered = state.bleDevices
        )

        // -----------------------------------------
        // 4. Dynamic normalization
        // -----------------------------------------

        val totalWeight = wt + wg + wb

        val alpha = wt / totalWeight
        val beta = wg / totalWeight
        val gamma = wb / totalWeight

        // -----------------------------------------
        // 5. Final convex combination
        // -----------------------------------------

        val finalScore =
            (alpha * sTime) +
                    (beta * sGps) +
                    (gamma * sBle)

        return finalScore
            .coerceIn(0.0, 1.0)
    }
}