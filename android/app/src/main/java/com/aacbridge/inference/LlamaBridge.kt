package com.aacbridge.inference

/**
 * The JNI bridge connecting the Kotlin runtime to the underlying
 * llama.cpp / Snapdragon ARM64 execution backend.
 *
 * This object maps strictly to the functions exposed in `llama_jni.cpp`.
 */
object LlamaBridge : LlamaBridgeAdapter { // 1. INHERIT THE ADAPTER HERE

    init {
        // Loads libaacbridge-jni.so from the APK's lib/arm64-v8a/ directory
        System.loadLibrary("aacbridge-jni")
    }

    /**
     * Initializes the ggml backend and allocates memory contexts.
     */
    external fun initializeBackend()

    /**
     * Loads the model weights from the given filesystem path.
     * @param modelPath The absolute path to the .gguf model file on the device.
     * @return True if the model successfully loaded into memory, false otherwise.
     */
    external fun initializeModel(modelPath: String): Boolean

    /**
     * Dumps the current KV cache state to disk for a given sequence ID.
     * @param filepath The absolute path where the cache should be written.
     * @param seqId The active sequence ID to save.
     * @return True on success, false on write failure.
     */
    override external fun saveKVCache(filepath: String, seqId: Int): Boolean

    /**
     * Restores a previously saved KV cache state into memory.
     * @param filepath The absolute path of the cache file on disk.
     * @param seqId The sequence ID to assign to the restored cache.
     * @return True on success, false if the file is invalid or missing.
     */
    override external fun loadKVCache(filepath: String, seqId: Int): Boolean

    /**
     * Clears the KV cache and resets session_tokens.
     */
    override external fun clearKVCache()

    /**
     * Executes the greedy-sampling generation loop on the input prompt.
     * @param prompt The context prompt string.
     * @return The generated text response from the model.
     */
    override external fun runInference(prompt: String): String

    /**
     * Tokenizes and decodes the prompt without generation.
     * Produces a clean KV cache state for saveKVCache().
     */
    override external fun prefillOnly(prompt: String): Boolean

    /**
     * Continues inference from a loaded KV cache.
     * Uses explicit positions to avoid collision with loaded entries.
     */
    override external fun resumeInference(prompt: String, seqId: Int): String

    /**
     * Drops KV entries and token history for one slot.
     * Called on eviction so a reused slot never inherits
     * stale positions from its previous occupant.
     */
    override external fun resetSlot(seqId: Int)

    // -------------------------------------------------
    // Native timing/token telemetry getters
    // -------------------------------------------------

    override external fun getLastPrefillMs(): Double

    override external fun getLastGenMs(): Double

    override external fun getLastPromptTokens(): Int

    override external fun getLastGenTokens(): Int

    /**
     * Frees the llama.cpp context, model buffers, and ggml backend.
     */
    external fun release()
}