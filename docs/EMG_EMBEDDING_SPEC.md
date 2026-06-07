# EMG Embedding Spec
**From:** Medha → **To:** Heer  
**Date:** June 2026

---

My EMG classifier outputs a vector of size **64** for each gesture window.

- Shape: `(1, 64)` (Keras/TFLite layout after `.squeeze()` applied)
- Type: float32
- Normalised: yes (L2)

This is the concatenated feature vector in the Late Fusion model.  
Your gaze vector is 5-dimensional: `[dx, dy, abs(dx), abs(dy), magnitude]`.

**Validation:** The export path has been validated. `np.testing.assert_allclose(pytorch, tflite, atol=1e-3)` passes with max absolute error 9.30e-04. The TFLite float32 artifact size is 1.26MB, and INT8 is 385KB. The tensor shape for Keras channels-last is `(1, 400, 16)`.