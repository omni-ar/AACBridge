# AACBridge Paper 262 — Current Ground Truth

**Verified against source code on branch `revision/reviewer-response`**
**Date:** 2026-10-03

## A. Context States

| # | stateId | expectedTime | lat | lng | BLE Anchors | Source |
|---|---------|-------------|-----|-----|-------------|--------|
| 1 | home_morning | 8.0 | 28.6139 | 77.2090 | EE:01 (0.8), EE:02 (0.5) | SeededStateRepository.kt:65-74 |
| 2 | home_evening | 19.0 | 28.6139 | 77.2090 | EE:01 (0.8), EE:03 (0.6) | SeededStateRepository.kt:76-85 |
| 3 | hospital_ward | 11.0 | 28.5672 | 77.2100 | EE:04 (0.9), EE:05 (0.7) | SeededStateRepository.kt:87-96 |
| 4 | therapy_room | 14.0 | 28.5672 | 77.2105 | EE:06 (0.85) | SeededStateRepository.kt:98-106 |
| 5 | caregiver_visit | 16.5 | 28.6139 | 77.2090 | EE:07 (0.95), EE:01 (0.8) | SeededStateRepository.kt:108-117 |

**Total:** 5 states. **Resident slots:** 3 (HardwareConfig.MAX_ACTIVE_KV_STATES).

## B. Scoring Formula

S(c_i) = α·S_time + β·S_gps + γ·S_ble

where α + β + γ = 1, dynamically computed from reliability weights.

| Parameter | Value | Source |
|-----------|-------|--------|
| Time sigma (σ) | 2.0 hours | TimeScorer.kt:42 |
| GPS decay (λ_km) | 0.1 km | GPSScorer.kt:48 |
| GPS reliability (λ_acc) | 50.0 m | GPSScorer.kt:57 |
| BLE sigmoid midpoint (r0) | -70.0 dBm | BLEScorer.kt:36 |
| BLE sigmoid steepness (k) | 0.1 | BLEScorer.kt:41 |
| Time baseline weight (w_t) | 0.4 | HardwareConfig.kt:51 |
| Dead-state threshold | 0.01 | StateRouter.kt:40 |

## C. Drift Detection

| Parameter | Value | Source |
|-----------|-------|--------|
| Hysteresis margin (Δ) | 0.10 | DriftDetector.kt:42 |
| Poll interval | 15 minutes | DriftDetector.kt:58-59 |
| BLE-blind snapshot | Yes (battery conservation) | DriftDetector.kt:142 |

## D. JNI Boundary (Verified)

**Core lifecycle/inference (9 methods):**
1. initializeBackend
2. initializeModel
3. saveKVCache
4. loadKVCache
5. clearKVCache
6. runInference
7. prefillOnly
8. resumeInference
9. release

**Read-only telemetry getters (4 methods):**
10. getLastPrefillMs
11. getLastGenMs
12. getLastPromptTokens
13. getLastGenTokens

**Total: 13 JNI methods** (9 core + 4 telemetry)

Source: LlamaBridge.kt (all declared as `external fun`)

## E. Cache Lifecycle

1. Prefill: engineLock → prefillOnly(prompt) → native KV populated
2. Serialize: engineLock held → saveKVCache(tmpPath, 0) → tmp .bin file
3. Atomic commit: lock released → rename(tmp, final.bin)

Source: ContextPrimerImpl.kt, KVCacheManager.kt

## F. Fallback Behavior

- Tier 1: Immediate SQLite-backed generic response (<50ms)
- Tier 2: Async RAG prefill upgrade when KV context becomes available
- Default: "Please wait a moment."

Source: FallbackRouter.kt:28-85

## G. Security Posture (Post-Hardening)

| Defense | Status | Source |
|---------|--------|--------|
| Mock-location detection | ✅ IMPLEMENTED | LocationValidator.kt:isMockLocation() |
| GPS staleness check (120s) | ✅ IMPLEMENTED | LocationValidator.kt:isStale() |
| GPS accuracy sanity (2-500m) | ✅ IMPLEMENTED | LocationValidator.kt:isAccuracyPlausible() |
| allowBackup=false | ✅ IMPLEMENTED | AndroidManifest.xml:63 |
| Data extraction rules | ✅ IMPLEMENTED | res/xml/data_extraction_rules.xml |
| BLE repeated observation | NOT YET | Pending evaluation |
| KV cache encryption | NOT IMPLEMENTED | Future work |
| GPS teleportation check | NOT IMPLEMENTED | Requires location history |
