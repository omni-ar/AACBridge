"""
evaluate.py
Evaluation script for the trained CNN-LSTM EMG intent classifier.

Reports:
  - Overall accuracy
  - Per-class precision, recall, F1
  - Confusion matrix (saved as PNG)
  - CPU inference latency (mean ± std over N trials) — relevant for Android deployment

Usage:
    python evaluate.py \
        --checkpoint ../checkpoints/best_model.pt \
        --data_root ../data/raw \
        --val_subjects 10 \
        --output_dir ../results
"""

import argparse
import sys
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from sklearn.metrics import (classification_report, confusion_matrix,
                              f1_score)
import matplotlib.pyplot as plt
import seaborn as sns

sys.path.insert(0, str(Path(__file__).parent))
from dataset import NinaProEMGDataset, build_subject_paths, AAC_INTENT_NAMES
from model import EMGIntentClassifier


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Evaluate EMG intent classifier")
    p.add_argument("--checkpoint", type=str, required=True)
    p.add_argument("--data_root", type=str, required=True)
    p.add_argument("--val_subjects", type=int, nargs="+", default=[10])
    p.add_argument("--output_dir", type=str, default="../results")
    p.add_argument("--latency_trials", type=int, default=200,
                   help="Number of single-sample passes to measure CPU inference latency")
    return p.parse_args()


def load_model(checkpoint_path: str) -> EMGIntentClassifier:
    ckpt = torch.load(checkpoint_path, map_location="cpu")
    model = EMGIntentClassifier()
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()
    print(f"Loaded checkpoint from epoch {ckpt['epoch']} "
          f"(val_acc={ckpt['val_acc']:.4f})")
    return model


@torch.no_grad()
def predict_all(model: nn.Module, loader) -> tuple[np.ndarray, np.ndarray]:
    all_preds, all_labels = [], []
    for x, y in loader:
        logits = model(x)
        preds = logits.argmax(dim=1).numpy()
        all_preds.append(preds)
        all_labels.append(y.numpy())
    return np.concatenate(all_preds), np.concatenate(all_labels)


def measure_cpu_latency(model: nn.Module,
                         n_trials: int = 200) -> tuple[float, float]:
    """
    Measure single-sample CPU inference latency in milliseconds.
    Relevant to Android CPU deployment for the paper's latency table.
    """
    model.eval()
    dummy = torch.randn(1, 16, 400)

    # Warm-up
    for _ in range(20):
        _ = model(dummy)

    times = []
    with torch.no_grad():
        for _ in range(n_trials):
            t0 = time.perf_counter()
            _ = model(dummy)
            times.append((time.perf_counter() - t0) * 1000)

    return float(np.mean(times)), float(np.std(times))


def plot_confusion_matrix(cm: np.ndarray,
                           class_names: list[str],
                           save_path: Path) -> None:
    fig, ax = plt.subplots(figsize=(7, 6))
    sns.heatmap(cm, annot=True, fmt="d", cmap="Blues",
                xticklabels=class_names, yticklabels=class_names, ax=ax)
    ax.set_xlabel("Predicted")
    ax.set_ylabel("True")
    ax.set_title("Confusion Matrix — EMG Intent Classifier")
    plt.tight_layout()
    fig.savefig(save_path, dpi=150)
    plt.close(fig)
    print(f"Confusion matrix saved to: {save_path}")


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # ── Load model ────────────────────────────────────────────────────────────
    model = load_model(args.checkpoint)

    # ── Load val data ─────────────────────────────────────────────────────────
    val_paths = build_subject_paths(args.data_root, args.val_subjects)
    val_ds = NinaProEMGDataset(val_paths, normalize=True, augment=False)
    val_loader = torch.utils.data.DataLoader(
        val_ds, batch_size=128, shuffle=False, num_workers=0)

    # ── Predictions ───────────────────────────────────────────────────────────
    preds, labels = predict_all(model, val_loader)

    class_names = [AAC_INTENT_NAMES[i] for i in range(5)]

    # ── Classification report ─────────────────────────────────────────────────
    report = classification_report(labels, preds, target_names=class_names, digits=4)
    print("\n=== Classification Report ===")
    print(report)

    report_path = output_dir / "classification_report.txt"
    report_path.write_text(report)
    print(f"Saved to: {report_path}")

    # ── Macro F1 ──────────────────────────────────────────────────────────────
    macro_f1 = f1_score(labels, preds, average="macro")
    print(f"Macro F1: {macro_f1:.4f}")

    # ── Confusion matrix ──────────────────────────────────────────────────────
    cm = confusion_matrix(labels, preds)
    plot_confusion_matrix(cm, class_names, output_dir / "confusion_matrix.png")

    # ── CPU latency ───────────────────────────────────────────────────────────
    mean_ms, std_ms = measure_cpu_latency(model, n_trials=args.latency_trials)
    print(f"\nCPU inference latency: {mean_ms:.2f} ± {std_ms:.2f} ms "
          f"(n={args.latency_trials}, single sample, no batching)")

    latency_path = output_dir / "cpu_latency.txt"
    latency_path.write_text(
        f"mean_ms={mean_ms:.4f}\nstd_ms={std_ms:.4f}\ntrials={args.latency_trials}\n"
    )


if __name__ == "__main__":
    main()