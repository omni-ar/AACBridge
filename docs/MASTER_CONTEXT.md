# AACBridge - Master Context Document

> Paste this entire file at the start of every new chat to restore full project context.
> Last updated: June 2026

---

## 1. Project Identity

**Full Title:** Predictive Amortized KV-Caching for Low-Latency Edge LLMs in Augmentative and Alternative Communication

**Short Name:** AACBridge

**Type:** B.Tech Capstone Project - Team of 3, one semester (May 15 - July 15, 2026)

**Domain:** AI / Healthcare / Embedded Systems / Edge Computing

**Target Publication:** IEEE Access (Q1, Scopus indexed, ~4-6 week review). Submit mid-July 2026 so "Under review at IEEE Access" appears on resume during placement season (July-August).

**Fallback Publication:** Computers in Biology and Medicine (Elsevier, Q1, Scopus).

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
- Late fusion: EMG embedding (64-dim) concatenated with gaze vector (5-dim) -> unified intent embedding
- Dataset: NinaPro DB5 (EMG), GazeCapture (Gaze)
- 5 intent classes: confirm, reject, scroll, select, call-help

### Layer 2 - Deterministic State Router (Arjit)

Scores all 50 context states deterministically. NO ML model.
**Scoring function:**
`S(ci) = alpha·S_time + beta·S_gps + gamma·S_ble`
*(alpha + beta + gamma = 1)*

### Layer 3 - Predictive KV Cache Manager (Arjit)

- Holds top-3 KV cache states in active RAM
- LRU eviction when new state must load
- Verified Lifecycle: `prime` -> `saveKVCache` -> `.bin` -> `loadKVCache`
- Stable Concurrency: protected by single shared `engineLock`

### Layer 4 - Async Semantic Drift Detector (Arjit)

Detects when conversation topic has drifted enough to invalidate cached context.
**Model:** all-MiniLM-L6-v2, INT8 quantized, ~22MB, via ONNX Runtime Android
**Method:** Cosine similarity between anchor embedding and recent `k=3` turns.

---

## 5. Repository Status

**Build Status:** PASS
**Test Status:** 41/41 PASS
**Phase 2:** CLOSED
**Phase 3:** READY TO START

---

## 6. Track Status

### Arjit Track
**Status: Complete.**
- `llama.cpp` JNI boundary stable and proven.
- `ContextDaemon`, `StateRouter`, `KVCacheManager` implemented.
- Cache serialization lifecycle verified (`prime` -> `saveKVCache` -> `.bin` -> `loadKVCache`).
- Concurrency model locked (SIGBUS native crashes completely eliminated).

### Heer Track
**Status: Complete.**
- Android UI and camera bindings validated.
- `FaceLandmarker` gaze extraction (400ms dwell) validated.
- Cross-Attention vs Late Fusion ablation complete.
- ONNX Runtime integration (`FusionInference.kt`) verified on-device.

### Medha Track
**Software Pipeline:** COMPLETE
**Hardware Integration:** PENDING

*Explanation:* The EMG TFLite export pipeline is verified mathematical equivalence (PyTorch → ONNX → TFLite). However, real on-device execution requires Medha's physical BLE armband hardware, which has not yet been integrated. A mock `FloatArray(64)` is currently used.

---

## 7. Fusion Architecture

**Label Leakage Fix:**
During validation, the gaze feature vector contained `intentIndex`, causing a 100% accuracy label leakage.
- Original: 6-dimensional `[dx, dy, abs(dx), abs(dy), magnitude, intentIndex]`
- Fixed: 5-dimensional `[dx, dy, abs(dx), abs(dy), magnitude]`

**Ablation Results:**
- Cross-Attention: Accuracy = 0.9467, F1 = 0.9437
- Late Fusion: Accuracy = 0.9967, F1 = 0.9963

**Winner:** Late Fusion
*Selection Criterion:* Cross-attention required a >3% F1 advantage to justify complexity. Criterion not met. Late Fusion selected.

---

## 8. EMG Pipeline

- `model.py` recovered from git history.
- Weight transfer from PyTorch to Keras (TFLite) implemented and verified.
- `assert_allclose(pytorch, tflite, atol=1e-3)`: **PASS** (Max abs error: 9.30e-04).
- PyTorch → ONNX → TFLite verified.
- Final artifacts: `emg_classifier.onnx`, `emg_classifier.tflite`, `emg_classifier_int8.tflite` generated.

---

## 9. KV Cache

**Lifecycle:**
1. Daemon identifies state -> `prime`
2. Inference engine executes prompt -> `saveKVCache`
3. Native memory written to `*.bin` on filesystem
4. Router requests state -> `loadKVCache`

**Device Validation:**
- 3 cache files generated simultaneously on Android filesystem.
- MB-scale `.bin` cache sizes verified.
- 3/3 successful cache loads observed.
- **SIGBUS memory mapping crashes eliminated** via the `engineLock` synchronization barrier.
- `ContextDaemon` startup ordering verified (`BootReceiver` -> `ActiveSweep`).

---

## 10. Benchmarking Plan (Phase 3)

Three configurations on Snapdragon 8-series device:
- Baseline 1: LLM inference, no context, no cache
- Baseline 2: Standard RAG - full context prefill every call
- System: KV Cache Primed - intent injection only

**Pending benchmarking:** Actual TTFT reductions, memory footprints, and drift detector CPU% will be measured in Phase 3.


## Archived Equations

* Scoring function: S(ci) fully derived with edge cases
* EMA update formula: wi <- (1-alpha)·wi + alpha·rt

## Archived Thresholds

* alpha in [0.1, 0.3]
* weights bounded naturally to [-2, 1] by EMA + reward range
* k=3 cosine similarity turn window

## Archived Design Decisions

* Fallback to concatenation late fusion if F1 gap <3%
* CMAB not full RL (no gradient updates, no rollouts)
* Style-conditioned generation: S = {concise, polite, urgent}
* Single LLM call preserved, bandit selects prompt modifier only
* Epsilon-greedy selection
