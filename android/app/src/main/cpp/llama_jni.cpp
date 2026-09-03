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

static std::vector<llama_token> session_tokens;

/*
 * Most-recent inference timing/token metrics.
 *
 * Updated atomically at the end of runInference()
 * and resumeInference(). Kotlin callers MUST read
 * these under the engine lock immediately after the
 * corresponding inference call returns to avoid
 * stale values from a concurrent call.
 */
static double   last_prefill_ms    = 0.0;
static double   last_gen_ms        = 0.0;
static int32_t  last_prompt_tokens = 0;
static int32_t  last_gen_tokens    = 0;

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

model = llama_model_load_from_file(
        path,
        model_params
);

env->ReleaseStringUTFChars(model_path, path);

if (model == nullptr) {
LOGE("Failed to load model");
return JNI_FALSE;
}

llama_context_params ctx_params =
        llama_context_default_params();

ctx_params.n_ctx = 2048;
ctx_params.n_batch = 512;
ctx_params.n_threads = 4;

ctx = llama_init_from_model(
        model,
        ctx_params
);

if (ctx == nullptr) {
LOGE("Failed to create context");
return JNI_FALSE;
}

LOGI("Model initialized successfully");

return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
        Java_com_aacbridge_inference_LlamaBridge_saveKVCache(
        JNIEnv *env,
        jobject thiz,
jstring filepath,
        jint seq_id) {

if (ctx == nullptr) {
LOGE("Context is null");
return JNI_FALSE;
}

const char * path =
        env->GetStringUTFChars(filepath, nullptr);

size_t result =
        llama_state_seq_save_file(
                ctx,
                path,
                (llama_seq_id) seq_id,
                session_tokens.data(),
                session_tokens.size()
        );

env->ReleaseStringUTFChars(filepath, path);

if (result == 0) {
LOGE("Failed to save KV cache");
return JNI_FALSE;
}

LOGI("KV cache saved successfully");

return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
        Java_com_aacbridge_inference_LlamaBridge_loadKVCache(
        JNIEnv *env,
        jobject thiz,
jstring filepath,
        jint seq_id) {

if (ctx == nullptr) {
LOGE("Context is null");
return JNI_FALSE;
}

const char * path =
        env->GetStringUTFChars(filepath, nullptr);

const size_t max_tokens = 4096;

size_t token_count = 0;

/*
 * Clear any existing KV entries for the target
 * sequence before restoring from disk.
 *
 * Without this, stale entries from a previous
 * runInference() call remain at positions beyond
 * the loaded range. When resumeInference() later
 * tries to decode at those positions, llama_decode
 * fails with error 1 ("could not find a KV slot")
 * because the positions are already occupied.
 *
 * llama_memory_seq_rm with p0=-1, p1=-1 removes
 * ALL positions for the given seq_id.
 */
llama_memory_seq_rm(
    llama_get_memory(ctx),
    (llama_seq_id) seq_id,
    -1,
    -1
);

session_tokens.resize(max_tokens);

size_t result =
        llama_state_seq_load_file(
                ctx,
                path,
                (llama_seq_id) seq_id,
                session_tokens.data(),
                max_tokens,
                &token_count
        );

env->ReleaseStringUTFChars(filepath, path);

if (result == 0) {
LOGE("Failed to load KV cache");
return JNI_FALSE;
}

session_tokens.resize(token_count);

LOGI("KV cache loaded successfully");

return JNI_TRUE;
}

extern "C"
JNIEXPORT jstring JNICALL
        Java_com_aacbridge_inference_LlamaBridge_runInference(
        JNIEnv *env,
        jobject thiz,
jstring prompt) {

if (ctx == nullptr || model == nullptr) {
LOGE("Model or context not initialized");
return env->NewStringUTF(
"Model not initialized"
);
}

const char * prompt_chars =
        env->GetStringUTFChars(prompt, nullptr);

std::string input_text(prompt_chars);

env->ReleaseStringUTFChars(prompt, prompt_chars);

session_tokens.clear();

const llama_vocab * vocab =
        llama_model_get_vocab(model);

int token_count =
        llama_tokenize(
                vocab,
                input_text.c_str(),
                input_text.length(),
                nullptr,
                0,
                true,
                true
        );

if (token_count < 0) {
    int required = -token_count;
    session_tokens.resize(required);
} else {
    session_tokens.resize(token_count);
}

llama_tokenize(
        vocab,
        input_text.c_str(),
        input_text.length(),
        session_tokens.data(),
        session_tokens.size(),
true,
true
);

llama_batch batch =
        llama_batch_get_one(
                session_tokens.data(),
                session_tokens.size()
        );

auto prefill_start =
    std::chrono::high_resolution_clock::now();

int decode_result =
        llama_decode(ctx, batch);

auto prefill_end =
    std::chrono::high_resolution_clock::now();

double prefill_ms =
    std::chrono::duration<double, std::milli>(
        prefill_end - prefill_start
    ).count();

int n_prompt_tokens = (int) session_tokens.size();

if (decode_result != 0) {
LOGE("Initial decode failed");

return env->NewStringUTF(
"Inference failed"
);
}

llama_sampler * smpl =
        llama_sampler_chain_init(
                llama_sampler_chain_default_params()
        );

llama_sampler_chain_add(
        smpl,
        llama_sampler_init_greedy()
);

std::string generated_text;

const int max_generation_tokens = 64;

auto gen_start =
    std::chrono::high_resolution_clock::now();

int gen_token_count = 0;

for (int i = 0;
i < max_generation_tokens;
i++) {

llama_token new_token =
        llama_sampler_sample(
                smpl,
                ctx,
                -1
        );

if (llama_vocab_is_eog(
        vocab,
        new_token
)) {

LOGI("EOS token reached");

break;
}

session_tokens.push_back(new_token);
gen_token_count++;

char piece[256];

int piece_length =
        llama_token_to_piece(
                vocab,
                new_token,
                piece,
                sizeof(piece),
                0,
                true
        );

if (piece_length > 0) {
generated_text.append(
        piece,
        piece_length
);
}

llama_batch next_batch =
        llama_batch_get_one(
                &new_token,
                1
        );

int next_decode =
        llama_decode(
                ctx,
                next_batch
        );

if (next_decode != 0) {

LOGE(
        "Decode failed during generation loop"
);

llama_sampler_free(smpl);

return env->NewStringUTF(
"Generation failed"
);
}
}

llama_sampler_free(smpl);

auto gen_end =
    std::chrono::high_resolution_clock::now();

double gen_ms =
    std::chrono::duration<double, std::milli>(
        gen_end - gen_start
    ).count();

/*
 * Store metrics into static tracking variables
 * before returning. Kotlin reads these under
 * engineLock immediately after this call.
 */
last_prefill_ms    = prefill_ms;
last_gen_ms        = gen_ms;
last_prompt_tokens = n_prompt_tokens;
last_gen_tokens    = gen_token_count;

LOGI("TIMING,runInference,"
     "prefill_ms=%.2f,"
     "gen_ms=%.2f,"
     "prompt_tokens=%d,"
     "gen_tokens=%d",
     prefill_ms, gen_ms,
     n_prompt_tokens, gen_token_count);

LOGI("Inference completed successfully");

return env->NewStringUTF(
        generated_text.c_str()
);
}

/*
 * =====================================================
 * clearKVCache()
 * =====================================================
 *
 * Clears all entries from the KV cache and resets
 * session_tokens. Must be called between independent
 * inference trials to prevent context exhaustion.
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_aacbridge_inference_LlamaBridge_clearKVCache(
        JNIEnv *env,
        jobject thiz) {

if (ctx == nullptr) {
LOGE("Context is null");
return;
}

llama_memory_clear(
    llama_get_memory(ctx),
    true
);

session_tokens.clear();

LOGI("KV cache cleared");
}

/*
 * =====================================================
 * prefillOnly()
 * =====================================================
 *
 * Tokenizes and decodes the prompt WITHOUT entering
 * the generation loop.
 *
 * Purpose:
 *   Populate the KV cache with context-only attention
 *   tensors so that saveKVCache() serializes a clean
 *   state without stale generation tokens.
 *
 * This replaces the previous priming flow which used
 * runInference() (generating 64 unwanted tokens)
 * followed by saveKVCache().
 *
 * After this call:
 *   - session_tokens contains the prompt tokens
 *   - KV cache contains attention tensors for the
 *     prompt tokens ONLY
 *   - No generation output is produced
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

const char * prompt_chars =
        env->GetStringUTFChars(prompt, nullptr);

std::string input_text(prompt_chars);

env->ReleaseStringUTFChars(prompt, prompt_chars);

session_tokens.clear();

const llama_vocab * vocab =
        llama_model_get_vocab(model);

int token_count =
        llama_tokenize(
                vocab,
                input_text.c_str(),
                input_text.length(),
                nullptr,
                0,
                true,
                true
        );

if (token_count < 0) {
    int required = -token_count;
    session_tokens.resize(required);
} else {
    session_tokens.resize(token_count);
}

llama_tokenize(
        vocab,
        input_text.c_str(),
        input_text.length(),
        session_tokens.data(),
        session_tokens.size(),
        true,
        true
);

llama_batch batch =
        llama_batch_get_one(
                session_tokens.data(),
                session_tokens.size()
        );

int decode_result =
        llama_decode(ctx, batch);

if (decode_result != 0) {
LOGE("Prefill decode failed");
return JNI_FALSE;
}

LOGI("Prefill completed: %d tokens",
     (int)session_tokens.size());

return JNI_TRUE;
}

/*
 * =====================================================
 * resumeInference()
 * =====================================================
 *
 * Continues generation from a previously loaded KV
 * cache state.
 *
 * Precondition:
 *   loadKVCache() must have been called successfully.
 *   session_tokens must contain the token history
 *   restored by loadKVCache().
 *
 * Differences from runInference():
 *   1. Does NOT clear session_tokens
 *   2. Tokenizes intent WITHOUT BOS (add_special=false)
 *   3. Creates batch with EXPLICIT positions starting
 *      at n_past = session_tokens.size()
 *   4. Uses explicit positions in generation loop
 *
 * This avoids the position collision that occurs when
 * llama_batch_get_one(pos=NULL) auto-assigns positions
 * starting from 0, overwriting loaded KV entries.
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_aacbridge_inference_LlamaBridge_resumeInference(
        JNIEnv *env,
        jobject thiz,
        jstring prompt) {

if (ctx == nullptr || model == nullptr) {
LOGE("Model or context not initialized");
return env->NewStringUTF(
        "Model not initialized"
);
}

int n_past = (int) session_tokens.size();

if (n_past == 0) {
LOGE("resumeInference: session_tokens is empty. "
     "Call loadKVCache() first.");
return env->NewStringUTF(
        "No KV cache loaded"
);
}

LOGI("resumeInference: n_past = %d", n_past);

const char * prompt_chars =
        env->GetStringUTFChars(prompt, nullptr);

std::string input_text(prompt_chars);

env->ReleaseStringUTFChars(prompt, prompt_chars);

const llama_vocab * vocab =
        llama_model_get_vocab(model);

/*
 * Tokenize the intent prompt.
 *
 * add_special = false:
 *   The original context already includes BOS at
 *   position 0 (from prefillOnly). Adding a second
 *   BOS here would corrupt the token stream.
 *
 * parse_special = true:
 *   Allow special token parsing in case the prompt
 *   contains control tokens.
 */
int token_count =
        llama_tokenize(
                vocab,
                input_text.c_str(),
                input_text.length(),
                nullptr,
                0,
                false,
                true
        );

std::vector<llama_token> new_tokens;

if (token_count < 0) {
    new_tokens.resize(-token_count);
} else {
    new_tokens.resize(token_count);
}

llama_tokenize(
        vocab,
        input_text.c_str(),
        input_text.length(),
        new_tokens.data(),
        new_tokens.size(),
        false,
        true
);

int n_new = (int) new_tokens.size();

LOGI("resumeInference: appending %d intent tokens "
     "at pos %d",
     n_new, n_past);

/*
 * Use llama_batch_get_one for auto-tracked positions.
 *
 * llama_state_seq_load_file updates the internal
 * position tracker. llama_batch_get_one relies on
 * this tracker to assign the correct positions
 * starting after the loaded KV cache entries.
 */
llama_batch batch =
        llama_batch_get_one(
                new_tokens.data(),
                n_new
        );

auto prefill_start =
    std::chrono::high_resolution_clock::now();

int decode_result =
        llama_decode(ctx, batch);

auto prefill_end =
    std::chrono::high_resolution_clock::now();

double prefill_ms =
    std::chrono::duration<double, std::milli>(
        prefill_end - prefill_start
    ).count();

if (decode_result != 0) {
LOGE("Resume prefill decode failed");
return env->NewStringUTF(
        "Inference failed"
);
}

/*
 * Append the intent tokens to session_tokens
 * so the token history remains consistent.
 */
for (int i = 0; i < n_new; i++) {
    session_tokens.push_back(new_tokens[i]);
}

/*
 * Generation loop with auto-tracked positions.
 *
 * Since we use llama_batch_get_one for the prefill,
 * the internal position tracker is already set to
 * n_past + n_new. Each generation step appends one
 * token and the tracker advances automatically.
 */
llama_sampler * smpl =
        llama_sampler_chain_init(
                llama_sampler_chain_default_params()
        );

llama_sampler_chain_add(
        smpl,
        llama_sampler_init_greedy()
);

std::string generated_text;

const int max_generation_tokens = 64;

auto gen_start =
    std::chrono::high_resolution_clock::now();

int gen_token_count = 0;

for (int i = 0;
     i < max_generation_tokens;
     i++) {

    llama_token new_token =
            llama_sampler_sample(
                    smpl,
                    ctx,
                    -1
            );

    if (llama_vocab_is_eog(
            vocab,
            new_token
    )) {

        LOGI("EOS token reached");

        break;
    }

    session_tokens.push_back(new_token);
    gen_token_count++;

    char piece[256];

    int piece_length =
            llama_token_to_piece(
                    vocab,
                    new_token,
                    piece,
                    sizeof(piece),
                    0,
                    true
            );

    if (piece_length > 0) {
        generated_text.append(
                piece,
                piece_length
        );
    }

    llama_batch gen_batch =
            llama_batch_get_one(
                    &new_token,
                    1
            );

    int next_decode =
            llama_decode(
                    ctx,
                    gen_batch
            );

    if (next_decode != 0) {

        LOGE(
          "Decode failed during generation loop"
        );

        llama_sampler_free(smpl);

        return env->NewStringUTF(
                "Generation failed"
        );
    }

}

llama_sampler_free(smpl);

auto gen_end =
    std::chrono::high_resolution_clock::now();

double gen_ms =
    std::chrono::duration<double, std::milli>(
        gen_end - gen_start
    ).count();

/*
 * Store metrics into static tracking variables
 * before returning. Kotlin reads these under
 * engineLock immediately after this call.
 */
last_prefill_ms    = prefill_ms;
last_gen_ms        = gen_ms;
last_prompt_tokens = n_new;
last_gen_tokens    = gen_token_count;

LOGI("TIMING,resumeInference,"
     "prefill_ms=%.2f,"
     "gen_ms=%.2f,"
     "prompt_tokens=%d,"
     "gen_tokens=%d",
     prefill_ms, gen_ms,
     n_new, gen_token_count);

LOGI("Resume inference completed successfully");

return env->NewStringUTF(
        generated_text.c_str()
);
}


/*
 * =====================================================
 * Native timing getters
 * =====================================================
 *
 * Return the most-recent inference timing and token
 * metrics. These are set by runInference() and
 * resumeInference() before they return.
 *
 * Thread safety:
 *   Callers MUST hold the Kotlin engineLock when
 *   calling these getters AND the preceding inference
 *   function within the same lock acquisition.
 *   This guarantees the values correspond to the
 *   intended inference call and are not overwritten
 *   by a concurrent call.
 */
extern "C"
JNIEXPORT jdouble JNICALL
Java_com_aacbridge_inference_LlamaBridge_getLastPrefillMs(
        JNIEnv *env,
        jobject thiz) {
    return (jdouble) last_prefill_ms;
}

extern "C"
JNIEXPORT jdouble JNICALL
Java_com_aacbridge_inference_LlamaBridge_getLastGenMs(
        JNIEnv *env,
        jobject thiz) {
    return (jdouble) last_gen_ms;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_aacbridge_inference_LlamaBridge_getLastPromptTokens(
        JNIEnv *env,
        jobject thiz) {
    return (jint) last_prompt_tokens;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_aacbridge_inference_LlamaBridge_getLastGenTokens(
        JNIEnv *env,
        jobject thiz) {
    return (jint) last_gen_tokens;
}

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

session_tokens.clear();

llama_backend_free();

LOGI("Resources released");
}