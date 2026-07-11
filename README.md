# AACBridge — Context-Aware Predictive KV-Cache Priming for Mobile AAC

A research prototype for predictive KV cache priming on edge devices,
evaluated on a OnePlus 11R (Snapdragon 8+ Gen 1) with Qwen2.5-0.5B-Instruct (Q4\_K\_M).

**Paper:** *Context-Aware Predictive KV-Cache Priming for Latency-Sensitive
Mobile AAC Inference* (IEEE Access, submitted 2026).

---

## Repository Structure

```
AACBridge/
├── android/                    # Android application (Kotlin + C++ JNI)
│   └── app/
│       ├── src/main/
│       │   ├── java/com/aacbridge/
│       │   │   ├── inference/     # LlamaBridge, LatencyProfiler
│       │   │   ├── cache/         # KVCacheManager, ContextPrimerImpl
│       │   │   ├── router/        # StateRouter, scorers
│       │   │   ├── daemon/        # ContextDaemon, DriftDetector, ActiveSweep
│       │   │   ├── fusion/        # FusionInference (ONNX)
│       │   │   └── gaze/          # GazeTracker (MediaPipe)
│       │   └── cpp/               # llama_jni.cpp (JNI bridge)
│       └── build.gradle
├── benchmarks/
│   ├── results/                # Benchmark data (canonical CSV, raw logs)
│   │   ├── canonical_benchmark.csv  # Single source of truth (180 rows)
│   │   ├── run4_complete.log        # Raw logcat benchmark dump
│   │   ├── ttft_clean.csv           # Figure data (derived)
│   │   ├── ttft_enriched.csv        # Figure data (derived)
│   │   ├── mem_0states.txt          # Memory profile baseline
│   │   └── mem_3states.txt          # Memory profile (3 cache states)
│   ├── regenerate_canonical.py # Raw log → canonical CSV
│   ├── final_stats.py          # Statistics pipeline → Table II numbers
│   ├── verify_stats.py         # Verification: computed vs manuscript
│   ├── extract_enriched.py     # Log → paired CSV extraction
│   └── create_clean_csv.py     # Canonical → figure CSVs
├── paper/
│   ├── final_main.tex          # Main manuscript (self-contained)
│   ├── generate_paper_figures.py  # Regenerates all data figures
│   ├── generate_architecture.py  # Regenerates system architecture fig
│   ├── validate_latex.py         # Structural LaTeX validation
│   └── figures/                # All paper figures (PNG + PDF)
├── fusion_model/
│   ├── fusion/                 # Training code (PyTorch)
│   └── results/                # Model weights, ablation CSV
├── emg_pipeline/
│   ├── src/                    # EMG preprocessing
│   ├── drift_ablation/         # Drift detection k-ablation
│   └── results/                # Classification reports
└── archive/                    # Archived development artifacts
```

## Prerequisites

### Android Build

| Component | Version | Source |
|-----------|---------|--------|
| Android Studio | Ladybug or later | Required for AGP compatibility |
| Android SDK | compileSdk 34, minSdk 26 | `build.gradle` L8, L16 |
| NDK | r27c (27.2.12479018) | `build.gradle` L11 |
| CMake | 3.22.1 | `build.gradle` L38 |
| Java | JDK 17 | `build.gradle` L65-66 |
| Kotlin | 1.9.x | Via Android Gradle Plugin |
| ABI | arm64-v8a only | `build.gradle` L30 |

### GGUF Model

The benchmark requires a GGUF model file at:
```
/data/local/tmp/models/qwen2.5-0.5b-instruct-q4_k_m.gguf
```
This path is configured in `MainActivity.kt` L120-122.

**Manual step required:** Download the Qwen2.5-0.5B-Instruct Q4\_K\_M GGUF file
from Hugging Face and push to device via:
```bash
adb push qwen2.5-0.5b-instruct-q4_k_m.gguf /data/local/tmp/models/
```

### Prebuilt Native Library

The build expects a prebuilt `libllama.so` at:
```
android/app/src/main/jniLibs/arm64-v8a/libllama.so
```
**Manual step required:** Build llama.cpp for Android arm64-v8a or obtain
a prebuilt shared library. See `CMakeLists.txt` for link configuration.

### Python Environment (statistics and figures)

| Component | Version |
|-----------|---------|
| Python | 3.11+ |
| matplotlib | 3.11+ |
| numpy | 2.4+ |
| scipy | 1.17+ |

```bash
python -m venv .venv
.venv\Scripts\activate    # Windows
pip install matplotlib numpy scipy
```

### Benchmark Device

All paper benchmarks were conducted on:
- **Device:** OnePlus 11R (CPH2487)
- **SoC:** Snapdragon 8+ Gen 1
- **RAM:** 8 GB LPDDR5X
- **Storage:** UFS 3.1
- **OS:** Android 14
- **Configuration:** Airplane mode, 50% brightness, no other foreground apps

## Reproducing Paper Results

### 1. Build and Deploy Android Application

```bash
cd android
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Run Benchmark

The benchmark runs automatically on app launch via `LatencyProfiler.runBenchmarkSuite()`
(triggered in `MainActivity.kt` after model initialization).

**Manual step required:** Monitor completion via logcat:
```bash
adb logcat -s LatencyProfiler AACBridgeJNI | tee run_output.log
```
Wait for `=== BENCHMARK SUITE COMPLETE ===` (approximately 30 minutes).

### 3. Extract Benchmark Data

```bash
# Regenerate canonical dataset from raw log
python benchmarks/regenerate_canonical.py

# Generate figure-specific CSVs from canonical dataset
python benchmarks/create_clean_csv.py
```

The canonical dataset (`canonical_benchmark.csv`) is the single source
of truth for all statistics and figures.

### 4. Generate Statistics (Table II)

```bash
python benchmarks/final_stats.py
```

Outputs E2E means, Welch t-tests, Cohen's d, prefill decomposition.
All values should match Table II in the manuscript.

### 5. Generate Figures

```bash
# Data figures (5 plots)
python paper/generate_paper_figures.py

# Architecture diagram
python paper/generate_architecture.py
```

Generates all figures in `paper/figures/`:
`latency_comparison.png`, `latency_breakdown.png`,
`memory_budget.png`, `drift_k_ablation.png`, `fusion_ablation.png`,
`system_architecture.png`.

### 6. Compile Manuscript

```bash
cd paper
pdflatex final_main.tex
pdflatex final_main.tex   # Second pass for references
```

**Requires:** LaTeX distribution with `IEEEtran.cls` installed system-wide.

## Data Provenance

| Paper Artifact | Source File | Produced By |
|---------------|------------|-------------|
| Table II (latency) | `benchmarks/results/canonical_benchmark.csv` | `final_stats.py` |
| Table III (memory) | `benchmarks/results/mem_0states.txt`, `mem_3states.txt` | `adb shell dumpsys meminfo` |
| Table IV (fusion) | `fusion_model/results/fusion_ablation.csv` | `fusion/ablation.py` |
| Table V (EMG) | `emg_pipeline/results/` | `emg_pipeline/src/` |
| Fig. 1 (architecture) | Programmatic | `generate_architecture.py` |
| Fig. 2 (latency comparison) | `benchmarks/results/ttft_clean.csv` | `generate_paper_figures.py` |
| Fig. 3 (latency breakdown) | `benchmarks/results/ttft_enriched.csv` | `generate_paper_figures.py` |
| Fig. 4 (memory budget) | `benchmarks/results/mem_*.txt` | `generate_paper_figures.py` |
| Fig. 5 (fusion ablation) | `fusion_model/results/fusion_ablation.csv` | `generate_paper_figures.py` |
| Fig. 6 (training curves) | `emg_pipeline/results/` | Training script |
| Fig. 7 (EMG confusion) | `emg_pipeline/results/` | Training script |
| Fig. 8 (drift ablation) | `emg_pipeline/drift_ablation/results/` | `generate_paper_figures.py` |

## Data Pipeline

```
run4_complete.log (raw logcat, 85 KB)
       │
       └─ regenerate_canonical.py
              │
              ▼
   canonical_benchmark.csv (180 rows, single source of truth)
       │
       ├─ final_stats.py ──────────────────────→ Table II numbers
       │
       ├─ verify_stats.py ─────────────────────→ Verification report
       │
       └─ create_clean_csv.py
              │
              ├─→ ttft_clean.csv ──────────────→ Fig. 2 (latency comparison)
              │
              └─→ ttft_enriched.csv ───────────→ Fig. 3 (latency breakdown)

mem_0states.txt + mem_3states.txt ─────────────→ Fig. 4 (memory budget)
fusion_ablation.csv ───────────────────────────→ Fig. 5 (fusion ablation)
k_ablation_results.csv ────────────────────────→ Fig. 8 (drift ablation)

All figures ──→ paper/figures/ ──→ final_main.tex ──→ pdflatex ──→ PDF
```

## Unit Tests

```bash
cd android
./gradlew test
```

Test suites: `StateRouterTest`, `SeededStateRepositoryContractTest`,
`KVCacheManagerTest`, `KVCacheManagerPrimingTest`, `ContextPrimerImplTest`.

## License

MIT License. See [LICENSE](LICENSE) for details.
