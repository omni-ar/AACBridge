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
     * Clears the KV cache and resets session_tokens.
     * Must be called between independent inference
     * trials to prevent context exhaustion.
     */
    fun clearKVCache()

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

    /**
     * Tokenizes and decodes the prompt WITHOUT
     * entering the generation loop.
     *
     * After this call, the KV cache contains attention
     * tensors for the prompt tokens ONLY — no stale
     * generation tokens. Designed for use before
     * saveKVCache() to produce clean cache files.
     *
     * @param prompt The context prompt to prefill.
     * @return true if prefill succeeded.
     */
    fun prefillOnly(
        prompt: String
    ): Boolean

    /**
     * Continues inference from a previously loaded
     * KV cache state.
     *
     * Precondition: loadKVCache() must have been
     * called successfully. session_tokens must contain
     * the token history restored by loadKVCache().
     *
     * Unlike runInference(), this function:
     * - Does NOT clear session_tokens
     * - Tokenizes intent WITHOUT BOS
     * - Uses explicit positions starting at n_past
     *
     * @param prompt The intent prompt to append.
     * @return Generated text response.
     */
    fun resumeInference(
        prompt: String,
        seqId: Int
    ): String

    /**
     * Drops KV entries and token history for one slot.
     * Called on eviction so a reused slot never inherits
     * stale positions from its previous occupant.
     */
    fun resetSlot(seqId: Int)

    // -------------------------------------------------
    // Native timing/token telemetry
    // -------------------------------------------------

    /**
     * Returns the prefill (prompt evaluation) duration
     * in milliseconds from the most recent
     * runInference() or resumeInference() call.
     *
     * For runInference(): measures the single
     * llama_decode() call that processes all prompt
     * tokens through the transformer.
     *
     * For resumeInference(): measures the single
     * llama_decode() call that processes ONLY the
     * newly appended intent tokens (not the restored
     * KV cache tokens).
     *
     * MUST be called under engineLock immediately
     * after the inference call that produced the
     * measurement.
     */
    fun getLastPrefillMs(): Double

    /**
     * Returns the autoregressive generation duration
     * in milliseconds from the most recent
     * runInference() or resumeInference() call.
     *
     * Measures the time from sampler initialization
     * through the final generated token (or EOS).
     * Excludes prefill.
     *
     * MUST be called under engineLock immediately
     * after the inference call.
     */
    fun getLastGenMs(): Double

    /**
     * Returns the number of prompt tokens evaluated
     * during the prefill phase of the most recent
     * runInference() or resumeInference() call.
     *
     * For runInference(): total prompt token count
     * (context + intent).
     *
     * For resumeInference(): only the newly appended
     * intent tokens (NOT the restored cache tokens).
     *
     * MUST be called under engineLock immediately
     * after the inference call.
     */
    fun getLastPromptTokens(): Int

    /**
     * Returns the number of tokens actually generated
     * during the autoregressive loop of the most
     * recent runInference() or resumeInference() call.
     *
     * This is the TRUE generated token count, not
     * the character length of the output string.
     *
     * MUST be called under engineLock immediately
     * after the inference call.
     */
    fun getLastGenTokens(): Int
}
