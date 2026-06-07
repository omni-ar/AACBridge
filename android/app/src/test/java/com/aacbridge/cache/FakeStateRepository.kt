package com.aacbridge.cache

import com.aacbridge.router.ContextState

/**
 * Deterministic in-memory test repository.
 *
 * Avoids:
 * - Room
 * - SQLite
 * - disk IO
 *
 * Used exclusively for JVM unit testing.
 */
class FakeStateRepository(
    states: List<ContextState>
) : StateRepository {

    private val stateMap =
        states.associateBy { it.stateId }

    override suspend fun getFilePath(stateId: String): String? {
    return if (stateMap.containsKey(stateId)) "/tmp/$stateId.bin" else null
    }

    override suspend fun getAllContextStates(): List<ContextState> {

        return stateMap.values.toList()
    }

    override suspend fun getPromptText(stateId: String): String? {
        return if (stateMap.containsKey(stateId))
            "Test context prompt for $stateId"
        else null
    }
}