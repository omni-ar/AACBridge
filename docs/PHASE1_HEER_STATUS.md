# Phase 1 & 2 Engineering Status Report: Android Runtime & App Layer (Heer)

**Date:** June 8, 2026
**Role:** Android App, UI, Camera Pipeline, and Fusion Orchestration (Heer)
**Phase Status:** COMPLETE

## 1. Overview

This document outlines the verified state of the Android app runtime, UI orchestration, hardware-accelerated camera pipelines, and fusion integration for the AACBridge project. Heer's ownership domain encompasses the entire user-facing surface, foreground service lifecycles, real-time input extraction (MediaPipe gaze tracking), Text-to-Speech (TTS) orchestration, and ONNX Runtime fusion integration.

## 2. Completed Runtime Infrastructure

### Android Runtime Layer
- **MainActivity Lifecycle:** The primary entry point coordinates camera binding, UI rendering, TTS initialization, and dynamic permission requests.
- **Restart Stability:** Safely handles configuration changes and process restarts without crashing the native JNI instance or leaking MediaPipe memory.

### CameraX Integration
- **ImageAnalysis Pipeline:** Non-blocking `ImageProxy` pipeline feeds raw sensor frames directly into MediaPipe.
- **No PreviewView Optimization:** Bypassing the rendering of a `PreviewView` on the UI drastically reduces GPU load and thermal throttling.

### MediaPipe FaceLandmarker
- **LIVE_STREAM Mode:** Initialized in real-time streaming mode using `face_landmarker.task`.
- **Gaze Vector Extraction:** The tracker dynamically calculates horizontal and vertical gaze vectors.
- **Label Leakage Fix:** A data leakage issue was discovered where the gaze vector inadvertently included `intentIndex`. The deployed gaze vector was correctly reduced to 5 dimensions: `[dx, dy, abs(dx), abs(dy), magnitude]`.

### Dwell System
- **400ms Threshold:** A strict 400ms dwell fixation is enforced.
- **Debounce Semantics (`hasFired`):** Prevents looping intent spam.

### Concurrency Stabilization
- **Deterministic Serialization:** An `Executors.newSingleThreadExecutor()` guarantees that all dwell evaluations are processed sequentially to prevent race conditions.

### ContextDaemon
- **Foreground Service:** Migrated to a formal Foreground Service with a persistent `NotificationChannel`.
- **START_STICKY Lifecycle:** Ensures resurrection by the OS.

### Fusion Architecture Integration
- **Model Selection:** Following an ablation study between Cross-Attention (F1=0.9437) and Late Fusion (F1=0.9963), Late Fusion was selected due to the Cross-Attention architecture failing to meet the >3% F1 advantage criterion.
- **ONNX Runtime:** The `gaze_emg_fusion.onnx` model (Late Fusion) is fully integrated into `FusionInference.kt` utilizing the `onnxruntime-android` dependency. Inference is verified on-device.

## 3. Known Limitations

- **No Adaptive Threshold Tuning:** Gaze thresholds are static (`0.02f` / `0.01f`). There is no auto-calibration sequence for users with limited neck mobility yet.
- **No Camera Occlusion Handling:** If the face is temporarily obscured, the system merely drops frames rather than interpolating intent.
- **No Accessibility UI:** There is no on-screen visual feedback for where the user is looking.
