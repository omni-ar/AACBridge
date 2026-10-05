# Agent A — Benchmark Audit

**Date:** 2026-10-05
**Scope:** Latency benchmark code, LatencyProfiler, JNI timing, raw logs, build configuration

---

## 1. What Is Measured

The benchmark has three modes:
- **ZERO_CONTEXT**: 9 intent tokens only, no context prepended
- **RAG_INLINE**: Context + intent decoded from scratch (50/100/200/500 token tiers)
- **CAP_KVC**: Restore cached 44-token context, decode 9 intent tokens only

Native timing comes from `std::chrono` timestamps around `llama_decode()` in C++. The profiler reads `getLastPrefillMs()` and `getLastGenMs()` via JNI while holding `engineLock`. Wall-clock `total_ms` is also recorded from `System.nanoTime()`.

### Timing Definitions
- **prefill_ms**: Time for `llama_decode()` on prompt tokens (context+intent for inline; intent-only for CAP_KVC)
- **gen_ms**: Time for autoregressive generation loop (up to 64 tokens, greedy)
- **cache_load_ms**: Time for `loadKVCache()` (restore from disk)
- **TTFT**: Not directly measured. Paper estimates as restore + prefill + "one generation step" ≈ 0.43s for CAP_KVC

## 2. Critical Confound: Gaze Interference

**CONFIRMED from new_run.log**: The gaze interface remained active during the 2026-10-04 benchmark run. `resumeInference` calls appear between BENCH trials (e.g., lines 3, 6, 12, 17...) — these are gaze-triggered interaction inferences that held the engine lock.

Evidence from the log:
- Between BENCH trial 6 and 7 for RAG_INLINE@100: a `resumeInference` call at 22:00:37
- Between BENCH trial 7 and 8: another at 22:01:04
- Pattern continues throughout the entire run

**Impact**: The `total_ms` column is contaminated — it includes time waiting for the lock held by gaze inference. The paper correctly states this and reports only natively-timed components (prefill, gen, restore). However, e2e latency cannot be reported.

**Severity**: HIGH — This benchmark needs to be rerun with gaze interaction disabled.

## 3. Trial Ordering

**PROBLEM**: Trials were run sequentially by condition:
- All 30 ZERO_CONTEXT first
- Then 30 RAG_INLINE@50
- Then 30 RAG_INLINE@100
- ...
- Then 30 CAP_KVC

This means thermal effects are confounded with condition. Early conditions (ZERO_CONTEXT, RAG_INLINE@50) ran on a cooler device. Later conditions (RAG_INLINE@500, CAP_KVC) ran on a warmer device.

**Evidence**: Looking at RAG_INLINE@100 trials:
- Trial 6: prefill 3824ms
- Trial 11: prefill 2481ms  
- Trial 14: prefill 2265ms (device warming up, then CPU boosted)
- Trial 25: prefill 5194ms (thermal throttling?)

The variance pattern suggests thermal cycling but cannot be confirmed without CPU frequency/temperature data.

**Required fix**: Randomize/interleave trial order in rerun.

## 4. Build Configuration

The benchmark was run on a **debug build** (no explicit release configuration in the profiler invocation). The `build.gradle` shows `release { minifyEnabled false }` — so release would still not be R8-optimized, but native code optimization flags from CMake may differ.

**Required check**: Verify whether the CMake build type was Debug or Release. Debug native builds include `-O0` and debug symbols, which can slow `llama_decode()` substantially.

## 5. Warm-up

The profiler uses 2 warm-up trials per condition (line 100-102 of LatencyProfiler.kt). This is visible in the log where early trials are discarded (e.g., two RAG_INLINE@500 runs before BENCH,1 starts).

**Assessment**: 2 warmups is minimal. For thermal stabilization, 3-5 would be safer.

## 6. Restore Timing

Cache restore (`loadKVCache`) averaged 2.42ms (SD 2.25, median 1.23ms). This was measured only for the 44-token context. **No scaling data exists** for different context sizes (89, 206, 439 tokens).

## 7. TTFT

**Not directly measured.** The paper estimates: "A rough time to first token (restore + prefill + one generation step) is about 0.43 s for CAP_KVC."

This calculation assumes:
- restore ≈ 2.4ms
- prefill ≈ 351ms  
- first generation step ≈ unknown

The first generation step time is not isolated from total gen_ms. Native code does not timestamp the first token separately.

**Required**: Either implement first-token timing in native code, or clearly label TTFT as estimated.

## 8. Summary of Issues

| Issue | Severity | Status |
|-------|----------|--------|
| Gaze interference contaminated total_ms | HIGH | Acknowledged in paper, but benchmark must be rerun |
| Non-randomized trial order | HIGH | Not acknowledged |
| No thermal/CPU frequency data | MEDIUM | Acknowledged as limitation |
| Debug vs Release build unclear | MEDIUM | Needs verification |
| TTFT only estimated | MEDIUM | Needs native instrumentation or clear labeling |
| Restore scaling unmeasured | MEDIUM | Acknowledged as limitation |
| Only 2 warmup trials | LOW | Adequate but minimal |
