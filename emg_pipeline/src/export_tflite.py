"""
export_tflite.py
Export trained CNN-LSTM EMG classifier to TFLite for Android CPU deployment.

Two outputs:
  emg_classifier.tflite        — float32 model (fallback / accuracy reference)
  emg_classifier_int8.tflite   — INT8 dynamic-range quantized (for device deployment)

The TFLite model exposes two outputs:
  output_0: logits   (1, 5)   — intent class scores
  output_1: embedding (1, 64) — EMG embedding for optional fusion

Usage:
    python export_tflite.py \
        --checkpoint ../checkpoints/best_model.pt \
        --output_dir ../exports \
        --rep_data_root ../data/raw   # for full-integer quantization calibration
"""

import argparse
import sys
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn

sys.path.insert(0, str(Path(__file__).parent))
from model import EMGIntentClassifier


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Export EMG classifier to TFLite")
    p.add_argument("--checkpoint", type=str, required=True)
    p.add_argument("--output_dir", type=str, default="../exports")
    p.add_argument("--rep_data_root", type=str, default=None,
                   help="Path to raw/ for representative dataset (INT8 full quantization). "
                        "If omitted, uses dynamic-range quantization.")
    return p.parse_args()


# ── Wrapper that returns both logits and embedding ────────────────────────────

class EMGExportWrapper(nn.Module):
    """Wraps the model to return (logits, embedding) as a tuple for export."""
    def __init__(self, model: EMGIntentClassifier):
        super().__init__()
        self.model = model

    def forward(self, x: torch.Tensor):
        logits, emb = self.model.forward_with_embedding(x)
        return logits, emb


def load_model(checkpoint_path: str) -> EMGIntentClassifier:
    ckpt = torch.load(checkpoint_path, map_location="cpu")
    model = EMGIntentClassifier()
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()
    return model


def export_onnx_intermediate(model: EMGExportWrapper,
                              onnx_path: Path) -> None:
    """Export to ONNX as intermediate step before TFLite conversion."""
    dummy = torch.randn(1, 16, 400)
    torch.onnx.export(
        model,
        dummy,
        str(onnx_path),
        opset_version=14,
        input_names=["emg_input"],
        output_names=["logits", "embedding"],
        dynamic_axes={
            "emg_input": {0: "batch_size"},
            "logits":    {0: "batch_size"},
            "embedding": {0: "batch_size"},
        },
        do_constant_folding=True,
    )
    print(f"ONNX intermediate saved: {onnx_path}")


def convert_onnx_to_tflite(onnx_path: Path,
                             tflite_path: Path,
                             quantize_int8: bool = False,
                             rep_data: np.ndarray = None) -> None:
    """
    Convert ONNX to TFLite via onnx-tf + TFLite converter.

    Requires: pip install onnx onnx-tf tensorflow
    """
    try:
        import onnx
        import onnx_tf
        import tensorflow as tf
    except ImportError:
        print("[ERROR] Missing dependencies. Install with:")
        print("  pip install onnx onnx-tf tensorflow")
        return

    # ── ONNX → TF SavedModel ─────────────────────────────────────────────────
    onnx_model = onnx.load(str(onnx_path))
    tf_rep = onnx_tf.backend.prepare(onnx_model)
    saved_model_dir = tflite_path.parent / "tf_saved_model"
    tf_rep.export_graph(str(saved_model_dir))
    print(f"TF SavedModel exported: {saved_model_dir}")

    # ── TF SavedModel → TFLite ────────────────────────────────────────────────
    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_dir))

    if quantize_int8 and rep_data is not None:
        # Full-integer quantization with representative dataset
        def rep_dataset():
            for i in range(min(200, len(rep_data))):
                sample = rep_data[i:i+1].astype(np.float32)  # (1, 16, 400)
                yield [sample]

        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        converter.representative_dataset = rep_dataset
        converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        converter.inference_input_type = tf.int8
        converter.inference_output_type = tf.float32  # keep output as float
        print("Quantization: INT8 full (representative dataset provided)")
    elif quantize_int8:
        # Dynamic-range quantization (no calibration data needed)
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        print("Quantization: INT8 dynamic-range")
    else:
        print("Quantization: none (float32)")

    tflite_model = converter.convert()
    tflite_path.write_bytes(tflite_model)

    size_mb = tflite_path.stat().st_size / 1e6
    print(f"TFLite model saved: {tflite_path}  ({size_mb:.2f} MB)")


def load_rep_data(data_root: str) -> np.ndarray:
    """Load a small representative dataset for INT8 calibration."""
    sys.path.insert(0, str(Path(__file__).parent))
    from dataset import NinaProEMGDataset, build_subject_paths

    # Use subject 1 only for calibration (fast)
    paths = build_subject_paths(data_root, subjects=[1])
    ds = NinaProEMGDataset(paths, normalize=True, augment=False)
    samples = np.stack([ds[i][0].numpy() for i in range(min(500, len(ds)))])
    return samples  # (N, 16, 400)


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    model = load_model(args.checkpoint)
    wrapper = EMGExportWrapper(model)
    wrapper.eval()

    onnx_path = output_dir / "emg_classifier.onnx"
    export_onnx_intermediate(wrapper, onnx_path)

    # ── Float32 TFLite ────────────────────────────────────────────────────────
    convert_onnx_to_tflite(
        onnx_path,
        output_dir / "emg_classifier.tflite",
        quantize_int8=False,
    )

    # ── INT8 TFLite ───────────────────────────────────────────────────────────
    rep_data = None
    if args.rep_data_root:
        print("\nLoading representative data for INT8 calibration...")
        rep_data = load_rep_data(args.rep_data_root)

    convert_onnx_to_tflite(
        onnx_path,
        output_dir / "emg_classifier_int8.tflite",
        quantize_int8=True,
        rep_data=rep_data,
    )

    print("\n=== Export complete ===")
    print(f"Outputs in: {output_dir}")
    print("  emg_classifier.tflite        — float32")
    print("  emg_classifier_int8.tflite   — INT8 (Android deployment target)")
    print("\nNext step: copy to Android project and load via TFLite Interpreter:")
    print("  val interpreter = Interpreter(loadMappedFile(\"emg_classifier_int8.tflite\"))")


if __name__ == "__main__":
    main()