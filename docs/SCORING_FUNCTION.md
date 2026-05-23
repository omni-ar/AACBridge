# State Router Scoring Function S(c_i)

This document formalizes the predictive context scoring mathematics implemented in Phase 2 for the deterministic `StateRouter`. 

## Core Scoring Equation

The absolute priority of any semantic state $c_i$ is determined by a dynamically weighted convex combination of three environmental anchors:

```
S(c_i) = α·S_time + β·S_gps + γ·S_ble
```

Where:
* $S(c_i)$ is bounded to `[0, 1]`.
* $\alpha + \beta + \gamma = 1$ under all conditions.

### 1. Temporal Anchor Score ($S_{time}$)
Implemented in `TimeScorer.kt`. Evaluates the expected hour of a context against the current decimal hour.

* **Circular Distance:** $d = \min(|t_1 - t_2|, 24 - |t_1 - t_2|)$
  Handles midnight wraparound seamlessly.
* **Decay Function:** $S_{time} = \exp(-d^2 / 2\sigma^2)$ (Gaussian Decay)
* **Constants:** `sigmaHours = 2.0`
  * **Rationale:** Clinically justified for AAC usage. A 2-hour delay (e.g., a caregiver is late) drops the temporal score to ~60%. If we used standard linear or aggressive exponential decay, a 2-hour variation would inappropriately penalize the context to near-zero.
* **Resilience:** Malformed inputs like `25.5` are safely normalized to `1.5` via modulo arithmetic.

### 2. Spatial Anchor Score ($S_{gps}$)
Implemented in `GPSScorer.kt`.

* **Distance:** $d = \text{Haversine}(\text{lat}_1, \text{lng}_1, \text{lat}_2, \text{lng}_2)$ in kilometers.
* **Decay Function:** $S_{gps} = \exp(-d / \lambda)$
* **Constants:** `lambdaKm = 0.1` (100-meter decay factor)
  * **Rationale:** A 100m separation represents a meaningful contextual shift (e.g., hospital room vs. cafeteria) without requiring hyper-precise millimeter targeting. At $d = 100\text{m}$, score falls to $0.367$. At $d = 1\text{km}$, it collapses to near-zero.
* **Atomic Validation:** Validations occur inside `GpsLocation` initialization, ensuring `lat`/`lng` pairs are never malformed or incomplete when passed to the scorer.

### 3. Topological Anchor Score ($S_{ble}$)
Implemented in `BLEScorer.kt`.

* **Equation:** $S_{ble} = \frac{\sum (w_i \cdot x_i)}{\sum w_i}$
  Where $x_i = 1$ if the device is detected, $0$ if absent.
* **Weights:** $w_i$ represents the static importance of a device (e.g., caregiver's phone > generic room speaker). The database contract strictly enforces $w_i > 0$.
* **Denominator Check:** An empty expected registry evaluates to `0.0`. Missing devices aggressively penalize the denominator.

## Dynamic Weight Normalization (Reliability Balancing)

The core innovation of the scoring function is the dynamic redistribution of the weights ($\alpha, \beta, \gamma$) based on real-time hardware signal reliability.

* **Baseline ($\alpha$):** `TIME_BASELINE_WEIGHT = 0.4`
  Time is the only hardware signal that is structurally guaranteed to be reliable. By enforcing $\alpha \ge 0.4$, we prevent divide-by-zero errors in denominator-redistribution logic if both GPS and BLE sensors fail simultaneously.
* **GPS Reliability ($a_{gps}$):** 
  Based on hardware accuracy. `lambdaAccuracyMeters = 50.0`.
  $a_{gps} = \exp(-\text{accuracyMeters} / 50.0)$
  (e.g., 50m indoor accuracy retains a 36% confidence weight).
* **BLE Reliability ($R_{ble}$):**
  Based on the average signal strength of *detected* devices, using a sigmoid activation centered at $-70\text{dBm}$.
  $R_{ble} = \frac{1}{M} \sum \frac{1}{1 + \exp(-k(\text{rssi}_i - (-70)))}$

### The `M = 0` Edge Case
If no BLE devices are detected ($M=0$), the `calculateReliability()` function immediately returns `0.0`. Consequently, $\gamma = 0$. 

The system gracefully redistributes the missing weight to $\alpha$ and $\beta$, scaling them proportionally so the convex property ($\alpha + \beta + \gamma = 1$) is maintained. This ensures the router continues outputting valid context rankings using only Time and GPS.

## Dead State Culling

* **Constant:** `DEAD_STATE_THRESHOLD = 0.01`
* **Rationale:** Because exponential and Gaussian decay functions never mathematically reach absolute zero, asymptotic "dead" states must be manually pruned to save CPU cycles during sorting and `KVCacheManager` iterations. Any state scoring below `0.01` is stripped from the ranking completely.
