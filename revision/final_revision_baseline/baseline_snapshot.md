# AACBridge Revision — Baseline Snapshot

**Created:** 2026-10-03
**Branch:** `revision/reviewer-response`
**Baseline Commit:** `1e75a68` (Untrack AACBridge_Applied_Scientist_Master_Dossier.pdf and add to .gitignore)
**Parent Branch:** `main`

## Repository State at Baseline

| Property | Value |
|----------|-------|
| Branch | `revision/reviewer-response` |
| Commit | `1e75a68` |
| Working tree | Clean |
| Build status | BUILD SUCCESSFUL (debug APK) |
| Unit test count | 11 test files, all passing at baseline |

## Key Files (Immutable Baseline)

| File | Status |
|------|--------|
| `paper/short_final.tex` (846 lines) | Submitted manuscript |
| `PROJECT_1_submitted_for_conference.pdf` | IMMUTABLE — do not overwrite |
| `benchmarks/results/canonical_benchmark.csv` (181 rows) | Original benchmark data |
| `.gitignore` | Includes dossier PDF exclusion |

## Benchmark Data Summary

| Mode | Trials | Token Tiers |
|------|--------|-------------|
| ZERO_CONTEXT | 30 | N=0 (9 tokens) |
| RAG_INLINE | 120 | N∈{50,100,200,500} × 30 |
| CAP_KVC | 30 | N=50 (cached), 9 intent tokens |
| **Total** | **180** | |

## Submitted Paper Headline Numbers (Verified Against Raw Data)

| Metric | Value | Source |
|--------|-------|--------|
| Prefill speedup (RAG@500 vs CAP_KVC) | 40.78× | canonical_benchmark.csv |
| E2E speedup (RAG@500 vs CAP_KVC) | 4.24× | canonical_benchmark.csv |
| Cache load time | 2.74 ms (σ=1.35) | canonical_benchmark.csv |
| Memory delta (3 states) | 118.7 MB | dumpsys meminfo snapshots |
| Late fusion F1 | 0.9963 | fusion_ablation.csv |
| EMG standalone accuracy | 42.4% | classification_report.txt |
| Drift F1 (k=1, θ=0.15) | 0.367 | drift evaluation script |

## Context Configuration

| # | State ID | Expected Time | GPS (lat, lng) | BLE Anchors |
|---|----------|--------------|----------------|-------------|
| 1 | home_morning | 8.0h | 28.6139, 77.2090 | EE:01, EE:02 |
| 2 | home_evening | 19.0h | 28.6139, 77.2090 | EE:01, EE:03 |
| 3 | hospital_ward | 11.0h | 28.5672, 77.2100 | EE:04, EE:05 |
| 4 | therapy_room | 14.0h | 28.5672, 77.2105 | EE:06 |
| 5 | caregiver_visit | 16.5h | 28.6139, 77.2090 | EE:07, EE:01 |

**GPS coordinate sharing:** home_morning, home_evening, caregiver_visit share identical GPS (28.6139, 77.2090).
