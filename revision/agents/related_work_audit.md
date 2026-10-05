# Agent C — Literature / Novelty Audit

**Date:** 2026-10-05
**Scope:** Related work positioning, novelty claims, inherited vs original contributions

---

## 1. What CAP-KVC Actually Does

Stripped to its core:
1. Pre-writes 5 context prompts (hardcoded)
2. Runs a sensor sweep every 60s (GPS, BLE, clock)
3. Scores each context via weighted combination of time/GPS/BLE similarity
4. Keeps top-3 scored contexts as resident KV states (llama.cpp sequence slots)
5. At interaction time, restores the best resident state and decodes only the intent
6. Rolls back the slot after generation so the state can be reused

## 2. What Is Inherited (Not Novel)

### From llama.cpp:
- **KV state save/load**: `llama_state_save_seq()` / `llama_state_load_seq()` — these are existing llama.cpp APIs
- **Sequence slots**: llama.cpp's multi-sequence context management
- **Inference engine**: All token processing, attention, generation
- **GGUF model loading**: Standard model format support

### From standard caching/systems:
- **LRU-style replacement**: Evicting the weakest entry when the cache is full
- **Hysteresis/margin**: Standard technique to prevent cache thrashing
- **Atomic file rename**: Standard crash-safety technique on POSIX/ext4

### From sensor fusion literature:
- **Weighted sensor combination**: Linear combination of sensor scores is standard
- **GPS distance decay**: Exponential decay with Haversine distance is textbook
- **BLE proximity detection**: Standard presence detection
- **Time-of-day Gaussian**: Standard temporal modeling

## 3. What Is Actually Novel

The novelty is **not** in any individual component but in the **combination and application**:

1. **Predictive (proactive) KV cache population**: Using sensor evidence to decide which context to prepare BEFORE a request arrives. This is the key distinction from all cited server systems (SGLang, vLLM, RAGCache, etc.) which populate caches reactively.

2. **Single-user edge operating point**: All prior KV cache reuse work targets multi-user serving. CAP-KVC operates on a single device with a single user.

3. **Sensor-driven context selection for LLM inference**: Using GPS, BLE, and time-of-day to predict which LLM context description the user will need. This specific application is not covered by prior work.

4. **Bounded residency with fallback**: The combination of K=3 slots, hysteresis-gated replacement, two-tier fallback, and rollback for state reuse as an integrated policy.

## 4. Prior Work Positioning

### Server-Side KV Reuse
| System | Mechanism | CAP-KVC Distinction |
|--------|-----------|-------------------|
| SGLang [radix tree] | Shares prompt prefixes across requests | Reactive; multi-user; GPU server |
| vLLM/PagedAttention | Shares KV blocks via paging | Reactive; multi-user; GPU server |
| Prompt Cache | Precomputed attention for reusable modules | Closer to CAP-KVC in spirit — precomputes attention states. But operates server-side with schema-defined modules |
| RAGCache | Caches KV for retrieved documents, retrieval-aware eviction | Most relevant server-side comparison. Still reactive — caches on first retrieval |
| CacheBlend | Fuses cached KV chunks for non-prefix reuse | Server-side; addresses different token ordering |
| CacheGen | Compresses KV for network transfer between servers | Server-side; focuses on transfer cost |

**Key distinction**: All server systems rely on request overlap from multiple users to amortize cache fills. A single phone has no request stream to overlap with.

### On-Device LLM
| System | Focus | CAP-KVC Distinction |
|--------|-------|-------------------|
| MobileLLM | Small model architecture | Reduces per-token cost; doesn't address context prefill |
| LLM in a Flash | Flash/DRAM offloading | Memory management; doesn't address KV state reuse |
| PowerInfer-2 | Heterogeneous NPU/CPU | Hardware utilization; doesn't address KV caching |

### Predictive/Preemptive Preparation
No direct prior work on **sensor-driven predictive KV cache preparation for on-device LLM inference** was found. Adjacent areas:
- **Predictive app prelaunch** (Android): OS predicts which app to preload. Similar in spirit.
- **Predictive prefetching** (web/mobile): Content prefetched based on usage patterns. Similar in spirit.

## 5. Overclaiming Risks

The paper should **NOT** claim:
- ❌ "Novel KV caching mechanism" — KV save/restore is llama.cpp functionality
- ❌ "Server systems do not apply" — too strong; they address different operating points
- ❌ "Eliminates prefill" — it moves context prefill to idle time; intent prefill remains
- ❌ "Zero prefill" — misleading; the intent tokens still require prefill

The paper **CAN** claim:
- ✅ "Sensor-driven predictive KV cache preparation for single-user edge inference"
- ✅ "Moves context-specific prefill from interaction time to idle time"  
- ✅ "First study combining environmental sensing with KV state management on a mobile device"
- ✅ "Evaluates prediction quality, latency benefit, and adversarial robustness in an integrated system"

## 6. Missing Citations to Consider

- **Prompt Cache** (Gim et al., MLSys 2024): Already cited. Most conceptually related server-side work.
- **llama.cpp state save/load**: The actual implementation foundation. Should be explicitly acknowledged.
- **Predictive app prelaunch**: Could strengthen the "predictive preparation" framing.

## 7. Recommended Related Work Structure

1. **KV reuse in LLM serving** (~150 words): SGLang, vLLM, Prompt Cache, RAGCache, CacheBlend, CacheGen — group by mechanism, state distinction from reactive/multi-user
2. **KV compression** (~80 words): H2O, Scissorhands, SnapKV, StreamingLLM, KVQuant — orthogonal
3. **On-device inference** (~80 words): quantization, MobileLLM, runtimes, offloading — reduce per-token cost but don't address context prefill
4. **CAP-KVC distinction** (~100 words): predictive vs reactive, single-user, sensor-driven, bounded residency

Total: ~410 words (currently ~500 words in paper — acceptable)
