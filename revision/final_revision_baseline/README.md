# Final Revision Baseline Snapshot

**Created:** 2026-10-04T00:05 IST
**Purpose:** Immutable snapshot before final evidence-first revision pass

## Repository State

| Property | Value |
|----------|-------|
| Git commit | `1e75a681d22b94f53daa0cba35afc974012b80c5` |
| Branch | `revision/reviewer-response` |
| Working tree | Modified (uncommitted revision changes) |
| Build status | Not yet verified in this pass |

## Build Environment

| Component | Version |
|-----------|---------|
| Java | 22.0.2 (Oracle HotSpot) |
| Kotlin | 1.9.0 |
| Gradle | 8.9 |
| AGP | 8.4.0 |
| NDK | 27.2.12479018 (r27c) |
| compileSdk | 34 |
| minSdk | 26 |
| targetSdk | 34 |
| ABI | arm64-v8a |
| C++ standard | C++17 |
| CMake | 3.22.1 |

## Model Configuration

| Property | Value |
|----------|-------|
| Model | Qwen2.5-0.5B-Instruct |
| Quantization | Q4_K_M (GGUF) |
| Framework | llama.cpp |

## Device

| Property | Value |
|----------|-------|
| Device | OnePlus 11R |
| SoC | Snapdragon 8+ Gen 1 |
| RAM | 8 GB LPDDR5X |
| Storage | UFS 3.1 |
| Android | 14 |

## Benchmark Configuration

| Property | Value |
|----------|-------|
| Modes | ZERO_CONTEXT, RAG_INLINE (N∈{50,100,200,500}), CAP_KVC |
| Trials per condition | 30 measured + 2 warmup |
| Total measured trials | 180 |

## Snapshotted Files

- `short_final.tex` — paper source at revision start
- `references.bib` — bibliography
- `current_ground_truth.md` — ground truth document
- `baseline_snapshot.md` — earlier baseline snapshot
- `prediction_ablation.csv` — prediction evaluation results
- `hysteresis_sweep.csv` — hysteresis sweep results
- `canonical_benchmark.csv` — original benchmark data
- `reviewer_matrix.md` — reviewer response matrix
- `short_final_pre_final_revision.pdf` — compiled PDF before this pass

## WARNING

Do NOT modify files in this directory. They serve as the immutable reference
for the final revision pass.
