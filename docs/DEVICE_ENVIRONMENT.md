# AACBridge Device Environment Specification

This document details the exact target hardware and runtime environment constraints for the AACBridge system benchmarking and deployment.

## Hardware Specifications
- **Device:** OnePlus 11R 5G
- **SoC:** Snapdragon 8 Gen 1 (taro)
- **Architecture (ABI):** arm64-v8a

## Software Environment
- **Android API Level:** 36
- **Runtime Model:** On-device offline inference

## Verified Observations (Phase 2 Integration)
During Block 2.1 Device Stabilization and Block 3 validation, the following behaviors were explicitly verified on the target device:
- **JNI Concurrency Stability:** Multi-threaded operations utilizing the JVM `engineLock` successfully serialize access across the JNI boundary. `SIGBUS` memory mapping crashes have been completely eliminated.
- **Cache I/O Readiness:** The filesystem successfully writes and loads multiple MB-scale `.bin` cache files simultaneously (3/3 successful cache loads verified).
- **Daemon Orchestration:** The Android lifecycle appropriately respects `BootReceiver` → `ActiveSweep` startup ordering, with background foreground services maintaining process persistence.

## Engineering Constraints & Deployment Strategy
- **ARM64-Only Deployment Strategy:** The system is exclusively compiled and deployed for the `arm64-v8a` ABI. This strict targeting ensures that all tensor math leverages the Snapdragon hardware's native NEON SIMD instructions.
- **Offline Execution Requirement:** The AACBridge architecture mandates 100% offline execution to ensure privacy and constant availability. No API calls or cloud processing are permitted.

*(Note: Target latency and cache hit-rate measurements are strictly pending Phase 3 TTFT benchmarking and are explicitly excluded from this structural specification.)*
