package com.aacbridge.cache

import com.aacbridge.inference.LlamaBridgeAdapter
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Concrete implementation of the KV cache priming lifecycle.
 *
 * Owns exclusively:
 * prefill → save
 *
 * Does NOT own:
 * - seqId allocation (KVCacheManager)
 * - seqId return on failure (KVCacheManager)
 * - slot eviction (KVCacheManager)
 * - sensor acquisition (ActiveSweep)
 * - state routing (StateRouter)
 *
 * Lifecycle per invocation:
 * 1. Resolve stateId → prompt text
 * 2. Acquire engine mutex (protects shared native state)
 * 3. runInference(prompt) → populates KV cache in memory
 * 4. saveKVCache(tmpPath, seqId) → serializes to .tmp file
 * 5. Release engine mutex
 * 6. Atomic rename .tmp → .bin (ext4 atomic guarantee)
 *
 * IMPORTANT:
 * The engine mutex MUST be held across both runInference()
 * and saveKVCache() atomically. The C++ global session_tokens
 * vector is shared — releasing between calls allows another
 * thread to corrupt it.
 *
 * This is a benchmarking-phase implementation.
 * Future optimization: add prefillOnly() JNI function
 * that skips the sampling loop.
 */
class ContextPrimerImpl(
    private val repository: StateRepository,
    private val bridge: LlamaBridgeAdapter,
    private val engineLock: ReentrantLock,
    private val modelReady: AtomicBoolean = AtomicBoolean(true)
) {

    companion object {
        /**
         * Native seq_id used during priming.
         *
         * runInference() → llama_batch_get_one() always
         * decodes into seq_id 0. saveKVCache() must match.
         */
        private const val PRIMING_SEQ_ID = 0
    }

    /**
     * Primes the KV cache for [stateId] and persists
     * the resulting tensors to [filePath].
     *
     * Sequence:
     * 1. Resolve prompt text from repository
     * 2. Acquire engine mutex
     * 3. Run prefill (generates KV tensors + sampled text)
     * 4. Save KV cache to .tmp file
     * 5. Release engine mutex
     * 6. Atomic rename .tmp → .bin
     *
     * @param stateId Semantic context identifier
     * @param seqId Native llama.cpp slot (owned by KVCacheManager)
     * @param filePath Target .bin path for the serialized cache
     *
     * @return true if priming and persistence succeeded.
     *         false on any failure — KVCacheManager handles
     *         seqId return to pool on failure.
     */
    @Suppress("UNUSED_PARAMETER") // seqId is the KVCacheManager load slot; priming always saves seq 0
    suspend fun primeAndSave(
        stateId: String,
        seqId: Int,
        filePath: String
    ): Boolean {

        /*
         * Step 0: Model readiness gate.
         *
         * Prevents priming on null/uninitialized native context.
         * Without this guard, saveKVCache() serializes only the
         * header (~450 bytes) with zero KV data.
         */
        if (!modelReady.get()) return false

        /*
         * Step 1: Resolve prompt text.
         */
        val prompt =
            repository.getPromptText(stateId)
                ?: return false

        val binFile = File(filePath)
        val tmpFile = File("$filePath.tmp")

        /*
         * Step 2-5: Engine mutex protects the
         * shared native state across the entire
         * prefill + save sequence.
         */
        val saveSuccess = engineLock.withLock {

            /*
             * Step 3: Prefill only (no generation).
             *
             * prefillOnly() populates the native KV cache
             * with attention tensors computed from the
             * prompt, WITHOUT entering the generation loop.
             *
             * This ensures the saved .bin file contains
             * ONLY the context tokens — no stale
             * generated output that would pollute the
             * KV cache when later restored.
             */
            val prefillSuccess = bridge.prefillOnly(prompt)

            if (!prefillSuccess) {
                return@withLock false
            }

            /*
             * Step 4: Serialize KV cache to .tmp file.
             *
             * CRITICAL: Always save seq_id 0.
             *
             * prefillOnly() uses llama_batch_get_one()
             * which decodes all tokens into seq_id 0.
             * The `seqId` parameter from KVCacheManager
             * is the target LOAD slot, not the native
             * decode slot.
             *
             * The .bin file is seq-id-agnostic on disk —
             * loadKVCache() can restore it into any slot.
             */
            bridge.saveKVCache(
                tmpFile.absolutePath,
                PRIMING_SEQ_ID
            )
        }

        if (!saveSuccess) {
            tmpFile.delete()
            return false
        }

        /*
         * Step 6: Atomic rename .tmp → .bin.
         *
         * rename() is atomic on ext4 (Android).
         * This guarantees that .bin is either fully
         * written or does not exist — never corrupt.
         */
        val renamed = tmpFile.renameTo(binFile)

        if (!renamed) {
            tmpFile.delete()
            return false
        }

        return true
    }
}
