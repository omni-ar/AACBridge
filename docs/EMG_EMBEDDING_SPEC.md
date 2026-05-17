# EMG Embedding Spec
**From:** Medha → **To:** Heer  
**Date:** May 21, 2026

---

My EMG classifier outputs a vector of size **64** for each gesture window.

- Shape: `(1, 64)`
- Type: float32
- Normalised: yes (L2)

This is your **Q (query)** in the cross-attention fusion model.  
Your gaze vector is K and V.

**Question for you:** what size is your gaze embedding going to be? I need to know so the two sides match up.