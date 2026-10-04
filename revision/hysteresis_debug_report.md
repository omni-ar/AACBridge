# Hysteresis Sweep Debug Report

**Date:** 2026-10-04

## Finding

The hysteresis sweep produces IDENTICAL results for all delta values:

| Delta | Switches/day | Hit@1 | Hit@3 (residency) | Cold Miss |
|-------|-------------|-------|-------------------|-----------|
| 0.00  | 2.76        | 89.33% | 99.75%           | 0.25%     |
| 0.05  | 2.76        | 89.33% | 99.75%           | 0.25%     |
| 0.10  | 2.76        | 89.33% | 99.75%           | 0.25%     |
| 0.15  | 2.76        | 89.33% | 99.75%           | 0.25%     |
| 0.20  | 2.76        | 89.33% | 99.75%           | 0.25%     |

## Root Cause

The hysteresis code IS correctly wired. The replacement condition:

```kotlin
if ((best.second - weakestScore) > delta) {
    residentStates.remove(weakest)
    residentStates.add(top1)
    totalSwaps++
}
```

functions correctly. However, the trace does not produce score-crossing events
near any tested delta boundary because:

1. **Geographic transitions produce large score deltas.** When the user moves
   from home (28.6139) to hospital (28.5672), the score difference is large
   enough that even delta=0.20 does not block the replacement.

2. **Between transitions, top-1 is already resident.** When the user stays in
   one context, the top-ranked state doesn't change, so the replacement branch
   (`top1 !in residentStates`) is never entered.

3. **The 2.76 swaps/day correspond to the 4 major geographic transitions
   per day** (home→hospital, hospital→therapy, therapy→home via caregiver).
   Some are absorbed by top-K already covering adjacent states.

## Conclusion

Delta=0.10 is an **untested implementation heuristic**. The controlled
synthetic trace does not contain score-crossing events near the hysteresis
boundary. The parameter has no measurable effect on the evaluated trace.

## Paper Implication

Do NOT claim "Delta=0.10 provides effective insurance against oscillation"
unless a trace with near-boundary crossings demonstrates a measurable effect.

Instead, state: "The tested traces did not produce score-margin events
sensitive to the hysteresis threshold. Delta=0.10 is retained as a
conservative implementation heuristic."
