# Memory Budget & Constraints

This document outlines the memory limits, allocations, and constraints for the AACBridge system running on edge devices (Snapdragon 8-series).

## Verified Values

The following values have been physically measured and verified on-device during Phase 1 and 2 testing:

- **GGUF Model Size:** ~2.2GB (`qwen2.5-0.5b-instruct-q4_k_m.gguf` Q4_K_M quantization).
- **KV Cache Resident States:** Exactly 3 states are maintained in active RAM via `KVCacheManager` using a strict LRU eviction policy.
- **KV Cache File Size:** MB-scale `.bin` files per state generated on the Android filesystem.
- **Drift Detector Model:** ~22MB (all-MiniLM-L6-v2, INT8 quantized via ONNX Runtime).
- **EMG Intent Model:** 
  - Float32 TFLite: 1.26MB (`emg_classifier.tflite`)
  - INT8 TFLite: 385KB (`emg_classifier_int8.tflite`)
- **Fusion Model:** 45KB (`gaze_emg_fusion.onnx`).

## Estimated / Future Values

The following values are estimated based on architectural constraints or are pending Phase 3 empirical benchmarking:

- **Total RAM Target Budget:** ~2GB (Android OS limit for background services to prevent silent OS kills). *Pending benchmarking.*
- **Per-state KV Cache Memory Footprint (RAM):** *Pending benchmarking.*
- **Maximum Token Cap per State:** 500 tokens.
- **Drift Detector Efficiency Core CPU Overhead:** Target <5%. *Pending benchmarking.*
- **TTFT (Time To First Token) Reduction:** Target <500ms. *Pending benchmarking.*
