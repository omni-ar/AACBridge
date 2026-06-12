# AACBridge

**Predictive Amortized KV-Caching for Low-Latency Edge LLMs in Augmentative and Alternative Communication**

> Arjit Tripathi · Medha Sriram · Heer Shah
> VIT University

---

## Overview

AACBridge is an Android application that brings context-aware language model inference to edge devices for users of Augmentative and Alternative Communication (AAC). The system addresses a fundamental latency problem: contextual system prompts (encoding a user's environment, medical state, and communication preferences) must be processed by the model at inference time, adding seconds of delay that disrupt real-time communication.

**CAP-KVC** (Context-Aware Predictive KV-Cache Priming) solves this by predicting which context the user will need—using GPS, Bluetooth Low Energy proximity, and time-of-day signals—and pre-computing the KV cache tensors during idle periods. At inference time, only a lightweight cache restoration (~2.63 ms) and the user's short intent tokens are processed.

### Key Results

| Metric | Value |
|---|---|
| Speedup vs RAG (N≈50) | **1.32×** |
| Speedup vs RAG (N≈500) | **4.59×** |
| Cache restoration time | **2.63 ms** (mean) |
| Memory overhead (3 states) | **121.6 MB** native heap |
| Fusion accuracy (synthetic) | **99.7%** (late fusion) |
| EMG standalone accuracy (LOSO) | **42.4%** |
| Target device | OnePlus 11R, Snapdragon 8+ Gen 1 |
| Model | Qwen2.5-0.5B-Instruct (Q4_K_M) |

## Architecture

```
┌─────────────────────────────────────────────────┐
│                  AACBridge App                   │
│                                                  │
│  ┌──────────┐  ┌────────────┐  ┌─────────────┐  │
│  │  Gaze    │  │    EMG     │  │   Sensor    │  │
│  │ Tracker  │  │ Classifier │  │   Sweep     │  │
│  │(MediaPipe│  │ (CNN-LSTM) │  │ (GPS/BLE/   │  │
│  │  468 lm) │  │  64-dim    │  │    Time)    │  │
│  └────┬─────┘  └─────┬──────┘  └──────┬──────┘  │
│       │              │                │          │
│       └──────┬───────┘                │          │
│              ▼                        ▼          │
│  ┌───────────────────┐  ┌──────────────────────┐ │
│  │   Late Fusion     │  │   State Router       │ │
│  │ (ONNX, 45 KB)     │  │ (Convex Scoring)     │ │
│  └────────┬──────────┘  └──────────┬───────────┘ │
│           │                        │             │
│           ▼                        ▼             │
│  ┌─────────────┐      ┌─────────────────────┐   │
│  │  Intent     │      │  KV Cache Manager   │   │
│  │  Prompt     │      │  (3 slots, LRU)     │   │
│  └──────┬──────┘      └──────────┬──────────┘   │
│         │                        │               │
│         └────────┬───────────────┘               │
│                  ▼                               │
│  ┌──────────────────────────────────────────┐    │
│  │        llama.cpp (JNI, C++)              │    │
│  │  prefillOnly │ saveKV │ loadKV │ resume  │    │
│  └──────────────────────────────────────────┘    │
└──────────────────────────────────────────────────┘
```

## Repository Structure

```
AACBridge/
├── android/                    # Android application
│   └── app/src/main/
│       ├── java/com/aacbridge/
│       │   ├── MainActivity.kt           # Application entry point
│       │   ├── cache/
│       │   │   ├── KVCacheManager.kt     # 3-slot LRU cache residency
│       │   │   └── ContextPrimerImpl.kt  # Background prefill lifecycle
│       │   ├── daemon/
│       │   │   ├── ActiveSweep.kt        # GPS + BLE sensor collector
│       │   │   ├── DriftDetector.kt      # WorkManager periodic drift check
│       │   │   └── BootReceiver.kt       # BOOT_COMPLETED cache warmup
│       │   ├── routing/
│       │   │   └── StateRouter.kt        # Convex scoring function
│       │   ├── gaze/
│       │   │   └── GazeTracker.kt        # MediaPipe face landmarker
│       │   └── fusion/
│       │       └── FusionClassifier.kt   # ONNX Runtime late fusion
│       └── cpp/
│           └── llama_jni.cpp             # JNI boundary (8 native functions)
│
├── emg_pipeline/               # EMG training & evaluation
│   ├── train_cnn_lstm.py                 # CNN-LSTM classifier (NinaPro DB5)
│   ├── drift_ablation/
│   │   └── k_ablation.py                # Offline drift detection ablation
│   └── results/
│       ├── training_curves.png           # Train/val accuracy plot
│       ├── confusion_matrix.png          # Per-class confusion matrix
│       └── classification_report.txt     # 42.4% LOSO accuracy
│
├── fusion_model/               # Multimodal fusion
│   ├── ablation.py                       # Late fusion vs cross-attention
│   ├── export_onnx.py                    # PyTorch → ONNX export
│   └── results/
│       ├── fusion_ablation.csv           # Ablation metrics
│       └── winner.txt                    # "late_fusion"
│
├── benchmarks/                 # Latency & memory benchmarks
│   ├── BenchmarkRunner.kt                # On-device benchmark harness
│   └── results/
│       ├── ttft_clean.csv                # 180 latency measurements
│       ├── mem_0states.txt               # Native heap: idle
│       └── mem_3states.txt               # Native heap: 3 KV states
│
├── paper/                      # IEEE Access manuscript
│   ├── final_main.tex                    # Self-contained paper (single file)
│   ├── main.tex                          # Modular version (uses \input)
│   ├── 01_introduction.tex – 07_conclusion.tex
│   ├── references.bib                    # 19 verified BibTeX entries
│   ├── generate_paper_figures.py         # Reproduces all figures from data
│   └── figures/                          # 6 publication-quality figures
│
├── docs/                       # Technical documentation
│   ├── MASTER_CONTEXT.md                 # Architecture & project overview
│   ├── CONCURRENCY_MODEL.md              # engineLock + per-state mutex
│   ├── COLDSTART_FLOW.md                 # prime → save → load lifecycle
│   ├── JNI_BOUNDARY_CONTRACT.md          # 8 JNI functions, scalar-only
│   ├── SCORING_FUNCTION.md               # Time/GPS/BLE scoring equations
│   ├── DRIFT_DETECTION.md                # Hysteresis-gated replacement
│   ├── MEMORY_BUDGET.md                  # Native heap profiling
│   └── PHASE2_CLOSURE_REPORT.md          # Integration verification
│
├── scripts/                    # Utility scripts
├── database/                   # Context state definitions
├── requirements.txt            # Python dependencies
└── build.gradle / settings.gradle        # Gradle build configuration
```

## How It Works

### 1. Background Context Priming (Idle Time)

```
Sensor Sweep → State Router → Top-k Contexts → prefillOnly() → saveKVCache() → .bin file
```

The system polls GPS, BLE, and time sensors, scores all registered contexts using a deterministic convex scoring function, and pre-computes KV cache tensors for the top-k contexts. Cache files are written atomically via `rename()`.

### 2. Inference (User Interaction)

```
loadKVCache(.bin) → resumeInference(intent tokens) → Generated Response
```

The pre-computed cache is loaded in ~2.63 ms. Only the user's short intent tokens (e.g., "I need water") are processed by the model. The full context prompt is never re-processed.

### 3. Drift Detection (Background)

Every 15 minutes, a `WorkManager` task re-evaluates sensor scores. If a new context exceeds the weakest resident state's score by Δ ≥ 0.10, a cache swap is triggered. This hysteresis margin prevents thrashing.

## Multimodal Intent Pipeline

User intent is classified from two modalities:

- **sEMG**: A CNN-LSTM classifier extracts a 64-dimensional embedding from 16-channel facial EMG signals (trained on NinaPro DB5, LOSO protocol)
- **Gaze**: MediaPipe Face Landmarker extracts a 5-dimensional displacement vector from 468 facial landmarks, mapped to intent classes via a dwell-based state machine (400 ms threshold)

Both signals are fused through a **Late Fusion** architecture (selected over cross-attention via pre-specified ablation criterion: cross-attention must exceed late fusion F1 by >3% to justify deployment). The fused model is exported to ONNX (45 KB) and runs on-device via ONNX Runtime.

## Benchmarks

All benchmarks were conducted on a OnePlus 11R (Snapdragon 8+ Gen 1, 8 GB RAM) with Qwen2.5-0.5B-Instruct (Q4_K_M), 30 measured trials per condition.

| Condition | Mean Latency (ms) | Std (ms) |
|---|---|---|
| ZERO_CONTEXT | 3018.70 | 99.65 |
| RAG_INLINE (N≈50) | 4505.82 | 167.94 |
| RAG_INLINE (N≈100) | 5538.74 | 202.85 |
| RAG_INLINE (N≈200) | 8759.02 | 243.53 |
| RAG_INLINE (N≈500) | 15632.06 | 802.95 |
| **CAP_KVC (total)** | **3403.92** | **285.30** |

> **Note:** All latency values are end-to-end inference latency (tokenization + prefill + generation up to 64 tokens), not TTFT. See the paper for a full discussion of the measurement methodology and confounding factors.

## Building

### Android Application

```bash
# Requires Android SDK 33+, NDK, and CMake
cd android
./gradlew assembleDebug
```

The pre-compiled `llama.cpp` native libraries for `arm64-v8a` are bundled in the APK. The Qwen2.5-0.5B-Instruct GGUF model file must be placed on the device at the path configured in the application.

### Python Pipelines

```bash
pip install -r requirements.txt

# EMG classifier training
python emg_pipeline/train_cnn_lstm.py

# Fusion ablation
python fusion_model/ablation.py

# Drift detection k-ablation
python emg_pipeline/drift_ablation/k_ablation.py

# Reproduce paper figures
python paper/generate_paper_figures.py
```

## Paper

The manuscript is located in `paper/` and targets **IEEE Access**.

- **Single-file version**: `paper/final_main.tex` — compile with `pdflatex final_main.tex` (×2, no bibtex needed)
- **Modular version**: `paper/main.tex` — compile with `pdflatex` + `bibtex` + `pdflatex` (×2)

To reproduce all figures from raw benchmark data:

```bash
python paper/generate_paper_figures.py
```

## Known Limitations

1. **Single-device evaluation** — all benchmarks on OnePlus 11R only
2. **Single model** — only Qwen2.5-0.5B-Instruct (Q4_K_M) evaluated
3. **Synthetic fusion data** — fusion model trained/evaluated on generated EMG-gaze pairs, not real clinical recordings
4. **End-to-end latency metric** — JNI boundary prevents true TTFT isolation
5. **EMG standalone accuracy** — 42.4% under cross-subject evaluation (motivates fusion approach)
6. **No clinical evaluation** — system not yet tested with AAC users in naturalistic settings

## Documentation

| Document | Description |
|---|---|
| [MASTER_CONTEXT.md](docs/MASTER_CONTEXT.md) | Architecture overview, project constraints |
| [CONCURRENCY_MODEL.md](docs/CONCURRENCY_MODEL.md) | engineLock + per-state mutex design |
| [COLDSTART_FLOW.md](docs/COLDSTART_FLOW.md) | prime → save → load cache lifecycle |
| [JNI_BOUNDARY_CONTRACT.md](docs/JNI_BOUNDARY_CONTRACT.md) | 8 JNI functions, scalar-only constraint |
| [SCORING_FUNCTION.md](docs/SCORING_FUNCTION.md) | Time/GPS/BLE convex scoring equations |
| [DRIFT_DETECTION.md](docs/DRIFT_DETECTION.md) | Hysteresis-gated cache replacement |
| [MEMORY_BUDGET.md](docs/MEMORY_BUDGET.md) | Native heap profiling results |
| [PHASE2_CLOSURE_REPORT.md](docs/PHASE2_CLOSURE_REPORT.md) | Integration verification report |

## License

This project is part of an academic research submission. Contact the authors for licensing information.

## Citation

If you use this work, please cite:

```bibtex
@article{tripathi2025aacbridge,
  title   = {Predictive Amortized KV-Caching for Low-Latency Edge LLMs
             in Augmentative and Alternative Communication},
  author  = {Tripathi, Arjit and Sriram, Medha and Shah, Heer},
  journal = {IEEE Access},
  year    = {2025}
}
```
