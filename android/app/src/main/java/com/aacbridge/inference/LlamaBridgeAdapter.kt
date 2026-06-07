package com.aacbridge.inference

/**
 * Testable abstraction layer above the native
 * llama.cpp JNI bridge.
 *
 * Exists to:
 * - decouple JVM tests from JNI
 * - allow fake bridge injection
 * - isolate native runtime behavior
 *
 * IMPORTANT:
 * All methods in this interface access shared
 * native global state (ctx, session_tokens).
 * Callers MUST hold the engine-level mutex
 * before invoking any method.
 */
interface LlamaBridgeAdapter {

    /**
     * Restores serialized KV cache tensors into
     * a specific llama.cpp sequence slot.
     *
     * @param filepath Absolute path to KV binary.
     * @param seqId Native llama.cpp slot ID.
     *
     * @return true if restore succeeded.
     */
    fun loadKVCache(
        filepath: String,
        seqId: Int
    ): Boolean

    /**
     * Serializes KV cache tensors from a specific
     * llama.cpp sequence slot to disk.
     *
     * @param filepath Absolute path for output binary.
     * @param seqId Native llama.cpp slot ID to save.
     *
     * @return true if serialization succeeded.
     */
    fun saveKVCache(
        filepath: String,
        seqId: Int
    ): Boolean

    /**
     * Executes tokenization, prefill, and greedy
     * sampling on the given prompt.
     *
     * After this call, the native KV cache contains
     * the computed attention tensors for the prompt.
     *
     * @param prompt The context prompt string.
     * @return Generated text response.
     */
    fun runInference(
        prompt: String
    ): String
}