"""
model.py
CNN-LSTM intent classifier for EMG-based AAC.

Architecture:
  Input:  (B, 16, 400)   — 16 EMG channels × 400 time samples
  
  CNN block (temporal feature extraction):
    Conv1d(16→32, k=3)  → BN → ReLU → MaxPool(2)   → (B, 32, 199)
    Conv1d(32→64, k=3)  → BN → ReLU → MaxPool(2)   → (B, 64, 98)
    Conv1d(64→128, k=3) → BN → ReLU → MaxPool(2)   → (B, 128, 48)

  LSTM block (temporal context):
    Transpose: (B, 128, 48) → (B, 48, 128)           sequence of 48 frames
    LSTM(128, hidden=128, layers=2, dropout=0.3)
    Take last hidden state: (B, 128)

  Embedding projection:
    Linear(128 → 64)  → ReLU → Dropout(0.3)
    Output: (B, 64)   ← this is the EMG embedding sent to Heer for fusion

  Classification head:
    Linear(64 → 5)
    Output: (B, 5) raw logits

  EMG embedding dim = 64  (matches EMG_EMBEDDING_SPEC.md)
"""

import torch
import torch.nn as nn
from typing import Tuple


EMG_EMBEDDING_DIM = 64   # canonical output dim — must match EMG_EMBEDDING_SPEC.md
N_CLASSES = 5


class CNNBlock(nn.Module):
    def __init__(self, in_ch: int, out_ch: int, kernel: int = 3):
        super().__init__()
        self.conv = nn.Conv1d(in_ch, out_ch, kernel_size=kernel, padding=kernel // 2)
        self.bn = nn.BatchNorm1d(out_ch)
        self.relu = nn.ReLU(inplace=True)
        self.pool = nn.MaxPool1d(kernel_size=2)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.pool(self.relu(self.bn(self.conv(x))))


class EMGIntentClassifier(nn.Module):
    """
    CNN-LSTM classifier: EMG windows → AAC intent class.

    Also exposes intermediate EMG embeddings for cross-attention fusion.
    """

    def __init__(self,
                 n_channels: int = 16,
                 n_classes: int = N_CLASSES,
                 lstm_hidden: int = 128,
                 lstm_layers: int = 2,
                 emb_dim: int = EMG_EMBEDDING_DIM,
                 dropout: float = 0.3):
        super().__init__()

        # ── CNN feature extractor ────────────────────────────────────────────
        self.cnn = nn.Sequential(
            CNNBlock(n_channels, 32),    # (B, 32, 200)
            CNNBlock(32, 64),            # (B, 64, 100)
            CNNBlock(64, 128),           # (B, 128, 50)  (exact size depends on padding)
        )

        # ── LSTM temporal context ────────────────────────────────────────────
        self.lstm = nn.LSTM(
            input_size=128,
            hidden_size=lstm_hidden,
            num_layers=lstm_layers,
            batch_first=True,
            dropout=dropout if lstm_layers > 1 else 0.0,
        )

        # ── Embedding projection ─────────────────────────────────────────────
        self.embed_proj = nn.Sequential(
            nn.Linear(lstm_hidden, emb_dim),
            nn.ReLU(inplace=True),
            nn.Dropout(dropout),
        )

        # ── Classification head ───────────────────────────────────────────────
        self.classifier = nn.Linear(emb_dim, n_classes)

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """
        Args:
            x: (B, 16, 400)
        Returns:
            logits: (B, N_CLASSES)
        """
        emb = self.encode(x)
        return self.classifier(emb)

    def encode(self, x: torch.Tensor) -> torch.Tensor:
        """
        Forward pass up to (and including) the embedding projection.
        This is the EMG embedding used by the fusion model.

        Args:
            x: (B, 16, 400)
        Returns:
            emb: (B, EMG_EMBEDDING_DIM=64)  — L2-normalised
        """
        # CNN: (B, 16, 400) → (B, 128, T')
        feat = self.cnn(x)

        # Transpose for LSTM: (B, 128, T') → (B, T', 128)
        feat = feat.permute(0, 2, 1)

        # LSTM: (B, T', 128) → last hidden (B, lstm_hidden)
        _, (h_n, _) = self.lstm(feat)
        h = h_n[-1]   # take top layer's last hidden state: (B, 128)

        # Project to embedding dim
        emb = self.embed_proj(h)   # (B, 64)

        # L2-normalise — required by EMG_EMBEDDING_SPEC.md for cross-attention
        emb = nn.functional.normalize(emb, dim=-1)
        return emb

    def forward_with_embedding(self,
                                x: torch.Tensor
                                ) -> Tuple[torch.Tensor, torch.Tensor]:
        """
        Returns both logits and embeddings in one forward pass.

        Returns:
            logits: (B, N_CLASSES)
            emb:    (B, EMG_EMBEDDING_DIM)
        """
        emb = self.encode(x)
        logits = self.classifier(emb)
        return logits, emb


def build_model(device: str = "cpu") -> EMGIntentClassifier:
    model = EMGIntentClassifier()
    model.to(device)
    return model


def count_parameters(model: nn.Module) -> int:
    return sum(p.numel() for p in model.parameters() if p.requires_grad)