package com.aacbridge.router

/**
 * Decides which context states to load into, and evict
 * from, the bounded KV residency set.
 *
 * Shared by:
 * - ActiveSweep (every 60 s)
 * - DriftDetector (every 15 min)
 * - the offline PredictionEvaluator
 *
 * so the trace evaluation measures the same replacement
 * rule the device runs.
 *
 * Rule:
 * Candidates are tried in rank order. A candidate that is
 * already resident is kept. Otherwise it fills a free slot
 * if one exists; if not, it replaces the weakest resident
 * state only when its score exceeds that state's score by
 * more than [margin]. Resident states absent from the
 * current ranking (below the dead-state threshold) score 0.
 *
 * Pure function: no Android or native dependencies.
 */
object ResidencyPolicy {

    data class Plan(
        /** States to evict, in eviction order. */
        val toEvict: List<String>,
        /** States to load, in rank order. */
        val toLoad: List<String>
    ) {
        val isEmpty: Boolean get() = toEvict.isEmpty() && toLoad.isEmpty()
    }

    /**
     * @param ranked Router output, highest score first.
     * @param resident Currently resident state ids.
     * @param capacity Maximum resident states.
     * @param margin Hysteresis margin; 0 reproduces plain top-k.
     * @param maxCandidates How many top-ranked states may be
     *        considered for loading (DriftDetector uses 1).
     */
    fun plan(
        ranked: List<Pair<String, Double>>,
        resident: Set<String>,
        capacity: Int = HardwareConfig.MAX_ACTIVE_KV_STATES,
        margin: Double = HardwareConfig.RESIDENCY_HYSTERESIS_MARGIN,
        maxCandidates: Int = capacity
    ): Plan {

        require(capacity > 0) { "capacity must be > 0, received: $capacity" }
        require(margin >= 0.0) { "margin must be >= 0, received: $margin" }

        val scores = ranked.toMap()
        val candidates = ranked.take(minOf(maxCandidates, capacity)).map { it.first }

        // Resident states the plan has not committed to keep.
        val evictable = resident
            .filter { it !in candidates }
            .sortedBy { scores[it] ?: 0.0 }
            .toMutableList()

        var freeSlots = capacity - resident.size
        val toLoad = mutableListOf<String>()
        val toEvict = mutableListOf<String>()

        for (candidate in candidates) {

            if (candidate in resident) continue

            if (freeSlots > 0) {
                toLoad += candidate
                freeSlots--
                continue
            }

            val weakest = evictable.firstOrNull() ?: break
            val advantage = (scores[candidate] ?: 0.0) - (scores[weakest] ?: 0.0)

            // Candidates are in descending score order and the
            // weakest resident only gets stronger, so no later
            // candidate can pass either.
            if (advantage <= margin) break

            evictable.removeAt(0)
            toEvict += weakest
            toLoad += candidate
        }

        return Plan(toEvict = toEvict, toLoad = toLoad)
    }

    /**
     * The context used at interaction time: the highest-ranked
     * state that is resident, or null (cold miss: Tier-1
     * fallback).
     */
    fun selectForInference(
        ranked: List<Pair<String, Double>>,
        resident: Set<String>
    ): String? = ranked.firstOrNull { it.first in resident }?.first
}
