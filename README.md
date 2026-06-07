# AACBridge

**Predictive Amortized KV-Caching for Low-Latency Edge LLMs in Augmentative and Alternative Communication**

This repository contains the full implementation of the AACBridge system. It brings real-time, low-latency contextual Large Language Models to offline edge devices (like Android tablets or smartphones) for users of Augmentative and Alternative Communication (AAC). 

## Documentation
The authoritative state of this repository is documented in the `docs/` directory.

Please read the following documents to understand the architecture, design decisions, and verified implementation status:
- **[Master Context](docs/MASTER_CONTEXT.md)**: The primary entry point. Contains the overall architecture, Phase 2 verified results, and project constraints.
- **[Phase 2 Closure Report](docs/PHASE2_CLOSURE_REPORT.md)**: The authoritative record of integrated systems, verified hardware metrics, and pipeline completion statuses.
- **[Concurrency Model](docs/CONCURRENCY_MODEL.md)**: Explanation of the JVM `engineLock` safeguarding JNI operations from memory mapping crashes.
- **[Cold Start Flow](docs/COLDSTART_FLOW.md)**: Explanation of the daemon background `ContextPrimerImpl` KV cache `prime` -> `saveKVCache` -> `.bin` -> `loadKVCache` orchestration flow.

## Project Structure
- `android/`: The Android application runtime, including MediaPipe gaze tracking, context sweep daemons, and UI orchestration.
- `fusion_model/`: ONNX export and ablation notebooks for combining the 64-dim EMG vector with the 5-dim gaze vector. (Late Fusion architecture).
- `emg_pipeline/`: Data processing and CNN-LSTM intent classification training pipelines targeting the NinaPro DB5 dataset.
- `benchmarks/`: Instrumentation and evaluation harnesses for Phase 3 latency testing.

## Current Status
- Software Pipeline: **COMPLETE** (41/41 Tests Passing)
- Hardware Integration (BLE Armband): **PENDING**
- Benchmarking (Phase 3): **READY TO START**
