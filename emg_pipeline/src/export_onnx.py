"""
export_onnx.py
Export trained CNN-LSTM EMG classifier to ONNX.

This ONNX model is used in two ways:
  1. Standalone: the embedding-only output feeds Heer's cross-attention fusion model
  2. As an intermediate step for TFLite conversion (see export_tflite.py)

Outputs:
  emg_classifier.onnx         — full model (logits + embedding)
  emg_embedding_only.onnx     — embedding output only (for fusion pipeline)

Usage:
    python export_onnx.py \
        --checkpoint ../checkpoints/best_model.pt \
        --output_dir ../exports
"""

import argparse
import sys
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn

sys.path.insert(0, str(Path(__file__).parent))
from model import EMGIntentClassifier, EMG_EMBEDDING_DIM


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Export EMG classifier to ONNX")
    p.add_argument("--checkpoint", type=str, required=True)
    p.add_argument("--output_dir", type=str, default="../exports")
    p.add_argument("--opset", type=int, default=14)
    return p.parse_args()


def load_model(checkpoint_path: str) -> EMGIntentClassifier:
    ckpt = torch.load(checkpoint_path, map_location="cpu")
    model = EMGIntentClassifier()
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()
    print(f"Loaded checkpoint (epoch={ckpt['epoch']}, val_acc={ckpt['val_acc']:.4f})")
    return model


# ── Export wrappers ───────────────────────────────────────────────────────────

class FullModelWrapper(nn.Module):
    """Returns (logits, embedding)."""
    def __init__(self, model: EMGIntentClassifier):
        super().__init__()
        self.model = model

    def forward(self, x: torch.Tensor):
        return self.model.forward_with_embedding(x)


class EmbeddingOnlyWrapper(nn.Module):
    """Returns embedding only — for fusion model input."""
    def __init__(self, model: EMGIntentClassifier):
        super().__init__()
        self.model = model

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.model.encode(x)


def export(wrapper: nn.Module,
           onnx_path: Path,
           output_names: list[str],
           opset: int) -> None:
    dummy = torch.randn(1, 16, 400)

    torch.onnx.export(
        wrapper,
        dummy,
        str(onnx_path),
        opset_version=opset,
        input_names=["emg_input"],
        output_names=output_names,
        dynamic_axes={name: {0: "batch_size"}
                      for name in ["emg_input"] + output_names},
        do_constant_folding=True,
    )

    size_mb = onnx_path.stat().st_size / 1e6
    print(f"Saved: {onnx_path}  ({size_mb:.2f} MB)")


def validate_onnx(onnx_path: Path, output_names: list[str]) -> None:
    try:
        import onnx
        import onnxruntime as ort
    except ImportError:
        print("[SKIP] onnx / onnxruntime not installed — skipping validation")
        return

    onnx.checker.check_model(str(onnx_path))
    sess = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])

    dummy = np.random.randn(1, 16, 400).astype(np.float32)
    outputs = sess.run(None, {"emg_input": dummy})

    for name, out in zip(output_names, outputs):
        print(f"  {name}: shape={out.shape}  dtype={out.dtype}")
    print("  ONNX validation passed ✓")


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    model = load_model(args.checkpoint)

    # ── Full model export ─────────────────────────────────────────────────────
    print("\n[1/2] Exporting full model (logits + embedding)...")
    full_path = output_dir / "emg_classifier.onnx"
    export(FullModelWrapper(model), full_path,
           output_names=["logits", "embedding"], opset=args.opset)
    validate_onnx(full_path, ["logits", "embedding"])

    # ── Embedding-only export ─────────────────────────────────────────────────
    print("\n[2/2] Exporting embedding-only model (for fusion)...")
    emb_path = output_dir / "emg_embedding_only.onnx"
    export(EmbeddingOnlyWrapper(model), emb_path,
           output_names=["embedding"], opset=args.opset)
    validate_onnx(emb_path, ["embedding"])

    print(f"\n=== ONNX export complete ===")
    print(f"  emg_classifier.onnx       — full model, input=(B,16,400), "
          f"outputs=(logits:(B,5), embedding:(B,{EMG_EMBEDDING_DIM}))")
    print(f"  emg_embedding_only.onnx   — embedding only, input=(B,16,400), "
          f"output=embedding:(B,{EMG_EMBEDDING_DIM})")
    print(f"\nHeer: use emg_embedding_only.onnx as the EMG encoder in the fusion model.")
    print(f"      EMG embedding dim = {EMG_EMBEDDING_DIM}, L2-normalised, float32.")


if __name__ == "__main__":
    main()