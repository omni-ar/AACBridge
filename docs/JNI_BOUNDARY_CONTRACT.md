# AACBridge JNI Boundary Contract

This document details the architectural and engineering boundary between the Kotlin runtime and the native Snapdragon ARM64 backend (`llama.cpp` + `ggml`).

## 1. Exported JNI Methods & Kotlin Signatures

The native layer exposes a strict, stateful API via JNI. The C++ functions map directly to the `com.aacbridge.inference.LlamaBridge` object.

### JNI Exports (`llama_jni.cpp`)
- `Java_com_aacbridge_inference_LlamaBridge_initializeBackend`
- `Java_com_aacbridge_inference_LlamaBridge_initializeModel`
- `Java_com_aacbridge_inference_LlamaBridge_saveKVCache`
- `Java_com_aacbridge_inference_LlamaBridge_loadKVCache`
- `Java_com_aacbridge_inference_LlamaBridge_runInference`
- `Java_com_aacbridge_inference_LlamaBridge_release`

### Kotlin Signatures (`LlamaBridge.kt`)
```kotlin
external fun initializeBackend()
external fun initializeModel(modelPath: String): Boolean
external fun saveKVCache(filepath: String, seqId: Int): Boolean
external fun loadKVCache(filepath: String, seqId: Int): Boolean
external fun runInference(prompt: String): String
external fun release()
```

## 2. Library Loading Flow
The JNI bridge is initialized via an `init` block inside the `LlamaBridge` Kotlin object:
```kotlin
init {
    System.loadLibrary("aacbridge-jni")
}
```
At runtime, Android's dynamic linker searches the APK's `lib/arm64-v8a/` directory for `libaacbridge-jni.so`. Upon loading, it automatically resolves upstream dependencies (such as `libllama.so`, `libggml.so`, `libggml-cpu.so`).

## 3. ARM64 ABI Restriction
The build is strictly restricted to `arm64-v8a` via `build.gradle` (`abiFilters "arm64-v8a"`). This is mandatory because `llama.cpp` performance relies heavily on hardware-specific NEON SIMD instructions available on the Snapdragon SoC. Compiling for x86 or 32-bit ARM introduces fallback paths that violate our low-latency constraints.

## 4. OpenMP Dependency Issue (`libomp.so`)
`llama.cpp` heavily utilizes OpenMP for parallelizing tensor operations across the CPU cores. Android NDK builds must ensure that the OpenMP runtime is available. If OpenMP is dynamically linked by the ggml/llama prebuilts, `libomp.so` must be packaged inside `jniLibs/arm64-v8a/`. Failure to package this will result in an `UnsatisfiedLinkError` when `libggml-cpu.so` attempts to resolve the OpenMP runtime dependency (`libomp.so`) at runtime.

## 5. GGUF Loading Path
Models are loaded via `initializeModel(modelPath: String)`. The `modelPath` is a standard Linux absolute filesystem path (e.g., `/data/local/tmp/models/qwen2.5-0.5b-instruct-q4_k_m.gguf`). 
The JVM passes a `jstring` which is safely converted using `env->GetStringUTFChars`, fed to `llama_model_load_from_file`, and then immediately released via `env->ReleaseStringUTFChars` to prevent JNI reference leaks.

## 6. Tokenization Negative Buffer Fix
The `llama_tokenize` function API expects an adequately sized output buffer. If the provided buffer is too small, it returns the *negative* number of tokens required to hold the output. `llama_jni.cpp` correctly handles this via a two-pass tokenization fix:
```cpp
if (token_count < 0) {
    int required = -token_count;
    session_tokens.resize(required);
} else {
    session_tokens.resize(token_count);
}
```
This ensures safe memory allocation before the second `llama_tokenize` call accurately fills the `session_tokens` vector.

## 7. Why Tensors are Not Passed Through JNI
The boundary contract strictly passes scalar types (Booleans, Ints) and Strings. We do *not* pass raw tensors (e.g., float arrays or ByteBuffer bindings) across the JNI boundary for the following reasons:
- **JNI Overhead**: Copying large tensor arrays between the C++ heap and the JVM heap is prohibitively expensive and violates the latency budget.
- **Ownership**: `ggml` strictly manages its own tensor memory arenas. Exposing these to the JVM Garbage Collector risks memory corruption or segfaults.
- **Design**: The system operates as an end-to-end generator natively, returning only the final decoded `String` back to Kotlin.

## 8. Threading Model Notes
- **Context Constraints**: The `llama_context` is globally shared in the JNI implementation (`static llama_context * ctx`). 
- **Execution Thread**: The current implementation uses a fixed thread count during early benchmarking, optimizing for Snapdragon big.LITTLE core architectures without oversaturating the thermal envelope, but this may later become dynamically configurable during Phase 3 benchmarking.
- **Kotlin Concurrency**: JNI invocations (like `runInference` and `initializeModel`) block the calling thread while native inference loops run. Therefore, Kotlin MUST dispatch these calls off the main UI thread (e.g., via `kotlin.concurrent.thread` or Coroutines) to prevent ANR (Application Not Responding) crashes, as correctly implemented in `MainActivity.kt`.
