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
     * Clears the scratch sequence (HardwareConfig.SCRATCH_SEQ_ID)
     * used by runInference() and benchmark restores.
     * Resident slots are not touched.
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
     * sampling on the given prompt (inline / RAG path).
     *
     * Runs in the scratch sequence
     * (HardwareConfig.SCRATCH_SEQ_ID), so resident
     * states are not disturbed.
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
     * @param seqId Sequence slot to fill (cleared first).
     * @return true if prefill succeeded.
     */
    fun prefillOnly(
        prompt: String,
        seqId: Int
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
     * - Does NOT clear the slot
     * - Tokenizes intent WITHOUT BOS
     * - Uses explicit positions starting at n_past
     * - Rolls the slot back to the cached context after
     *   generation, so the resident state can be reused
     *   for the next interaction without reloading
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
    fun getLastPrefillMs(): Double = 0.0

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
    fun getLastGenMs(): Double = 0.0

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
    fun getLastPromptTokens(): Int = 0

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
    fun getLastGenTokens(): Int = 0
}
