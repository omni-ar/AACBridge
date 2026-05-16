"""
k_ablation.py
Ablation study: drift detection F1 for k ∈ {1, 2, 3, 4, 5} turn windows.

For each k, we simulate the async drift detector:
  - Anchor embedding = embedding of the first turn of each conversation
  - Recent embedding = mean of last k turn embeddings (sliding window centroid)
  - Predict shift if cosine(anchor, recent) < theta
  - Ground truth = shift_indices from annotate_dailydialog.py output

Reports precision, recall, F1 per k → justifies k=3 choice in the paper.
Results saved to: drift_ablation/results/k_ablation_results.csv

Usage:
    python k_ablation.py \
        --annotated_path ../data/dailydialog_annotated.json \
        --output_csv results/k_ablation_results.csv \
        --theta 0.6
"""

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
from sentence_transformers import SentenceTransformer
from sklearn.metrics import f1_score, precision_score, recall_score


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("--annotated_path", type=str,
                   default="../data/dailydialog_annotated.json")
    p.add_argument("--output_csv", type=str,
                   default="results/k_ablation_results.csv")
    p.add_argument("--theta", type=float, default=0.6,
                   help="Cosine similarity threshold for drift prediction")
    p.add_argument("--model_name", type=str,
                   default="sentence-transformers/all-MiniLM-L6-v2")
    p.add_argument("--k_values", type=int, nargs="+", default=[1, 2, 3, 4, 5])
    return p.parse_args()


def embed_conversations(conversations: list[dict],
                         model: SentenceTransformer) -> list[np.ndarray]:
    """Pre-compute embeddings for all turns in all conversations."""
    embedded = []
    for conv in conversations:
        embs = model.encode(conv["turns"], batch_size=64, show_progress_bar=False)
        embedded.append(embs)
    return embedded


def predict_shifts_for_k(conversations: list[dict],
                           embeddings: list[np.ndarray],
                           k: int,
                           theta: float) -> tuple[np.ndarray, np.ndarray]:
    """
    For a given k, predict drift at each turn transition across all conversations.
    
    Returns:
        y_true: (N_transitions,) binary — 1 if ground-truth shift
        y_pred: (N_transitions,) binary — 1 if predicted shift
    """
    y_true_all = []
    y_pred_all = []

    for conv, embs in zip(conversations, embeddings):
        n_turns = len(conv["turns"])
        gt_shifts = set(conv["shift_indices"])

        # Anchor = embedding of the first turn
        anchor = embs[0]

        for i in range(1, n_turns):
            # Recent = mean of last min(k, i) turns
            start = max(0, i - k)
            recent = embs[start:i].mean(axis=0)

            # Cosine similarity
            sim = float(np.dot(anchor, recent) /
                        (np.linalg.norm(anchor) * np.linalg.norm(recent) + 1e-8))

            y_pred = 1 if sim < theta else 0
            y_true = 1 if i in gt_shifts else 0

            y_true_all.append(y_true)
            y_pred_all.append(y_pred)

    return np.array(y_true_all), np.array(y_pred_all)


def main() -> None:
    args = parse_args()
    output_csv = Path(args.output_csv)
    output_csv.parent.mkdir(parents=True, exist_ok=True)

    print(f"Loading annotated conversations: {args.annotated_path}")
    conversations = json.loads(Path(args.annotated_path).read_text())
    print(f"  {len(conversations)} conversations loaded")

    print(f"Loading sentence encoder: {args.model_name}")
    model = SentenceTransformer(args.model_name)

    print("Pre-computing embeddings...")
    embeddings = embed_conversations(conversations, model)

    results = []
    print(f"\nRunning k-ablation (theta={args.theta})...")

    for k in args.k_values:
        y_true, y_pred = predict_shifts_for_k(
            conversations, embeddings, k=k, theta=args.theta)

        precision = precision_score(y_true, y_pred, zero_division=0)
        recall = recall_score(y_true, y_pred, zero_division=0)
        f1 = f1_score(y_true, y_pred, zero_division=0)
        n_pred_shifts = int(y_pred.sum())
        n_true_shifts = int(y_true.sum())

        results.append({
            "k": k,
            "theta": args.theta,
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "n_true_shifts": n_true_shifts,
            "n_predicted_shifts": n_pred_shifts,
        })

        print(f"  k={k}: precision={precision:.4f}  recall={recall:.4f}  F1={f1:.4f}")

    df = pd.DataFrame(results)
    df.to_csv(output_csv, index=False)
    print(f"\nResults saved to: {output_csv}")
    print(df.to_string(index=False))

    best_k = df.loc[df["f1"].idxmax(), "k"]
    print(f"\nBest k by F1: k={best_k}")
    print("(Expected: k=3 — see MASTER_CONTEXT.md Section 4 for justification)")


if __name__ == "__main__":
    main()  