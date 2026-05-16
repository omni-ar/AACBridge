# AACBridge Device Environment Specification

This document details the exact target hardware and runtime environment constraints for the AACBridge system benchmarking and deployment.

## Hardware Specifications
- **Device:** OnePlus 11R 5G
- **SoC:** Snapdragon 8 Gen 1 (taro)
- **Architecture (ABI):** arm64-v8a

## Software Environment
- **Android API Level:** 36
- **Runtime Model:** On-device offline inference

## Engineering Constraints & Deployment Strategy

- **ARM64-Only Deployment Strategy:** The system is exclusively compiled and deployed for the `arm64-v8a` ABI. This strict targeting ensures that all tensor math leverages the Snapdragon hardware's native NEON SIMD instructions, which is critical for meeting the sub-500ms TTFT (Time to First Token) latency requirements. Fallback paths for 32-bit ARM or x86 emulators are intentionally excluded.
- **Offline Execution Requirement:** The AACBridge architecture mandates 100% offline execution to ensure privacy and constant availability for non-verbal users. No API calls or cloud processing are permitted in the inference loop or intent classification pipelines.
- **Benchmark Environment Consistency:** All latency (TTFT), memory usage, and thermal threshold benchmarks must be run on this exact device specification. Variations in operating system background tasks or thermal throttling on different hardware will invalidate the baseline comparison metrics between standard RAG and the KV Cache Priming implementations.
