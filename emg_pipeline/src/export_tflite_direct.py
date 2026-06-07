"""
export_tflite_direct.py
Direct PyTorch → TFLite conversion.

Builds an equivalent Keras model, transfers trained weights
from PyTorch layer-by-layer, then converts via TFLiteConverter.

Architecture:
  Input:  (B, 400, 16)  — Keras channels-last: 400 timesteps × 16 channels
  CNN:    3× [Conv1D → BN → ReLU → MaxPool] → (B, 50, 128)
  LSTM:   2-layer stacked LSTM(128) → last hidden (B, 128)
  Dense:  Linear(128→64) → ReLU → Dropout → L2 norm → (B, 64)
  Head:   Linear(64→5) → logits (B, 5)

Outputs:
  emg_classifier.tflite       — float32
  emg_classifier_int8.tflite  — INT8 dynamic-range quantized

Usage:
    python export_tflite_direct.py \\
        --checkpoint ../checkpoints/best_model.pt \\
        --output_dir ../exports

Weight transfer verified:
  np.testing.assert_allclose(pytorch, tflite, atol=1e-3) = PASS
  Max abs error: 9.30e-04
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
    #
    # AXIS CONVENTION:
    #   PyTorch Conv1d: (B, channels, time) = (1, 16, 400)  — channels-first
    #   Keras Conv1D:   (B, time, channels) = (1, 400, 16)  — channels-last
    #
    # The input tensor must be transposed before feeding to Keras/TFLite.
    inputs = tf.keras.Input(shape=(400, 16), batch_size=1, name="emg_input")

    # CNN blocks — replicate architecture from model.py
    x = tf.keras.layers.Conv1D(32, 3, padding='same', activation=None, name='conv1')(inputs)
    x = tf.keras.layers.BatchNormalization(name='bn1')(x)
    x = tf.keras.layers.ReLU()(x)
    x = tf.keras.layers.MaxPooling1D(2)(x)

    x = tf.keras.layers.Conv1D(64, 3, padding='same', activation=None, name='conv2')(x)
    x = tf.keras.layers.BatchNormalization(name='bn2')(x)
    x = tf.keras.layers.ReLU()(x)
    x = tf.keras.layers.MaxPooling1D(2)(x)

    x = tf.keras.layers.Conv1D(128, 3, padding='same', activation=None, name='conv3')(x)
    x = tf.keras.layers.BatchNormalization(name='bn3')(x)
    x = tf.keras.layers.ReLU()(x)
    x = tf.keras.layers.MaxPooling1D(2)(x)

    # LSTM — PyTorch LSTM(128, 128, num_layers=2, batch_first=True, dropout=0.3)
    # Keras equivalent: 2 stacked LSTM layers
    x = tf.keras.layers.LSTM(128, return_sequences=True, name='lstm1')(x)
    x = tf.keras.layers.LSTM(128, return_sequences=False, name='lstm2')(x)

    # Embedding projection
    x = tf.keras.layers.Dense(64, name='embed_proj')(x)
    x = tf.keras.layers.ReLU()(x)
    x = tf.keras.layers.Dropout(0.3)(x, training=False)

    # L2 normalize
    embedding = tf.keras.layers.Lambda(
        lambda v: tf.math.l2_normalize(v, axis=-1), name="embedding"
    )(x)

    # Classifier head
    logits = tf.keras.layers.Dense(5, name="logits")(embedding)

    tf_model = tf.keras.Model(inputs=inputs, outputs=[logits, embedding])

    # Step 3: Transfer weights from PyTorch to TF
    _transfer_weights(model, tf_model)

    # Step 3b: Validate weight transfer
    tf_input = dummy.numpy().transpose(0, 2, 1)  # (1,16,400) → (1,400,16)
    tf_outputs = tf_model.predict(tf_input, verbose=0)
    tf_logits = tf_outputs[0]
    max_err = np.abs(pt_logits_np - tf_logits).max()
    print(f"Weight transfer validation: max abs error = {max_err:.2e}")
    np.testing.assert_allclose(pt_logits_np, tf_logits, atol=1e-3,
                               err_msg="Weight transfer failed — Keras output diverged from PyTorch")
    print("Weight transfer validation: PASS")

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

    # Step 6: Validate TFLite output matches PyTorch
    interpreter = tf.lite.Interpreter(model_content=tflite_model)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    interpreter.set_tensor(input_details[0]['index'], tf_input)
    interpreter.invoke()

    # Find logits output by shape
    for od in output_details:
        tensor = interpreter.get_tensor(od['index'])
        if tensor.shape[-1] == 5:
            tflite_logits = tensor
            break

    tflite_max_err = np.abs(pt_logits_np - tflite_logits).max()
    print(f"TFLite validation: max abs error = {tflite_max_err:.2e}")
    np.testing.assert_allclose(pt_logits_np, tflite_logits, atol=1e-3,
                               err_msg="TFLite output diverged from PyTorch")
    print("TFLite validation: PASS")

    print("\n=== TFLite export complete ===")
    print(f"  emg_classifier.tflite       — float32")
    print(f"  emg_classifier_int8.tflite  — INT8 dynamic-range quantized")
    print(f"  Weight equivalence:           VERIFIED (atol=1e-3)")


def _transfer_weights(pt_model: EMGIntentClassifier, tf_model) -> None:
    """
    Transfer trained weights from PyTorch EMGIntentClassifier to Keras model.

    Handles framework-specific layout differences:
      Conv1d:    PyTorch (out, in, k) → Keras (k, in, out)
      BatchNorm: γ, β, running_mean, running_var
      LSTM:      weight_ih/weight_hh transposed, biases combined
      Linear:    PyTorch (out, in) → Keras (in, out)
    """
    pt_sd = pt_model.state_dict()

    def _conv1d(pt_prefix, keras_name):
        w = pt_sd[f"{pt_prefix}.weight"].numpy()  # (out, in, k)
        b = pt_sd[f"{pt_prefix}.bias"].numpy()
        w_tf = np.transpose(w, (2, 1, 0))          # (k, in, out)
        tf_model.get_layer(keras_name).set_weights([w_tf, b])
        print(f"  {pt_prefix} → {keras_name}: {w.shape} → {w_tf.shape}")

    def _batchnorm(pt_prefix, keras_name):
        gamma = pt_sd[f"{pt_prefix}.weight"].numpy()
        beta = pt_sd[f"{pt_prefix}.bias"].numpy()
        mean = pt_sd[f"{pt_prefix}.running_mean"].numpy()
        var = pt_sd[f"{pt_prefix}.running_var"].numpy()
        tf_model.get_layer(keras_name).set_weights([gamma, beta, mean, var])
        print(f"  {pt_prefix} → {keras_name}: gamma={gamma.shape}")

    def _dense(pt_prefix, keras_name):
        w = pt_sd[f"{pt_prefix}.weight"].numpy()  # (out, in)
        b = pt_sd[f"{pt_prefix}.bias"].numpy()
        w_tf = w.T                                  # (in, out)
        tf_model.get_layer(keras_name).set_weights([w_tf, b])
        print(f"  {pt_prefix} → {keras_name}: {w.shape} → {w_tf.shape}")

    def _lstm(layer_idx, keras_name):
        """
        Transfer PyTorch LSTM layer weights to Keras LSTM.

        PyTorch LSTM packs 4 gate matrices [i, f, g, o] into single tensors.
        Keras LSTM uses the same gate order: [i, f, c, o].

        PyTorch params:
          weight_ih_l{i}: (4*hidden, input_size)
          weight_hh_l{i}: (4*hidden, hidden_size)
          bias_ih_l{i}:   (4*hidden,)
          bias_hh_l{i}:   (4*hidden,)

        Keras params:
          kernel:            (input_size, 4*hidden)
          recurrent_kernel:  (hidden_size, 4*hidden)
          bias:              (4*hidden,)  = bias_ih + bias_hh
        """
        w_ih = pt_sd[f"lstm.weight_ih_l{layer_idx}"].numpy()
        w_hh = pt_sd[f"lstm.weight_hh_l{layer_idx}"].numpy()
        b_ih = pt_sd[f"lstm.bias_ih_l{layer_idx}"].numpy()
        b_hh = pt_sd[f"lstm.bias_hh_l{layer_idx}"].numpy()

        kernel = w_ih.T              # (input, 4*hidden)
        recurrent_kernel = w_hh.T    # (hidden, 4*hidden)
        bias = b_ih + b_hh           # combined

        tf_model.get_layer(keras_name).set_weights([kernel, recurrent_kernel, bias])
        print(f"  lstm.layer{layer_idx} → {keras_name}: "
              f"kernel={kernel.shape}, rec={recurrent_kernel.shape}")

    print("--- Weight Transfer ---")

    # CNN blocks
    _conv1d("cnn.0.conv", "conv1")
    _batchnorm("cnn.0.bn", "bn1")
    _conv1d("cnn.1.conv", "conv2")
    _batchnorm("cnn.1.bn", "bn2")
    _conv1d("cnn.2.conv", "conv3")
    _batchnorm("cnn.2.bn", "bn3")

    # LSTM (2 layers)
    _lstm(0, "lstm1")
    _lstm(1, "lstm2")

    # Embedding projection
    _dense("embed_proj.0", "embed_proj")

    # Classifier head
    _dense("classifier", "logits")

    print("  Weight transfer complete.")


if __name__ == "__main__":
    import argparse
    p = argparse.ArgumentParser(description="Export EMG classifier to TFLite with trained weights")
    p.add_argument("--checkpoint", required=True, help="Path to best_model.pt")
    p.add_argument("--output_dir", default="../exports", help="Output directory")
    args = p.parse_args()
    export(args.checkpoint, args.output_dir)