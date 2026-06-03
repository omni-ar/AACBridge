# Architectural Critique: AACBridge vs. Effective Agent Principles

This document provides a brutal, surface-level-stripped architectural critique of the AACBridge project against the five principles for effective agents (Scalability, Modularity, Continuous Learning, Resilience, Future-proofing).

---

## 1. Scalability 
**Status:** $O(N)$ Computational Bottleneck
* **The Reality:** Bounding the KV Cache to the top-3 states in RAM is good local resource management, but it is not computational scalability. The `StateRouter.getScoredStates()` function iterates over every single state in the database to compute complex trigonometric functions (Haversine distance, Gaussian decay, BLE sigmoids). As the state count grows (e.g., $N=500$ contexts over a year), these math operations become a severe bottleneck. While daemon triggers (`BOOT_COMPLETED`, geofencing, `AlarmManager`) correctly run on background threads, the specific bug to guard against is Heer calling `getTopContextIds()` directly from the UI thread in `MainActivity`.
* **The Required Fix:** `StateRouter` scoring must strictly enforce background execution (e.g., via `withContext(Dispatchers.Default)`). For long-term scalability, geospatial indexing (like an R-Tree for GPS locations) should be implemented to cull distant states before running heavy math.

## 2. Modularity
**Status:** Strong and Architecturally Sound
* **The Reality:** The architecture adheres well to this principle. The most successful example is the `LlamaBridgeAdapter` interface, which completely abstracts the JVM from the complex JNI pointer lifecycle. The separation of concerns between the deterministic State Router, the KV Cache Manager, and the Context Daemon ensures components can be isolated and tested.
* **The Required Fix:** None required at this time. Maintain strict adherence to these existing boundary contracts.

## 3. Continuous Learning
**Status:** Overclaimed / Not Yet Implemented
* **The Reality:** Describing the current system as possessing "continuous learning" is an academic overreach. The `DriftDetector` is a static heuristic—it checks a cosine similarity threshold. It does not update weights, backpropagate, or adapt its logic. True learning relies on the Contextual Multi-Armed Bandit (CMAB) for adaptive response styling, which currently remains on the drawing board as an unimplemented, "cuttable" feature.
* **The Required Fix:** Acknowledge in the paper that the current implementation relies on rule-based heuristics. To legitimately claim continuous learning, the CMAB (`ResponseSelector.kt`) must be fully implemented, utilizing the Exponential Moving Average (EMA) to adapt weights based on implicit user feedback.

## 4. Resilience
**Status:** The Persistence Gap (Broken Cold Start)
* **The Reality:** While runtime fallbacks (two-tier response) and concurrency protections (4-layer mutex strategy) are highly resilient, data persistence is fundamentally broken. The `saveKVCache()` function is never orchestrated or called. Consequently, if the Android OS kills the foreground service, all pre-computed LLM context is wiped out. The system is amnesiac on reboot.
* **The Required Fix:** The `saveKVCache()` lifecycle must be explicitly defined and triggered by the daemon immediately after prefill. Furthermore, this fix MUST include atomic write semantics: the JNI layer must write the serialized state to a `.tmp` file, flush to disk, and then `rename()` it to the final `.bin` path. `rename` is atomic on ext4 (Android), preventing corrupted half-written files from causing C++ segfaults if the process is killed mid-write.

## 5. Future-proofing
**Status:** The `.bin` Corruption Danger
* **The Reality:** The system is blindly future-proofing models without future-proofing the cache. `llama_state_save_seq` dumps raw memory bytes without any metadata. If the underlying GGUF model is updated (e.g., swapping `Qwen2.5` for `Phi-3`), loading an old `.bin` file will inject mismatched tensor shapes into the C++ execution graph, resulting in a catastrophic memory segfault.
* **The Required Fix:** A custom magic byte header protocol must be implemented for cache serialization. Before calling `llama_state_save_seq`, the JNI bridge must write a header containing the schema version and the SHA-256 hash of the specific `.gguf` file (exactly 32 bytes). String names are mutable and unreliable; cryptographic hashes are not. The `loadKVCache` function must strictly validate this 32-byte hash against the currently active model before passing the file handle to `llama.cpp`.
