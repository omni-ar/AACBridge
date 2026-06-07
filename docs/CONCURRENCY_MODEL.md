# KV Cache Concurrency Model

This document outlines the threading and synchronization mechanisms governing the KV Cache integration in Phase 2. The concurrency model is designed to prevent `IOException` and JNI hard crashes (specifically `SIGBUS` memory mapping errors) when the `StateRouter`, `DriftDetector`, and Inference Engine attempt to interact with the underlying `llama.cpp` context pointers concurrently.

## The Problem: The Eviction Race Condition & SIGBUS
If the `StateRouter` decides to load a new state, it must evict an old one. In early versions, if eviction deleted a `.bin` file from flash memory at the exact millisecond the inference engine's `llama.cpp` was executing a `Java_loadKVCache()` call on that same file, the JVM threw an `IOException` or the native library crashed with `SIGBUS`, collapsing the JNI bridge and hard-crashing the app.

## The Solution: Single Shared Engine Lock

During Block 2.1 Device Stabilization, the architecture was refactored to implement a strict, provable concurrency model.

### 1. The `engineLock` (ReentrantLock)
* **Implementation:** A single shared `engineLock` (Kotlin `ReentrantLock` or `Mutex` wrapper) spans across the application layer.
* **Why:** `llama.cpp` holds massive global state for its active context. Multi-threaded access across the JNI boundary to the same `llama_context` memory blocks is fundamentally unsafe. By introducing `engineLock`, we ensure that only one thread can ever interact with `llama.cpp` memory at any given nanosecond.

### 2. Lock Boundaries
The `engineLock` strictly serializes operations across three primary orchestrators:
- **`MainActivity`:** When the user triggers an intent and `runInference` is called, the lock is acquired before prompt injection.
- **`KVCacheManager`:** When evicting an old state or swapping a `seqId`, the lock prevents the UI from attempting inference during a cache swap.
- **`ContextPrimerImpl`:** Background cache precomputation (`prime` -> `saveKVCache`) must hold the lock so it does not collide with foreground `runInference` calls.

### 3. Safe Eviction Lifecycle
Eviction does not immediately delete files. It follows a strict safety protocol:
1. **Acquire `engineLock`:** To ensure `llama.cpp` is not currently reading or writing.
2. **Mark Inactive:** Act as an immediate gate preventing any *new* inference requests from acquiring this state.
3. **Execution:** Once it is safe, recycle the `seqId` and instruct the `LlamaBridge` to load the new file over the old one.

### 4. JNI Boundary Contract Protection
The concurrency model exists explicitly to protect the JNI layer. The Kotlin code manages the `engineLock` so that the C++ code (`llama_jni.cpp`) never has to handle complex thread-safety mechanics. The JVM orchestrates safe entry into native space.
