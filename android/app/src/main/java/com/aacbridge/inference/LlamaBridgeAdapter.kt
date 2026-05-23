package com.aacbridge.inference

/**
 * Testable abstraction layer above the native
 * llama.cpp JNI bridge.
 *
 * Exists to:
 * - decouple JVM tests from JNI
 * - allow fake bridge injection
 * - isolate native runtime behavior
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
}