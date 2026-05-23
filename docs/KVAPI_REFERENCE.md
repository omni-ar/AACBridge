# KV Cache API Reference & seqId Lifecycle

This document defines the boundary contract between the JVM orchestration layer (`KVCacheManager`) and the native C++ inference engine (`llama.cpp`) for caching operations.

## The Native Ring Buffer (`seqId` Ownership)

A critical architectural realization of Phase 2 is understanding exactly who owns the KV Cache memory. 
* **The JVM does not own the tensor memory.**
* **The file path does not dictate the slot.**

Instead, `llama.cpp` owns the KV memory through a series of fixed native slots called **`seqId`**s (Sequence IDs). 

### The Slot Mapping Model
The `HardwareConfig` defines `MAX_ACTIVE_KV_STATES = 3` (enforcing a strict ~2GB memory budget on the edge device). 

1. `KVCacheManager` initializes a `ConcurrentLinkedQueue` pool of available IDs: `[0, 1, 2]`.
2. When a semantic context (e.g., "home_mom") scores high enough to be resident, the Manager pops an available `seqId` from the pool.
3. The Manager instructs the `LlamaBridgeAdapter` to load the `.bin` file into that specific `seqId` slot natively.
4. The semantic mapping `{"home_mom" -> seqId 1}` is tracked in the JVM's `activeStates` map.

### The Absence of `freeKVCache()`
There is no explicit API to "free" or "clear" a KV cache natively. 

* **Why:** Native memory allocation is expensive. Continually freeing and re-`malloc`ing gigabytes of memory destroys latency.
* **How Eviction Works:** When "home_mom" is evicted, the JVM simply takes its assigned `seqId` (e.g., `1`) and pushes it back into the available pool. When the next state needs to load, it grabs `seqId 1` and `llama_state_load_seq` simply **overwrites** the existing native memory buffer. 

This model guarantees an invariant: `activeStates.size + availableSeqIds.size == MAX_ACTIVE_KV_STATES` at all times.

## JNI Bridge Methods

The `LlamaBridgeAdapter` requires the following interface implementations for KV caching:

* `fun loadKVCache(filepath: String, seqId: Int): Boolean`
  Loads a saved KV cache binary from flash memory into the specified native sequence ID slot. If this fails at the JNI layer, the JVM immediately catches the `false` return and returns the `seqId` to the available pool to prevent slot leakage.
  
* `fun saveKVCache(filepath: String, seqId: Int): Boolean`
  Saves the current state of a native sequence slot to flash memory. (Implementation pending Room DB serialization).

### ⚠️ Open Architectural Question: The `saveKVCache()` Lifecycle
Currently, the exact orchestration of *when* a KV cache is written to disk remains unresolved. While `loadKVCache()` is fully orchestrated by the daemon and router, `saveKVCache()` is never called in the Phase 2 implementation. 

**Required Phase 3 Resolution:**
Who triggers the save? Does it happen asynchronously after inference completes? Does a separate worker pre-compute and serialize states overnight? If the cache is never saved to disk, the cold start recovery mechanism will have nothing to load into RAM.
