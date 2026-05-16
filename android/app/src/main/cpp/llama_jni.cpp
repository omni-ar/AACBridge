#include <jni.h>
#include <string>
#include <vector>

#include <android/log.h>

#include "llama.h"

#define LOG_TAG "AACBridgeJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static llama_model * model = nullptr;
static llama_context * ctx = nullptr;

static std::vector<llama_token> session_tokens;

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

session_tokens.resize(max_tokens);

size_t token_count = 0;

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

int decode_result =
        llama_decode(ctx, batch);

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

LOGI("Inference completed successfully");

return env->NewStringUTF(
        generated_text.c_str()
);
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

session_tokens.clear();

llama_backend_free();

LOGI("Resources released");
}