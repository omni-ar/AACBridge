package com.aacbridge.cache

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe in-memory metadata for an active KV cache state.
 *
 * IMPORTANT:
 * This class tracks ONLY metadata.
 *
 * Actual KV tensor memory remains native-side inside llama.cpp.
 *
 * Kotlin/JVM side is responsible only for:
 * - lifecycle tracking
 * - LRU ordering
 * - inference safety
 * - eviction coordination
 */
data class CacheState(

    /**
     * Globally unique semantic state identifier.
     */
    val stateId: String,

    /**
     * Absolute disk path to serialized KV cache binary.
     */
    val kvFilePath: String,

    /**
     * Tracks which native llama.cpp sequence slot
     * (0, 1, or 2) this state currently occupies.
     *
     * This maps semantic cache residency to the
     * underlying native KV ring-buffer slot.
     */
    val seqId: Int,

    /**
     * Last access timestamp for LRU eviction.
     *
     * Updated by:
     * - inference thread
     * Read by:
     * - eviction daemon
     */
    val lastAccessed: AtomicLong =
        AtomicLong(System.currentTimeMillis()),

    /**
     * Number of active inference readers currently
     * using this KV cache.
     *
     * INVARIANT:
     * KV cache MUST NOT be evicted while:
     *
     * refCount > 0
     */
    val refCount: AtomicInteger =
        AtomicInteger(0),

    /**
     * Indicates whether the state is still eligible
     * for new inference acquisitions.
     *
     * During eviction:
     * - set false BEFORE waiting for refCount drain
     *
     * This blocks new inference requests from racing
     * into the state while eviction is pending.
     */
    @Volatile
    var isActive: Boolean = true
)