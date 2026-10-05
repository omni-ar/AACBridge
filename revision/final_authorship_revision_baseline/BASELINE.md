# Phase 0 — Final Authorship Revision Baseline

**Created:** 2026-10-05T19:38+05:30
**Purpose:** Immutable snapshot before final scientific + authorship revision

## Repository State

| Property | Value |
|----------|-------|
| Branch | `revision/reviewer-response` |
| Commit | `862b20373bfe8140622b1400034471ce94457bf7` |
| Working tree | Clean (1 untracked: Pangram report PDF) |

## Build Environment

| Property | Value |
|----------|-------|
| Java/Kotlin | JVM target 17 (Kotlin Android) |
| Gradle/AGP | com.android.application plugin |
| compileSdk | 34 |
| minSdk | 26 |
| targetSdk | 34 |
| NDK version | 27.2.12479018 (r27c) |
| ABI | arm64-v8a |
| CMake | 3.22.1 |
| C++ standard | C++17 |
| llama.cpp | Built from source via CMake (submodule in src/main/cpp/) |
| Model | Qwen2.5-0.5B-Instruct, GGUF, Q4_K_M |
| Device | OnePlus 11R |
| Android version | 14 |
| CPU | Snapdragon 8+ Gen 1 |
| RAM | 8 GB |
| Thread count | 4 |
| Batch size | 512 |
| Ubatch size | Not explicitly set (llama.cpp default) |
| Build type | Debug (current benchmark run) |
| Compiler flags | `-std=c++17` |

## Manuscript State

| Property | Value |
|----------|-------|
| Canonical source | `paper/ieee_main.tex` |
| TEX checksum (SHA-256) | `1BDE0173E8B1040DB5FEEBBB2E24A4B50A094CD8CF8EA3FFCA72C4F8B5576B85` |
| PDF checksum (SHA-256) | `F6432A3DBDA984BFEA358B8AC119BF2055DF7D8A883B09EA60C91D05C214A972` |
| Lines | 463 |
| Words (approx) | 5,408 |
| Page count | ~10 (IEEE two-column) |

## JNI Method Count (Verified from LlamaBridge.kt)

**14 native methods total:**

Core lifecycle/inference (10):
1. `initializeBackend()`
2. `initializeModel(modelPath: String): Boolean`
3. `saveKVCache(filepath: String, seqId: Int): Boolean`
4. `loadKVCache(filepath: String, seqId: Int): Boolean`
5. `clearKVCache()`
6. `runInference(prompt: String): String`
7. `prefillOnly(prompt: String, seqId: Int): Boolean`
8. `resumeInference(prompt: String, seqId: Int): String`
9. `resetSlot(seqId: Int)`
10. `release()`

Read-only telemetry (4):
11. `getLastPrefillMs(): Double`
12. `getLastGenMs(): Double`
13. `getLastPromptTokens(): Int`
14. `getLastGenTokens(): Int`

**Note:** Paper currently says "14 JNI functions" (line 241) but current_ground_truth.md says "13 JNI methods (9 core + 4 telemetry)". Actual count from source: 14 (10 core + 4 telemetry). The discrepancy is that `resetSlot` was added later.

## Pangram AI Report (Latest)

- Total words scanned: ~5,423
- AI-generated: 64%
- Human-written: 36%
- Heavily flagged sections: abstract/positioning, related work, system design, discussion/conclusion

## Snapshot Files

- `ieee_main.tex.snapshot` — verbatim copy of manuscript
- `references.bib.snapshot` — verbatim copy of bibliography
- **DO NOT OVERWRITE THESE FILES**
