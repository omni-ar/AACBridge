# Agent B — Prediction Evaluator Audit

**Date:** 2026-10-05
**Scope:** PredictionEvaluator, StateRouter scoring, residency, cache replacement, ground truth, trace generation

---

## 1. Evaluator Architecture

The prediction evaluation uses production code paths:
- `StateRouter.getScoredStates()` → same scoring as on-device
- `ResidencyPolicy.plan()` → same replacement logic as on-device
- `ResidencyPolicy.selectForInference()` → same selection as on-device

The evaluator runs the same `TimeScorer`, `GPSScorer`, `BLEScorer` classes with the same parameters. This is good — the trace evaluation measures the deployed policy.

## 2. Ground Truth Definition

For each simulated interaction, the "ground truth context" is the context whose time window contains the simulated time. The schedule defines five contexts across 06:00-22:00 with time boundaries.

**Hit = Correct** means: the context selected by `selectForInference()` matches the ground truth context.

**This is the right definition** — it measures whether the user would get the correct context at interaction time.

## 3. Circularity Problem — TIME

**CRITICAL**: The synthetic trace generator and the `TimeScorer` share the same underlying assumption — contexts are anchored to specific times of day, and the traces follow those same time anchors.

From the paper (line 311): "we wrote the context time anchors from the same daily routine that the traces follow, so time-of-day evidence is favourable to our system by construction."

The paper acknowledges this, which is good. But the consequence is severe:
- Time-only baseline achieves 92.2% correct — almost the same as request-driven LRU
- This high baseline means the marginal contribution of GPS/BLE sensing is smaller than it appears
- The entire schedule is deterministic: five contexts visited in order every day

**Required**: Add independent traces with shifted/jittered schedules as the task specifies.

## 4. Does Residency Leak Into Prediction?

The prediction evaluator tracks which states are resident. The `selectForInference()` function picks "the highest-ranked state that IS resident." This is correct behavior — it models the real system.

But does residency itself affect prediction? No — `getScoredStates()` scores independently of what's resident. The ranking is pure sensor-based. Only the final selection is constrained to resident states.

**No circularity here.**

## 5. Does Time Leak Into GPS-only / BLE-only?

Looking at the ablation code (prediction_eval.csv):
- `GPS_ONLY`: Uses `timeBaselineWeightOverride` to set w_t very small
- `BLE_ONLY`: Same mechanism

From the paper (line 317): "GPS only or BLE only with w_t=0.001 so time does not leak through normalization."

**Assessment**: w_t=0.001 is small but not zero. Through normalization:
```
alpha = 0.001 / (0.001 + w_g + w_b)
```
If w_g is large (good GPS fix), alpha becomes negligible. But if w_g is small (poor fix), alpha could reach ~5-10%.

**Verdict**: Minor time leakage possible in GPS-only/BLE-only baselines, but unlikely to meaningfully affect results given the normalization denominator.

## 6. Cold vs Wrong — Mutual Exclusivity

From prediction_eval.csv:
- `served_correct + wrong_context + cold_miss = 1.000` for every row ✓

These are mutually exclusive and exhaustive. Correct.

## 7. Priming Delay Not Modeled

From the paper (line 313): "It does not model priming delay: a state chosen at one sweep is assumed usable by the next simulated minute."

**Impact**: In reality, prefilling a context takes seconds (14s for 439 tokens). If a context transition occurs, the system needs time to prime the new context. The evaluator assumes instant availability.

**Required**: Add readiness-lag experiment as specified in Phase 16.

## 8. LRU Baseline Fairness

The `REACTIVE_LRU_NO_SENSING` baseline:
- Gets 92.2% correct (all cold misses, zero wrong)
- Uses K=3 cache size
- Maintains cache across days
- Correct context "becomes known at request time" — i.e., perfect identification

**Is this fair?** It's generous to LRU in one way (perfect identification) but realistic in another (LRU can't predict). The 7.8% cold miss rate comes from the structure: 5 contexts, K=3, sequential visits. Every first visit after cache fills is a miss.

**Assessment**: Fair as stated. The comparison is meaningful.

## 9. Is K=3 Justified?

Current evaluation only uses K=3. No K=1 or K=2 ablation exists.

With 5 contexts and K=3, 60% coverage is guaranteed. K=2 would give 40%. K=1 would give 20%.

**Required**: K ablation (Phase 20).

## 10. Single Seed Problem

All results use seed 42. A single seed means:
- The same random GPS noise realization
- The same BLE dropout pattern  
- The same time jitter
- Results may not generalize to other noise realizations

**Required**: Multi-seed evaluation (Phase 18) with confidence intervals.

## 11. Missing Baselines

Current baselines: Oracle, Request-driven LRU, Random, Time-only, GPS-only, BLE-only.

**Missing**: Markov/successor predictor. A simple P(next|current) baseline would test whether sensing adds value beyond deterministic context ordering.

## 12. Summary

| Issue | Severity | Status |
|-------|----------|--------|
| Circular time traces | HIGH | Acknowledged in paper, needs independent traces |
| Priming delay not modeled | HIGH | Not measured |
| Single seed | HIGH | Not addressed |
| No K ablation | MEDIUM | Not measured |
| No Markov baseline | MEDIUM | Not implemented |
| No readiness-lag experiment | MEDIUM | Not measured |
| No abstention/risk-coverage | LOW | Not implemented |
| Minor time leakage in ablations | LOW | Acknowledged via w_t=0.001 |
