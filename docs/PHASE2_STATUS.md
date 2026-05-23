# Phase 2 Engineering Status Report: Orchestration & Scoring

**Date:** May 23, 2026
**Role:** Backend / LLM Infrastructure (Arjit)

## 1. Completed Phase 2 Modules

Phase 2 implementation focused heavily on JVM-side orchestration, deterministic routing, and concurrency safety. The following modules are architecturally complete, tested, and structurally verified:

* **Deterministic State Router (`com.aacbridge.router`):**
  * Core scoring function $S(c_i) = \alpha S_{time} + \beta S_{gps} + \gamma S_{ble}$ is implemented and unit-tested.
  * Dynamic weight normalization successfully handles edge cases, specifically the $M=0$ scenario where missing BLE signals correctly redistribute weights to Time and GPS anchors without division-by-zero crashes.
* **KV Cache Manager (`com.aacbridge.cache`):**
  * `seqId` native ownership model and lifecycle is fully integrated.
  * 4-layer concurrency model (per-state mutexes, atomic `isActive` checking, lock-free releases) formally guarantees thread safety against eviction race conditions.
* **Daemon Subsystem (`com.aacbridge.daemon`):**
  * `BootReceiver` + parallel `ActiveSweep` recovery gracefully mitigates geofencing bypass bugs.
  * `DriftDetector` successfully scheduled via WorkManager, utilizing hysteresis logic (`margin=0.10`) with a BLE-bypass strategy to protect battery life while ensuring valid relative context shifts.
* **Application Wiring:**
  * `AACBridgeApplication` correctly initializes the manual `AppContainer` dependency graph and explicitly schedules the `DriftDetector` daemon.

## 2. Unresolved External Dependencies & Handoffs

The following dependencies are pending from teammates and are blocking final Phase 3 integration:
* **Medha:** TFLite CNN-LSTM intent model export (due June 8). Currently utilizing `MockIntentGenerator` for safe JVM abstraction.
* **Medha:** Empirical k-ablation F1 results for the Drift Detector window size (due June 7). The architecture currently defaults to $k=3$ based on preliminary assumptions.
* **Heer:** Final decision regarding cross-attention fusion vs. simple concatenation fallback (due June 7), and the resulting ONNX model export (due June 8).

## 3. Remaining Phase 3 Prerequisites

Before latency benchmarking can formally begin, the following architectural gaps must be closed:
* **Room Database Implementation:** `InMemoryStateRepository` and `InMemoryFallbackRepository` must be completely replaced with SQLite Room DAOs. Static BLE weights must be strictly enforced ($w_i > 0$) at the DB ingestion layer.
* **Real-Device SeqId Validation:** The `loadKVCache(filepath, seqId)` behavior must be validated end-to-end on the Snapdragon 8 Gen 1 hardware with the actual 2.2GB `.gguf` model to ensure the native ring buffer strictly overwrites memory without leaking RAM.
* **Hysteresis Tuning:** The `HYSTERESIS_MARGIN = 0.10` is an analytical assumption and must be empirically validated on device to ensure the cache does not thrash in highly noisy environments.
* **The `saveKVCache()` Lifecycle:** The exact timing and ownership of saving a newly computed KV cache to flash memory is currently un-orchestrated. This must be resolved, as a failure to serialize the cache means cold start recovery has nothing to load into the `seqId` slots.
