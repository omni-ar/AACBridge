package com.aacbridge.cache

import com.aacbridge.inference.LlamaBridgeAdapter

/**
 * Deterministic JVM-safe fake native bridge.
 *
 * Tracks:
 * - load calls
 * - save calls
 * - inference calls
 * - prefill calls
 * - seqId ownership
 * - failure injection
 *
 * WITHOUT:
 * - JNI
 * - llama.cpp
 * - native memory
 */
class FakeLlamaBridge : LlamaBridgeAdapter {

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
     * Ordered list of prompts received by runInference.
     */
    val inferencePrompts =
        mutableListOf<String>()

    var inferenceCallCount = 0
        private set

    var inferenceResponse = "Generated text"

    /**
     * Ordered list of prompts received by prefillOnly.
     */
    val prefillPrompts =
        mutableListOf<String>()

    var prefillCallCount = 0
        private set

    var shouldFailPrefill = false

    override fun loadKVCache(
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

    override fun clearKVCache() {
        // No-op for tests
    }

    override fun saveKVCache(
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

    override fun runInference(
        prompt: String
    ): String {

        inferenceCallCount++
        inferencePrompts.add(prompt)

        return inferenceResponse
    }

    override fun prefillOnly(
        prompt: String
    ): Boolean {

        prefillCallCount++
        prefillPrompts.add(prompt)

        return !shouldFailPrefill
    }

    override fun resumeInference(
        prompt: String,
        seqId: Int
    ): String {

        return inferenceResponse
    }

    override fun resetSlot(seqId: Int) {
        // No-op for tests
    }

    // Timing getters return zero in test fake
    override fun getLastPrefillMs(): Double = 0.0
    override fun getLastGenMs(): Double = 0.0
    override fun getLastPromptTokens(): Int = 0
    override fun getLastGenTokens(): Int = 0
}