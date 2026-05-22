package com.aacbridge.cache

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/**
 * Centralized per-state mutex registry.
 *
 * IMPORTANT:
 * Never globally lock the entire cache system.
 *
 * Per-state locking guarantees:
 * - concurrent independent state loads
 * - safe eviction synchronization
 * - no global inference stalls
 */
class CacheMutexRegistry {

    private val mutexes =
        ConcurrentHashMap<String, Mutex>()

    /**
     * Returns the dedicated mutex for a stateId.
     *
     * Thread-safe and atomic.
     */
    fun getMutex(stateId: String): Mutex {

        return mutexes.computeIfAbsent(stateId) {
            Mutex()
        }
    }
}