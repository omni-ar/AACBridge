# Phase 2 Engineering Status Report: Orchestration & Scoring

**Date:** June 8, 2026
**Role:** Backend / LLM Infrastructure (Arjit)
**Phase Status:** COMPLETE

## 1. Completed Phase 2 Modules

Phase 2 implementation focused heavily on JVM-side orchestration, deterministic routing, and concurrency safety. The following modules are architecturally complete, tested, and structurally verified:

* **Deterministic State Router (`com.aacbridge.router`):**
  * Core scoring function $S(c_i) = \alpha S_{time} + \beta S_{gps} + \gamma S_{ble}$ is implemented and unit-tested.
  * Dynamic weight normalization successfully handles edge cases, specifically the $M=0$ scenario where missing BLE signals correctly redistribute weights to Time and GPS anchors without division-by-zero crashes.
* **KV Cache Manager (`com.aacbridge.cache`):**
  * `seqId` native ownership model and lifecycle is fully integrated.
  * Single shared `engineLock` guarantees thread safety against eviction race conditions, completely eliminating native `SIGBUS` crashes.
  * Explicit serialization lifecycle verified: `prime` -> `saveKVCache` -> `.bin` -> `loadKVCache`.
* **Daemon Subsystem (`com.aacbridge.daemon`):**
  * `BootReceiver` + parallel `ActiveSweep` recovery gracefully mitigates geofencing bypass bugs.
  * `DriftDetector` successfully scheduled via WorkManager, utilizing hysteresis logic (`margin=0.10`) with a BLE-bypass strategy to protect battery life while ensuring valid relative context shifts.
* **Application Wiring:**
  * `AACBridgeApplication` correctly initializes the manual `AppContainer` dependency graph and explicitly schedules the `DriftDetector` daemon.

## 2. Resolved External Dependencies & Handoffs

All previously unresolved Phase 2 dependencies have been successfully integrated:
* **Medha:** TFLite CNN-LSTM intent model export is mathematically verified (`assert_allclose` PASS) and generated. (Hardware validation pending).
* **Medha:** Empirical k-ablation F1 results for the Drift Detector window size are finalized. The k-ablation study on DailyDialog did not identify k=3 as the highest-performing configuration. However, because DailyDialog differs substantially from AAC communication patterns, k=3 was retained as an engineering heuristic for sparse AAC interactions rather than selected solely on the basis of ablation metrics.
* **Heer:** Final decision regarding cross-attention fusion vs. simple concatenation fallback finalized: Late Fusion selected (Accuracy=0.9967, F1=0.9963). The resulting ONNX model is exported and integrated on-device.

## 3. Pending Phase 3 Follow-ups

While Phase 2 software integration is formally closed, the following non-blocking implementations will be completed alongside benchmarking in Phase 3:
* **Room Database Implementation:** `InMemoryStateRepository` must be replaced with SQLite Room DAOs.
* **Hysteresis Tuning:** The `HYSTERESIS_MARGIN = 0.10` is an analytical assumption and must be empirically tuned with real usage data.
* **TTFT Profiling:** Measuring actual cache hit-rate and exact latency reductions against the Snapdragon 8 Gen 1 hardware.
