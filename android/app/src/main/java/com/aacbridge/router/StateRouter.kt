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
 *
 * Architectural boundary:
 *
 * SensorSnapshot + ContextState list
 *              ↓
 *         StateRouter
 *              ↓
 *     Ranked context IDs
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
         *
         * Engineering rationale:
         * Exponential decay mathematically approaches zero
         * asymptotically but never reaches exact zero.
         *
         * Extremely low-relevance states should not consume:
         * - RAM residency
         * - KV cache loading bandwidth
         * - eviction pressure
         */
        private const val DEAD_STATE_THRESHOLD = 0.01
    }

    /**
     * Evaluates all candidate context states and returns
     * the highest-relevance state IDs.
     *
     * Pipeline:
     * 1. Score every state
     * 2. Cull mathematically dead states
     * 3. Sort descending by relevance
     * 4. Return top-N IDs
     *
     * @param snapshot Immutable live sensor snapshot.
     *
     * @param states Candidate semantic context states.
     *
     * @param limit Maximum number of active states to return.
     * Defaults to HardwareConfig.MAX_ACTIVE_KV_STATES.
     *
     * @return Ordered list of highest-relevance state IDs.
     */
    fun getTopContextIds(
        snapshot: SensorSnapshot,
        states: List<ContextState>,
        limit: Int = HardwareConfig.MAX_ACTIVE_KV_STATES
    ): List<String> {

        require(limit > 0) {
            "limit must be > 0, received: $limit"
        }

        return states
            .asSequence()

            // score states
            .map { state ->
                state.stateId to calculateStateScore(
                    snapshot = snapshot,
                    state = state
                )
            }

            // cull mathematically dead states
            .filter { (_, score) ->
                score >= DEAD_STATE_THRESHOLD
            }

            // descending relevance
            .sortedByDescending { (_, score) ->
                score
            }

            // top-N only
            .take(limit)

            // return IDs only
            .map { (stateId, _) ->
                stateId
            }

            .toList()
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
     * where:
     * - alpha/beta/gamma are dynamically normalized
     *   reliability weights
     *
     * - S_time/S_gps/S_ble are semantic similarity scores
     *
     * INVARIANT:
     *
     * The normalization denominator:
     *
     * totalWeight = wt + wg + wb
     *
     * is protected from divide-by-zero EXCLUSIVELY because:
     *
     * HardwareConfig.TIME_BASELINE_WEIGHT > 0.0
     *
     * If TIME_BASELINE_WEIGHT is ever externalized into
     * runtime configuration, validation MUST enforce:
     *
     * TIME_BASELINE_WEIGHT > 0.0
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

        // -------------------------------------------------
        // 1. Temporal scoring (always available)
        // -------------------------------------------------

        val wt = HardwareConfig.TIME_BASELINE_WEIGHT

        val sTime = timeScorer.score(
            currentHourDecimal = snapshot.currentHourDecimal,
            anchorHourDecimal = state.expectedTime
        )

        // -------------------------------------------------
        // 2. GPS scoring
        // -------------------------------------------------

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

            // GPS modality unavailable
            wg = 0.0
            sGps = 0.0
        }

        // -------------------------------------------------
        // 3. BLE scoring
        // -------------------------------------------------

        val wb = bleScorer.calculateReliability(
            snapshot.detectedBleDevices
        )

        val sBle = bleScorer.score(
            detected = snapshot.detectedBleDevices,
            registered = state.bleDevices
        )

        // -------------------------------------------------
        // 4. Dynamic normalization
        // -------------------------------------------------

        val totalWeight = wt + wg + wb

        val alpha = wt / totalWeight
        val beta = wg / totalWeight
        val gamma = wb / totalWeight

        // -------------------------------------------------
        // 5. Final convex combination
        // -------------------------------------------------

        val finalScore =
            (alpha * sTime) +
                    (beta * sGps) +
                    (gamma * sBle)

        return finalScore
            .coerceIn(0.0, 1.0)
    }
}