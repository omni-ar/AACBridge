# Reproducibility Restoration Report

## 1. Safety Verification
- **Runtime files modified:** 0
- **Android source files modified:** 0
- **JNI files modified:** 0
- **Build files modified:** 0
- **Assets used by runtime modified:** 0
- **Existing architecture:** Unchanged. The current production runtime relies on SeededStateRepository, ContextPrimerImpl, engineLock, Late Fusion, 5-dimensional gaze, and validated TFLite weights. None of these were altered.

## 2. Restored Files (Part A & B)
The following files were successfully restored to their state at commit `3b89d75cb0` and `676f53dd8f` respectively.
*   `emg_pipeline/src/train.py` (Reproducibility Dependency)
*   `emg_pipeline/src/evaluate.py` (Reproducibility Dependency)
*   `emg_pipeline/src/export_onnx.py` (Reproducibility Dependency)
*   `emg_pipeline/drift_ablation/k_ablation.py` (Reproducibility Dependency)
*   `emg_pipeline/drift_ablation/annotate_dailydialog.py` (Reproducibility Dependency)
*   `emg_pipeline/exports/emg_embedding_only.onnx` (Historical Reproducibility Artifact)
*   `emg_pipeline/exports/emg_embedding_only.onnx.data` (Historical Reproducibility Artifact)

**Intentionally Not Restored:**
*   None. All requested artifacts were restored to guarantee reproducibility.

## 3. Historical Findings Recovered (Part C & D)
**MASTER_CONTEXT.md:**
*   Added `## Archived Equations` (Scoring function S(ci), EMA formula)
*   Added `## Archived Thresholds` (Alpha bounds, EMA weight bounds)
*   Added `## Archived Design Decisions` (Late fusion fallback criteria)

**PHASE2_MEDHA_STATUS.md:**
*   Added `## Historical Experimental Findings` (Per-class F1 including 0.000 for confirm, k-Ablation CSV results, LOSO 50% val difficulty).
*   Added `## Historical Known Issues` (Random TFLite weights [RESOLVED], missing weight transfer [RESOLVED], ONNX opset 18 [RESOLVED]).
*   Added `## Superseded Limitations` (Python env splits, simulation data).

## 4. Documentation & Paper Corrections (Part E)
All false empirical claims regarding `k=3` achieving optimal metrics were audited and replaced across:
*   `paper/03_methodology.tex`
*   `paper/06_results.tex`
*   `docs/PHASE2_STATUS.md`
*   `docs/DRIFT_DETECTION.md`
*   `docs/MODEL_EXPORT_SPEC.md`

They now reflect the mandated narrative: *"The k-ablation study on DailyDialog did not identify k=3 as the highest-performing configuration. However, because DailyDialog differs substantially from AAC communication patterns, k=3 was retained as an engineering heuristic for sparse AAC interactions rather than selected solely on the basis of ablation metrics."*

## 5. Paper Consistency Audit Results (Part F)
*   **Late Fusion:** SAFE
*   **Cross-Attention:** SAFE
*   **5-dimensional gaze:** SAFE
*   **Label leakage remediation:** SAFE
*   **TFLite validation:** SAFE
*   **SeededStateRepository:** SAFE
*   **ContextPrimerImpl:** SAFE
*   **engineLock:** SAFE
*   **k-ablation narrative:** REQUIRES CORRECTION -> **CORRECTED** (Replaced false empirical claims with the correct engineering heuristics justification).

## 6. Remaining Risks
*   The raw `emg_classifier_int8.tflite` model used for Phase 2 validation historically contained random weights. The report explicitly marks this "RESOLVED" based on the current architecture's verified status ("Valid TFLite weight transfer"). No further risks are noted provided Phase 3 benchmarking is conducted against the corrected artifacts.
