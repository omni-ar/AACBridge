# CAP-KVC

**Context-Aware Predictive KV-Cache Priming for edge LLM
inference on Android.**

An LLM-backed augmentative and alternative communication
(AAC) prototype that moves context prefill off the
interactive path. Environmental sensors (GPS, BLE
proximity, time of day) select which semantic context to
pre-compute; the resulting KV cache tensors are
serialized to flash during idle periods and restored at
interaction time, so only the user's short intent tokens
are prefilled.

Runs fully offline on a single device. No network calls.

---

## Status

| | |
|---|---|
| Build | `./gradlew assembleDebug` — <!-- UPDATE after first green build --> unverified since sequence-slot rework |
| Unit tests | 41 tests across 11 suites |
| Target | `arm64-v8a`, Android 8.0+ (API 26) |
| Benchmark device | OnePlus 11R (Snapdragon 8+ Gen 1, 8 GB LPDDR5X) |
| Model | Qwen2.5-0.5B-Instruct, Q4\_K\_M GGUF |

**This is a research prototype.** It has not been used by
AAC users, has no clinical validation, and stores KV
cache files unencrypted. Do not deploy it.

---

## Results

Measured on a OnePlus 11R, n = 30 trials per condition,
airplane mode, greedy decoding, max 64 output tokens.
Native-side `std::chrono` instrumentation separates the
prefill forward pass from the generation loop.

| Condition | E2E (ms) | Prefill (ms) | Gen (ms) | Tokens |
|---|---:|---:|---:|---:|
| ZERO_CONTEXT | 3823.78 | 274.79 | 3544.58 | 9 |
| RAG @ N≈50 | 6547.13 | 2021.46 | 4508.53 | 52 |
| RAG @ N≈100 | 7900.92 | 3015.09 | 4877.95 | 89 |
| RAG @ N≈200 | 12122.30 | 6673.53 | 5439.96 | 206 |
| RAG @ N≈500 | 19408.89 | 14001.46 | 5390.76 | 439 |
| CAP_KVC (cache load) | 2.74 | — | — | — |
| CAP_KVC (total) | 4574.70 | 343.35 | 4226.74 | 9 |

- **40.78×** prefill reduction at N≈500 (343 ms vs 14001 ms)
- **4.24×** end-to-end reduction (4574 ms vs 19408 ms)
- **2.74 ms** cache restoration (σ = 1.35)

### What these numbers do and don't show

They measure **cache restoration versus repeated
prefill**. They do not measure whether the sensor router
picks the right context — there is no cache hit rate, no
routing accuracy against ground truth, and no
static-cache baseline that would separate the value of
*prediction* from the value of *caching*. Treat the
speedups as a property of KV state persistence, not
evidence that sensor-driven prediction works.

The comparison is also asymmetric: CAP_KVC caches were
primed from ~50-token prompts, so the 40.78× partly
reflects a 9-vs-439 token count difference rather than
the caching mechanism alone.

**Memory numbers are currently stale.** The previously
reported 118.7 MB delta was measured before the
sequence-slot fix, when `n_seq_max` defaulted to 1 and
only one slot was addressable. Needs re-measuring.

---

## How it works

```
GPS ─┐
BLE ─┼─→ SensorSnapshot ─→ StateRouter ─→ top-k contexts
Time ┘                      (convex scoring)      │
                                                  ▼
                          ContextPrimer: prefillOnly() → saveKVCache()
                                     → atomic rename → {stateId}.bin
                                                  │
User intent ─────────────────────────────────────┼─→ loadKVCache(slot)
                                                  └─→ resumeInference(intent, slot)
                                                             │
                                                             ▼
                                                     generated response → TTS
```

**Scoring.** `S(c) = α·S_time + β·S_gps + γ·S_ble`, with
α+β+γ = 1. Weights come from runtime sensor reliability:
time holds a fixed baseline of 0.4 (guaranteeing a
positive denominator when GPS and BLE both fail), GPS
decays as `exp(−accuracy/50)`, BLE from mean
sigmoid-transformed RSSI centred at −70 dBm. Temporal
similarity is a Gaussian over circular 24-hour distance
(σ = 2.0 h); spatial is `exp(−d_haversine/λ)` with
λ = 0.1 km, so 100 m scores 0.37.

**Residency.** Three native sequence slots, LRU eviction,
reference-counted so a state under active inference can't
be evicted mid-call. Invariant: `activeStates.size +
availableSeqIds.size == 3`.

**Drift.** A WorkManager job re-scores every 15 minutes
against a BLE-blind snapshot. A swap fires only when a
candidate beats the weakest resident by Δ ≥ 0.10.

---

## Architecture

**JNI boundary.** Ten native functions, all arguments and
returns are JNI scalars (`jstring`, `jint`, `jboolean`,
`jdouble`). No Java objects, arrays, or callbacks cross —
this eliminates GC root pinning and avoids copying
multi-megabyte tensors across the boundary. KV data stays
native-side; the JVM orchestrates via file paths and
integer slot IDs.

| Group | Functions |
|---|---|
| Lifecycle | `initializeBackend`, `initializeModel`, `release` |
| KV cache | `saveKVCache`, `loadKVCache`, `clearKVCache`, `resetSlot` |
| Inference | `runInference`, `prefillOnly`, `resumeInference` |
| Telemetry | `getLastPrefillMs`, `getLastGenMs`, `getLastPromptTokens`, `getLastGenTokens` |

`runInference` clears the slot and decodes from position
zero. `resumeInference` preserves restored history and
appends at `n_past` without a BOS token. Confusing them
produces position-ID collisions that corrupt attention.

**Concurrency.** A single `ReentrantLock` (`engineLock`)
serializes all JNI sequences touching native state. It is
held across `loadKVCache` → `resumeInference` in one
acquisition, and across `prefillOnly` → `saveKVCache`
during priming — releasing between would let another
thread mutate slot history. Per-state coroutine mutexes
protect residency metadata separately.

**Packages** (`com.aacbridge`):

```
inference/  LlamaBridge, LlamaBridgeAdapter, LatencyProfiler
cache/      KVCacheManager, ContextPrimerImpl, CacheState, SeededStateRepository
router/     StateRouter, TimeScorer, GPSScorer, BLEScorer
daemon/     ContextDaemon, DriftDetector, ActiveSweep, BootReceiver
gaze/       GazeTracker, CalibrationManager, DwellOverlayView
fusion/     FusionInference
fallback/   FallbackRepository, FallbackRouter
```

---

## Build

Requires Android Studio with **NDK 27.2.12479018** and
**CMake 3.22.1** (SDK Manager → SDK Tools → show package
details).

```bash
cd android
./gradlew clean assembleDebug testDebugUnitTest
```

`clean` matters — CMake caches object files and will
happily link a stale `llama_jni.o` against new Kotlin
signatures, producing an `UnsatisfiedLinkError` at
runtime rather than a build error.

### Model deployment

The GGUF is not bundled. Push it once:

```bash
adb shell mkdir -p /data/local/tmp/models
adb push qwen2.5-0.5b-instruct-q4_k_m.gguf /data/local/tmp/models/
```

If the file is absent, `initializeModel` returns false
and the app runs fallback-only.

### Verifying a build

```bash
adb logcat -s AACBridgeJNI:V AACBridge:V GazeTracker:V
```

1. `Model initialized: n_ctx=6144 n_seq_max=3 (2048 per slot)`
   — if it reads 2048, the native rebuild didn't happen.
2. `KV cache loaded: slot=1` and `slot=2` — multi-slot
   residency working.
3. Tap a UI intent; the response must differ from the
   canned strings in `InMemoryFallbackRepository`.

### Gaze calibration

Gaze requires a one-time neutral baseline. The app
auto-calibrates on first launch (look at screen centre
for ~2 s) and exposes a **Recalibrate** button.

Nose-to-eye displacement is divided by interocular
distance and offset by the captured baseline, so
thresholds are ratios of face width, not raw image
coordinates. If looking top-left prints a *positive*
`gx` in logcat, flip `MIRROR_X` in `GazeTracker.kt`.

---

## Known issues

- **Generation is slow.** ~66 ms/token for a 0.5B Q4_K_M
  on an 8+ Gen 1 is well below what the hardware should
  do. `n_threads=4` on a 1+3+4 core layout is untuned.
  Generation dominates end-to-end latency and masks the
  prefill win.
- **Fusion model distribution mismatch.** The bundled
  `gaze_emg_fusion.onnx` was trained on raw-delta gaze
  features. It now receives baseline-corrected,
  scale-normalized values. It won't crash; its output is
  meaningless until retrained.
- **EMG is mock.** `FloatArray(64)` placeholder pending
  physical armband integration.
- **Cross-subject EMG accuracy is 42.4%** (LOSO, NinaPro
  DB5), with the *reject* class at 0% recall. NinaPro DB5
  is hand/wrist sEMG, not facial.
- **Fusion evaluation is synthetic.** 2000 randomly
  paired EMG–gaze samples. The 0.9963 macro F1 measures
  architectural capacity, not classification ability. No
  gaze-only baseline was run.
- **Cache files are unencrypted.** They encode the
  semantic content of context prompts and rely on the
  Android sandbox alone.
- **Static contexts.** Five hardcoded states in
  `SeededStateRepository`. `StateRepository` is an
  interface, so a Room-backed implementation is a
  one-line swap in `AppContainer`.

### Fixed

- **Sequence-slot aliasing.** Session token history was a
  single C++ global and batches were built with
  `llama_batch_get_one()`, which leaves positions
  unassigned and defaults to sequence 0 — so every
  resume landed on slot 0 regardless of the slot the
  cache manager allocated. Compounding it,
  `n_seq_max` was never set and defaults to 1, so
  restores into slots 1 and 2 failed outright and were
  silently returned to the pool. The manager's
  bookkeeping invariant held the whole time while the
  resource it described did not exist. Now: explicit
  `n_seq_max`, per-slot token history, and explicit
  positions and sequence IDs on every batch.
- **UI never reached the LLM.** `handleIntent` returned
  canned strings from `FallbackRouter`; `resumeInference`
  ran only inside `LatencyProfiler`. Now wired, on
  `Dispatchers.IO`, with refcount release in `finally`.
- **Gaze stuck on one target.** Raw `noseTip.y −
  eyeCenter.y` is always positive (nose sits below the
  eyes; handheld pitch pushes it further), so
  `deltaY < −0.01` was unsatisfiable and confirm/reject
  could never fire. Fixed with baseline calibration and
  scale normalization. `call-help` also no longer sits in
  the `else` branch — ambiguous reads now emit nothing
  rather than a spurious request for assistance.
- **`release()` missing `extern "C"`**, so the symbol was
  C++ name-mangled and unresolvable at runtime.

---

## Reproducing the benchmarks

`LatencyProfiler.runBenchmarkSuite()` runs on launch,
executes six conditions × 32 trials (2 warmup discarded),
and emits structured CSV via logcat. Takes ~30 minutes.

```bash
adb logcat -d -s LatencyProfiler > run.log
python regenerate_canonical.py    # → canonical_benchmark.csv
python final_stats.py             # means, Welch t-tests, Cohen's d
python verify_stats.py            # verification report
```

The extraction script matches native timing entries to
Kotlin trial records by strict proximity and aborts on
any misalignment, guarding against logcat ring-buffer
drops.

---

## Citation

Preprint in preparation. Until then:

```bibtex
@misc{aacbridge2026,
  title  = {Predictive KV Cache Amortization for Edge
            Language Model Inference on Mobile Devices},
  author = {Tripathi, Arjit and Shah, Heer and
            Sriram, Medha and Shalini, L},
  year   = {2026},
  note   = {Vellore Institute of Technology},
  url    = {https://github.com/omni-ar/AACBridge}
}
```

## License

<!-- ADD ONE. Note that llama.cpp is MIT and
     Qwen2.5-0.5B-Instruct is Apache 2.0. -->