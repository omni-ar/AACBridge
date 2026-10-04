# AACBridge Paper 262 — Reviewer Response Matrix

**Status Key:** ✅ Addressed | 🔧 In Progress | ⏳ Pending | ❌ Cannot Address | 📝 Future Work

---

## Reviewer #1

| ID | Comment | Interpretation | Code / Artifact Implementation | Experimental Evidence | Paper Changes | Status |
|---|---|---|---|---|---|:---:|
| **R1-A** | Insufficient comparison vs RAGCache, SGLang | Position CAP-KVC relative to server-side reactive cache reuse | Cited and contrasted RAGCache (SOSP '24), SGLang (NeurIPS '24), vLLM (SOSP '23) | Analytical comparison of reactive caching (first query suffers full $O(N)$ prefill penalty) vs predictive priming (zero prefill penalty on turn 1) | §II-A & §II-D: Added dedicated discussion contrasting reactive query matching vs ambient predictive edge priming | ✅ |
| **R1-B** | Intro moves too quickly into technical details | Need conceptual framing first | Restructured narrative to lead with AAC clinical motivation and turn-taking latency constraints | None (conceptual framing) | §I Introduction: Fully rewritten with motivation-first structure highlighting communication rate and conversational turn-taking | ✅ |
| **R1-C** | Novelty needs clearer positioning | Sensor-driven prediction is the novel contribution, not KV caching per se | Replaced generic caching claims with sensor-driven physical-world context prediction | 100-episode trace evaluation isolating sensor routing contribution | §I Contributions: Reframed Contribution 1 around proactive physical-world sensor prediction decoupling prefill from query | ✅ |
| **R1-D** | How does CAP-KVC differ from cache reuse? | Predictive preloading vs reactive caching | Orchestration via `ActiveSweep`, `StateRouter`, `KVCacheManager` | Trace evaluation demonstrating pre-computed cache residency before query arrival | §I, §II-A, §III-A: Formalized distinction between reactive cache reuse (request-driven) and CAP-KVC (ambient idle-period pre-computation) | ✅ |

---

## Reviewer #3

| ID | Comment | Interpretation | Code / Artifact Implementation | Experimental Evidence | Paper Changes | Status |
|---|---|---|---|---|---|:---:|
| **R3-A** | Prediction layer not evaluated | Need quantitative evaluation of router prediction quality | `PredictionEvaluator.kt`, `PredictionEvaluationTest.kt` | 100 simulated daily traces (6,400 user interactions); Hit@1 = 98.77%, Hit@3 = 100.00% | §V-B & §VI-B: Added formal prediction methodology, Table II, and routing ablation | ✅ |
| **R3-B** | No hit-rate measurement | Quantitative Hit@1 and Hit@3 measurements required | Evaluated across 8 test configurations in `PredictionEvaluationTest.kt` | Hit@1: 98.77% (Full Fusion), 98.14% (BLE-only), 95.97% (GPS-only), 92.39% (Time-only), 19.44% (Random) | §VI-B, Table II: Full hit-rate breakdown across configurations | ✅ |
| **R3-C** | No cold-miss measurement | Quantify cold-miss rate under controlled traces | Evaluator tracks interactions where ground truth state is absent from resident cache slots | Cold miss rate = 0.00% under normal fusion; 0.05% under GPS spoofed attack; 40.23% under random guessing | §VI-B, Table II: Explicit cold-miss rate column and analysis | ✅ |
| **R3-D** | No realistic sensor trace evaluation | Multi-modal traces with independent noise models required | `TraceGenerator` with independent Gaussian spatial noise ($\sigma=50$m/200m), dropout ($10\%$--$70\%$), time jitter ($\pm 0.5$h/$\pm 1.5$h), BLE RSSI and dropouts | 100 episodes under Normal, High Noise Stress, Stale GPS, and Adversarial Spoofing | §V-B: Comprehensive noise model specification; §VI-B: Robustness results | ✅ |
| **R3-E** | Latency evaluation assumes cache hit | Account for miss penalty in user-perceived latency | Evaluated fallback penalty $T_{\text{miss}} = 2021.46$~ms (prefill) and $6547.13$~ms (e2e) | Expected latency $E[T] = h \cdot T_{\text{hit}} + (1-h) \cdot T_{\text{miss}}$; $E[T_{\text{prefill}}] = 363.99$~ms under full fusion, $522.40$~ms under high noise | §VI-A: Added formal expected latency derivation (Eq. 4) and quantitative discussion | ✅ |
| **R3-F** | No adequate non-predictive baseline | Need random and single-modality baselines | Implemented Uniform Random baseline and single-modal ablations in `PredictionEvaluationTest.kt` | Random baseline (19.44% Hit@1, 40.23% cold miss); GPS-only, Time-only, and BLE-only single-sensor ablations | §VI-B, Table II: Direct comparison of Full Fusion vs Random and Single-Modality baselines | ✅ |
| **R3-G** | Context-length mismatch in headline | Comparing RAG@500 vs CAP-KVC@50 is unfair headline comparison | Matched-context benchmark analysis in `analyze_benchmarks.py` | At matched $\approx 50$ tokens: CAP-KVC achieves 5.89$\times$ prefill speedup (343.35 ms vs 2021.46 ms); e2e is 1.43$\times$ (4574.70 ms vs 6547.13 ms) | Abstract, §I, Table I, and §VI-A: Prominently lead with matched-context latency comparison alongside length scaling | ✅ |
| **R3-H** | CAP_KVC vs RAG@50 not significant | Honest reporting of statistical significance required | Welch's two-sample $t$-test calculations in `analyze_benchmarks.py` | Prefill difference is statistically significant ($t(29.0) = -2.71, p = 0.011, d = 0.70$); e2e difference is not significant ($t = 1.28, p = 0.209$) due to generation variance | §VI-A: Explicitly report non-significance of e2e difference at $N \approx 50$ and explain generation-phase masking | ✅ |
| **R3-I** | Battery/power not characterized | Measure or characterize sensing and daemon power overhead | Characterized sensing duty cycle (3-second BLE scan, cached GPS fix, BLE-blind 15-min periodic polling) | WorkManager Doze-mode alignment and low-memory daemon safeguards | §IV-F & §VII (Limitations): Characterized background sensing overhead and power implications | ✅ |
| **R3-J** | Single-device evaluation | Acknowledged limitation of evaluating solely on OnePlus 11R | Documented Snapdragon 8+ Gen 1 hardware environment | Benchmarked 180 trials on OnePlus 11R | Abstract & §VII Limitations (Item 1): Explicitly emphasized single-device limitation and call for cross-chipset validation | ✅ |
| **R3-K** | Drift/hysteresis evaluation disconnected | Evaluate deployed `DriftDetector` sensor-score hysteresis mechanism | Evaluated deployed hysteresis margin mechanism in `PredictionEvaluationTest.kt` | Hysteresis sweep across $\Delta \in \{0.00, 0.05, 0.10, 0.15, 0.20\}$ under high noise: 2.76 switches/day, 99.75% residency hit rate, 0.25% cold miss | §VI-D: Added dedicated deployed sensor-score hysteresis sweep results demonstrating thrashing suppression | ✅ |

---

## Reviewer #4

| ID | Comment | Interpretation | Code / Artifact Implementation | Experimental Evidence | Paper Changes | Status |
|---|---|---|---|---|---|:---:|
| **R4-A** | GPS spoofing / mock-location | Implement mock location detection and validation | `LocationValidator.kt`: `isMock()` (API 31+) and `isFromMockProvider()` rejection | Tested under mock location attack scenarios | §IV-F & §IV-D Threat Model: Documented mock detection filter and verified rejection behavior | ✅ |
| **R4-B** | BLE beacon impersonation | Analyze vulnerability to cloned beacons | Multi-sensor agreement required in `StateRouter.kt` | GPS spoofed trace evaluation (Hit@3 remains 99.95% via multi-sensor consensus) | §IV-D Threat Model: Formalized BLE impersonation attack vector and consensus mitigation | ✅ |
| **R4-C** | Falsified sensor readings | Sanity-check sensor inputs against physical limits | `LocationValidator.kt`: accuracy sanity check ($2.0\text{m} \le a \le 500.0\text{m}$) and staleness check ($\le 120$s) | Evaluated under high noise stress ($\tau = 1.5$h, $200$m noise) | §IV-F & §IV-D Threat Model: Detailed plausible accuracy gating and staleness thresholds | ✅ |
| **R4-D** | Unencrypted KV cache | Evaluate encryption feasibility and residual risks | Documented app sandbox isolation in internal storage | Benchmarked 2.74 ms raw deserialization | §IV-D & §VII Limitations: Acknowledged unencrypted cache as residual risk on rooted devices; detailed Keystore encryption as future work | 📝 |
| **R4-E** | Semantic mis-selection | Analyze risk of incorrect context selection | Bounded blast radius: router selects only from 5 clinically vetted contexts | Trace evaluation: wrong context rate is only 1.23% under full fusion (10.67% under extreme noise) | §IV-D Threat Model: Formalized bounded semantic blast radius (user never exposed to unconstrained toxic generation) | ✅ |
| **R4-F** | $k=3$ residency justification | Justify 5-context set and 3-slot active cache budget | `HardwareConfig.kt`: `MAX_ACTIVE_KV_STATES = 3`; `SeededStateRepository.kt`: 5 semantic states | Memory profiling: 3 slots consume 118.7 MB native heap (~39.6 MB/slot), leaving ample headroom in 192--256 MB Android process budget | §III-E & §IV: Provided concrete memory budget rationale and 60% simultaneous context coverage justification | ✅ |
| **R4-G** | Battery/thermal overhead | Characterize energy overhead of sensing and priming | Documented background daemon execution frequencies | WorkManager 15-minute periodic schedule with BLE-blind snapshot | §IV-F & §VII Limitations: Detailed duty cycle and power trade-offs | ✅ |
| **R4-H** | Need explicit threat model | Add dedicated Threat Model section | Implemented `LocationValidator.kt` defenses | Evaluated under high-noise and GPS-spoofed traces | §IV-D: Added dedicated "Threat Model and Attack Surface Boundary" section | ✅ |
| **R4-I** | Clearer attack surface boundary | Document what system CAN vs CANNOT defend against | Implemented defensive boundaries in `LocationValidator.kt` and `StateRouter.kt` | Verified mitigation of non-root spoofing and bounded blast radius | §IV-D: Added explicit itemized enumeration of CAN vs CANNOT defense boundaries | ✅ |

---

## Summary

| Reviewer | Total Comments | Addressed (✅) | In Progress (🔧) | Pending (⏳) | Future Work (📝) |
|---|:---:|:---:|:---:|:---:|:---:|
| **Reviewer #1** | 4 | 4 | 0 | 0 | 0 |
| **Reviewer #3** | 11 | 11 | 0 | 0 | 0 |
| **Reviewer #4** | 9 | 8 | 0 | 0 | 1 |
| **Total** | **24** | **23** | **0** | **0** | **1** |
