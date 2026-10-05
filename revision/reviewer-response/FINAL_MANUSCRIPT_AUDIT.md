# Final Manuscript Audit — AACBridge Paper Revision

**Date:** 2026-10-05  
**Branch:** `revision/reviewer-response`  
**Manuscript:** `paper/ieee_main.tex`  
**Compiled Output:** `paper/ieee_main.pdf` (Exactly 7 pages, clean compile, zero overflow)  
**Evaluator:** Antigravity (Post-Rewrite Phase)

---

## Executive Summary

The AACBridge manuscript (`paper/ieee_main.tex`) has undergone a thorough authorship-level scientific rewrite. All claims have been mapped directly to source code and benchmark data in the repository. Numerical inconsistencies between earlier preliminary benchmarks and the canonical interleaved dataset have been resolved across every section. The writing style has been reconstructed to reflect the genuine technical voice of a systems research group, eliminating repetitive formulaic structures, generic academic filler, and artificial enthusiasm.

---

## 1. Resolution of Numerical Inconsistencies

Every numerical discrepancy identified in Phase 1 has been corrected in the revised manuscript:

| Claim Description | Pre-Revision Value | Revised Value | Source Artifact | Status |
|-------------------|-------------------|---------------|-----------------|:------:|
| **Intro Matched Prefill** | 351 ms vs. 2507 ms (7.1×) | **366 ms vs. 1847 ms (5.0×)** | `interleaved_20261005_trials.csv` | **FIXED** |
| **Intro Restore Overhead** | 2.4 ms | **2.9 ms (mean) / 2.1 ms (median)** | `interleaved_20261005_summary.csv` | **FIXED** |
| **Intro 439-token Prefill** | 14.2 s | **14.9 s (14,941 ms)** | `interleaved_20261005_summary.csv` | **FIXED** |
| **Intro 52-token Prefill** | 2.5 s | **1.8 s (1,847 ms)** | `interleaved_20261005_summary.csv` | **FIXED** |
| **OLS Scaling Slope** | ~30 ms/token, $R^2 = 0.87$ | **34.1 ms/token, $R^2 = 0.88$** | `benchmarks/analyze_interleaved.py` | **FIXED** |
| **Multi-seed Max Seed** | Seed 19 (98.6%) | **Seed 4 (98.58%)** | `multi_seed_eval.csv` | **FIXED** |
| **Multi-seed Std Dev** | 0.15 pp | **0.16 pp** (actual 0.158 pp) | `multi_seed_eval.csv` | **FIXED** |
| **Schedule Jitter (±2h) Error** | 4.3% | **4.4%** (actual 4.375%) | `independent_traces.csv` | **FIXED** |
| **Spearman Correlation Bounds** | $|\rho| < 0.13$, $|\rho| < 0.30$ | **$|\rho| \le 0.13$, $|\rho| \le 0.30$** | `benchmarks/analyze_interleaved.py` | **FIXED** |
| **JNI Export Function Count** | 13 (in some docs) vs. 14 | **14 native functions** | `llama_jni.cpp` & `LlamaBridge.kt` | **VERIFIED** |
| **Battery Temperature** | Ambiguous in older text | **Unreadable (returned -1.0)** | `interleaved_20261005_trials.csv` | **VERIFIED** |
| **Benchmark Build Type** | Ambiguous | **Debug build** | `android/app/build.gradle` | **VERIFIED** |

---

## 2. Reviewer Coverage Audit

All 24 reviewer comments across Reviewers #1, #3, and #4 were audited against code and empirical logs:

- **Reviewer #1 (4/4 Addressed):**
  - R1-A: Positioned against SGLang, vLLM, and RAGCache; clarified multi-user reactive caching vs. edge single-user proactive priming (§II, Table I).
  - R1-B: Structured introduction from clinical AAC turn-taking constraints and latency bottlenecks (§I).
  - R1-C & R1-D: Focused novelty on multi-modal ambient sensor prediction and idle KV prefill (§I, §III).
- **Reviewer #3 (11/11 Addressed):**
  - R3-A & R3-B: Evaluated prediction accuracy (98.5% Full Fusion vs. 92.2% LRU vs. 20.7% Random across 6,400 interactions; §V-C, Table II).
  - R3-C: Quantified cold-miss rates (0.0% Full Fusion, 7.8% LRU, 40.2% Random; §V-C, Table II).
  - R3-D: Modeled multi-modal Gaussian spatial noise, dropouts, time jitter, and beacon outages (§V-C).
  - R3-E: Formulated fallback and wrong-context implications (§III-D, §V-C).
  - R3-F: Provided Random, Time-only, GPS-only, and BLE-only baselines alongside Oracle (§V-C, Table II).
  - R3-G & R3-H: Addressed context-length mismatch by leading with matched 52-token prefill (366 ms vs. 1847 ms, 5.0× reduction, Welch $t(32.3)=15.5, p<10^{-15}$) and acknowledging generation-time dominance (§V-B, Table I).
  - R3-I: Characterized sensor duty cycles; documented physical power measurement as unmeasured (§V-K, §VII).
  - R3-J: Explicitly highlighted single-device limitation (§VII).
  - R3-K: Evaluated deployed hysteresis margin sweep ($\Delta \in [0.00, 0.50]$) suppressing churn from 65.9 to 31.6 loads/day without accuracy loss (§V-D, Fig. 3).
- **Reviewer #4 (9/9 Addressed / Accounted):**
  - R4-A & R4-C: Verified Android mock location rejection (`isMock()`, `isFromMockProvider()`) and accuracy/staleness filters (§IV, §V-J, §VI).
  - R4-B & R4-E: Analyzed BLE beacon cloning and bounded blast radius (§V-J, §VI).
  - R4-D: Formally documented unencrypted KV cache files in app-private storage, relying on the Android application sandbox; acknowledged cryptographic encryption as future work (§VI, §VII).
  - R4-F: Documented $K=3$ residency allocation (48 MiB KV allocation across 4 sequence slots; §IV, §V-F).
  - R4-G: Detailed foreground service duty cycle and 15-minute periodic low-power checks (§III, §IV).
  - R4-H & R4-I: Dedicated Threat Model section detailing assets, attacker profiles, defensive boundaries, and residual risks (§VI).

---

## 3. Scope Boundaries & Claims Without Direct Artifact Support

To maintain absolute scientific integrity, the manuscript explicitly distinguishes between verified measurements and unmeasured quantities:

1. **Hardware Power & Battery Consumption:**
   - *Status:* **NOT MEASURED** on physical instrumentation.
   - *Manuscript treatment:* Section V-K and Section VII explicitly state that physical battery drain and power draw were not evaluated with external hardware power monitors.
2. **Arbitrary KV Restore Scaling:**
   - *Status:* **PARTIALLY MEASURED**.
   - *Manuscript treatment:* Restore latency was measured for the 44-token context (mean 2.9 ms, median 2.1 ms, IQR [1.1, 4.0]). Section V-B explicitly states that scaling across arbitrary context token lengths was not measured.
3. **Process Memory Post-Reconfiguration:**
   - *Status:* **HISTORICAL SNAPSHOT**.
   - *Manuscript treatment:* The snapshot of 627 MB PSS / 151 MB native heap was taken prior to moving to the 4-sequence slot layout. Section IV explicitly notes that memory was not re-measured after this layout change.
4. **Clinical Efficacy:**
   - *Status:* **NOT EVALUATED**.
   - *Manuscript treatment:* The system was not evaluated in clinical AAC trials with disabled users; no clinical efficacy claims are made.
5. **Synthetic Schedule Predictability:**
   - *Status:* **EXPLICIT CEILING REPORTED**.
   - *Manuscript treatment:* The Markov baseline achieves 99.7% on the synthetic trace. Section V-E and Section VII explicitly discuss this result to clarify that the synthetic schedule is trivially predictable, establishing that the sensor router's value lies in handling irregular schedules, unexpected detours, and noisy real-world transitions.

---

## 4. Prose Style & Authorship Reconstruction

The manuscript has been systematically purged of formulaic AI writing patterns:

- **Elimination of Boilerplate Transitions:**
  - Zero occurrences of: *Furthermore, Moreover, However, In contrast, It is worth noting, Within these limits, Taken together, This underscores, This demonstrates, The question is, Three quantities matter*.
- **Direct Systems Voice:**
  - Sentences state concrete actions and empirical findings directly (e.g., *"We varied resident slot capacity across $K \in \{1, 2, 3\}$"*, *"Increasing $\Delta$ from 0.00 to the deployed 0.10 reduced loads per day from 65.9 to 31.6"*).
- **Structural Rhythm:**
  - Balanced short declarative statements with detailed technical specifications.
  - Eliminated repetitive "Problem $\rightarrow$ Therefore $\rightarrow$ Solution" structures across subsections.
- **Related Work Reorganization:**
  - Shifted from a paper-by-paper enumeration to a clear four-part taxonomy based on architectural assumptions (request-driven KV reuse, KV compression, on-device runtimes, and ambient proactive preparation).

---

## 5. Typesetting & Artifact Verification

- **LaTeX Engine:** `MiKTeX-pdfTeX 4.23 (MiKTeX 25.12)`.
- **Target Document:** `paper/ieee_main.tex`.
- **Output Document:** `paper/ieee_main.pdf`.
- **Compilation Status:** Return code 0, clean compile, zero undefined references.
- **Page Count:** Exactly 7 pages.
- **Page Layout:** Both columns on Page 7 are completely and evenly filled, with References [1]–[20] concluding at the base of column 2. Zero orphan overflow onto an eighth page.

---

## 6. Action Items for Human Authors Prior to Final Submission

The following items are designated for final author review:

1. **Author Affiliations & Emails:**
   - Verify student email addresses and advisor designations on Page 1 (`\author{...}`).
2. **Clinical Motivation Alignment:**
   - Review Section I (Introduction) to ensure the clinical AAC framing aligns with your intended conference or journal scope.
3. **Acknowledgment Section:**
   - Review Section VIII (Acknowledgment) to confirm institutional compliance regarding AI tool disclosure.
4. **Repository Link:**
   - Confirm that the repository URL (\url{https://github.com/omni-ar/AACBridge}) is public or appropriately anonymized depending on whether the review process is double-blind.
