package com.aacbridge.cache

/**
 * Deterministic JVM-safe fake native bridge.
 *
 * Tracks:
 * - load calls
 * - seqId ownership
 * - failure injection
 *
 * WITHOUT:
 * - JNI
 * - llama.cpp
 * - native memory
 */
class FakeLlamaBridge {

    /**
     * seqId -> filepath
     */
    val loadedStates =
        mutableMapOf<Int, String>()

    var loadCallCount = 0
        private set

    var shouldFailLoad = false

    fun loadKVCache(
        filepath: String,
        seqId: Int
    ): Boolean {

        loadCallCount++

        if (shouldFailLoad) {
            return false
        }

        loadedStates[seqId] = filepath

        return true
    }
}