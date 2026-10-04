# Prediction / residency evaluation

All traces here are **controlled/synthetic** (seeded), not recordings from users.

## Files

| File | Produced by | Contents |
|---|---|---|
| `tables/prediction_eval.csv` | `PredictionEvaluationTest` | Baselines (oracle, reactive LRU, random), single-modality ablations, full router, noise and spoofing scenarios |
| `tables/hysteresis_eval.csv` | `PredictionEvaluationTest` | Residency hysteresis margin sweep, normal and high-noise traces |
| `tables/ground_truth_distribution.csv` | `PredictionEvaluationTest` | Interactions per context (for interpreting the random baseline) |
| `parse_device_logs.py` | — | Converts on-device `SWEEP` / `INTERACTION` logcat lines to CSV |
| `tables/prediction_ablation.csv`, `tables/hysteresis_sweep.csv` | old evaluator | **Superseded.** Produced by the earlier harness (instant top-3 residency, its own eviction rule, top-1 changes counted as "switches", outcomes not partitioned). Kept for history only. |

## What the evaluator simulates

- One ActiveSweep per minute, 06:00–22:00, 100 days; one interaction every 15 min (6,400 interactions).
- Each sweep: production `StateRouter.getScoredStates` → production `ResidencyPolicy.plan` (hysteresis margin `HardwareConfig.RESIDENCY_HYSTERESIS_MARGIN`).
- Each interaction: production `ResidencyPolicy.selectForInference` (highest-ranked resident context), the same rule `MainActivity.handleIntent` uses.
- Ground truth: five-context routine with each boundary shifted per day by U(±0.5 h) (±1.5 h high-noise). The device clock is exact. The router's time anchors come from the same routine, so time-of-day evidence is favourable by construction.
- Not simulated: priming/restore delay (a state chosen at a sweep is assumed usable a minute later).

## Metrics (per interaction; the last three partition all interactions)

- `hit1`: router top-1 == ground truth.
- `gt_resident`: ground truth resident at interaction time.
- `no_prediction`: router returned no context above the 0.01 dead-state threshold.
- `served_correct`: context used == ground truth.
- `wrong_context`: a resident context was used but it was not the ground truth.
- `cold_miss`: no ranked context resident → Tier-1 generic response.
- `loads_per_day` / `evictions_per_day`: residency changes (each load is a restore from file, or a prime if the file is missing).

Baselines: `ORACLE_ALWAYS_RESIDENT` (ground truth always resident, isolates KV-restore benefit), `REACTIVE_LRU_NO_SENSING` (context known only when the request arrives, LRU of 3, as in request-driven prefix/RAG caches), `RANDOM_RANKING` (uniform random scores each sweep, same policy).

Spoofing (only while the user is at a `home_*` context): `GPS_SPOOF_UNDETECTED` (forged fix at the hospital with 5 m accuracy that passes `LocationValidator`, e.g. rooted device), `GPS_SPOOF_MOCK_REJECTED` (mock-flagged fix dropped by `LocationValidator`), `BLE_SPOOF` (cloned hospital beacons at −55 dBm).

## Reproduce

With Gradle (Android SDK installed):

```
cd android
./gradlew :app:testDebugUnitTest --tests "com.aacbridge.router.PredictionEvaluationTest"
```

The test locates the repo root and rewrites the three CSVs above.
