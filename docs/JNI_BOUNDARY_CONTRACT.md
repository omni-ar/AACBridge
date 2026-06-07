# AACBridge JNI Boundary Contract

This document details the architectural and engineering boundary between the Kotlin runtime and the native Snapdragon ARM64 backend (`llama.cpp` + `ggml`).

## 1. Exported JNI Methods & Kotlin Signatures

The native layer exposes a strict, stateful API via JNI. The C++ functions map directly to the `com.aacbridge.inference.LlamaBridgeAdapter` interface implementations.

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
The JNI bridge is initialized via an `init` block:
```kotlin
init {
    System.loadLibrary("aacbridge-jni")
}
```

## 3. Threading & `engineLock` Expectations
The `llama_context` is globally shared in the JNI implementation (`static llama_context * ctx`). 

Because JNI invocations block the calling thread and `llama.cpp` modifies global state, **Kotlin MUST acquire the `engineLock` (ReentrantLock) before crossing the JNI boundary** for operations like `runInference`, `saveKVCache`, or `loadKVCache`. This strictly serializes access across the JVM and prevents `SIGBUS` memory mapping crashes that occur when background cache eviction collides with foreground LLM execution.

## 4. Why Tensors are Not Passed Through JNI
The boundary contract strictly passes scalar types (Booleans, Ints) and Strings. We do *not* pass raw tensors (e.g., float arrays or ByteBuffer bindings) across the JNI boundary for the following reasons:
- **JNI Overhead**: Copying large tensor arrays between the C++ heap and the JVM heap is prohibitively expensive and violates the latency budget.
- **Ownership**: `ggml` strictly manages its own tensor memory arenas. Exposing these to the JVM Garbage Collector risks memory corruption.
