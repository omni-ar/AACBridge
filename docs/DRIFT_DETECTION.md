# Async Semantic Drift Detection

This document formalizes the hysteresis reasoning behind the semantic drift detector implemented in Phase 2. The detector is orchestrated via `DriftDetector.kt`.

## The Purpose of Drift Detection
The predictive KV cache system assumes that the context parameters (Time, GPS, BLE) are stable enough to keep the correct states resident in RAM. However, human conversation naturally drifts. The `DriftDetector` is a background daemon that periodically checks if the user's *actual* semantic topic has drifted away from the *environmental* prediction, invalidating the cache if necessary.

## Implementation Details
The daemon runs as a `CoroutineWorker` scheduled via Android `WorkManager` with a 15-minute periodic interval, utilizing `ExistingPeriodicWorkPolicy.KEEP` to ensure a single singleton worker survives app restarts and Doze mode.

## The BLE-Bypass Hysteresis Reasoning
A core optimization in Phase 2 is the intentional bypassing of the BLE radio during the background drift polling sweep.

* **The Problem:** Turning on the BLE radio to perform a 3-second MAC-address topology scan every 15 minutes destroys mobile battery life. Since AAC devices must last an entire day, background power efficiency is critical.
* **The Solution:** The `DriftDetector` pulls a lightweight `SensorSnapshot` using only Time and a cached `getLastKnownLocation()` GPS coordinate. It explicitly bypasses BLE, setting `detectedBleDevices = emptyMap()`.
* **The Mathematical Validation:**
  Because the BLE scan is empty, the absolute $S(c_i)$ scores for all contexts will be artificially deflated. However, the hysteresis logic relies on a **relative score delta**, not an absolute threshold.
  
  The daemon scores both the *candidate* states and the currently *resident* states against the **exact same BLE-blind snapshot**. Therefore, any delta between the best candidate and the weakest resident state remains mathematically consistent and valid. 

## Cache Swap Protection (`HYSTERESIS_MARGIN`)
To prevent the KV cache from thrashing (constantly evicting and loading files into the native slots due to trivial score fluctuations), a threshold margin is enforced:
* **Constant:** `HYSTERESIS_MARGIN = 0.10`
* **Trigger:** The swap is only executed if `(bestCandidateScore - weakestResidentScore) > 0.10`.

This ensures that the expensive JNI operations and flash memory reads are only triggered when the environment has definitively and meaningfully shifted.
