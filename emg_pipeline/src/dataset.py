"""
dataset.py
NinaPro DB5 dataset loader with AAC intent label mapping.

NinaPro DB5 has 52 hand/wrist gestures across 3 exercises.
We map a subset of 5 gestures to the 5 AAC intent classes:
  confirm, reject, scroll, select, call-help

Intent mapping (from NinaPro DB5 Exercise 1 gesture indices):
  Gesture 1  → confirm    (hand open)
  Gesture 2  → reject     (hand close / fist)
  Gesture 6  → scroll     (wrist flexion)
  Gesture 7  → select     (wrist extension)
  Gesture 12 → call-help  (pinch / fine motor — closest available)

Windows with stimulus=0 (rest) are excluded from training.
Windows with unmapped gesture labels are also excluded.

Output label space: {0: confirm, 1: reject, 2: scroll, 3: select, 4: call-help}
"""

import numpy as np
import torch
from torch.utils.data import Dataset, DataLoader
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from preprocess import preprocess_mat, normalize_windows


# ── Intent label mapping ──────────────────────────────────────────────────────

# Maps NinaPro DB5 gesture index → AAC intent index
# These are Exercise 1 (E1) gesture IDs from the DB5 protocol
NINAPRO_TO_AAC: Dict[int, int] = {
    1:  0,   # hand open          → confirm
    2:  1,   # hand close (fist)  → reject
    6:  2,   # wrist flexion      → scroll
    7:  3,   # wrist extension    → select
    12: 4,   # fine pinch         → call-help
}

AAC_INTENT_NAMES = {
    0: "confirm",
    1: "reject",
    2: "scroll",
    3: "select",
    4: "call-help",
}

N_CLASSES = 5


# ── Dataset ───────────────────────────────────────────────────────────────────

class NinaProEMGDataset(Dataset):
    """
    PyTorch Dataset for NinaPro DB5 EMG windows mapped to AAC intents.

    Loads from one or more .mat files, filters to the 5 mapped gesture classes,
    applies normalization, and returns (window_tensor, label) pairs.

    Args:
        mat_paths:  list of paths to .mat files (e.g. S1_E1_A1.mat)
        normalize:  apply per-channel z-score normalization
        augment:    apply Gaussian noise augmentation during training
        noise_std:  std dev of augmentation noise (fraction of signal std)
    """

    def __init__(self,
                 mat_paths: List[str | Path],
                 normalize: bool = True,
                 augment: bool = False,
                 noise_std: float = 0.05):
        self.augment = augment
        self.noise_std = noise_std

        all_windows: List[np.ndarray] = []
        all_labels: List[np.ndarray] = []

        for path in mat_paths:
            windows, labels = preprocess_mat(path)
            windows, labels = self._filter_to_aac(windows, labels)
            if len(windows) == 0:
                continue
            all_windows.append(windows)
            all_labels.append(labels)

        if not all_windows:
            raise ValueError("No valid AAC-mapped windows found in provided .mat files.")

        self.windows = np.concatenate(all_windows, axis=0)  # (N, 400, 16)
        self.labels = np.concatenate(all_labels, axis=0)    # (N,)

        if normalize:
            self.windows = normalize_windows(self.windows)

    def _filter_to_aac(self,
                        windows: np.ndarray,
                        labels: np.ndarray
                        ) -> Tuple[np.ndarray, np.ndarray]:
        """Keep only windows whose label maps to an AAC intent."""
        mapped_gestures = list(NINAPRO_TO_AAC.keys())
        mask = np.isin(labels, mapped_gestures)
        filtered_windows = windows[mask]
        filtered_labels = np.array(
            [NINAPRO_TO_AAC[l] for l in labels[mask]], dtype=np.int64
        )
        return filtered_windows, filtered_labels

    def __len__(self) -> int:
        return len(self.labels)

    def __getitem__(self, idx: int) -> Tuple[torch.Tensor, torch.Tensor]:
        window = self.windows[idx].copy()   # (400, 16)

        if self.augment:
            noise = np.random.normal(0, self.noise_std * window.std(), window.shape)
            window = window + noise.astype(np.float32)

        # Transpose to (C, T) = (16, 400) for Conv1d input
        x = torch.from_numpy(window.T).float()          # (16, 400)
        y = torch.tensor(self.labels[idx], dtype=torch.long)
        return x, y

    def class_counts(self) -> Dict[int, int]:
        unique, counts = np.unique(self.labels, return_counts=True)
        return {int(k): int(v) for k, v in zip(unique, counts)}

    def class_weights(self) -> torch.Tensor:
        """Inverse-frequency weights for imbalanced class handling."""
        counts = self.class_counts()
        total = sum(counts.values())
        weights = torch.zeros(N_CLASSES)
        for cls, cnt in counts.items():
            weights[cls] = total / (N_CLASSES * cnt)
        return weights


# ── Loader helpers ────────────────────────────────────────────────────────────

def build_subject_paths(data_root: str | Path,
                        subjects: List[int],
                        exercises: List[str] = ["E1"]) -> List[Path]:
    """
    Build list of .mat file paths for given subjects and exercises.

    Args:
        data_root: path to raw/ directory (contains s1/, s2/, ...)
        subjects:  list of subject IDs (1-indexed)
        exercises: list of exercise keys e.g. ["E1", "E2"]

    Returns:
        list of existing .mat paths
    """
    data_root = Path(data_root)
    paths = []
    for s in subjects:
        for ex in exercises:
            p = data_root / f"s{s}" / f"S{s}_{ex}_A1.mat"
            if p.exists():
                paths.append(p)
            else:
                print(f"[WARN] Missing: {p}")
    return paths


def make_dataloaders(data_root: str | Path,
                     train_subjects: List[int],
                     val_subjects: List[int],
                     batch_size: int = 64,
                     num_workers: int = 2
                     ) -> Tuple[DataLoader, DataLoader]:
    """
    Build train and validation DataLoaders.
    Leave-one-subject-out split is the standard NinaPro evaluation protocol.

    Args:
        data_root:       path to raw/ directory
        train_subjects:  subject IDs for training (e.g. [1,2,3,4,5,6,7,8,9])
        val_subjects:    subject IDs for validation (e.g. [10])
        batch_size:      mini-batch size
        num_workers:     DataLoader worker processes

    Returns:
        train_loader, val_loader
    """
    train_paths = build_subject_paths(data_root, train_subjects)
    val_paths = build_subject_paths(data_root, val_subjects)

    train_ds = NinaProEMGDataset(train_paths, normalize=True, augment=True)
    val_ds = NinaProEMGDataset(val_paths, normalize=True, augment=False)

    train_loader = DataLoader(
        train_ds, batch_size=batch_size, shuffle=True,
        num_workers=num_workers, pin_memory=True
    )
    val_loader = DataLoader(
        val_ds, batch_size=batch_size, shuffle=False,
        num_workers=num_workers, pin_memory=True
    )

    print(f"Train: {len(train_ds)} windows | Val: {len(val_ds)} windows")
    print(f"Train class counts: {train_ds.class_counts()}")
    print(f"Val   class counts: {val_ds.class_counts()}")

    return train_loader, val_loader