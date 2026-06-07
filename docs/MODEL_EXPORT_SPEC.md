# Model Export Specification

**Owner:** Medha & Heer
**Last updated:** June 2026

---

## 1. EMG Intent Classifier — Architecture Summary

**Model:** CNN-LSTM  
**Input:** (1, 400, 16) — 400 time samples (200ms @ 2kHz) × 16 EMG channels (Keras channels-last)
**Output:** (1, 5) logits + (1, 64) L2-normalised embedding  
**Parameters:** ~500K  

---

## 2. ONNX Export (Fusion)

**Model:** Late Fusion
**Artifact:** `gaze_emg_fusion.onnx`
**Rationale for `dynamo=False`:** During the ONNX export of the fusion model, the PyTorch Dynamo exporter (`torch.export`) generated an external `.onnx.data` artifact alongside the main `.onnx` file. ONNX Runtime for Android does not seamlessly load multi-file external data without complex custom data locators, causing the app to crash when attempting to read the weights. The export script was updated to use the legacy `torch.onnx.export` with `dynamo=False`, which inlined the weights into a single, contiguous 45KB file, verified to load correctly on the Android device.

---

## 3. TFLite Export (EMG)

**Rationale for Direct Export:** The original `export_tflite.py` used `onnx-tf`, which is abandoned and incompatible with `onnx>=1.14`. A direct PyTorch→Keras→TFLite script (`export_tflite_direct.py`) was written. 

**Validation:** The script correctly transfers weights layer-by-layer (Conv1d transpose, BatchNorm, LSTM gate transposition, Dense). Validation via `np.testing.assert_allclose(pytorch, tflite, atol=1e-3)` passes with a maximum error of 9.30e-04.

| Artifact | Format | Size |
|----------|--------|------|
| `emg_classifier.onnx` | ONNX opset 14 | 1.22 MB |
| `emg_classifier.tflite` | TFLite float32 | 1.26 MB |
| `emg_classifier_int8.tflite` | TFLite INT8 | 385 KB |

---

## 4. Drift Detector k-Ablation

**Validation:** The detector computes cosine similarity between an anchor embedding and the mean of the last `k=3` turn embeddings to detect context shift.
**Results:** `k=3` achieves F1=0.1199 on DialogSum. `k=3` is deliberately selected over `k=1` because AAC turns are sparse single words with high semantic variance, whereas DialogSum turns are multi-sentence. Precision is favored over recall to avoid costly (~2s TTFT) KV cache re-prefills.