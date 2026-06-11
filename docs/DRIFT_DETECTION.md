# Async Semantic Drift Detection

This document formalizes the hysteresis reasoning behind the semantic drift detector implemented in Phase 2. The detector is orchestrated via `DriftDetector.kt`.

## Implementation Status

### Implemented
* **ONNX Runtime Android Integration:** `all-MiniLM-L6-v2` runs on-device.
* **Daemon Orchestration:** Runs as a `CoroutineWorker` scheduled via Android `WorkManager` with a 15-minute periodic interval, utilizing `ExistingPeriodicWorkPolicy.KEEP` to ensure a single singleton worker survives app restarts and Doze mode.
* **BLE-Bypass Hysteresis:** The daemon pulls a lightweight `SensorSnapshot` using only Time and GPS, setting `detectedBleDevices = emptyMap()` to save battery. The hysteresis relies on a **relative score delta**, scoring both candidate and resident states against the same BLE-blind snapshot.
* **k=3 Turn Window:** The k-ablation study on DailyDialog did not identify k=3 as the highest-performing configuration. However, because DailyDialog differs substantially from AAC communication patterns, k=3 was retained as an engineering heuristic for sparse AAC interactions rather than selected solely on the basis of ablation metrics.

### Deferred (Pending Benchmarking)
* **Empirical tuning of `HYSTERESIS_MARGIN = 0.10`:** The swap is only executed if `(bestCandidateScore - weakestResidentScore) > 0.10`. This threshold is currently analytical and must be tuned with real usage data in Phase 3.
* **CPU Efficiency Overhead Verification:** Ensuring the ONNX embedding model consumes <5% CPU on background efficiency cores remains to be benchmarked.

## Cache Swap Protection (`HYSTERESIS_MARGIN`)
To prevent the KV cache from thrashing (constantly evicting and loading files into the native slots due to trivial score fluctuations), a threshold margin is enforced:
* **Trigger:** The swap is only executed if `(bestCandidateScore - weakestResidentScore) > 0.10`.

This ensures that the expensive JNI operations and flash memory reads are only triggered when the environment has definitively and meaningfully shifted.
