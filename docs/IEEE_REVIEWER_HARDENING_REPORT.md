# IEEE Reviewer Hardening Report

This audit represents the final hardening pass on the LaTeX manuscript prior to Phase 3 benchmarking. The objective was to eliminate unverified empirical claims, exaggerated superiority statements, and reviewer attack surfaces, ensuring that the paper strictly adheres to repository reality.

## 1. File-by-File Findings, Risky Statements, and Corrections

### `paper/01_introduction.tex`
**Finding A: Unverified Latency Reduction Claim**
*   **Exact Risky Statement:** "This preemptive architecture drastically cuts down generation latency..." (Line 36)
*   **Suggested Correction:** Change to "This preemptive architecture is designed to target generation latency reductions..."
*   **Severity:** **Critical**. Claiming drastic latency cuts before Phase 3 benchmarking is completed violates empirical research standards.

**Finding B: Unverified Demonstration Claim**
*   **Exact Risky Statement:** "...demonstrating a robust, latency-optimized edge framework..." (Line 42)
*   **Suggested Correction:** Change to "...proposing a robust, latency-optimized edge framework intended to achieve empirical validation during Phase 3..."
*   **Severity:** **Critical**. The system has been architected but not yet empirically "demonstrated" on hardware.

### `paper/02_related_work.tex`
**Finding C: Exaggerated "Solved" Claim**
*   **Exact Risky Statement:** "Consequently, AACBridge solves the Contextual Multi-Armed Bandit (CMAB) problem..." (Line 24)
*   **Suggested Correction:** Change to "Consequently, AACBridge adapts Contextual Multi-Armed Bandit (CMAB) principles..."
*   **Severity:** **Moderate**. Claiming to "solve" a foundational reinforcement learning problem opens a massive reviewer attack surface.

**Finding D: Unsupported Superiority Claim**
*   **Exact Risky Statement:** "AACBridge's CAP-KVC architecture outperforms all existing mobile LLM pipelines by decoupling..." (Line 34)
*   **Suggested Correction:** Change to "AACBridge's CAP-KVC architecture builds upon existing mobile LLM pipelines by decoupling..."
*   **Severity:** **Critical**. Asserting superiority over all existing state-of-the-art pipelines without comparative benchmarking data is an automatic rejection trigger for IEEE reviewers.

### `paper/04_implementation.tex`
**Finding E: Unverified Performance Adjective**
*   **Exact Risky Statement:** "...providing instantaneous semantic switching..." (Line 14)
*   **Suggested Correction:** Change to "...facilitating low-latency semantic switching..."
*   **Severity:** **Moderate**. "Instantaneous" implies zero milliseconds, which is physically impossible and factually incorrect given the stated JNI boundary overheads.

### `paper/06_results.tex`
**Finding F: Formatting Typo in Corrected Narrative**
*   **Exact Risky Statement:** `% - Report Medha's -ablation F1 score results on the DailyDialog dataset.` (Line 50)
*   **Suggested Correction:** Change to `% - Report Medha's $k$-ablation F1 score results on the DailyDialog dataset.`
*   **Severity:** **Minor**. A missing `$k$` was introduced during the previous narrative replacement operation.

---

## 2. Verification of Specifically Requested Items

1. **TTFT CLAIMS:** Audited. Violations found in `01_introduction.tex` and `04_implementation.tex` (see Findings A, B, E).
2. **FUSION ARCHITECTURE:** **SAFE**. `03_methodology.tex`, `05_experiments.tex`, and `06_results.tex` perfectly align on the Late Fusion vs Cross-Attention ablation, the label leakage discovery, the shift to a 5-dimensional gaze vector, and the pre-defined $>3\%$ margin logic.
3. **K-ABLATION NARRATIVE:** **SAFE**. The text confirms that `k=3` was an engineering heuristic and was not optimal on the DailyDialog dataset. (Minor LaTeX syntax typo flagged in Finding F).
4. **EMG PIPELINE CLAIMS:** **SAFE**. `06_results.tex` correctly reports the `assert_allclose(atol=1e-3)` pass and max error of `9.30e-04`, while safely disclaiming clinical deployment performance.
5. **IMPLEMENTATION SECTION:** **SAFE**. `SeededStateRepository`, `engineLock`, and `ContextPrimerImpl` are documented in perfect alignment with the Android source logic. SQLite/Room is correctly presented as a future enhancement rather than a blocker.
6. **RELATED WORK:** Audited. Aggressive claims of outperforming existing systems and solving CMAB were identified (see Findings C, D).
7. **EXPERIMENTS SECTION:** **SAFE**. The experimental setup is properly isolated from the actual Results.
8. **ABSTRACT / CONCLUSION:** **SAFE**. Intentionally left blank as requested.

---

## 3. Final Recommendation

Based on the critical unverified performance claims present in the Introduction and Related Work sections, the manuscript status is:

### **REQUIRES CORRECTIONS BEFORE PHASE 3**

Targeted textual edits must be applied to `01_introduction.tex`, `02_related_work.tex`, and `04_implementation.tex` to soften the latency and superiority claims before moving forward.
