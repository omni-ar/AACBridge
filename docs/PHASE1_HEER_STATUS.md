# Phase 1 & 2.5 Engineering Status Report: Android Runtime & App Layer (Heer)

**Date:** May 28, 2026
**Role:** Android App, UI, Camera Pipeline, and Daemon Orchestration (Heer)

## 1. Overview

This document outlines the current state of the Android app runtime, UI orchestration, and hardware-accelerated camera pipelines required for the AACBridge project. Heer's ownership domain encompasses the entire user-facing surface, foreground service lifecycles, real-time input extraction (MediaPipe gaze tracking), Text-to-Speech (TTS) orchestration, and fusion inference placeholders.

Crucially, the app runtime layer serves as the deterministic orchestrator for Arjit's native JNI backend. It does not duplicate backend logic; rather, it coordinates the Android lifecycle around it. The `ContextDaemon` drives the background pre-computation of the KV cache by polling the environment and dispatching signals to the `StateRouter` and `ActiveSweep` pipelines. The `GazeTracker` processes real-time visual streams and emits discrete intent payloads that are routed into the caching architecture. This strict separation of concerns ensures that the JVM garbage collector does not interfere with the native LLM inference engine.

## 2. Completed Runtime Infrastructure

The following Android runtime systems have been fully implemented, integrated, and validated on physical hardware.

### Android Runtime Layer
- **MainActivity Lifecycle:** The primary entry point is fully responsible for coordinating camera binding, UI rendering, TTS initialization, and dynamic permission requests (CAMERA, POST_NOTIFICATIONS, LOCATION).
- **Restart Stability:** The application architecture safely handles configuration changes and process restarts without crashing the native JNI instance or leaking MediaPipe memory.
- **Runtime Validation Flow:** Integrated robust debug logging for end-to-end trace validation without exposing the user to verbose logs in production.

### CameraX Integration
- **ImageAnalysis Pipeline:** Successfully implemented a non-blocking `ImageProxy` pipeline that feeds raw sensor frames directly into MediaPipe.
- **No PreviewView Optimization:** The camera is bound *exclusively* for headless image analysis. Bypassing the rendering of a `PreviewView` on the UI drastically reduces GPU load and thermal throttling on the edge device, preserving overhead for LLM inference.
- **detectAsync Lifecycle:** Frames are timestamped and fed asynchronously to avoid blocking the CameraX producer thread.

### MediaPipe FaceLandmarker
- **LIVE_STREAM Mode:** Initialized in real-time streaming mode using the hardware-accelerated `face_landmarker.task` model asset.
- **Gaze Vector Extraction:** The tracker dynamically calculates horizontal and vertical gaze vectors by evaluating the spatial displacement between the user's nose tip (landmark 4) and the midpoint of the inner eyes (landmarks 33 and 362).
- **Target Mapping:** Displacements map strictly to the 5 targeted AAC intents (confirm, reject, scroll, select, call-help) via static thresholds.

### Dwell System
- **400ms Threshold:** A strict 400ms dwell fixation is enforced before emitting an intent to prevent accidental micro-saccade activations.
- **Debounce Semantics (`hasFired`):** Once an intent is triggered, the `hasFired` flag locks the state. The user must break their gaze (shift to a new target or lose tracking) to re-arm the trigger, completely preventing looping intent spam.
- **Threshold Extraction:** Gaze constraints are extracted into highly tunable `const val` boundaries (`THRESHOLD_X = 0.02f`, `THRESHOLD_Y = 0.01f`) for future empirical optimization.

### Concurrency Stabilization
- **Thread Safety Challenge:** MediaPipe `processResult` callbacks are invoked asynchronously on internal worker threads. Mutating the `currentTarget`, `dwellStartTime`, and `hasFired` state concurrently caused race conditions.
- **Deterministic Serialization:** An `Executors.newSingleThreadExecutor()` guarantees that all dwell evaluations, threshold updates, and state resets are processed sequentially. This prevents race conditions without the overhead of heavy global locks.

### ContextDaemon
- **Foreground Service Migration:** Due to Android API 26+ restrictions, `ContextDaemon` was migrated to a formal Foreground Service with a persistent `NotificationChannel`. This prevents the OS from killing the background cache orchestrator.
- **START_STICKY Lifecycle:** Ensures the daemon is automatically resurrected by the OS if resource constraints force a temporary termination.
- **Delegation Architecture:** The daemon strictly delegates context acquisition to `ActiveSweep` rather than duplicating location/BLE polling logic, cleanly preserving the backend architectural boundary.

### SpeechOutputManager
- **TTS Orchestration:** A lightweight wrapper around Android's native TextToSpeech engine.
- **Backend Isolation:** Handles verbalization of generated outputs entirely at the app level. It does not interact with the JNI or KV cache, isolating audio processing from core memory constraints.

### Runtime Validation
The entire camera and gaze pipeline was rigorously validated on a physical Snapdragon Android device via `adb logcat`:
- `assembleDebug`, `lintDebug`, and `installDebug` completed seamlessly.
- **Camera Frame Delivery:** `Camera frame received` logs verified that CameraX pushes `ImageProxy` frames continuously.
- **MediaPipe Extraction:** `Face landmarks detected` logs confirmed the `.task` asset successfully maps the Face Mesh.
- **Intent Resolution:** `Resolved gaze target` dynamically responded to real-time head movements.
- **Dwell Debounce:** The `Dwell threshold reached` event fired exactly once per fixation, proving the `hasFired` synchronization successfully prevented event spamming.

## 3. Architectural Decisions

1. **CameraX Without PreviewView**
   - *Decision:* Bind `ImageAnalysis` exclusively.
   - *Rationale:* Saves significant battery and GPU rendering overhead.
   - *Prevents:* Thermal throttling that would otherwise steal processing cycles from the local LLM.
2. **BuildConfig.DEBUG Gated Instrumentation**
   - *Decision:* High-frequency camera and MediaPipe logs are strictly gated behind `BuildConfig.DEBUG`.
   - *Rationale:* 30 FPS logging in a production build causes severe I/O bottlenecking.
   - *Prevents:* `logcat` buffer overflows and CPU starvation on the main thread.
3. **Foreground Daemon Requirement**
   - *Decision:* The `ContextDaemon` is a bound Foreground Service.
   - *Rationale:* Android API 34+ aggressively kills background services to save battery. The KV cache must stay alive in the background.
   - *Prevents:* The caching architecture from being unexpectedly suspended.
4. **Deterministic Dwell Serialization**
   - *Decision:* `updateDwell()` mutations are funneled through a single-thread executor.
   - *Rationale:* MediaPipe dispatches results on multi-threaded worker pools.
   - *Prevents:* Race conditions that could cause a gaze intent to misfire or double-fire.
5. **No JNI Calls from the Gaze Layer**
   - *Decision:* The `GazeTracker` emits only UI-level Kotlin string intents via a callback.
   - *Rationale:* Hard-coupling computer vision pipelines to native LLM pointers creates monolithic memory leaks.
   - *Prevents:* The UI thread from blocking on C++ execution.

## 4. Known Limitations

While the runtime layer is highly stable, the following deliberate limitations remain in this branch:
- **Fusion Inference Still Placeholder:** The gaze intent is currently routed cleanly, but the actual cross-attention ONNX fusion with Medha's EMG embedding is not yet integrated.
- **No Adaptive Threshold Tuning:** Gaze thresholds are static (`0.02f` / `0.01f`). There is no auto-calibration sequence for users with limited neck mobility yet.
- **No Camera Occlusion Handling:** If the face is temporarily obscured, the system merely drops frames rather than interpolating intent.
- **No Accessibility UI:** There is no on-screen visual feedback for where the user is looking. (This was intentionally omitted for performance, but may be needed for clinical trials).

## 5. Runtime Validation Timeline

- **Initial App-Layer Integration:** Scaffolded `MainActivity`, `ContextDaemon`, and `SpeechOutputManager`.
- **CameraX Fixes:** Bound the `ProcessCameraProvider` and optimized `ImageAnalysis` to bypass the UI rendering tree.
- **MediaPipe Asset Fixes:** Addressed the missing `face_landmarker.task` asset by directly injecting it into the APK's `assets/` directory.
- **Foreground Daemon Fixes:** Elevated `ContextDaemon` to a foreground service to prevent aggressive OS termination.
- **Debounce Fixes:** Implemented the `hasFired` gate to prevent multi-triggering.
- **Concurrency Fixes:** Migrated multi-threaded MediaPipe callbacks to a single-threaded executor for safe dwell evaluation.
- **Final Snapdragon Validation:** Successfully tested on physical hardware, validating the 400ms dwell, intent resolution, and log outputs.
