# Phase 2 Engineering Status Report: EMG Pipeline & Drift Ablation (Medha)

**Date:** June 8, 2026  
**Role:** Input / ML — NinaPro EMG Pipeline, CNN-LSTM Classifier, Drift Detection Ablation
**Phase Status:** SOFTWARE PIPELINE COMPLETE (HARDWARE PENDING)

---

## 1. Overview

This document covers the complete Phase 2 implementation of the EMG intent classification pipeline for AACBridge. Medha's ownership domain encompasses the full sEMG signal processing stack: raw `.mat` file ingestion from NinaPro DB5, bandpass filtering and windowing, CNN-LSTM intent classification, ONNX model export for late fusion, TFLite export for Android deployment, and the empirical k-ablation study that validates the async drift detector's window size selection.

The software pipeline is mathematically verified and complete. Physical integration with the BLE wearable remains the sole pending item.

---

## 2. Completed Phase 2 Deliverables

### NinaPro DB5 Preprocessing Pipeline (`emg_pipeline/src/preprocess.py`)
- **Status:** Complete.
- **Details:** 4th-order Butterworth bandpass filter (20–450 Hz), full-wave rectification, and non-overlapping 200ms window segmentation (400 samples at 2kHz). Per-channel z-score normalisation.

### Dataset Loader with AAC Intent Mapping (`emg_pipeline/src/dataset.py`)
- **Status:** Complete.
- **Details:** `NinaProEMGDataset` filters to the 5 mapped AAC intent classes (confirm, reject, scroll, select, call-help).

### CNN-LSTM Intent Classifier (`emg_pipeline/src/model.py`, `train.py`)
- **Status:** Complete.
- **Details:** Best val_acc: 50.0%. Model architecture mathematically verified.

### ONNX Export (`emg_pipeline/src/export_onnx.py`, `emg_pipeline/exports/`)
- **Status:** Complete. Validated with onnxruntime.
- **Artifacts:** `emg_classifier.onnx`
- **Known Issue — Output Shape:** Embedding output shape is `(1,1,64)` not `(1,64)` due to the `torch.export` batch dimension. Heer correctly applies `.squeeze(1)` before cross-attention input.

### TFLite Export (`emg_pipeline/src/export_tflite_direct.py`, `emg_pipeline/exports/`)
- **Status:** Complete. Both float32 and INT8 artifacts produced.
- **Validation:** A direct `export_tflite_direct.py` script actively transfers exact trained weights from PyTorch to Keras. 
  - `np.testing.assert_allclose(pytorch_logits, tflite_logits, atol=1e-3)`: **PASS**
  - Max Absolute Error: 9.30e-04
- **Artifacts:**
  - `emg_classifier.tflite` — float32, 1.26 MB
  - `emg_classifier_int8.tflite` — INT8 dynamic-range quantized, 385 KB

### k-Ablation Study (`emg_pipeline/drift_ablation/`)
- **Status:** Complete. Results delivered to Arjit.
- **Selection:** `k=3` selected to provide a local semantic centroid that suppresses single-turn variance without diluting recent signal history.

---

## 3. Teammate Handoffs (Resolved)

| Artifact | Recipient | Status | Notes |
|----------|-----------|--------|-------|
| `emg_embedding_only.onnx` | Heer | ✅ Complete | Squeeze applied. Late Fusion winner selected. |
| `k_ablation_results.csv` | Arjit | ✅ Complete | k=3 explicitly adopted in DriftDetector. |
| `emg_classifier_int8.tflite` | Arjit | ✅ Complete | Validated weights. Ready for hardware injection. |

---

## 4. Known Limitations

- **confirm Class Failure:** F1=0.000 for confirm class in best checkpoint. Cross-subject failure for subject 10 specifically.
- **Simulation Data:** NinaPro DB5 is hand/wrist, not facial sEMG. Architecture validated, not deployment accuracy.
- **Hardware Disconnect:** Android deployment requires physical pairing to the wearable. A mock `FloatArray(64)` currently acts as a placeholder in `MainActivity.kt`.