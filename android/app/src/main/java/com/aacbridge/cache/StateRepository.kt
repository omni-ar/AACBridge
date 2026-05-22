package com.aacbridge.cache
import com.aacbridge.router.ContextState
/**
 * Persistent lookup contract for serialized KV cache paths.
 *
 * Future implementation:
 * - Room
 * - SQLite
 * - remote sync
 *
 * KVCacheManager depends ONLY on this abstraction,
 * not concrete storage implementation.
 */
interface StateRepository {

    /**
     * Resolves semantic stateId to serialized
     * KV cache binary file path.
     *
     * @return absolute file path or null if missing
     */
    suspend fun getFilePath(
        stateId: String
    ): String?

    suspend fun getAllContextStates(): List<ContextState>
}