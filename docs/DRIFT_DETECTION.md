# Async Semantic Drift Detection

This document formalizes the hysteresis reasoning behind the semantic drift detector implemented in Phase 2. The detector is orchestrated via `DriftDetector.kt`.

## Implementation Status

### Implemented
* **ONNX Runtime Android Integration:** `all-MiniLM-L6-v2` runs on-device.
* **Daemon Orchestration:** Runs as a `CoroutineWorker` scheduled via Android `WorkManager` with a 15-minute periodic interval, utilizing `ExistingPeriodicWorkPolicy.KEEP` to ensure a single singleton worker survives app restarts and Doze mode.
* **BLE-Bypass Hysteresis:** The daemon pulls a lightweight `SensorSnapshot` using only Time and GPS, setting `detectedBleDevices = emptyMap()` to save battery. The hysteresis relies on a **relative score delta**, scoring both candidate and resident states against the same BLE-blind snapshot.
* **k=3 Turn Window:** Based on the empirical k-ablation study completed by Medha, the detector aggregates the last 3 user intent turns as the recent embedding window.

### Deferred (Pending Benchmarking)
* **Empirical tuning of `HYSTERESIS_MARGIN = 0.10`:** The swap is only executed if `(bestCandidateScore - weakestResidentScore) > 0.10`. This threshold is currently analytical and must be tuned with real usage data in Phase 3.
* **CPU Efficiency Overhead Verification:** Ensuring the ONNX embedding model consumes <5% CPU on background efficiency cores remains to be benchmarked.

## Cache Swap Protection (`HYSTERESIS_MARGIN`)
To prevent the KV cache from thrashing (constantly evicting and loading files into the native slots due to trivial score fluctuations), a threshold margin is enforced:
* **Trigger:** The swap is only executed if `(bestCandidateScore - weakestResidentScore) > 0.10`.

This ensures that the expensive JNI operations and flash memory reads are only triggered when the environment has definitively and meaningfully shifted.
