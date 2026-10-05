# Claim-Evidence Matrix

Every important numerical claim in ieee_main.tex mapped to its exact source artifact.

## Latency Claims

| Claim | Location | Statistic | Value | Source File | Verified |
|-------|----------|-----------|-------|-------------|----------|
| CAP-KVC prefill 366 ms | Abstract L63, Body L299 | Mean | 366.04 ms | interleaved_20261005_summary.csv | YES |
| RAG@50 prefill 1847 ms | Abstract L63, Body L299 | Mean | 1847.08 ms | interleaved_20261005_summary.csv | YES |
| 5.0x ratio | Abstract L63, Body L299 | Ratio of means | 5.046 | Computed from above | YES |
| Welch t(32.3)=15.5, p<1e-15 | Body L299 | Welch's t-test | t=15.5, df=32.3, p=1.75e-16 | Computed from trials CSV | YES |
| Restore 2.9 ms (mean) | Abstract L63, Body L299 | Mean | 2.942 ms | interleaved_20261005_summary.csv | YES |
| Restore 2.1 ms (median) | Table I L287 | Median | 2.1 ms | interleaved_20261005_trials.csv | YES |
| 14.9 s at 439 tokens | Abstract L63 | Mean | 14941.4 ms | interleaved_20261005_summary.csv | YES |
| 40.8x at 439 tokens | Body L303 | Ratio of means | 40.8 | Computed | YES |
| ~30 ms/token OLS | Body L303 | OLS slope | **34.1 ms/token** | interleaved trials | **WRONG** (old: 29.9) |
| R^2 = 0.87 | Body L303 | OLS fit | **0.88** | interleaved trials | **WRONG** (old: 0.91) |
| TTFT ~0.37 s CAP-KVC | Body L301 | Approx | restore+prefill+gen_step | Computed | Approximate OK |
| TTFT ~1.8 s inline | Body L301 | Approx | prefill+gen_step | Computed | OK |
| **Intro 351 ms** | Intro L84 | Mean | **351.40** | **OLD canonical_benchmark.csv** | **WRONG DATASET** |
| **Intro 2507 ms** | Intro L84 | Mean | **2506.67** | **OLD canonical_benchmark.csv** | **WRONG DATASET** |
| **Intro 7.1x** | Intro L84 | Ratio | **7.13** | OLD dataset | **WRONG DATASET** |
| **Intro 2.4 ms restore** | Intro L84 | Mean | **2.42** | OLD dataset | **WRONG DATASET** |
| **Intro 14.2 s** | Intro L78 | Mean | **14235** | OLD dataset | **WRONG DATASET** |
| **Intro 2.5 s** | Intro L78 | Mean | **2507** | OLD dataset | **WRONG DATASET** |

## Prediction Claims

| Claim | Location | Value | Source File | Verified |
|-------|----------|-------|-------------|----------|
| 98.5% correct, all sensors | Abstract, Table III | 0.984844 | prediction_eval.csv | YES |
| 92.2% LRU | Abstract, Table III | 0.921875 | prediction_eval.csv | YES |
| 1.5% wrong context | Abstract, Table III | 0.015156 | prediction_eval.csv | YES |
| 7.8% LRU cold miss | Table III | 0.078125 | prediction_eval.csv | YES |
| 20.7% random correct | Table III | 0.207031 | prediction_eval.csv | YES |
| 92.2% time only | Table III | 0.922344 | prediction_eval.csv | YES |
| 87.9% GPS only | Table III | 0.879063 | prediction_eval.csv | YES |
| 92.6% BLE only | Table III | 0.926406 | prediction_eval.csv | YES |
| 90.6% high noise | Table III | 0.905938 | prediction_eval.csv | YES |
| 61.1% GPS forged | Table III | 0.611094 | prediction_eval.csv | YES |
| 38.9% GPS forged wrong | Abstract, Table III | 0.388906 | prediction_eval.csv | YES |
| 98.3% GPS mock rejected | Table III | 0.982969 | prediction_eval.csv | YES |
| 90.1% BLE cloned | Table III | 0.900781 | prediction_eval.csv | YES |
| Loads/day all match | Table III | Various | prediction_eval.csv | YES |

## Markov & Ablation Claims

| Claim | Location | Value | Source File | Verified |
|-------|----------|-------|-------------|----------|
| Markov 99.7% | Sec V.D | 0.996875 → 99.7% | markov_baseline.csv | YES |
| Markov 6.0 loads/day | Sec V.D | 6.0 | markov_baseline.csv | YES |
| K=1: 99.4%, 0.6%, 10.7 | Sec V.E | Matches CSV | k_ablation.csv | YES |
| K=2: 98.5%, 1.5%, 39.1 | Sec V.E | Matches CSV | k_ablation.csv | YES |
| K=3: 98.5%, 1.5%, 31.6 | Sec V.E | Matches CSV | k_ablation.csv | YES |

## Multi-Seed Claims

| Claim | Location | Value | Source File | Verified |
|-------|----------|-------|-------------|----------|
| 20 seeds | Sec V.F | 20 | multi_seed_eval.csv | YES |
| Mean 98.3% | Sec V.F | 98.33% | multi_seed_eval.csv | YES |
| SD 0.15 pp | Sec V.F | **0.16 pp** | multi_seed_eval.csv | **SLIGHTLY OFF** |
| seed 8 min, 98.0% | Sec V.F | 98.03% | multi_seed_eval.csv | YES |
| **seed 19 max, 98.6%** | Sec V.F | **seed 4, 98.58%** | multi_seed_eval.csv | **WRONG SEED** |
| LRU constant 92.2% | Sec V.F | 0.921875 | multi_seed_eval.csv | YES |
| +6.1 pp advantage | Sec V.F | 6.14 pp → 6.1 | Computed | YES |

## Schedule Jitter Claims

| Claim | Location | Value | Source File | Verified |
|-------|----------|-------|-------------|----------|
| +-1h: 97.7% | Sec V.G | 97.69% | independent_traces.csv | YES |
| +-2h: 95.6% | Sec V.G | 95.63% | independent_traces.csv | YES |
| Variable dwell: 98.3% | Sec V.G | 98.34% | independent_traces.csv | YES |
| High jitter: 95.9% | Sec V.G | 95.89% | independent_traces.csv | YES |
| +-2h wrong: **4.3%** | Sec V.G | **4.37% → 4.4%** | independent_traces.csv | **SLIGHTLY OFF** |

## Abstention Claims

| Claim | Location | Value | Source | Verified |
|-------|----------|-------|--------|----------|
| tau=0.05: 97.1% correct | Sec V.H | 97.09% | risk_coverage.csv | YES |
| tau=0.05: 0.8% wrong | Sec V.H | 0.84% | risk_coverage.csv | YES |
| tau=0.05: 2.1% abstain | Sec V.H | 2.06% | risk_coverage.csv | YES |
| tau=0.10: 94.6% correct | Sec V.H | 94.64% | risk_coverage.csv | YES |
| tau=0.10: 0.4% wrong | Sec V.H | 0.44% | risk_coverage.csv | YES |
| tau=0.10: 4.9% abstain | Sec V.H | 4.92% | risk_coverage.csv | YES |

## Hysteresis Claims

| Claim | Location | Value | Source | Verified |
|-------|----------|-------|--------|----------|
| Delta=0 → 0.10: 65.9 to 31.6 loads/day (normal) | Sec V.C, Fig 2 | 65.91 to 31.60 | hysteresis_eval.csv | YES |
| Delta=0 → 0.10: 297.5 to 106.2 (high noise) | Sec V.C, Fig 2 | 297.54 to 106.17 | hysteresis_eval.csv | YES |
| Accuracy unchanged up to 0.5 | Sec V.C | 98.5% / 90.6% stable | hysteresis_eval.csv | YES |

## Implementation Claims

| Claim | Location | Source | Verified |
|-------|----------|--------|----------|
| 14 JNI methods | Sec IV L241 | llama_jni.cpp: 14 JNIEXPORT | YES |
| ReentrantLock | Sec IV L241 | LatencyProfiler.kt, ContextPrimerImpl.kt | YES |
| 4 sequences, 1024 tokens | Sec IV L244 | Code configuration | YES |
| Atomic rename | Sec IV L246 | KVCacheManager.kt | YES |
| allowBackup=false | Sec IV L256 | AndroidManifest.xml:63 | YES |
| KV cache excluded from backup | Sec IV L256 | data_extraction_rules.xml | YES |
| API 26+ | Sec IV L239 | build.gradle:16, minSdk 26 | YES |
| arm64-v8a | Sec IV L239 | build.gradle:30 | YES |
| Debug build | Limitations L405 | build.gradle: no release benchmark config | YES |
| Battery temp not readable | Limitations L405 | 0/180 readable in trials CSV | YES |
