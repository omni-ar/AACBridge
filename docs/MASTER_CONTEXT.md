# AACBridge - Master Context Document

> Paste this entire file at the start of every new chat to restore full project context.
> Last updated: April 2026

---



## 1. Project Identity

**Full Title:** Predictive Amortized KV-Caching for Low-Latency Edge LLMs in Augmentative and Alternative Communication

**Short Name:** AACBridge

**Type:** B.Tech Capstone Project - Team of 3, one semester (May 15 - July 15, 2026)

**Domain:** AI / Healthcare / Embedded Systems / Edge Computing

**Target Publication:** IEEE Access (Q1, Scopus indexed, ~4-6 week review). Submit mid-July 2026 so "Under review at IEEE Access" appears on resume during placement season (July-August).

**Fallback Publication:** Computers in Biology and Medicine (Elsevier, Q1, Scopus).

**NOT targeting:** AAAI (requires theoretical proofs + clinical user studies), ACM MobiSys (6-12 month cycle, misses placement season), IEEE/CVF (computer vision, wrong scope), ECIR (information retrieval, wrong scope).

---

## 2. Team & Role Split

| Member    | Domain                | Owns                                                                                                                                                     |
| --------- | --------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Arjit** | Backend / LLM         | llama.cpp JNI bridge, KV cache manager, state router (scoring function), async drift detector, latency benchmarking, memory profiling, concurrency model |
| **Medha** | Input / ML            | NinaPro EMG pipeline, CNN-LSTM intent classifier, dataset preprocessing, k-ablation for drift detection, TFLite/ONNX model export                        |
| **Heer**  | App / UI + Paper lead | Android app (Kotlin, UI, context collection - GPS/BLE/time), gaze tracking, cross-attention fusion model, manuscript writing lead                        |

---

## 3. Core Technical Contribution

### The Problem

Standard RAG on edge devices for real-time AAC is broken. When an LLM dynamically injects user context (location, conversation history, speaker profile) at runtime, it must process an O(N) token prefill phase before generating a single word. On a mobile NPU, this causes 2-3 second delays. For an AAC user mid-conversation, this is unacceptable.

### The Solution: KV Cache Priming (CAP-KVC)

Instead of dynamically assembling text prompts at runtime, a background daemon pre-computes the patient's personal context as Key-Value (KV) cache tensors and holds them in memory. When an intent signal arrives, the LLM processes only the new intent tokens against the already-cached context.

**Latency reduction:** from O(N) prefill to a localized memory retrieval.
**Target:** TTFT < 500ms end-to-end on Snapdragon 8-series Android device.
**Key claim:** Amortized prefill latency reduction (NOT O(1) globally - the O(N) work happens upfront in the daemon, not at inference time. Reframe as "amortized" not "eliminated").

---

## 4. Full System Architecture

### Layer 1 - Sensor & Intent Layer (Medha + Heer)

- Surface EMG (sEMG) via BLE-connected facial wearable -> CNN-LSTM classifier -> discrete intent payload
- Eye gaze via front camera MediaPipe Face Mesh -> 400ms dwell threshold -> gaze fixation vector
- Cross-attention fusion: EMG embedding (query) x gaze vector (key/value) -> unified intent embedding
- Fallback: concatenation-based late fusion if cross-attention F1 gap vs late fusion < 3% (decision point: June 7)
- EMG dataset: NinaPro DB5 (public, simulated - hardware unavailable)
- 5 intent classes: confirm, reject, scroll, select, call-help

### Layer 2 - Deterministic State Router (Arjit)

Scores all 50 context states deterministically. NO ML model.

**Scoring function:**

```
S(ci) = alpha·S_time + beta·S_gps + gamma·S_ble
```

where alpha + beta + gamma = 1 (convex combination, scores bounded [0,1])

**Time score:**

```
d = min(|t1-t2|, 24-|t1-t2|)   <- circular distance
S_time = exp(-d²/2sigma²)      <- Gaussian decay, tunable sigma
```

**GPS score:**

```
d = Haversine(lat1,lng1, lat2,lng2)
S_gps = exp(-d/lambda)         <- exponential decay, tunable lambda
```

**BLE score:**

```
S_ble = Σ(wi·xi) / Σwi         <- weighted detection (xi in {0,1})
```

**Dynamic weights (signal reliability):**

```
beta(a_gps) = exp(-a_gps/lambda_gps)   <- GPS accuracy in meters, low=precise=high weight
gamma = R_ble = (1/M)·Σsigma(rssii)    <- RSSI sigmoid, M=detected devices
  where sigma(rssi) = 1/(1+exp(-k(rssi-r0))), r0=-70dBm reference
  EDGE CASE: M=0 -> R_ble=0, gamma=0, weight redistributes to alpha and beta
alpha = baseline (time always available), renormalized so alpha+beta+gamma=1
```

**BLE Soft OR (alternative aggregation):**

```
score = 1 - ∏(1 - wi xi)
EDGE CASE: all xi=0 -> score=0 (correct, no devices detected)
```

Top-3 states by S(ci) are loaded into RAM. Others stay on flash.

### Layer 3 - Predictive KV Cache Manager (Arjit)

- Holds top-3 KV cache states in active RAM
- LRU eviction when new state must load
- KV cache size cap: N=500 tokens max, INT4 quantized
- RAM budget: ~2GB (Android OS limit for background services)
- Storage: `*.bin` files in `/data/user/0/com.aacbridge/files/states/`

### Layer 4 - Async Semantic Drift Detector (Arjit)

Detects when conversation topic has drifted enough to invalidate cached context.

**Model:** all-MiniLM-L6-v2, INT8 quantized, ~22MB, via ONNX Runtime Android

**Method:**

- On cache load: pre-compute anchor embedding E_anchor of baseline context (once)
- Post-generation (async, NOT in critical path): embed last k=3 turns as E_recent
- Compute cosine similarity between E_anchor and E_recent
- If similarity < theta: invalidate cache, trigger recomputation

**Why k=3:** In AAC, single turns are sparse (one word/phrase). k=1/2 -> high-variance embeddings. k=3 approximates a local semantic centroid, stabilizing similarity scores. k=4/5 -> signal dilution from stale context, artificially high similarity, misses real drift. k=3 is validated empirically via Medha's k-ablation on DailyDialog (F1 per k in {1,2,3,4,5}).

**Overhead:** Runs on Android efficiency cores, post-generation only. Target: <5% CPU utilization.

**Dataset for ablation:** DailyDialog (100+ conversations annotated with topic-shift boundaries by Medha).

---

## 5. Inference Stack

| Component         | Choice                                              | Reason                                                           |
| ----------------- | --------------------------------------------------- | ---------------------------------------------------------------- |
| Device            | Snapdragon 8 Gen 1+ Android (e.g. S23/S24), 8GB RAM | Accessible, representative                                       |
| Model             | Phi-3-Mini (3.8B) or Qwen2.5-1.5B, Q4_K_M, `.gguf`  | ~1.1-2.2GB footprint                                             |
| Runtime           | `llama.cpp` (C++, NDK)                              | Exposes `llama_state_save_seq` / `llama_state_load_seq` natively |
| Bridge            | JNI (Java Native Interface)                         | Kotlin <-> C++                                                   |
| Drift model       | all-MiniLM-L6-v2 INT8 via ONNX Runtime Android      | ~22MB, efficiency cores                                          |
| Intent classifier | CNN-LSTM -> TFLite                                  | Medha exports, ~few MB                                           |
| Fusion model      | Cross-attention -> ONNX                             | Heer exports, ~few MB                                            |

---

## 6. Critical Engineering Decisions & Constraints

### 6.1 JNI Boundary Contract (NEVER VIOLATE)

**Kotlin holds strings. C++ holds bytes. Nothing else crosses the JNI boundary.**

- `Java_saveKVCache()`: llama_state_save_seq -> std::ofstream -> writes `*.bin` directly to `/data/user/0/com.aacbridge/files/states/` -> returns jstring path to Kotlin
- `Java_loadKVCache()`: receives path jstring from Kotlin -> std::ifstream -> reads `*.bin` directly into llama.cpp pre-allocated memory
- `Java_inferWithKV()`: injects intent tokens into loaded KV state -> returns generated jstring only
- `Java_isFileBeingRead()`: returns bool, used by safe eviction protocol

**Why:** Passing a 100MB+ raw tensor buffer across JNI forces the JVM to allocate a massive `byte[]`, copy every byte, and trigger a Stop-The-World GC pause. TTFT budget is immediately dead.

### 6.2 Asset Trap (NEVER put LLM in assets/)

Android AssetManager is for small files. 2.2GB `.gguf` in `assets/` = OOM crash before llama.cpp initializes. APK/AAB size limits also prevent bundling.

**Fix:** Model provisioned externally via:

```
adb push phi3-mini-q4_k_m.gguf /data/user/0/com.aacbridge/files/models/
```

C++ reads directly from filesystem. Never bundled in APK.

### 6.3 Concurrency Model

Three concurrent threads: ContextDaemon, MainActivity UI thread, async DriftDetector.

**Race condition:** StateRouter loads "home_mom" into RAM at same millisecond DriftDetector issues delete. C++ tries to read a file Kotlin is deleting. IOException -> JNI collapse -> hard crash.

**Fix (4 layers):**

1. Per-state Mutex: `Map<stateId, Mutex>` in CacheMutexRegistry.kt. Never lock entire system globally.
2. All cache ops wrapped in `cacheMutex.withLock{}`
3. Safe eviction protocol: mark `isActive=false` -> wait `refCount==0` -> delete file -> remove from DB
4. JNI safety rule: KV cache file can only be evicted AFTER all active `Java_loadKVCache()` calls on that file complete. `isFileBeingRead()` gates this.
5. Reference counting: `refCount++` on InferenceEngine load, `refCount--` on release. Delete only when `refCount==0`.

### 6.4 Cold Start Recovery

GeofencingApi only fires on boundary crossing. Reboot inside existing geofence = daemon never wakes.

**Fix:**

- BOOT_COMPLETED receiver (Heer) -> daemon wakes before user opens app
- Application.onCreate() checks null llama.cpp RAM pointer (Arjit backend, Heer calls)
- If null: active sweep - LocationManager.getLastKnownLocation() + clock + 3s BLE scan
- Score all 50 states, load top-3 KV caches
- Two-tier fallback if cold start still running when user triggers:
  - Tier 1: SQLite generic response via Android TTS, <50ms
  - Tier 2: async llama.cpp prefill in background, upgrades from next intent

### 6.5 EMG Fallback (Mock Intent Generator)

If Medha's TFLite model not exportable by June 8, benchmarking must not block. Core contribution = KV caching, NOT EMG.

MockIntentGenerator.kt (Arjit): rule-based intent payloads. All KV cache + latency + drift experiments run against mock. Real model swapped in when ready.

### 6.6 Fusion Model Fallback

Decision point June 7: if cross-attention F1 does NOT exceed concatenation late fusion by >3%, switch to late fusion. Report both in ablation.

---

## 7. Benchmarking Plan (Core Empirical Contribution)

Three configurations on Snapdragon 8-series device:

- Baseline 1: LLM inference, no context, no cache
- Baseline 2: Standard RAG - full context prefill every call
- System: KV Cache Primed - intent injection only

Variables:

- Quantization: INT4, INT8, FP16
- Context size N in {50, 100, 200, 500} tokens
- 30 trials per configuration
- Report: mean TTFT + standard deviation

Additional measurements:

- RAM usage per state (validates 2GB budget claim)
- CPU% during MiniLM drift detection on efficiency cores (validates <5% claim)
- LRU eviction correctness under concurrent load

---

## 8. Key Handoff Dates

| Date    | Handoff                                                                |
| ------- | ---------------------------------------------------------------------- |
| May 21  | Medha -> Heer: EMG embedding spec (tensor dims, format, normalization) |
| May 21  | Arjit -> Heer: KVCacheManager API contract (Kotlin interface)          |
| June 1  | Medha: TFLite export dry run (flag issues before June 8)               |
| June 7  | Medha -> Arjit: k-ablation F1 table                                    |
| June 7  | Heer: Fusion F1 decision - cross-attention vs late fusion              |
| June 8  | Medha -> Arjit: Final TFLite model + inference wrapper                 |
| June 8  | Heer -> Arjit: Final ONNX fusion model                                 |
| June 25 | All benchmarking complete (non-negotiable)                             |
| July 11 | Full paper draft complete                                              |
| July 15 | Submit to IEEE Access                                                  |

---

## 9. Paper Structure

| Section              | Owner                     |
| -------------------- | ------------------------- |
| Introduction         | Heer                      |
| Related Work         | Heer                      |
| Methodology          | All                       |
| Implementation       | Arjit                     |
| Experiments          | Arjit + Medha             |
| Results & Discussion | Heer                      |
| Conclusion           | Heer                      |
| References           | All (Zotero, IEEE format) |

Target: IEEE Access two-column LaTeX template (IEEEtran.cls)

---

## 10. What Has Been Explicitly Ruled Out

| Item                                | Reason                                              |
| ----------------------------------- | --------------------------------------------------- |
| Continual LoRA fine-tuning          | Separate research paper, out of scope               |
| Voice cloning / patient's own voice | Non-trivial separate system, out of scope           |
| AAAI submission                     | Requires theoretical proofs + clinical user studies |
| ACM MobiSys/MobiCom                 | 6-12 month cycle, misses placement season           |
| RL for self-improvement             | No feasible reward signal for AAC; LoRA already cut |
| Real EMG hardware                   | Unavailable, using NinaPro DB5 simulation           |
| Cloud inference                     | Privacy risk, network dependency, defeats purpose   |
| LLM in APK assets                   | OOM crash, APK size limits                          |
| Global system Mutex                 | Use per-state Mutex only                            |
| Passing tensor buffers across JNI   | GC pause, TTFT budget destroyed                     |

---

## 11. Placement Context

Arjit is a final year CS student at VIT. On-campus placements start July-August 2026. Major companies (Google, Microsoft, Uber, Atlassian, Goldman Sachs tech) recruit for SDE/SWE roles. DSA performance in OA is the primary filter. This project is a resume multiplier, not a DSA replacement. Placement prep split recommendation: 70% DSA, 20% project, 10% system design.

The IEEE Access submission gives "Under review at IEEE Access" on resume during placement season. This is the primary reason for the venue choice over technically superior fits like ACM MobiSys.

---

## 12. Current Status (as of end of April 2026)

- [x] Project idea finalized and approved by professor
- [x] Abstract submitted and approved (v4 - voice cloning and LoRA removed)
- [x] Professor sent novelty enhancement document (Enhancement 1: semantic drift, Enhancement 4: latency ablation are priority)
- [x] Full architecture designed and stress-tested
- [x] Scoring function S(ci) fully derived with edge cases
- [x] Concurrency model designed (per-state Mutex, safe eviction, refCount)
- [x] JNI boundary contract established
- [x] Asset trap identified and fixed
- [x] Cold start recovery designed
- [x] Two-tier fallback designed
- [x] Venue decision locked (IEEE Access primary)
- [x] Full project plan document generated (AACBridge_ProjectPlan_v2.docx)
- [x] Complete folder structure defined with owner annotations
- [ ] Phase 1 not yet started (starts May 15)
- [ ] llama.cpp not yet compiled for ARM64
- [ ] NinaPro pipeline not yet built
- [ ] Android app not yet scaffolded

---

## 13. Open Questions / Next Decisions Pending

- Arjit: confirm target device availability (S23 or S24 for benchmarking)
- Arjit: choose between Phi-3-Mini (3.8B, ~2.2GB) vs Qwen2.5-1.5B (~1.1GB) based on RAM budget after testing
- Medha: confirm NinaPro DB5 download and preprocessing works before May 21
- Heer: confirm MediaPipe gaze tracking works on target Android device front camera
- All: GitHub repo must be created and shared before May 15

---

## 14. Contextual Bandit - Adaptive Response Selection

Module: ResponseSelector.kt (Arjit, inference/ package)
Week: Phase 2, after KV cache works (May 31 target)
Cuttable: Yes - if Week 3 slips, remove without affecting core contribution

Full design: see teammate's formalization document.
Key decisions:

- CMAB not full RL (no gradient updates, no rollouts)
- Style-conditioned generation: S = {concise, polite, urgent}
- Single LLM call preserved, bandit selects prompt modifier only
- EMA update: wi <- (1-alpha)·wi + alpha·rt, alpha in [0.1, 0.3]
- Weights bounded naturally to [-2, 1] by EMA + reward range
- Epsilon-greedy selection
- Persistence: bandit_weights SQLite table, async flush
- Reward: implicit (+1/-1/-2/0), logged async, never in critical path
- New state init: wi=0 -> uniform exploration -> EMA adapts quickly

## 15. Phase 1 Completion Status (May 17, 2026)

### Arjit - COMPLETE
- llama.cpp compiled for Android ARM64 via NDK r27c
- JNI bridge: llama_jni.cpp with initializeBackend, initializeModel,
  saveKVCache, loadKVCache, runInference, release
- LlamaBridge.kt as Kotlin object with external fun declarations
- System.loadLibrary("aacbridge-jni") in companion init block
- libomp.so manually added to jniLibs/arm64-v8a/ for OpenMP dependency
- Tokenization fix: negative return = required buffer size, use -token_count
- On-device inference verified: Qwen2.5-0.5B-Instruct Q4_K_M
- Initial baseline latency:
  - model initialization ~1.7s
  - first inference response ~2.8s
  (unoptimized, no KV priming)
- MockIntentGenerator.kt + IntentPayload.kt complete
- build.gradle, settings.gradle, gradle.properties, gradlew configured
- Application.kt calls LlamaBridge.initializeBackend() in onCreate()
- KV cache JNI hooks compiled but NOT end-to-end tested yet
- Verified runtime pipeline:
  Android App -> Kotlin -> JNI -> llama.cpp -> GGUF model -> generated response -> Kotlin log output

### Medha - COMPLETE
- NinaPro DB5 preprocessing: bandpass 20-450Hz, rectification, 200ms windowing
- EMG embedding spec delivered: shape (1,64), float32, L2 normalized
- dataset.py with NINAPRO_TO_AAC mapping and PyTorch Dataset
- 01_eda.ipynb with 16 channels, 130267 samples
- WARNING: NinaPro→AAC gesture mapping requires justification in paper methodology

### Heer - UNCONFIRMED
- Status unknown, sync checkpoint May 21

## 16. Device Environment
- Device: OnePlus 11R 5G
- SoC: Snapdragon 8 Gen 1 (platform: taro)
- ABI: arm64-v8a
- Android API: 36
- Runtime: On-device offline inference
- Model path on device: /data/local/tmp/models/
- Model used for testing: qwen2.5-0.5b-instruct-q4_k_m.gguf (~469MB)

## 17. Key File Locations
- JNI bridge: android/app/src/main/cpp/llama_jni.cpp
- Kotlin bridge: android/app/src/main/java/com/aacbridge/inference/LlamaBridge.kt
- Native libs: android/app/src/main/jniLibs/arm64-v8a/
- Headers: android/app/src/main/cpp/include/
- llama.cpp source: D:\projects\llama.cpp\
- Models: D:\Models\

## 18. Phase 2 Starting Point (May 22)

Priority order for Arjit:
1. StateRouter.kt - scoring function S(ci) = alpha*S_time + beta*S_gps + gamma*S_ble
2. TimeScorer.kt, GPSScorer.kt, BLEScorer.kt
3. KVCacheManager.kt - SQLite backed, LRU eviction, top-3 in RAM
4. BootReceiver.kt + cold start active sweep
5. Two-tier fallback

CRITICAL edge case to handle first:
When M=0 (no BLE devices):
- R_ble = 0
- gamma = 0
- alpha + beta must renormalize to sum to 1

Do NOT proceed without explicitly handling this edge case.

## 19. Phase 2 Completion Status (May 23, 2026)

### Arjit - COMPLETE

**Router Package (com.aacbridge.router)** — 9 files, 14 tests green
- `HardwareConfig.kt` — MAX_ACTIVE_KV_STATES=3, TIME_BASELINE_WEIGHT=0.4
- `GpsLocation.kt` — atomic validated GPS type, validation in init block
- `Rssi.kt` — @JvmInline value class, enforces [-127, 0] dBm range
- `SensorSnapshot.kt` — immutable frozen hardware state, no Android types
- `ContextState.kt` — routing domain object only, does NOT contain kvFilePath
- `TimeScorer.kt` — circular Gaussian decay, sigma=2.0h, fault-tolerant normalization
- `GPSScorer.kt` — Haversine, lambda=0.1km, lambdaAccuracy=50m, decoupled reliability
- `BLEScorer.kt` — static weight match score decoupled from RSSI reliability weight
- `StateRouter.kt` — getScoredStates() + getTopContextIds() wrapper, cull threshold 0.01

**Cache Package (com.aacbridge.cache)** — 5 files, 4 tests green
- `CacheState.kt` — metadata only: stateId, kvFilePath, seqId, AtomicLong lastAccessed, AtomicInteger refCount, @Volatile isActive
- `CacheMutexRegistry.kt` — ConcurrentHashMap<String, Mutex>, per-state locking only
- `StateRepository.kt` — interface: getFilePath() + getAllContextStates()
- `LlamaBridgeAdapter.kt` — interface: loadKVCache(filepath, seqId) for JVM testability
- `KVCacheManager.kt` — seqId pool [0,1,2], LRU eviction, 4-layer concurrency model

**Daemon Package (com.aacbridge.daemon)** — 3 files
- `ActiveSweep.kt` — concurrent GPS+BLE acquisition, withTimeoutOrNull(3000ms) BLE, invokeOnCancellation cleanup
- `BootReceiver.kt` — goAsync() + GlobalScope.launch(Dispatchers.IO) + finally { pendingResult.finish() }
- `DriftDetector.kt` — CoroutineWorker, 15min polling, HYSTERESIS_MARGIN=0.10, BLE-bypass snapshot

**Fallback Package (com.aacbridge.fallback)** — 3 files
- `FallbackRouter.kt` — Tier 1 <50ms SQLite, Tier 2 async llama.cpp upgrade
- `FallbackRepository.kt` — interface
- `InMemoryFallbackRepository.kt` — temporary, 6 intent→response mappings

**Application Wiring**
- `AACBridgeApplication.kt` — extends Application, initializes AppContainer, calls DriftDetector.schedule(this)
- `AppContainer.kt` — manual DI, no Hilt/Dagger/Koin, wires all dependencies
- `AndroidManifest.xml` — BootReceiver registered, all permissions declared

**Build Status:** `.\gradlew.bat test` → BUILD SUCCESSFUL, 18 tests passing

### Medha - STATUS UNKNOWN
- k-ablation F1 table due June 7
- TFLite export dry run due June 1

### Heer - STATUS UNKNOWN
- Fusion F1 decision due June 7
- ONNX export due June 8

## 20. Critical Architectural Decisions Made in Phase 2

- **Decision 1: seqId ownership model**
  llama.cpp owns KV memory through seqIds, not file paths. KVCacheManager maps semantic states onto native ring buffer slots [0,1,2]. No freeKVCache() exists — evicting a slot returns its seqId to the pool for overwriting. Invariant: activeStates.size + availableSeqIds.size == MAX_ACTIVE_KV_STATES always.
- **Decision 2: ContextState does not own kvFilePath**
  File paths are StateRepository's concern. ContextState is a pure routing domain object. This was an architectural correction made during Phase 2 after an early mistake.
- **Decision 3: LlamaBridgeAdapter interface**
  LlamaBridge object implements LlamaBridgeAdapter interface. This decouples JVM tests from JNI. KVCacheManager depends on the interface, not the concrete singleton.
- **Decision 4: releaseState() does not acquire mutex**
  Safe because after isActive=false, refCount is monotonically decreasing. Proven formally. Atomic decrement is sufficient.
- **Decision 5: DriftDetector uses BLE-blind snapshot**
  Battery protection. Both candidate and resident states evaluated against same BLE-blind snapshot — delta remains mathematically valid even if absolute scores are deflated.
- **Decision 6: Open-Closed for getScoredStates()**
  Added getScoredStates() returning raw scores for DriftDetector hysteresis. getTopContextIds() refactored as wrapper. ActiveSweep contract unchanged.

## 21. Assumptions Requiring Paper Documentation

| Assumption | Value | Justification Status |
| --- | --- | --- |
| TIME_BASELINE_WEIGHT | 0.4 | Analytical — prevents divide-by-zero, suppresses time when physical sensors available |
| sigmaHours | 2.0h | Clinical — 2hr caregiver delay → ~60% score, not catastrophic |
| lambdaKm | 0.1km | Clinical — 100m is meaningful AAC context boundary |
| lambdaAccuracyMeters | 50m | Empirical — indoor Wi-Fi accuracy 15-50m retains partial weight |
| HYSTERESIS_MARGIN | 0.10 | Analytical — not empirically validated, must tune on device |
| DEAD_STATE_THRESHOLD | 0.01 | Floating-point epsilon — asymptotic states culled |
| k=3 drift window | 3 turns | Pending Medha's k-ablation F1 table (due June 7) |
| BLE_SCAN_WINDOW_MS | 3000ms | Chosen for cold start budget — not benchmarked |
| EVICTION_POLL_INTERVAL_MS | 10ms | Chosen to avoid busy-spin — not benchmarked |

## 22. Phase 3 Prerequisites (Must Complete Before June 8)

**Critical — benchmarking blocked without these:**
1. **Room-backed StateRepository** — InMemoryStateRepository always returns null for getFilePath(), causing KVCacheManager.loadTopStates() to skip every load silently
2. **saveKVCache() lifecycle** — currently no code calls LlamaBridge.saveKVCache(). Without serialization, cold start recovery has nothing to load. Must decide: who calls it, when, after which event
3. **Real-device smoke test** — connect phone, confirm AACBridgeApplication.onCreate() completes without JNI crash

**Important but not blocking:**
4. HYSTERESIS_MARGIN empirical tuning on device
5. seqId overwrite behavior validation on Snapdragon with real GGUF model
6. Room-backed FallbackRepository

## 23. Open Architectural Question — saveKVCache() Lifecycle

**Current state:** `LlamaBridge.saveKVCache(filepath, seqId)` exists in JNI but is never called from Kotlin.
**The question:** When does the system serialize a newly primed KV cache to disk?
**Options:**
- After ContextDaemon primes context on first load — save immediately after prefill
- After first successful inference in a new context — lazy serialization
- Explicitly triggered by KVCacheManager after verifying the seqId is stable

This must be resolved before Phase 3 benchmarking. An unresolved save path means the cache is only ever loaded from stale or nonexistent files.
