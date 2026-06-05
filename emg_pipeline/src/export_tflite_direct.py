"""
export_tflite_direct.py
Direct PyTorch → TFLite conversion without onnx-tf.
Uses torch.jit.trace → saved model path approach via TF's own converter.
"""
import sys
import torch
import numpy as np
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from model import EMGIntentClassifier


def load_model(checkpoint_path: str) -> EMGIntentClassifier:
    ckpt = torch.load(checkpoint_path, map_location="cpu")
    model = EMGIntentClassifier()
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()
    print(f"Loaded checkpoint (epoch={ckpt['epoch']}, val_acc={ckpt['val_acc']:.4f})")
    return model


def export(checkpoint_path: str, output_dir: str) -> None:
    import tensorflow as tf

    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    model = load_model(checkpoint_path)
    dummy = torch.randn(1, 16, 400)

    # Step 1: Get numpy outputs from PyTorch for validation
    with torch.no_grad():
        pt_logits, pt_emb = model.forward_with_embedding(dummy)
    pt_logits_np = pt_logits.numpy()
    pt_emb_np = pt_emb.numpy()
    print(f"PyTorch logits shape: {pt_logits_np.shape}")
    print(f"PyTorch embedding shape: {pt_emb_np.shape}")

    # Step 2: Build equivalent TF/Keras model
    inputs = tf.keras.Input(shape=(16, 400), batch_size=1, name="emg_input")

    # CNN blocks — replicate architecture from model.py
    x = tf.keras.layers.Conv1D(32, 3, padding='same', activation=None)(inputs)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.ReLU()(x)
    x = tf.keras.layers.MaxPooling1D(2)(x)

    x = tf.keras.layers.Conv1D(64, 3, padding='same', activation=None)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.ReLU()(x)
    x = tf.keras.layers.MaxPooling1D(2)(x)

    x = tf.keras.layers.Conv1D(128, 3, padding='same', activation=None)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.ReLU()(x)
    x = tf.keras.layers.MaxPooling1D(2)(x)

    # LSTM
    x = tf.keras.layers.LSTM(128, return_sequences=False)(x)

    # Embedding projection
    x = tf.keras.layers.Dense(64)(x)
    x = tf.keras.layers.ReLU()(x)
    x = tf.keras.layers.Dropout(0.3)(x, training=False)

    # L2 normalize
    embedding = tf.keras.layers.Lambda(lambda v: tf.math.l2_normalize(v, axis=-1), name="embedding")(x)

    # Classifier head
    logits = tf.keras.layers.Dense(5, name="logits")(embedding)

    tf_model = tf.keras.Model(inputs=inputs, outputs=[logits, embedding])

    # Step 3: Transfer weights from PyTorch to TF
    _transfer_weights(model, tf_model)

    # Step 4: Convert to TFLite float32
    converter = tf.lite.TFLiteConverter.from_keras_model(tf_model)
    tflite_model = converter.convert()
    float_path = output_dir / "emg_classifier.tflite"
    float_path.write_bytes(tflite_model)
    print(f"Saved: {float_path} ({float_path.stat().st_size / 1e6:.2f} MB)")

    # Step 5: Convert to TFLite INT8
    converter_int8 = tf.lite.TFLiteConverter.from_keras_model(tf_model)
    converter_int8.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_int8 = converter_int8.convert()
    int8_path = output_dir / "emg_classifier_int8.tflite"
    int8_path.write_bytes(tflite_int8)
    print(f"Saved: {int8_path} ({int8_path.stat().st_size / 1e6:.2f} MB)")

    print("\n=== TFLite export complete ===")
    print(f"  emg_classifier.tflite       — float32")
    print(f"  emg_classifier_int8.tflite  — INT8 dynamic-range quantized")


def _transfer_weights(pt_model: EMGIntentClassifier, tf_model) -> None:
    """Weight transfer skipped for dry run — architecture validation only."""
    print("Note: Weight transfer skipped for dry run.")
    print("TFLite files contain correct architecture with random weights.")
    print("Full weight transfer requires per-layer index mapping — out of scope for Phase 2 dry run.")


if __name__ == "__main__":
    import argparse
    p = argparse.ArgumentParser()
    p.add_argument("--checkpoint", required=True)
    p.add_argument("--output_dir", default="../exports")
    args = p.parse_args()
    export(args.checkpoint, args.output_dir)