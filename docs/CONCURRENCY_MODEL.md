# KV Cache Concurrency Model

This document outlines the threading and synchronization mechanisms governing the KV Cache in Phase 2. The concurrency model is designed to prevent `IOException` and JNI hard crashes when the `StateRouter`, `DriftDetector`, and Inference Engine attempt to read, write, and evict the same memory slots concurrently.

## The Problem: The Eviction Race Condition
If the `StateRouter` decides to load a new state, it must evict an old one. If the eviction process deletes a `.bin` file from flash memory at the exact millisecond the inference engine's `llama.cpp` is executing a `Java_loadKVCache()` call on that same file, the JVM throws an `IOException` which collapses the JNI bridge and hard-crashes the app.

## The Solution: Four-Layer Concurrency Strategy

To guarantee thread safety without sacrificing the sub-500ms TTFT budget, the architecture entirely avoids global locks. Instead, it relies on atomic state variables and fine-grained mutexes.

### 1. Per-State Mutex Isolation
* **Implementation:** `CacheMutexRegistry` manages a `ConcurrentHashMap<String, Mutex>`.
* **Why:** Locking the entire `KVCacheManager` globally during a multi-second flash read/write would freeze the UI and block orthogonal operations. By using `computeIfAbsent` to atomically generate a mutex per `stateId`, operations on "State_A" never block operations on "State_B".

### 2. Atomic Acquisition (`isActive` + `refCount`)
* **Variables:** `CacheState` uses a `@Volatile var isActive: Boolean` and an `AtomicInteger` for `refCount`.
* **Acquisition (`acquireStateForInference`):** 
  Both variables are checked and modified while holding the specific state's Mutex. If the state `isActive`, its `refCount` is incremented. This guarantees that eviction logic cannot mark the state as inactive while the inference engine is grabbing a reference to it.

### 3. Safe Eviction Lifecycle
Eviction does not immediately delete files. It follows a strict safety protocol:
1. **Mark Inactive:** Acquire the state's Mutex and set `isActive = false`. This acts as an immediate gate preventing any *new* inference requests from acquiring this state.
2. **Release Mutex:** The Mutex is released so we do not block other threads pointlessly.
3. **Drain Wait:** The eviction coroutine polls `refCount.get() > 0`. Because `isActive` is false, the `refCount` is mathematically guaranteed to be **monotonically decreasing**. It will eventually reach `0`.
4. **Execution:** Once `refCount == 0`, it is absolutely safe to recycle the `seqId` and instruct the `LlamaBridge` to load the new file over the old one.

### 4. Lock-Free `releaseState`
* **Why:** The `releaseState()` function explicitly does *not* acquire the state's Mutex.
* **Justification:** Releasing a state simply involves calling `refCount.decrementAndGet()`. Because atomic integers handle their own thread-safety, and because we have proven that after `isActive = false` the count only goes down, forcing the release thread to wait for a Mutex is unnecessary overhead. The eviction polling loop will catch the zero-state organically.

## Testing Verification
This specific concurrency logic—specifically the proof that eviction properly waits for the `refCount` to drain before recycling a `seqId`—is verified using `kotlinx-coroutines-test` in `KVCacheManagerTest.kt`, utilizing `TestCoroutineScheduler` to precisely control virtual time and thread suspension.
