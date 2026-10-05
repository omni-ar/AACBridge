# Final Pre-Write Audit — ieee_main.tex

**Date:** 2026-10-05  
**Branch:** `revision/reviewer-response`  
**Head commit:** `5ab42fc` — Interleaved benchmark rerun: 30 trials × 6 conditions  
**Auditor:** Antigravity (Phase 1 read-only audit)

---

## Data Sources

| Artifact | Path | Role |
|----------|------|------|
| **Interleaved benchmark (CANONICAL)** | `benchmarks/results/interleaved_20261005_trials.csv` | 180 trials (30×6), randomized order, gaze isolated |
| Interleaved summary | `benchmarks/results/interleaved_20261005_summary.csv` | Aggregated stats from above |
| Old canonical benchmark | `benchmarks/results/canonical_benchmark.csv` | 180 trials, NON-interleaved, older run |
| Old prefill_results.csv | `evaluation/latency/prefill_results.csv` | Stats from OLD canonical benchmark |
| Prediction eval | `evaluation/prediction/tables/prediction_eval.csv` | 13 configs, 6400 interactions each |
| Multi-seed | `evaluation/prediction/tables/multi_seed_eval.csv` | 20 seeds × 3 policies |
| K ablation | `evaluation/prediction/tables/k_ablation.csv` | K=1,2,3 |
| Markov | `evaluation/prediction/tables/markov_baseline.csv` | FULL vs MARKOV vs LRU |
| Independent traces | `evaluation/prediction/tables/independent_traces.csv` | 4 configs × 5 seeds |
| Risk coverage | `evaluation/prediction/tables/risk_coverage.csv` | τ=0..0.20 |
| Hysteresis eval | `evaluation/prediction/tables/hysteresis_eval.csv` | Δ=0..0.70, normal + high noise |
| Memory (3 states) | `benchmarks/results/mem_3states.txt` | dumpsys meminfo snapshot |
| Memory (0 states) | `benchmarks/results/mem_0states.txt` | dumpsys meminfo snapshot |
| JNI source | `android/app/src/main/cpp/llama_jni.cpp` | 14 JNIEXPORT functions |
| Kotlin bridge | `android/app/src/main/java/com/aacbridge/inference/LlamaBridge.kt` | 14 `external fun` |
| Build config | `android/app/build.gradle` | No explicit debug/release benchmark config |
| Manifest | `android/app/src/main/AndroidManifest.xml` | allowBackup=false, data extraction rules |

---

## CRITICAL INCONSISTENCY #1: Two Different Benchmark Runs Mixed

The manuscript mixes numbers from **two different benchmark runs**:

| Location in manuscript | Source dataset | CAP-KVC prefill | RAG@50 prefill | Ratio | Restore |
|----------------------|----------------|-----------------|----------------|-------|---------|
| **Abstract** (line 63) | Interleaved (20261005) | 366 ms | 1847 ms | 5.0× | 2.9 ms |
| **Introduction** (line 84) | OLD canonical | **351 ms** | **2507 ms** | **7.1×** | **2.4 ms** |
| **Table I** (line 272-289) | Interleaved (20261005) | med 293 [267,467] | med 1741 [1363,2223] | — | 2.1 [1.1,4.0] |
| **Body text** (line 299) | Interleaved (20261005) | mean 366, SD 122 | mean 1847, SD 510 | 5.0× | mean 2.9 |

**The introduction paragraph (line 84) uses OLD non-interleaved data. The rest uses the new interleaved data. These DISAGREE.**

Additionally, line 78 says "439-token prompt took **14.2 s**" and "52-token prompt took **2.5 s**":
- Interleaved data: 439-token mean = **14,941 ms = 14.9 s** (not 14.2)
- Interleaved data: 52-token mean = **1,847 ms = 1.8 s** (not 2.5)
- Old canonical: 439-token mean = 14,235 ms = 14.2 s (old data)
- Old canonical: 52-token mean = 2,507 ms = 2.5 s (old data)

**Resolution required:** The introduction must use the same interleaved benchmark numbers as the rest of the paper.

---

## CRITICAL INCONSISTENCY #2: Multi-Seed Max Seed

| Claim | Manuscript | Actual |
|-------|-----------|--------|
| Min seed | seed 8, 98.0% | seed 8, 98.03% ✓ |
| Max seed | **seed 19, 98.6%** | **seed 4, 98.58%** WRONG |
| SD | 0.15 pp | **0.16 pp** (rounds to 0.16 at 2 decimal places) |

Seed 19 is 98.55%, seed 4 is 98.58%. The manuscript identifies the wrong seed as the maximum.

---

## Claim-by-Claim Verification

### 1. Matched prefill: 366 ms vs 1847 ms (5.0x)

| Item | Manuscript | Interleaved data | Match |
|------|-----------|-----------------|-------|
| CAP-KVC prefill mean | 366 ms | 366.04 ms | YES |
| CAP-KVC prefill SD | 122 | 121.88 | YES |
| RAG@50 prefill mean | 1847 ms | 1847.08 ms | YES |
| RAG@50 prefill SD | 510 | 510.36 | YES |
| Ratio | 5.0x | 5.046x | YES |
| Welch t | 15.5 | 15.5 | YES |
| Welch df | 32.3 | 32.3 | YES |
| Welch p | <10^-15 | 1.75e-16 | YES |
| n per condition | 30 | 30 | YES |

### 2. Introduction paragraph: 351 ms vs 2507 ms (7.1x)

Uses OLD canonical data. Must be updated to interleaved numbers:
- CAP-KVC prefill: 351 ms (old) vs 366 ms (interleaved)
- RAG@50 prefill: 2507 ms (old) vs 1847 ms (interleaved)  
- Ratio: 7.1x (old) vs 5.0x (interleaved)
- Restore: 2.4 ms (old) vs 2.9 ms (interleaved mean), 2.1 ms (interleaved median)

### 3. Restore time: 2.9 ms (abstract) vs 2.4 ms (intro)

Abstract uses interleaved mean (2.942 ms → 2.9 ms). Correct.
Intro uses old canonical mean (2.420 ms → 2.4 ms). Wrong dataset.
Table I shows median 2.1 [1.1, 4.0]. Correct (interleaved).

### 4. 439-token latency

Abstract says 14.9 s → interleaved mean 14,941 ms. YES.
Intro says 14.2 s → old canonical mean 14,235 ms. WRONG DATASET.
Table I median 13989 → interleaved median 13989. YES.

### 5. JNI method count: 14

Source: 14 JNIEXPORT in llama_jni.cpp, 14 external fun in LlamaBridge.kt.
Manuscript says 14. VERIFIED.

Breakdown:
- Lifecycle (3): initializeBackend, initializeModel, release
- KV operations (4): saveKVCache, loadKVCache, clearKVCache, resetSlot
- Inference (3): runInference, prefillOnly, resumeInference
- Telemetry (4): getLastPrefillMs, getLastGenMs, getLastPromptTokens, getLastGenTokens

Note: current_ground_truth.md incorrectly says 13 (omits resetSlot).

### 6. Battery temperature

All 180 trials show battery_c = -1.0. Manuscript says "not readable (returned -1)". VERIFIED.

### 7. Debug vs release build

build.gradle has no special benchmark build type. Default Android Studio deployment is debug.
Manuscript says "The benchmark ran on a debug build." VERIFIED.

### 8. Memory numbers

| Claim | Manuscript | mem_3states.txt |
|-------|-----------|-----------------|
| 627 MB PSS | 627 MB | 612,940 KB ~ 599 MB |
| 151 MB native heap | 151 MB | 140,458 KB ~ 137 MB |
| 391 MB mmap | 391 MB | Not in extract |

DISCREPANCY. Numbers don't match current snapshot. Manuscript acknowledges "We did not re-measure process memory after this layout change" (line 249).

### 9. Table III (prediction) values

All 13 rows verified against prediction_eval.csv. ALL MATCH.

### 10. Markov baseline

99.7% correct (CSV: 0.996875 = 99.69% rounds to 99.7). VERIFIED.
6.0 loads/day. VERIFIED.
Train on first 50 days. Code confirms trainDays = 50. VERIFIED.

### 11. K ablation

K=1: 99.4% correct, 0.6% wrong, 10.7 loads/day. ALL MATCH CSV.
K=2: 98.5% correct, 1.5% wrong, 39.1 loads/day. ALL MATCH CSV.
K=3: 98.5% correct, 1.5% wrong, 31.6 loads/day. ALL MATCH CSV.

### 12. Multi-seed evaluation

20 seeds: YES.
Mean 98.3%: YES (98.33%).
SD 0.15 pp: SHOULD BE 0.16 pp (actual 0.158).
Range: seed 8 (98.03%) to seed 4 (98.58%). Manuscript says seed 19. WRONG.
LRU constant 92.2%: YES.
Advantage +6.1 pp: YES (6.14 rounds to 6.1).

### 13. Schedule jitter

+-1h: 97.7%. YES.
+-2h: 95.6%. YES.
Variable dwell: 98.3%. YES.
High jitter: 95.9%. YES.
+-2h wrong-context rate: manuscript says 4.3%, actual mean is 4.4%. SLIGHTLY OFF.

### 14. Abstention/risk-coverage

tau=0.05: 97.1% correct, 0.8% wrong, 2.1% abstention. ALL VERIFIED.
tau=0.10: 94.6% correct, 0.4% wrong, 4.9% abstention. ALL VERIFIED.

### 15. Security experiments

GPS forgery and BLE cloning are simulated in PredictionEvaluationTest.kt.
Not physical hardware attacks. Results match prediction_eval.csv. VERIFIED.

### 16. OLS ms/token and R-squared

Manuscript: "about 30 ms per token", R^2=0.87.
Interleaved data: 34.1 ms/token, R^2=0.88.
Old canonical data: 29.9 ms/token, R^2=0.91.
DISCREPANCY: manuscript uses old OLS values.

### 17. Spearman correlations

Position-prefill: max |rho| = 0.13 exactly. Manuscript says "<0.13". Should be "<=0.13".
Thermal-prefill: max |rho| = 0.30 exactly. Manuscript says "<0.30". Should be "<=0.30".

### 18. Thermal zone temperature

65.8-78.0 C, median 69.7. VERIFIED.
cpu7 787-1402 MHz. VERIFIED.
68% at max freq. VERIFIED (122/180 = 67.8%).
11% throttled. VERIFIED (19/180 = 10.6%).

### 19. Table I IQR values

Minor rounding differences (1-81 ms) from percentile interpolation method.
Medians all match exactly.

### 20. Generation time ranges

"3.9-5.7 s at median": CAP_KVC med=3922, RAG@500 med=5677. VERIFIED.

---

## Summary of Issues

### MUST FIX

1. Introduction uses old benchmark data (351/2507/7.1x/2.4/14.2/2.5) while abstract/table/body use interleaved (366/1847/5.0x/2.9/14.9/1.8). All must use the same dataset.

2. Multi-seed max seed: manuscript says seed 19, actual max is seed 4 (98.58%).

3. OLS slope: "about 30 ms/token" from old data; interleaved gives 34 ms/token.

### SHOULD FIX

4. Multi-seed SD: 0.16 pp, not 0.15 pp.
5. +-2h wrong-context rate: 4.4%, not 4.3%.
6. Spearman bounds: should be <=0.13 and <=0.30 (values at boundary).
7. Memory numbers (627/151/391 MB) stale; re-measure or mark as pre-layout-change.
8. Table I IQR values: minor rounding from percentile method.
9. R-squared: 0.88, not 0.87.

### VERIFIED CORRECT

- Abstract latency numbers (366, 1847, 5.0x, 2.9 ms)
- Table I median values
- All Table III prediction values (13 rows)
- Welch t-test statistics
- Markov baseline (99.7%, 6.0 loads/day)
- K ablation (all values)
- Multi-seed mean and advantage
- Schedule jitter means
- Abstention values
- Security experiment results
- Thermal/CPU data
- Battery temp (not readable)
- JNI count (14)
- Debug build claim
- Backup/security configuration
- Hysteresis sweep values
- 40.8x at 439 tokens
- Contribution claims match implementation
