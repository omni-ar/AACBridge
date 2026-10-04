package com.aacbridge.router

/**
 * Most recent full-sensor router ranking, published by
 * ActiveSweep and read at interaction time to pick which
 * resident context to resume (ResidencyPolicy
 * .selectForInference).
 *
 * DriftDetector does not publish here: its snapshot is
 * BLE-blind and would understate BLE-anchored contexts.
 */
class LatestRanking {

    data class Snapshot(
        val ranked: List<Pair<String, Double>>,
        /** System.currentTimeMillis() when published. */
        val publishedAtMs: Long
    )

    @Volatile
    private var latest: Snapshot? = null

    fun publish(ranked: List<Pair<String, Double>>, nowMs: Long = System.currentTimeMillis()) {
        latest = Snapshot(ranked = ranked, publishedAtMs = nowMs)
    }

    fun get(): Snapshot? = latest
}
