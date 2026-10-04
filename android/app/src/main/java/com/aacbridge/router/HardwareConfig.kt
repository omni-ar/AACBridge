package com.aacbridge.router

/**
 * Centralized hardware and routing constraints.
 *
 * IMPORTANT:
 * These values are shared across multiple backend subsystems.
 * They MUST remain globally consistent.
 *
 * Example:
 * - StateRouter uses MAX_ACTIVE_KV_STATES to determine
 *   how many top context IDs to return.
 *
 * - KVCacheManager uses the exact same constant to enforce
 *   RAM residency and LRU eviction policy.
 *
 * If these diverge, routing and cache residency become
 * mathematically inconsistent.
 */
object HardwareConfig {

    /**
     * Maximum number of KV cache states allowed
     * to remain active in RAM simultaneously.
     *
     * Constraint derived from:
     * - Android background memory limits
     * - llama.cpp KV cache footprint
     * - target edge-device RAM budget
     */
    const val MAX_ACTIVE_KV_STATES = 3

    /**
     * Native llama.cpp sequence id reserved for work that
     * must not disturb resident states: inline (RAG)
     * inference, the startup smoke test and benchmark
     * restores.
     *
     * Resident states occupy seqIds 0 until
     * MAX_ACTIVE_KV_STATES. The native layer allocates
     * MAX_ACTIVE_KV_STATES + 1 sequences (llama_jni.cpp
     * RESIDENT_SLOTS / SCRATCH_SLOT must match).
     */
    const val SCRATCH_SEQ_ID = MAX_ACTIVE_KV_STATES

    /**
     * Minimum score advantage a non-resident candidate
     * needs over the weakest resident state before it
     * replaces that state (see ResidencyPolicy).
     *
     * Applied by both ActiveSweep (60 s) and
     * DriftDetector (15 min). Not a smoothing window and
     * unrelated to MAX_ACTIVE_KV_STATES.
     */
    const val RESIDENCY_HYSTERESIS_MARGIN = 0.10

    /**
     * Fixed baseline reliability weight for temporal context.
     *
     * INVARIANT:
     * This value MUST remain strictly > 0.0.
     *
     * The normalization denominator in StateRouter:
     *
     * totalWeight = wt + wg + wb
     *
     * is protected from divide-by-zero exclusively because
     * TIME_BASELINE_WEIGHT is guaranteed positive.
     *
     * If this is ever externalized into runtime configuration,
     * validation MUST enforce:
     *
     * TIME_BASELINE_WEIGHT > 0.0
     */
    const val TIME_BASELINE_WEIGHT = 0.4
}