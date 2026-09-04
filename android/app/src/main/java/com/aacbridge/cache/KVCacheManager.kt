package com.aacbridge.cache

import com.aacbridge.inference.LlamaBridgeAdapter
import com.aacbridge.router.HardwareConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock as lockWithLock

/**
 * Predictive KV cache residency manager.
 *
 * Responsibilities:
 * - maintain bounded RAM residency
 * - coordinate safe llama.cpp KV slot restoration
 * - enforce LRU eviction
 * - prevent eviction during active inference
 *
 * IMPORTANT:
 * This class manages ONLY lifecycle orchestration.
 *
 * Actual KV tensor memory remains native-side
 * inside llama.cpp.
 *
 * Concurrency model:
 * - per-state mutexes only
 * - never globally lock cache system
 * - inference-safe eviction via refCount draining
 *
 * Native model:
 * - llama.cpp owns KV memory through seqId slots
 * - seqIds behave like a small native ring buffer
 * - semantic states are mapped onto those slots
 */
class KVCacheManager(
    private val repository: StateRepository,
    private val mutexRegistry: CacheMutexRegistry,
    private val jniBridge: LlamaBridgeAdapter,
    private val contextPrimer: ContextPrimerImpl? = null,
    private val engineLock: ReentrantLock? = null
) {
    companion object {

        /**
         * Poll interval while waiting for active
         * inference readers to drain.
         *
         * Small delay avoids CPU busy-spin while still
         * maintaining responsive eviction.
         */
        private const val EVICTION_POLL_INTERVAL_MS = 10L
    }

    /**
     * Currently resident semantic states.
     *
     * Key:
     * stateId
     */
    private val activeStates =
        ConcurrentHashMap<String, CacheState>()

    /**
     * Pool of available native llama.cpp sequence slots.
     *
     * Since:
     * MAX_ACTIVE_KV_STATES = 3
     *
     * Available slots are:
     * 0, 1, 2
     */
    private val availableSeqIds =
        ConcurrentLinkedQueue(
            (0 until HardwareConfig.MAX_ACTIVE_KV_STATES).toList()
        )

    /**
     * Loads highest-priority routing states.
     *
     * IMPORTANT:
     * States already resident in RAM are skipped
     * to avoid redundant JNI restore operations.
     *
     * Algorithm:
     * 1. Diff requested states against activeStates
     * 2. Evict LRU victims if RAM full
     * 3. Allocate native seqId slot
     * 4. Restore KV cache into that slot
     */
    suspend fun loadTopStates(
        topIds: List<String>
    ) {

        val missingStates =
            topIds.filterNot { stateId ->
                activeStates.containsKey(stateId)
            }

        for (stateId in missingStates) {

            /*
             * Ensure native slot capacity.
             */
            while (
                activeStates.size >=
                HardwareConfig.MAX_ACTIVE_KV_STATES
            ) {

                val victim =
                    activeStates.values
                        .minByOrNull {
                            it.lastAccessed.get()
                        }

                if (victim != null) {
                    evictVictim(victim.stateId)
                } else {
                    break
                }
            }

            val mutex =
                mutexRegistry.getMutex(stateId)

            mutex.withLock {

                /*
                 * Another coroutine may have loaded
                 * the state while awaiting lock.
                 */
                if (activeStates.containsKey(stateId)) {
                    return@withLock
                }

                val filePath =
                    repository.getFilePath(stateId)
                        ?: return@withLock

                /*
                 * Allocate available native seqId slot.
                 */
                val seqId =
                    availableSeqIds.poll()
                        ?: return@withLock

                /*
                 * Determine load strategy:
                 *
                 * 1. If .bin file exists on disk:
                 *    → Restore KV cache via loadKVCache()
                 *    → Engine lock protects shared native state
                 *
                 * 2. If .bin file does NOT exist:
                 *    → Delegate to ContextPrimer for
                 *      prefill → save lifecycle
                 *    → Primer acquires engine lock internally
                 *
                 * 3. If no primer available and no file:
                 *    → Return seqId to pool (existing behavior)
                 */
                val cacheFile = File(filePath)

                val success = if (cacheFile.exists()) {

                    /*
                     * Fast path: restore from disk.
                     * Engine lock protects native ctx/session_tokens.
                     */
                    if (engineLock != null) {
                        engineLock.lockWithLock {
                            jniBridge.loadKVCache(filePath, seqId)
                        }
                    } else {
                        jniBridge.loadKVCache(filePath, seqId)
                    }

                } else if (contextPrimer != null) {

                    /*
                     * Slow path: prime context.
                     * prefill → save → .bin created
                     * Primer holds engine lock internally.
                     */
                    contextPrimer.primeAndSave(
                        stateId, seqId, filePath
                    )

                } else {
                    false
                }

                /*
                 * Failed restore or prime:
                 * return slot back to pool.
                 */
                if (!success) {
                    availableSeqIds.add(seqId)
                    return@withLock
                }

                val cacheState =
                    CacheState(
                        stateId = stateId,
                        kvFilePath = filePath,
                        seqId = seqId
                    )

                activeStates[stateId] = cacheState
            }
        }
    }

    /**
     * Executes safe eviction sequence:
     *
     * 1. Acquire mutex
     * 2. Mark inactive
     * 3. Wait for refCount drain
     * 4. Return seqId slot to pool
     * 5. Remove from activeStates
     * 6. Release mutex
     *
     * IMPORTANT:
     * Current JNI layer does NOT expose explicit
     * freeKVCache().
     *
     * Reusing seqId safely overwrites previous
     * llama.cpp KV contents.
     */
    private suspend fun evictVictim(
        victimId: String
    ) {

        val mutex =
            mutexRegistry.getMutex(victimId)

        mutex.withLock {

            val victim =
                activeStates[victimId]
                    ?: return

            /*
             * Block future acquisitions.
             */
            victim.isActive = false

            /*
             * Wait for active readers to drain.
             *
             * IMPORTANT:
             * delay() avoids CPU busy-spin.
             */
            while (victim.refCount.get() > 0) {
                delay(EVICTION_POLL_INTERVAL_MS)
            }

            /*
             * Drop native KV entries for this slot before
             * reuse. Without this the next occupant inherits
             * stale positions from the evicted state, and
             * llama_decode fails with "could not find a KV
             * slot" once positions overlap.
             */
            if (engineLock != null) {
                engineLock.lockWithLock {
                    jniBridge.resetSlot(victim.seqId)
                }
            } else {
                jniBridge.resetSlot(victim.seqId)
            }

            availableSeqIds.add(victim.seqId)

            /*
             * Remove residency tracking.
             */
            activeStates.remove(victimId)
        }
    }

    /**
     * Acquires a state for inference.
     *
     * Atomic under mutex:
     * - check isActive
     * - increment refCount
     *
     * This prevents eviction races.
     *
     * @return active CacheState or null.
     */
    suspend fun acquireStateForInference(
        stateId: String
    ): CacheState? {

        val mutex =
            mutexRegistry.getMutex(stateId)

        return mutex.withLock {

            val state =
                activeStates[stateId]
                    ?: return@withLock null

            /*
             * Eviction already started.
             */
            if (!state.isActive) {
                return@withLock null
            }

            /*
             * Safe acquisition.
             */
            state.refCount.incrementAndGet()

            state.lastAccessed.set(
                System.currentTimeMillis()
            )

            state
        }
    }

    /**
     * Acquires the most recently used resident state for
     * inference without naming it.
     *
     * Used by the interactive path, where the caller wants
     * "whatever context is currently primed" rather than a
     * specific stateId.
     */
    suspend fun acquireBestResident(): CacheState? {

        val candidate =
            activeStates.values
                .filter { it.isActive }
                .maxByOrNull { it.lastAccessed.get() }
                ?: return null

        return acquireStateForInference(candidate.stateId)
    }

    /**
     * Releases previously acquired inference state.
     *
     * Silent no-op if state already evicted.
     *
     * IMPORTANT:
     * releaseState() intentionally does NOT acquire
     * the mutex.
     *
     * Safe because:
     * - refCount decrement is atomic
     * - once isActive=false, refCount becomes
     *   monotonic decreasing
     */
    fun releaseState(
        stateId: String
    ) {

        val state =
            activeStates[stateId]
                ?: return

        state.refCount.decrementAndGet()

        state.lastAccessed.set(
            System.currentTimeMillis()
        )
    }

        /**
     * Lightweight residency check.
     *
     * Does NOT acquire inference ownership.
     *
     * Safe because:
     * - only checks current residency snapshot
     * - does not mutate refCount
     * - avoids unnecessary acquire/release churn
     */
    fun isStateResident(
        stateId: String
    ): Boolean {

        return activeStates[stateId]?.isActive == true
    }

    internal fun getActiveStateCount(): Int {
    return activeStates.size
    }

    internal fun getAvailableSeqIdCount(): Int {
        return availableSeqIds.size
    }
}