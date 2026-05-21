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