package com.aacbridge.cache

import com.aacbridge.router.ContextState

/**
 * Temporary in-memory repository implementation.
 *
 * Exists ONLY to stabilize orchestration and
 * compile the dependency graph before Room
 * persistence integration.
 *
 * Future replacement:
 * - Room database
 * - DAO-backed state lookup
 * - persistent KV metadata
 */
class InMemoryStateRepository : StateRepository {

    /**
     * Temporary empty state registry.
     */
    private val states =
        mutableListOf<ContextState>()

    override suspend fun getFilePath(
        stateId: String
    ): String? {

        return states
            .firstOrNull { it.stateId == stateId }
            ?.kvFilePath
    }

    override suspend fun getAllContextStates(): List<ContextState> {
        return states.toList()
    }
}