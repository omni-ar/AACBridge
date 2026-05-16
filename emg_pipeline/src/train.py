"""
train.py
Training script for the CNN-LSTM EMG intent classifier.

Usage:
    python train.py --data_root ../data/raw --epochs 50 --batch_size 64

Leave-one-subject-out (LOSO) is the default evaluation protocol.
By default, subject 10 is held out as validation; 1-9 are used for training.

Outputs:
    best_model.pt  — saved to --output_dir (default: ../checkpoints/)
    wandb run      — if --use_wandb flag is set
"""

import argparse
import os
import sys
import time
from pathlib import Path

import torch
import torch.nn as nn
from torch.optim import Adam
from torch.optim.lr_scheduler import CosineAnnealingLR

sys.path.insert(0, str(Path(__file__).parent))
from dataset import make_dataloaders, N_CLASSES
from model import build_model, count_parameters


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Train CNN-LSTM EMG intent classifier")
    p.add_argument("--data_root", type=str, required=True,
                   help="Path to raw/ directory containing s1/ ... s10/")
    p.add_argument("--output_dir", type=str, default="../checkpoints",
                   help="Where to save best_model.pt")
    p.add_argument("--train_subjects", type=int, nargs="+",
                   default=list(range(1, 10)),
                   help="Subject IDs for training (default: 1-9)")
    p.add_argument("--val_subjects", type=int, nargs="+",
                   default=[10],
                   help="Subject IDs for validation (default: 10)")
    p.add_argument("--epochs", type=int, default=50)
    p.add_argument("--batch_size", type=int, default=64)
    p.add_argument("--lr", type=float, default=1e-3)
    p.add_argument("--dropout", type=float, default=0.3)
    p.add_argument("--use_wandb", action="store_true")
    p.add_argument("--seed", type=int, default=42)
    return p.parse_args()


def set_seed(seed: int) -> None:
    import random, numpy as np
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)


def train_one_epoch(model: nn.Module,
                    loader,
                    optimizer,
                    criterion,
                    device: str) -> tuple[float, float]:
    model.train()
    total_loss = 0.0
    correct = 0
    total = 0

    for x, y in loader:
        x, y = x.to(device), y.to(device)
        optimizer.zero_grad()
        logits = model(x)
        loss = criterion(logits, y)
        loss.backward()
        nn.utils.clip_grad_norm_(model.parameters(), max_norm=1.0)
        optimizer.step()

        total_loss += loss.item() * len(y)
        correct += (logits.argmax(dim=1) == y).sum().item()
        total += len(y)

    return total_loss / total, correct / total


@torch.no_grad()
def evaluate(model: nn.Module,
             loader,
             criterion,
             device: str) -> tuple[float, float]:
    model.eval()
    total_loss = 0.0
    correct = 0
    total = 0

    for x, y in loader:
        x, y = x.to(device), y.to(device)
        logits = model(x)
        loss = criterion(logits, y)
        total_loss += loss.item() * len(y)
        correct += (logits.argmax(dim=1) == y).sum().item()
        total += len(y)

    return total_loss / total, correct / total


def main() -> None:
    args = parse_args()
    set_seed(args.seed)

    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"Device: {device}")

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    # ── Data ─────────────────────────────────────────────────────────────────
    train_loader, val_loader = make_dataloaders(
        data_root=args.data_root,
        train_subjects=args.train_subjects,
        val_subjects=args.val_subjects,
        batch_size=args.batch_size,
    )

    # ── Model ─────────────────────────────────────────────────────────────────
    model = build_model(device=device)
    print(f"Parameters: {count_parameters(model):,}")

    # Class-weighted loss to handle imbalance
    train_ds = train_loader.dataset
    weights = train_ds.class_weights().to(device)
    criterion = nn.CrossEntropyLoss(weight=weights)

    optimizer = Adam(model.parameters(), lr=args.lr, weight_decay=1e-4)
    scheduler = CosineAnnealingLR(optimizer, T_max=args.epochs, eta_min=1e-5)

    # ── WandB ─────────────────────────────────────────────────────────────────
    if args.use_wandb:
        import wandb
        wandb.init(project="AACBridge-EMG", config=vars(args))

    # ── Training loop ─────────────────────────────────────────────────────────
    best_val_acc = 0.0
    best_path = output_dir / "best_model.pt"

    for epoch in range(1, args.epochs + 1):
        t0 = time.time()
        train_loss, train_acc = train_one_epoch(
            model, train_loader, optimizer, criterion, device)
        val_loss, val_acc = evaluate(model, val_loader, criterion, device)
        scheduler.step()
        elapsed = time.time() - t0

        print(f"Epoch {epoch:3d}/{args.epochs} | "
              f"train_loss={train_loss:.4f} train_acc={train_acc:.4f} | "
              f"val_loss={val_loss:.4f} val_acc={val_acc:.4f} | "
              f"{elapsed:.1f}s")

        if args.use_wandb:
            import wandb
            wandb.log({"train_loss": train_loss, "train_acc": train_acc,
                       "val_loss": val_loss, "val_acc": val_acc,
                       "lr": scheduler.get_last_lr()[0]}, step=epoch)

        if val_acc > best_val_acc:
            best_val_acc = val_acc
            torch.save({
                "epoch": epoch,
                "model_state_dict": model.state_dict(),
                "val_acc": val_acc,
                "args": vars(args),
            }, best_path)
            print(f"  ✓ Saved best model (val_acc={val_acc:.4f})")

    print(f"\nTraining complete. Best val_acc={best_val_acc:.4f}")
    print(f"Model saved to: {best_path}")

    if args.use_wandb:
        import wandb
        wandb.finish()


if __name__ == "__main__":
    main()