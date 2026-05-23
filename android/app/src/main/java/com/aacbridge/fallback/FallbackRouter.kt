package com.aacbridge.fallback

import com.aacbridge.cache.KVCacheManager
import com.aacbridge.router.ContextState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Two-tier resilience fallback router.
 *
 * PURPOSE:
 * Guarantees user-visible response continuity even when:
 * - KV cache preload still running
 * - llama.cpp cold-starting
 * - ActiveSweep incomplete
 * - native inference unavailable
 *
 * Tier 1:
 * Immediate generic SQLite-backed fallback response.
 *
 * Tier 2:
 * Async upgraded llama.cpp response once KV context
 * becomes available.
 *
 * IMPORTANT:
 * Tier 1 exists to eliminate "dead air" UX failure.
 */
class FallbackRouter(
    private val fallbackRepository: FallbackRepository,
    private val kvCacheManager: KVCacheManager
) {

    /**
     * Returns immediate low-latency fallback response.
     *
     * Target:
     * <50ms
     *
     * Never blocks on:
     * - BLE
     * - GPS
     * - llama.cpp
     * - ActiveSweep
     */
    suspend fun getImmediateFallback(
        intent: String,
        state: ContextState?
    ): String {

        return withContext(Dispatchers.IO) {

            fallbackRepository.getFallbackResponse(
                intent = intent,
                stateId = state?.stateId
            )
                ?: DEFAULT_FALLBACK_RESPONSE
        }
    }

    /**
     * Determines whether upgraded contextual inference
     * is currently possible.
     *
     * Requirements:
     * - state exists
     * - state active
     * - KV cache resident
     */
    suspend fun canUpgradeToContextualInference(
        stateId: String
    ): Boolean {

        return kvCacheManager
            .isStateResident(stateId)
    }

    companion object {

        /**
         * Safety fallback if repository lookup fails.
         */
        private const val DEFAULT_FALLBACK_RESPONSE =
            "Please wait a moment."
    }
}