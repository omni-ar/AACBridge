"""
annotate_dailydialog.py
Annotate DailyDialog conversations with topic-shift boundaries.

Uses all-MiniLM-L6-v2 to compute sentence embeddings for consecutive turns,
then flags a topic shift when cosine similarity drops below a threshold.

Output: emg_pipeline/data/dailydialog_annotated.json
  List of conversations, each with:
    - turns: list of utterance strings
    - shift_indices: list of turn indices where a topic shift occurs

Usage:
    python annotate_dailydialog.py \
        --output ../data/dailydialog_annotated.json \
        --threshold 0.4 \
        --n_conversations 150
"""

import argparse
import json
from pathlib import Path

import numpy as np
from sentence_transformers import SentenceTransformer
from sklearn.metrics.pairwise import cosine_similarity


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser()
    p.add_argument("--output", type=str,
                   default="../data/dailydialog_annotated.json")
    p.add_argument("--threshold", type=float, default=0.4,
                   help="Cosine similarity below this → topic shift boundary")
    p.add_argument("--n_conversations", type=int, default=150,
                   help="Number of DailyDialog conversations to annotate")
    p.add_argument("--model_name", type=str,
                   default="sentence-transformers/all-MiniLM-L6-v2")
    return p.parse_args()


def load_dailydialog(n: int) -> list[list[str]]:
    """Load first n conversations from DailyDialog via HuggingFace datasets."""
    from datasets import load_dataset
    ds = load_dataset("daily_dialog", split="train", trust_remote_code=True)
    conversations = []
    for item in ds:
        if len(conversations) >= n:
            break
        turns = [t.strip() for t in item["dialog"] if t.strip()]
        if len(turns) >= 4:   # skip very short conversations
            conversations.append(turns)
    return conversations


def detect_shifts(turns: list[str],
                  model: SentenceTransformer,
                  threshold: float) -> list[int]:
    """
    Return list of turn indices where a topic shift occurs.
    A shift at index i means the transition from turn i-1 → turn i is a shift.
    """
    if len(turns) < 2:
        return []

    embeddings = model.encode(turns, batch_size=32, show_progress_bar=False)

    shifts = []
    for i in range(1, len(turns)):
        sim = cosine_similarity(
            embeddings[i-1].reshape(1, -1),
            embeddings[i].reshape(1, -1)
        )[0][0]
        if sim < threshold:
            shifts.append(i)

    return shifts


def main() -> None:
    args = parse_args()
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    print(f"Loading {args.n_conversations} DailyDialog conversations...")
    conversations = load_dailydialog(args.n_conversations)
    print(f"Loaded {len(conversations)} conversations")

    print(f"Loading sentence encoder: {args.model_name}")
    model = SentenceTransformer(args.model_name)

    annotated = []
    total_shifts = 0

    for i, turns in enumerate(conversations):
        shifts = detect_shifts(turns, model, threshold=args.threshold)
        annotated.append({
            "conversation_id": i,
            "turns": turns,
            "shift_indices": shifts,
            "n_turns": len(turns),
            "n_shifts": len(shifts),
        })
        total_shifts += len(shifts)

        if (i + 1) % 20 == 0:
            print(f"  Annotated {i+1}/{len(conversations)} conversations...")

    output_path.write_text(json.dumps(annotated, indent=2))

    print(f"\nDone. Annotated {len(annotated)} conversations.")
    print(f"Total topic shifts detected: {total_shifts}")
    print(f"Avg shifts per conversation: {total_shifts / len(annotated):.2f}")
    print(f"Saved to: {output_path}")


if __name__ == "__main__":
    main()