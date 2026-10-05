# Reproducibility Notes — AACBridge

**Date:** 2026-10-05  
**Branch:** `revision/reviewer-response`  
**Repository:** `omni-ar/AACBridge`

---

## 1. Hardware and Execution Environment

| Parameter | Value | Notes |
|-----------|-------|-------|
| **Device** | OnePlus 11R (CPH2487) | Physical mobile hardware |
| **SoC** | Qualcomm Snapdragon 8+ Gen 1 | 1× Cortex-X2 @ 3.0 GHz, 3× Cortex-A710 @ 2.5 GHz, 4× Cortex-A510 @ 1.8 GHz |
| **RAM** | 8 GB LPDDR5 | Android unified system/graphics memory |
| **OS** | Android 14 | Stock OxygenOS / ColorOS base |
| **Host Toolchain** | Android NDK 27.2.12479018 (r27c) | CMake 3.22.1, C++17 standard |
| **Target ABI** | `arm64-v8a` | 64-bit ARM NEON support |
| **SDK Levels** | `minSdk 26`, `compileSdk 34`, `targetSdk 34` | Android 8.0 Oreo through Android 14 |
| **Build Type** | Debug | Default Gradle development build |

---

## 2. Model & Runtime Specifications

| Parameter | Value | Location / Configuration |
|-----------|-------|--------------------------|
| **Base Model** | Qwen2.5-0.5B-Instruct | Alibaba Cloud / Qwen Team |
| **Quantization** | Q4_K_M | Medium 4-bit k-quant |
| **Weight Format** | GGUF | Stored in app internal assets / cache |
| **Runtime Engine** | llama.cpp | Built from source as CMake native submodule |
| **Thread Count** | 4 threads | Optimal big-core affinity on Snapdragon 8+ Gen 1 |
| **Batch Size** | 512 | Native context evaluation batch |
| **Decoding** | Greedy ($T=0.0$) | Deterministic token selection, cap = 64 tokens |
| **Context Geometry** | 4 sequences × 1024 tokens | Slots 0–2: resident contexts; Slot 3: benchmark/scratch |
| **KV Memory** | 48 MiB allocated at init | 12 KiB/token (24 layers, 2 heads, dim 64, fp16) |

---

## 3. Data Provenance & Benchmark Artifacts

### A. Latency Benchmarks
- **Canonical Dataset:** `benchmarks/results/interleaved_20261005_trials.csv`
  - 180 measured trials across 6 conditions (30 trials per condition).
  - Seed 20261005 randomized condition order within each of the 30 rounds.
  - 2 warmup rounds discarded prior to data collection.
  - Gaze isolation active: 336 interaction attempts intercepted and rejected, 0 leaked.
  - Per-trial thermal zone temperature and CPU core 7 frequencies recorded.
- **Summary Statistics:** `benchmarks/results/interleaved_20261005_summary.csv`
- **Analysis Script:** `benchmarks/analyze_interleaved.py`
  - Computes means, standard deviations, medians, IQRs, Welch's $t$-test ($t(32.3) = 15.5, p = 1.75 \times 10^{-16}$), OLS slope ($34.1$ ms/token, $R^2 = 0.88$), and Spearman rank correlations ($|\rho| \le 0.13$ for position, $|\rho| \le 0.30$ for thermal).

### B. Routing & Simulation Evaluations
All trace evaluations originate from `PredictionEvaluationTest.kt` in `android/app/src/test/java/com/aacbridge/router/`:
- **Main Routing Evaluation (Table II):** `evaluation/prediction/tables/prediction_eval.csv`
  - 100 simulated days, 6,400 user interactions across 5 candidate contexts.
  - Generates Full Fusion (98.5%), LRU (92.2%), Random (20.7%), Time-only (92.2%), GPS-only (87.9%), BLE-only (92.6%), High Noise (90.6%), and Adversarial scenarios.
- **Hysteresis Sweep (Fig. 3):** `evaluation/prediction/tables/hysteresis_eval.csv`
  - Evaluates $\Delta \in [0.00, 0.50]$ under normal and high-noise regimes.
- **Markov Successor Baseline:** `evaluation/prediction/tables/markov_baseline.csv`
  - Trained on days 1–50, evaluated on days 51–100 ($99.69\%$ accuracy, $6.0$ loads/day).
- **$K$ Residency Ablation:** `evaluation/prediction/tables/k_ablation.csv`
  - Evaluates $K \in \{1, 2, 3\}$.
- **Multi-Seed Robustness:** `evaluation/prediction/tables/multi_seed_eval.csv`
  - 20 independent seeds (mean 98.33%, SD 0.158 pp, min 98.03% [seed 8], max 98.58% [seed 4]).
- **Schedule Variation Traces:** `evaluation/prediction/tables/independent_traces.csv`
  - Evaluates $\pm 1$ h, $\pm 2$ h, variable dwell, and high jitter.
- **Abstention Risk-Coverage:** `evaluation/prediction/tables/risk_coverage.csv`
  - Evaluates confidence threshold $\tau \in [0.00, 0.20]$.
- **Readiness Lag:** `evaluation/prediction/tables/readiness_lag.csv`
  - Evaluates interaction accuracy at 0.5, 1.0, 2.0, 5.0, and 15.0 minutes post-transition.

---

## 4. Replication Commands

### Step 1: Recompute Latency Statistics and Tests
To verify all numerical claims in Section V-B from the raw trial logs:
```bash
python benchmarks/analyze_interleaved.py
```

### Step 2: Rerun Simulation Evaluation Harness
To execute the Kotlin unit tests and re-generate all routing CSV tables:
```bash
cd android
./gradlew testDebugUnitTest --tests "com.aacbridge.router.PredictionEvaluationTest"
```

### Step 3: Re-render Manuscript Figures
To regenerate the vector PDF figures (`prefill_scaling.pdf` and `hysteresis_churn.pdf`):
```bash
python paper/make_figures.py
```

### Step 4: Compile Manuscript PDF
To compile `paper/ieee_main.tex` into `paper/ieee_main.pdf`:
```bash
cd paper
pdflatex -interaction=nonstopmode ieee_main.tex
pdflatex -interaction=nonstopmode ieee_main.tex
```
Verification of clean compilation:
- Return code: `0`
- Output: `ieee_main.pdf`
- Page count: Exactly 7 pages (columns balanced on page 7, zero orphan overflows).
