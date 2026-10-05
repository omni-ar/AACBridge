# Manuscript Change Log — ieee_main.tex

**Date:** 2026-10-05  
**Branch:** `revision/reviewer-response`  
**Base Commit:** `5ab42fc`  
**Purpose:** Document every change made during the final scientific and authorship revision of the AACBridge IEEE conference paper.

---

## 1. Abstract
- **Old text:** Used inconsistent latency numbers across different paragraphs (e.g. 14.9 s at 439 tokens mixed with older phrasing).
- **Revision:** Fully rewritten in direct, concise systems prose. 
- **Exact verified numbers included:**
  - Prefill scaling: 439 tokens took 14.9 s on OnePlus 11R running Qwen2.5-0.5B-Instruct (Q4_K_M).
  - Matched prefill: 44-token context + 9 intent tokens averaged 366 ms vs. 1847 ms for matched 52-token inline prefill (5.0× reduction; Welch $t(32.3) = 15.5$, $p < 10^{-15}$, $n=30$ interleaved trials).
  - State restore: 2.9 ms mean (median 2.1 ms).
  - Prediction accuracy: 98.5% over 100 synthetic days (6,400 interactions, 5 contexts) vs. 92.2% for request-driven LRU caching; 1.5% wrong-context rate.
  - Multi-seed robustness: mean 98.3%, SD 0.16 pp across 20 seeds.
  - Markov baseline: 99.7% on deterministic trace, explicitly acknowledged.
  - Security vulnerability: Undetected GPS spoofing raises wrong-context rate to 38.9%.
  - Scope: Explicitly scoped to one phone, one model, five contexts, and synthetic traces.

---

## 2. Section I: Introduction
- **CRITICAL DATA CORRECTION (Inconsistency #1 from Audit):**
  - Old Introduction paragraph (line 84 in previous draft) used numbers from an obsolete, non-interleaved benchmark run (`canonical_benchmark.csv`): **351 ms vs. 2507 ms (7.1×), 2.4 ms restore, 14.2 s at 439 tokens, 2.5 s at 52 tokens, ~30 ms/token**.
  - Corrected to the canonical interleaved benchmark (`benchmarks/results/interleaved_20261005_trials.csv`): **366 ms vs. 1847 ms (5.0×), 2.9 ms mean / 2.1 ms median restore, 14.9 s at 439 tokens, 1.8 s at 52 tokens, 34.1 ms/token OLS slope ($R^2 = 0.88$)**.
- **Voice and Structure:**
  - Removed generic formulaic opening.
  - Began with concrete clinical AAC motivation (turn-taking latency constraints and environmental adaptation).
  - Clarified why multi-tenant server KV caching mechanisms (SGLang, vLLM, Prompt Cache, RAGCache) fail on a personal phone: single-user devices lack multi-query concurrency; the first turn in a newly entered context pays full prefill latency.
  - Stated the three contributions crisply without generic academic filler.

---

## 3. Section II: Related Work
- **Structure Overhaul:**
  - Replaced the high-AI-detected catalog format ("SGLang does X, vLLM does Y, Prompt Cache does Z...") with four technical categories:
    1. *Request-driven KV reuse:* Contrasting server-side multi-tenant reactive caching against edge single-user proactive priming.
    2. *KV cache compression:* Explaining why sequence-internal compression (H2O, Scissorhands, SnapKV, StreamingLLM, KVQuant) is orthogonal and complementary to proactive preloading.
    3. *On-device inference runtimes:* Distinguishing token-by-token decode optimizations (quantization, MobileLLM, llama.cpp, MLC-LLM, PowerInfer-2) from prefill amortization.
    4. *Where CAP-KVC differs:* Highlighting the proactive physical-world sensor trigger.
  - Retained and streamlined Table I (Architectural Comparison) with clean column widths.

---

## 4. Section III: System Design
- **Implementation-Outward Narrative:**
  - Clarified data structures: $M=5$ candidate contexts, pre-written 44-token prompt templates, serialized `.kv` dumps in private storage.
  - Mathematical formulation of multi-modal scoring (Eqs. 1–4): circular time distance wrapping at midnight ($\sigma=2$ h), Haversine GPS distance ($\lambda=0.1$ km), weighted BLE beacon matching, and dynamic reliability weights ($w_t=0.4$, $w_g$, $w_b$).
  - Residency policy and hysteresis: $K=3$ active resident slots, sequence slot allocation, score replacement margin $\Delta = 0.10$.
  - Priming and fallback: Atomic disk serialization, slot rollback after inference to keep resident KV clean, Tier-1 canned responses and Tier-2 background priming on cold misses, and bounded semantic blast radius on wrong-context selection.

---

## 5. Section IV: Implementation
- **Technical Rigor and Detail:**
  - Android stack: Kotlin, minSdk 26, targetSdk 34, `arm64-v8a`, llama.cpp via JNI.
  - Verified native boundary: Exactly 14 JNIEXPORT methods in `llama_jni.cpp` matching 14 `external fun` declarations in `LlamaBridge.kt`, serialized by a single `ReentrantLock`.
  - Context layout: Four sequences of 1024 tokens (slots 0–2 resident, slot 3 scratch), reserving 48 MiB at initialization (12 KiB per token for 24 layers, 2 heads, dim 64, fp16).
  - Memory snapshot note: Documented that the recorded 627 MB PSS / 151 MB native heap / 391 MB mmap snapshot was taken prior to the four-sequence slot reconfiguration.
  - Sensor filtering: Android mock location rejection (`isMock()`, `isFromMockProvider()`), 120-s staleness filter, 2–500 m accuracy gating.
  - Security hardening: `allowBackup="false"`, `data_extraction_rules.xml` excluding KV files.

---

## 6. Section V: Evaluation
- **Structured Experiment Order (11 subsections):**
  1. *Setup:* Interleaved randomized protocol (seed 20261005, 30 rounds × 6 conditions), 2 warmup rounds discarded, gaze interface guarded (336 blocked, 0 leaked), thermal/CPU telemetry recorded, debug build noted.
  2. *Matched-Context Latency:*
     - Prefill: CAP-KVC 366 ms (SD 122) vs. Inline matched 52 tokens 1847 ms (SD 510), 5.0× reduction (Welch $t(32.3) = 15.5$, $p < 10^{-15}$).
     - Restore overhead: 2.9 ms mean (median 2.1 ms, IQR [1.1, 4.0]).
     - 439-token prefill: 14.9 s (14,941 ms), ratio 40.8×.
     - OLS slope: 34.1 ms/token, $R^2 = 0.88$ (corrected from old "about 30 ms/token, $R^2=0.87$").
     - TTFT estimate: ~0.37 s CAP-KVC vs. ~1.8 s inline.
     - Generation dominance: 3.9–5.7 s median across conditions.
     - Spearman correlations: position $|\rho| \le 0.13$; thermal $|\rho| \le 0.30$.
     - Thermal telemetry: 65.8–78.0°C (median 69.7°C), cpu7 787–1402 MHz (68% max, 11% throttled), battery temp unreadable (-1.0).
  3. *Prediction and Cache Policy:* Full Table II verified against `prediction_eval.csv` across 6,400 interactions (98.5% all sensors vs. 92.2% LRU vs. 20.7% random vs. single-sensor ablations).
  4. *Hysteresis:* $\Delta = 0.10$ sweep cutting churn from 65.9 to 31.6 loads/day (normal) and 297.5 to 106.2 (high noise) with unchanged 98.5% / 90.6% accuracy.
  5. *Markov Baseline:* Explaining why the deterministic synthetic trace reaches 99.7% with 6.0 loads/day, establishing the predictability ceiling and clarifying the role of sensor fusion for irregular real-world mobility.
  6. *Residency Capacity ($K$) Ablation:* $K=1$ (99.4%, 10.7 loads/d), $K=2$ (98.5%, 39.1 loads/d), $K=3$ (98.5%, 31.6 loads/d).
  7. *Multi-Seed Robustness:* 20 seeds, mean 98.3%, SD 0.16 pp (corrected from 0.15 pp), range 98.03% (seed 8) to 98.58% (seed 4; corrected from seed 19), LRU constant 92.19%, advantage +6.1 pp.
  8. *Schedule Variation:* $\pm 1.0$ h (97.7%), $\pm 2.0$ h (95.6%, wrong context 4.4%), variable dwell (98.3%), high jitter (95.9%).
  9. *Abstention and Risk-Coverage:* $\tau=0.05$ yielding 97.1% correct, 0.8% wrong context, 2.1% abstention; $\tau=0.10$ yielding 94.6% correct, 0.4% wrong context, 4.9% abstention.
  10. *Security Experiments:* GPS spoofing (38.9% wrong context without mock check vs. 1.7% with Android mock rejection); cloned BLE (9.9% wrong context).
  11. *Resource Limitations:* Explicitly marking physical battery/power consumption and arbitrary cache restore scaling as NOT MEASURED.

---

## 7. Section VI: Threat Model
- Explicitly defined assets: context selection integrity and KV cache confidentiality.
- Formally characterized three attacker profiles (software mock location, physical BLE cloning, storage extraction).
- Detailed defensive boundaries and residual risks.

---

## 8. Section VII: Limitations and Conclusion
- Retained and numbered all fundamental limitations: single device, single model, five contexts, synthetic traces, deterministic schedule, unmeasured hardware energy, unmodeled runtime priming contention, and lack of clinical trials.
- Summarized core empirical findings without overclaiming.

---

## 9. Section VIII: Acknowledgment
- Transparent and accurate disclosure of Claude (Anthropic) for text drafting/editing assistance and code collaboration, and Google Antigravity for Android/JNI implementation assistance.
- Affirmed that research questions, system design decisions, experiments, and interpretations are the original work of the authors.

---

## 10. Typesetting & Layout
- Replaced block `itemize` / `enumerate` environments with clean inline lists, eliminating underfull hboxes and excessive vertical whitespace.
- Fitted figures (`prefill_scaling.pdf` and `hysteresis_churn.pdf`) to $0.88\columnwidth$, preventing float deferrals and badness 10000 underfull vboxes.
- The compiled document spans exactly 7 pages with balanced columns on the final page.
