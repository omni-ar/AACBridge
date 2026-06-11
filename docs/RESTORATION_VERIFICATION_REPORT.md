# Restoration Verification Report

## 1. MASTER_CONTEXT.md
**Status:** RECONSTRUCTED

The following sections were successfully added to the current authoritative `MASTER_CONTEXT.md`:
*   `## Archived Equations`
*   `## Archived Thresholds`
*   `## Archived Design Decisions`

**Audit Details:**
*   **Exact Git Commit Source:** Information extracted from state prior to `695f57b0c91d38d96cd2480721edad403b4ba42e` (the Phase 2 closure and doc sync commit).
*   **Exact File Source:** `docs/MASTER_CONTEXT.md` (historical version).
*   **Verbatim vs Reconstructed:** Reconstructed.
*   **Reconstructed Statements:**
    *   Scoring function: S(ci) fully derived with edge cases.
    *   EMA update formula: `wi <- (1-alpha)·wi + alpha·rt`.
    *   Threshold: `alpha in [0.1, 0.3]`.
    *   Threshold: Weights bounded naturally to `[-2, 1]` by EMA + reward range.
    *   Design Decision: k=3 cosine similarity turn window.
    *   Design Decision: Fallback to concatenation late fusion if F1 gap < 3%.
    *   Design Decision: CMAB not full RL (no gradient updates, no rollouts).
    *   Design Decision: Style-conditioned generation: S = {concise, polite, urgent}.
    *   Design Decision: Single LLM call preserved, bandit selects prompt modifier only.
    *   Design Decision: Epsilon-greedy selection.

---

## 2. PHASE2_MEDHA_STATUS.md
**Status:** RECONSTRUCTED

The following sections were successfully added to the current authoritative `PHASE2_MEDHA_STATUS.md`:
*   `## Historical Experimental Findings`
*   `## Historical Known Issues`
*   `## Superseded Limitations`

**Audit Details:**
*   **Exact Git Commit Source:** Information extracted from state prior to `695f57b0c91d38d96cd2480721edad403b4ba42e`.
*   **Exact File Source:** `docs/PHASE2_MEDHA_STATUS.md` (historical version).
*   **Verbatim vs Reconstructed:** Reconstructed.
*   **Reconstructed Statements:**
    *   Per-class F1 table: confirm=0.0000, reject=0.5172, scroll=0.5161, select=0.6061, call-help=0.6190.
    *   k-ablation table: k=1 (F1 0.3667), k=2 (F1 0.3264), k=3 (F1 0.1199), k=4 (F1 0.0513), k=5 (F1 0.0198).
    *   LOSO discussion: Val accuracy of 50% reflects LOSO difficulty with 808 training windows.
    *   Known issue: Random TFLite weights → RESOLVED.
    *   Known issue: confirm Class Failure → RESOLVED.
    *   Known issue: Missing weight transfer → RESOLVED.
    *   Known issue: ONNX export issues (Opset 18) → RESOLVED.
    *   Superseded Limitation: Python Environment Split.
    *   Superseded Limitation: Simulation Data limitations.

---

## 3. Restored Scripts
**Status:** VERIFIED FROM HISTORY

*   `emg_pipeline/src/train.py`
    *   **Restored commit:** `3b89d75cb0b7fb00e898d3123716bcb8c3e9c711`
    *   **Line count:** 186
    *   **Matches historical version:** Yes, exact.
*   `emg_pipeline/src/evaluate.py`
    *   **Restored commit:** `3b89d75cb0b7fb00e898d3123716bcb8c3e9c711`
    *   **Line count:** 155
    *   **Matches historical version:** Yes, exact.
*   `emg_pipeline/src/export_onnx.py`
    *   **Restored commit:** `3b89d75cb0b7fb00e898d3123716bcb8c3e9c711`
    *   **Line count:** 142
    *   **Matches historical version:** Yes, exact.
*   `emg_pipeline/drift_ablation/k_ablation.py`
    *   **Restored commit:** `3b89d75cb0b7fb00e898d3123716bcb8c3e9c711`
    *   **Line count:** 145
    *   **Matches historical version:** Yes, exact.
*   `emg_pipeline/drift_ablation/annotate_dailydialog.py`
    *   **Restored commit:** `3b89d75cb0b7fb00e898d3123716bcb8c3e9c711`
    *   **Line count:** 118
    *   **Matches historical version:** Yes, exact.

---

## 4. emg_embedding_only Artifacts
**Status:** VERIFIED FROM HISTORY

*   `emg_pipeline/exports/emg_embedding_only.onnx`
    *   **File Size:** 75,827 bytes
    *   **SHA256 Hash:** `C2595341172FC0FE4B789B32FB7B3090973373787E81FEA4B3F61F1E3E361EC1`
    *   **Source Commit:** `0c2277d97180b1a60da4a56f63aab7a9a3c9aa83`
*   `emg_pipeline/exports/emg_embedding_only.onnx.data`
    *   **File Size:** 1,219,072 bytes
    *   **SHA256 Hash:** `05E45708C38BD29232B608F219E85ECDDB876B27E428AF9309D33F89D73157A8`
    *   **Source Commit:** `0c2277d97180b1a60da4a56f63aab7a9a3c9aa83`

**Artifact Classification:** Historical Reproducibility Artifacts.
*Evidence:* There are no active code references to these files in the current runtime pipeline or benchmarking harness. `fusion/export_onnx.py` generates dummy tensors for evaluation (`torch.randn(1, 1, 64)`). They are preserved strictly for historical Phase 2 reproduction.

---

## 5. Drift Detection Narrative
**Status:** VERIFIED FROM HISTORY

An exhaustive search across the repository confirms that:
*   Dataset naming anomalies (DialogSum) have been contextualized properly or removed from explicit authoritative declarations.
*   No document or paper draft still claims that `k=3` was "selected because it achieved the best metrics", "won empirically", or was "empirically validated".
*   All occurrences have successfully been replaced with the mandated engineering heuristic rationale.

---

## 6. Runtime Safety Verification
**Status:** VERIFIED FROM HISTORY

A `git diff --name-only HEAD` audit confirms that the following operational boundaries were respected:
*   `android/` - UNTOUCHED.
*   `fusion_model/` - UNTOUCHED.
*   `database/` - UNTOUCHED.
*   `benchmarks/` - UNTOUCHED.

The only modified paths were:
*   `docs/*` (Appending historical sections and correcting drift narrative)
*   `emg_pipeline/*` (Restoring deleted scripts and artifact files)
*   `paper/*` (Correcting drift narrative)

The underlying Android app logic, JNI bridging, and current Model architecture remain absolutely unchanged.
