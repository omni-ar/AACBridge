#include <jni.h>
#include <string>
#include <vector>
#include <chrono>

#include <android/log.h>

#include "llama.h"

#define LOG_TAG "AACBridgeJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model * model = nullptr;
static llama_context * ctx = nullptr;

/*
 * =====================================================
 * Per-slot session state
 * =====================================================
 *
 * MUST match HardwareConfig.MAX_ACTIVE_KV_STATES on the
 * Kotlin side.
 *
 * Previously this was a single global session_tokens
 * vector. That made it impossible to resume inference
 * from any slot other than the most recently loaded one:
 * KVCacheManager hands out seqId 0/1/2, but every
 * resumeInference() read the same shared history.
 *
 * Each native sequence slot now owns its own token
 * history, so slot N can be resumed independently.
 */
static constexpr int MAX_SLOTS = 3;

/* Per-sequence context window. Total n_ctx is
 * PER_SEQ_CTX * MAX_SLOTS, because llama.cpp divides
 * the context window across sequences. */
static constexpr int PER_SEQ_CTX = 2048;

static std::vector<llama_token> slot_tokens[MAX_SLOTS];

static inline bool valid_slot(int s) {
    return s >= 0 && s < MAX_SLOTS;
}

/*
 * Most-recent inference timing/token metrics.
 *
 * Updated at the end of runInference() and
 * resumeInference(). Kotlin callers MUST read these
 * under the engine lock immediately after the
 * corresponding inference call returns to avoid stale
 * values from a concurrent call.
 */
static double   last_prefill_ms    = 0.0;
static double   last_gen_ms        = 0.0;
static int32_t  last_prompt_tokens = 0;
static int32_t  last_gen_tokens    = 0;

/*
 * =====================================================
 * decode_into_seq()
 * =====================================================
 *
 * Decodes n tokens into an EXPLICIT sequence slot at
 * EXPLICIT positions.
 *
 * This replaces llama_batch_get_one(), which leaves
 * pos == NULL and seq_id unset. llama.cpp then defaults
 * the batch to sequence 0 and auto-assigns positions
 * from sequence 0's memory state. That is why every
 * resume landed on slot 0 regardless of the seqId the
 * cache manager selected.
 *
 * Logits are requested only for the final token, which
 * is all llama_sampler_sample(smpl, ctx, -1) needs.
 */
static bool decode_into_seq(
        const llama_token * tokens,
        int n,
        int pos_start,
        int seq_id) {

    if (n <= 0) {
        return false;
    }

    llama_batch batch = llama_batch_init(n, 0, 1);

    for (int i = 0; i < n; i++) {
        batch.token[i]     = tokens[i];
        batch.pos[i]       = (llama_pos) (pos_start + i);
        batch.n_seq_id[i]  = 1;
        batch.seq_id[i][0] = (llama_seq_id) seq_id;
        batch.logits[i]    = (int8_t) (i == n - 1);
    }

    batch.n_tokens = n;

    const int rc = llama_decode(ctx, batch);

    llama_batch_free(batch);

    if (rc != 0) {
        LOGE("decode_into_seq: llama_decode returned %d "
             "(n=%d pos_start=%d seq=%d)",
             rc, n, pos_start, seq_id);
        return false;
    }

    return true;
}

/*
 * Tokenizes text into out. add_bos controls whether a
 * BOS token is prepended.
 *
 * llama_tokenize returns a NEGATIVE count when the
 * output buffer is too small; the magnitude is the
 * required size. The two-pass call below relies on that.
 */
static bool tokenize_text(
        const std::string & text,
        bool add_bos,
        std::vector<llama_token> & out) {

    const llama_vocab * vocab = llama_model_get_vocab(model);

    int n = llama_tokenize(
            vocab,
            text.c_str(),
            text.length(),
            nullptr,
            0,
            add_bos,
            true
    );

    out.resize(n < 0 ? -n : n);

    int written = llama_tokenize(
            vocab,
            text.c_str(),
            text.length(),
            out.data(),
            (int32_t) out.size(),
            add_bos,
            true
    );

    if (written < 0) {
        LOGE("tokenize_text: failed (%d)", written);
        return false;
    }

    out.resize(written);

    return !out.empty();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_aacbridge_inference_LlamaBridge_initializeBackend(
        JNIEnv *env,
        jobject thiz) {

    llama_backend_init();

    LOGI("Llama backend initialized");
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_aacbridge_inference_LlamaBridge_initializeModel(
        JNIEnv *env,
        jobject thiz,
        jstring model_path) {

    const char * path = env->GetStringUTFChars(model_path, nullptr);

    LOGI("Initializing model from path: %s", path);

    llama_model_params model_params =
            llama_model_default_params();

    model = llama_model_load_from_file(path, model_params);

    env->ReleaseStringUTFChars(model_path, path);

    if (model == nullptr) {
        LOGE("Failed to load model");
        return JNI_FALSE;
    }

    llama_context_params ctx_params =
            llama_context_default_params();

    /*
     * n_seq_max MUST be set explicitly.
     *
     * It defaults to 1. With the default, sequence slots
     * 1 and 2 do not exist, so every
     * llama_state_seq_load_file() into slot 1 or 2 fails
     * and KVCacheManager silently returns the slot to the
     * pool -- meaning only ONE state was ever really
     * resident despite the 3-slot bookkeeping.
     *
     * n_ctx is the TOTAL context across all sequences,
     * so it must be scaled by MAX_SLOTS to preserve
     * PER_SEQ_CTX tokens per slot.
     */
    ctx_params.n_seq_max = MAX_SLOTS;
    ctx_params.n_ctx     = PER_SEQ_CTX * MAX_SLOTS;
    ctx_params.n_batch   = 512;
    ctx_params.n_threads = 4;

    ctx = llama_init_from_model(model, ctx_params);

    if (ctx == nullptr) {
        LOGE("Failed to create context");
        return JNI_FALSE;
    }

    for (int s = 0; s < MAX_SLOTS; s++) {
        slot_tokens[s].clear();
    }

    LOGI("Model initialized: n_ctx=%d n_seq_max=%d (%d per slot)",
         PER_SEQ_CTX * MAX_SLOTS, MAX_SLOTS, PER_SEQ_CTX);

    return JNI_TRUE;
}

/*
 * =====================================================
 * saveKVCache()
 * =====================================================
 *
 * Serializes the KV tensors of seq_id together with that
 * slot's token history.
 */
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_aacbridge_inference_LlamaBridge_saveKVCache(
        JNIEnv *env,
        jobject thiz,
        jstring filepath,
        jint seq_id) {

    if (ctx == nullptr) {
        LOGE("saveKVCache: context is null");
        return JNI_FALSE;
    }

    if (!valid_slot(seq_id)) {
        LOGE("saveKVCache: invalid seq_id %d", (int) seq_id);
        return JNI_FALSE;
    }

    std::vector<llama_token> & tokens = slot_tokens[seq_id];

    if (tokens.empty()) {
        LOGE("saveKVCache: slot %d has no token history; "
             "call prefillOnly() first", (int) seq_id);
        return JNI_FALSE;
    }

    const char * path = env->GetStringUTFChars(filepath, nullptr);

    size_t result =
            llama_state_seq_save_file(
                    ctx,
                    path,
                    (llama_seq_id) seq_id,
                    tokens.data(),
                    tokens.size()
            );

    env->ReleaseStringUTFChars(filepath, path);

    if (result == 0) {
        LOGE("Failed to save KV cache for slot %d", (int) seq_id);
        return JNI_FALSE;
    }

    LOGI("KV cache saved: slot=%d tokens=%d",
         (int) seq_id, (int) tokens.size());

    return JNI_TRUE;
}

/*
 * =====================================================
 * loadKVCache()
 * =====================================================
 *
 * Restores serialized KV tensors into seq_id and
 * repopulates that slot's token history.
 *
 * llama_state_seq_load_file returns the token history
 * that was saved alongside the tensors, so the slot's
 * n_past is recovered from the file rather than tracked
 * separately.
 */
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_aacbridge_inference_LlamaBridge_loadKVCache(
        JNIEnv *env,
        jobject thiz,
        jstring filepath,
        jint seq_id) {

    if (ctx == nullptr) {
        LOGE("loadKVCache: context is null");
        return JNI_FALSE;
    }

    if (!valid_slot(seq_id)) {
        LOGE("loadKVCache: invalid seq_id %d", (int) seq_id);
        return JNI_FALSE;
    }

    const char * path = env->GetStringUTFChars(filepath, nullptr);

    /*
     * Clear existing KV entries for the target sequence
     * before restoring.
     *
     * Without this, stale entries from a previous
     * inference remain at positions beyond the loaded
     * range. A later decode at those positions fails with
     * "could not find a KV slot" because the positions are
     * already occupied.
     */
    llama_memory_seq_rm(
            llama_get_memory(ctx),
            (llama_seq_id) seq_id,
            -1,
            -1
    );

    std::vector<llama_token> restored(PER_SEQ_CTX);

    size_t token_count = 0;

    size_t result =
            llama_state_seq_load_file(
                    ctx,
                    path,
                    (llama_seq_id) seq_id,
                    restored.data(),
                    restored.size(),
                    &token_count
            );

    env->ReleaseStringUTFChars(filepath, path);

    if (result == 0) {
        LOGE("Failed to load KV cache into slot %d", (int) seq_id);
        slot_tokens[seq_id].clear();
        return JNI_FALSE;
    }

    restored.resize(token_count);
    slot_tokens[seq_id] = std::move(restored);

    LOGI("KV cache loaded: slot=%d tokens=%d",
         (int) seq_id, (int) token_count);

    return JNI_TRUE;
}

/*
 * =====================================================
 * runInference()
 * =====================================================
 *
 * Full RAG-style inference from position zero.
 * Always operates on slot 0 and destroys whatever was
 * resident there.
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_aacbridge_inference_LlamaBridge_runInference(
        JNIEnv *env,
        jobject thiz,
        jstring prompt) {

    if (ctx == nullptr || model == nullptr) {
        LOGE("Model or context not initialized");
        return env->NewStringUTF("Model not initialized");
    }

    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    std::string input_text(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    const int slot = 0;

    llama_memory_seq_rm(
            llama_get_memory(ctx),
            (llama_seq_id) slot,
            -1,
            -1
    );

    slot_tokens[slot].clear();

    if (!tokenize_text(input_text, true, slot_tokens[slot])) {
        return env->NewStringUTF("Tokenization failed");
    }

    const llama_vocab * vocab = llama_model_get_vocab(model);

    int n_prompt_tokens = (int) slot_tokens[slot].size();

    auto prefill_start = std::chrono::high_resolution_clock::now();

    bool ok = decode_into_seq(
            slot_tokens[slot].data(),
            n_prompt_tokens,
            0,
            slot
    );

    auto prefill_end = std::chrono::high_resolution_clock::now();

    double prefill_ms =
            std::chrono::duration<double, std::milli>(
                    prefill_end - prefill_start
            ).count();

    if (!ok) {
        LOGE("Initial decode failed");
        return env->NewStringUTF("Inference failed");
    }

    llama_sampler * smpl =
            llama_sampler_chain_init(
                    llama_sampler_chain_default_params()
            );

    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    std::string generated_text;

    const int max_generation_tokens = 64;

    int pos = n_prompt_tokens;
    int gen_token_count = 0;

    auto gen_start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < max_generation_tokens; i++) {

        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGI("EOS token reached");
            break;
        }

        char piece[256];

        int piece_length =
                llama_token_to_piece(
                        vocab, new_token, piece, sizeof(piece), 0, true
                );

        if (piece_length > 0) {
            generated_text.append(piece, piece_length);
        }

        if (!decode_into_seq(&new_token, 1, pos, slot)) {
            LOGE("Decode failed during generation loop");
            llama_sampler_free(smpl);
            return env->NewStringUTF("Generation failed");
        }

        slot_tokens[slot].push_back(new_token);
        pos++;
        gen_token_count++;
    }

    llama_sampler_free(smpl);

    auto gen_end = std::chrono::high_resolution_clock::now();

    double gen_ms =
            std::chrono::duration<double, std::milli>(
                    gen_end - gen_start
            ).count();

    last_prefill_ms    = prefill_ms;
    last_gen_ms        = gen_ms;
    last_prompt_tokens = n_prompt_tokens;
    last_gen_tokens    = gen_token_count;

    LOGI("TIMING,runInference,"
         "prefill_ms=%.2f,gen_ms=%.2f,"
         "prompt_tokens=%d,gen_tokens=%d",
         prefill_ms, gen_ms, n_prompt_tokens, gen_token_count);

    return env->NewStringUTF(generated_text.c_str());
}

/*
 * =====================================================
 * clearKVCache()
 * =====================================================
 *
 * Clears ALL sequence slots and their token histories.
 * Called between independent benchmark trials to prevent
 * context exhaustion.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_aacbridge_inference_LlamaBridge_clearKVCache(
        JNIEnv *env,
        jobject thiz) {

    if (ctx == nullptr) {
        LOGE("clearKVCache: context is null");
        return;
    }

    llama_memory_clear(llama_get_memory(ctx), true);

    for (int s = 0; s < MAX_SLOTS; s++) {
        slot_tokens[s].clear();
    }

    LOGI("KV cache cleared (all %d slots)", MAX_SLOTS);
}

/*
 * =====================================================
 * prefillOnly()
 * =====================================================
 *
 * Tokenizes and decodes the prompt WITHOUT entering the
 * generation loop, populating slot 0 with context-only
 * attention tensors.
 *
 * Slot 0 is cleared first. Priming and inference both
 * touch slot 0, so ContextPrimerImpl must hold the engine
 * lock across prefillOnly() -> saveKVCache(), which it
 * already does.
 */
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_aacbridge_inference_LlamaBridge_prefillOnly(
        JNIEnv *env,
        jobject thiz,
        jstring prompt) {

    if (ctx == nullptr || model == nullptr) {
        LOGE("Model or context not initialized");
        return JNI_FALSE;
    }

    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    std::string input_text(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    const int slot = 0;

    llama_memory_seq_rm(
            llama_get_memory(ctx),
            (llama_seq_id) slot,
            -1,
            -1
    );

    slot_tokens[slot].clear();

    if (!tokenize_text(input_text, true, slot_tokens[slot])) {
        LOGE("prefillOnly: tokenization failed");
        return JNI_FALSE;
    }

    if (!decode_into_seq(
            slot_tokens[slot].data(),
            (int) slot_tokens[slot].size(),
            0,
            slot)) {

        LOGE("Prefill decode failed");
        slot_tokens[slot].clear();
        return JNI_FALSE;
    }

    LOGI("Prefill completed: %d tokens into slot %d",
         (int) slot_tokens[slot].size(), slot);

    return JNI_TRUE;
}

/*
 * =====================================================
 * resumeInference()
 * =====================================================
 *
 * Continues generation from a KV cache previously
 * restored into seqId.
 *
 * Precondition:
 *   loadKVCache(path, seqId) must have succeeded, so
 *   slot_tokens[seqId] holds the restored history.
 *
 * Differences from runInference():
 *   1. Does NOT clear the slot
 *   2. Tokenizes intent WITHOUT BOS (add_special=false)
 *   3. Decodes at EXPLICIT positions starting at
 *      n_past = slot_tokens[seqId].size(), into the
 *      EXPLICIT sequence seqId
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_aacbridge_inference_LlamaBridge_resumeInference(
        JNIEnv *env,
        jobject thiz,
        jstring prompt,
        jint seqId) {

    if (ctx == nullptr || model == nullptr) {
        LOGE("Model or context not initialized");
        return env->NewStringUTF("Model not initialized");
    }

    if (!valid_slot(seqId)) {
        LOGE("resumeInference: invalid seqId %d", (int) seqId);
        return env->NewStringUTF("Invalid cache slot");
    }

    std::vector<llama_token> & tokens = slot_tokens[seqId];

    int n_past = (int) tokens.size();

    if (n_past == 0) {
        LOGE("resumeInference: slot %d is empty. "
             "Call loadKVCache() first.", (int) seqId);
        return env->NewStringUTF("No KV cache loaded");
    }

    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    std::string input_text(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    const llama_vocab * vocab = llama_model_get_vocab(model);

    /*
     * add_special = false:
     *   The cached context already includes BOS at
     *   position 0 (from prefillOnly). A second BOS here
     *   would corrupt the restored attention pattern.
     */
    std::vector<llama_token> new_tokens;

    if (!tokenize_text(input_text, false, new_tokens)) {
        return env->NewStringUTF("Tokenization failed");
    }

    int n_new = (int) new_tokens.size();

    if (n_past + n_new + 64 > PER_SEQ_CTX) {
        LOGE("resumeInference: slot %d would overflow "
             "(n_past=%d n_new=%d cap=%d)",
             (int) seqId, n_past, n_new, PER_SEQ_CTX);
        return env->NewStringUTF("Context window exhausted");
    }

    LOGI("resumeInference: slot=%d appending %d intent "
         "tokens at pos %d",
         (int) seqId, n_new, n_past);

    auto prefill_start = std::chrono::high_resolution_clock::now();

    bool ok = decode_into_seq(
            new_tokens.data(),
            n_new,
            n_past,
            seqId
    );

    auto prefill_end = std::chrono::high_resolution_clock::now();

    double prefill_ms =
            std::chrono::duration<double, std::milli>(
                    prefill_end - prefill_start
            ).count();

    if (!ok) {
        LOGE("Resume prefill decode failed");
        return env->NewStringUTF("Inference failed");
    }

    tokens.insert(tokens.end(), new_tokens.begin(), new_tokens.end());

    int pos = (int) tokens.size();

    llama_sampler * smpl =
            llama_sampler_chain_init(
                    llama_sampler_chain_default_params()
            );

    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    std::string generated_text;

    const int max_generation_tokens = 64;

    int gen_token_count = 0;

    auto gen_start = std::chrono::high_resolution_clock::now();

    for (int i = 0; i < max_generation_tokens; i++) {

        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);

        if (llama_vocab_is_eog(vocab, new_token)) {
            LOGI("EOS token reached");
            break;
        }

        char piece[256];

        int piece_length =
                llama_token_to_piece(
                        vocab, new_token, piece, sizeof(piece), 0, true
                );

        if (piece_length > 0) {
            generated_text.append(piece, piece_length);
        }

        if (!decode_into_seq(&new_token, 1, pos, seqId)) {
            LOGE("Decode failed during generation loop");
            llama_sampler_free(smpl);
            return env->NewStringUTF("Generation failed");
        }

        tokens.push_back(new_token);
        pos++;
        gen_token_count++;
    }

    llama_sampler_free(smpl);

    auto gen_end = std::chrono::high_resolution_clock::now();

    double gen_ms =
            std::chrono::duration<double, std::milli>(
                    gen_end - gen_start
            ).count();

    last_prefill_ms    = prefill_ms;
    last_gen_ms        = gen_ms;
    last_prompt_tokens = n_new;
    last_gen_tokens    = gen_token_count;

    LOGI("TIMING,resumeInference,"
         "prefill_ms=%.2f,gen_ms=%.2f,"
         "prompt_tokens=%d,gen_tokens=%d,seq=%d",
         prefill_ms, gen_ms, n_new, gen_token_count, (int) seqId);

    return env->NewStringUTF(generated_text.c_str());
}

/*
 * =====================================================
 * resetSlot()
 * =====================================================
 *
 * Drops the KV entries and token history for a single
 * sequence slot. Called by KVCacheManager during
 * eviction so a reused slot never inherits stale
 * positions from its previous occupant.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_aacbridge_inference_LlamaBridge_resetSlot(
        JNIEnv *env,
        jobject thiz,
        jint seqId) {

    if (ctx == nullptr || !valid_slot(seqId)) {
        return;
    }

    llama_memory_seq_rm(
            llama_get_memory(ctx),
            (llama_seq_id) seqId,
            -1,
            -1
    );

    slot_tokens[seqId].clear();

    LOGI("Slot %d reset", (int) seqId);
}

/*
 * =====================================================
 * Native timing getters
 * =====================================================
 *
 * Callers MUST hold the Kotlin engineLock across the
 * inference call AND these getters within a single
 * acquisition.
 */
extern "C"
JNIEXPORT jdouble JNICALL
Java_com_aacbridge_inference_LlamaBridge_getLastPrefillMs(
        JNIEnv *env, jobject thiz) {
    return (jdouble) last_prefill_ms;
}

extern "C"
JNIEXPORT jdouble JNICALL
Java_com_aacbridge_inference_LlamaBridge_getLastGenMs(
        JNIEnv *env, jobject thiz) {
    return (jdouble) last_gen_ms;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_aacbridge_inference_LlamaBridge_getLastPromptTokens(
        JNIEnv *env, jobject thiz) {
    return (jint) last_prompt_tokens;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_aacbridge_inference_LlamaBridge_getLastGenTokens(
        JNIEnv *env, jobject thiz) {
    return (jint) last_gen_tokens;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_aacbridge_inference_LlamaBridge_release(
        JNIEnv *env,
        jobject thiz) {

    if (ctx != nullptr) {
        llama_free(ctx);
        ctx = nullptr;
    }

    if (model != nullptr) {
        llama_model_free(model);
        model = nullptr;
    }

    for (int s = 0; s < MAX_SLOTS; s++) {
        slot_tokens[s].clear();
    }

    llama_backend_free();

    LOGI("Resources released");
}