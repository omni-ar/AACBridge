# AACBridge Prediction Evaluator — Debug Report

**Date:** 2026-10-04
**Auditor:** Final revision pass
**Files audited:**
- PredictionEvaluator.kt
- PredictionEvaluationTest.kt
- StateRouter.kt, TimeScorer.kt, GPSScorer.kt, BLEScorer.kt
- HardwareConfig.kt

---

## A. Ground-Truth Generation

The daily schedule is hardcoded in TraceGenerator (lines 97-103):

```
home_morning:    06:00 - 10:00
hospital_ward:   10:00 - 13:00
therapy_room:    13:00 - 15:30
caregiver_visit: 15:30 - 18:00
home_evening:    18:00 - 22:00
```

Interactions generated every 15 minutes from 06:00 to 22:00 = 64 points per day.

CIRCULARITY ISSUE: The ground-truth schedule uses the SAME representative daily
schedule that defined the expectedTime anchors in each ContextState. The TimeScorer
Gaussian peaks at exactly these anchor times. The evaluation measures "how well does
the scorer match its own design assumptions under noise" - NOT independent prediction.

Severity: HIGH - Must label traces as "controlled synthetic" not "realistic longitudinal."

## B. Sensor Generation

Sensor observations are independently sampled:
- Time: uniform perturbation (independent)
- GPS: Gaussian noise per-point (independent)
- BLE: per-anchor detection + RSSI noise (independent)

Each episode uses Random(seed + episodeId) - deterministic but episode-unique.

Finding: Sensor noise generation is mathematically independent of scoring. OK.

## C-D. Router Invocation and State Ranking

Uses production StateRouter. Correct sort order. Dead-state threshold 0.01 applied. OK.

## E. Residency Semantics

BUG 1: AGGRESSIVE RESIDENCY UPDATE
The evaluator updates the resident set at EVERY interaction point (every 15 min).
This gives optimistic Hit@3/cold-miss results because the resident set is always
synchronized with the latest router scores.

Severity: MEDIUM

## F. Cache Replacement vs Hysteresis

BUG 2: MAIN EVALUATOR HAS NO HYSTERESIS
The full fusion / GPS-only / BLE-only evaluations use PredictionEvaluator.evaluateEpisode()
which has NO hysteresis. Only the hysteresis sweep test has its own inline hysteresis
implementation. The main conditions are hysteresis-free.

Severity: MEDIUM

## G. Hit@1 Calculation

Line 308: if (top1 == gt) hit1++
Correct - measures whether highest-scoring state matches ground truth. OK.

## H. Hit@3 Calculation

BUG 3: HIT@3 HAS TWO DIFFERENT DEFINITIONS

Main evaluator (line 309): if (gt in topK) hit3++
  -> This is TOP-3 RANKING, not residency.

Hysteresis sweep (line 370): if (gt in residentStates) totalHit3++
  -> This is ACTUAL CACHE RESIDENCY.

Paper claims Hit@3 = "ground truth is resident among the 3 active cache states"
but main evaluator measures "ground truth is in the top-3 RANKED states."

Impact: 100.00% Hit@3 reflects ranking (trivially near-100% with 5 states),
not actual cache residency.

Severity: HIGH

## I. Cold-Miss Calculation

Line 310: if (gt !in residentStates) coldMiss++
Semantically correct definition. But optimistic due to Bug 1+2.

## J. Wrong-Context Calculation

BUG 4: GPS_SPOOFED HARDCODES wrongContextRate = 0.0
Lines 276-284 in GPS spoofed test: wrongContextRate = 0.0 (HARDCODED!)
The spoofed test never tracks wrongContext. The reported 0% is fabricated.

Severity: HIGH

## K. Switch Count

Measures top-1 rank changes (main evaluator) vs actual replacements (hysteresis sweep).
Different metrics reported under the same name.

Severity: MEDIUM

## L-M. TIME INFORMATION LEAKAGE IN ABLATIONS

BUG 5: "GPS-ONLY" AND "BLE-ONLY" ARE NOT TRUE SINGLE-MODALITY ABLATIONS

GPS-Only test (lines 114-128):
- gpsDropoutProb = 0.05 (GPS available ~95%)
- bleDropoutProb = 1.0 (BLE unavailable)
- timeNoiseHours = 0.5 (time noise present)
- Uses fullRouter with TIME_BASELINE_WEIGHT = 0.4

When BLE unavailable (wb=0), normalization becomes:
  totalWeight = wt + wg + 0 = 0.4 + wg
  alpha = 0.4 / (0.4 + wg)  ~= 0.35
  beta  = wg  / (0.4 + wg)  ~= 0.65

TIME IS STILL ACTIVE at ~35% weight.

Similarly BLE-Only becomes ~50-60% time + 40-50% BLE.

Neither is a true single-modality ablation. Both include substantial time scoring.

GPS-only 95.97% depends on time disambiguating collocated states (home_morning,
home_evening, caregiver_visit share identical GPS 28.6139, 77.2090).

BLE-only 98.14% includes time contribution for BLE-outage periods.

Severity: CRITICAL

## N-O. Ground Truth Circularity

The expectedTime values (8.0, 19.0, 11.0, 14.0, 16.5) are the Gaussian centers
for TimeScorer. The ground-truth schedule windows are designed around these times.
GPS and BLE anchors in the trace match GPS and BLE anchors in context states.

The evaluation measures "does the scorer correctly identify perturbed versions
of its own anchor data?" - a valid robustness test but NOT independent prediction.

## P. Stale State

No stale-state counting bug. Aggressive updates minimize stale-state window. OK.

---

## RED FLAG ANALYSIS

### GPS-only 95.97%
Actually ~65% GPS + 35% time. Time disambiguates collocated states.
The 4.03% error is at transition boundaries where time perturbation
causes wrong collocated state to score higher.
Label is misleading.

### BLE-only 98.14%
Actually ~50-60% time + 40-50% BLE. BLE provides discrimination
when available; time handles BLE outage periods.
Label is misleading.

### Hit@3 = 100% everywhere
Measured as top-3 ranking (not residency). With only 5 states
and dead-state threshold 0.01, ground truth almost always appears
in top-3 of 5 candidates. Trivially achievable.

### GPS Spoofed: 67.73% Hit@1, 0% Wrong Context
Wrong context is HARDCODED to 0.0 (not measured).
67.73% Hit@1 is plausible from time+BLE vs spoofed GPS competition.

### Hysteresis sweep identical for all delta
Score differences at transitions are large (geographic moves).
Between transitions, top-1 is already resident.
The trace has no near-boundary crossings. Delta has no measurable effect.
Untested implementation heuristic.

---

## REQUIRED FIXES

| # | Bug | Severity | Fix |
|---|-----|----------|-----|
| 1 | Resident set updated every 15min (optimistic) | MEDIUM | Document as assumption |
| 2 | Main evaluator has no hysteresis | MEDIUM | Unify code paths or document |
| 3 | Hit@3 = top-3 ranking, not residency | HIGH | Fix to measure actual residency |
| 4 | GPS_SPOOFED wrongContextRate hardcoded 0.0 | HIGH | Compute actual wrong-context |
| 5 | GPS-only/BLE-only include time weight | CRITICAL | True ablation (wt=0) or rename |
| 6 | Ground-truth circularity not acknowledged | HIGH | Add explicit caveat in paper |
| 7 | Hysteresis insensitive | MEDIUM | Acknowledge or create targeted test |
