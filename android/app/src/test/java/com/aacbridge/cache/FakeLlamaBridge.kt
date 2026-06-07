package com.aacbridge.cache

/**
 * Deterministic JVM-safe fake native bridge.
 *
 * Tracks:
 * - load calls
 * - save calls
 * - inference calls
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

    /**
     * seqId -> filepath
     */
    val savedStates =
        mutableMapOf<Int, String>()

    var saveCallCount = 0
        private set

    var shouldFailSave = false

    /**
     * Ordered list of prompts received.
     */
    val inferencePrompts =
        mutableListOf<String>()

    var inferenceCallCount = 0
        private set

    var inferenceResponse = "Generated text"

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

    fun saveKVCache(
        filepath: String,
        seqId: Int
    ): Boolean {

        saveCallCount++

        if (shouldFailSave) {
            return false
        }

        savedStates[seqId] = filepath

        /*
         * Create the actual file so that atomic rename
         * operations in ContextPrimerImpl work during tests.
         */
        val file = java.io.File(filepath)
        file.parentFile?.mkdirs()
        file.writeText("fake_kv_cache_data")

        return true
    }

    fun runInference(
        prompt: String
    ): String {

        inferenceCallCount++
        inferencePrompts.add(prompt)

        return inferenceResponse
    }
}