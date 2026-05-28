package com.aacbridge.cache

/**
 * Orchestration interface for priming and persisting KV caches.
 *
 * This layer will eventually sit between the ContextDaemon's
 * ActiveSweep and the KVCacheManager.
 *
 * Responsibilities in Phase 3:
 * - Detect when KVCacheManager loads a state without an underlying .bin file.
 * - Trigger LlamaBridge.runInference() silently (prefill).
 * - Trigger LlamaBridge.saveKVCache() to serialize the memory to flash.
 *
 * Current Phase: Scaffold / Interface Definition only.
 * Full automatic persistence lifecycle is pending backend review.
 */
interface ContextPrimer {

    /**
     * Determines if a newly allocated seqId requires prefill priming
     * before it can be used for standard inference.
     */
    fun requiresPriming(stateId: String, seqId: Int): Boolean

    /**
     * Instructs the InferenceEngine to perform a silent prefill pass
     * on the context text associated with [stateId], and subsequently
     * triggers serialization to disk for future cache hits.
     *
     * @param stateId The semantic ID of the context (e.g. "home_mom")
     * @param seqId The native ring buffer slot allocated for this context
     * @return True if priming and serialization succeeded
     */
    suspend fun primeAndSaveContext(stateId: String, seqId: Int): Boolean
}
