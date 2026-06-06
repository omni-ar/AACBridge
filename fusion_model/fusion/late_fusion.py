import torch
import torch.nn as nn
from fusion.cross_attention import GazeProjection

class LateFusion(nn.Module):
    """
    Concatenation-based fallback architecture for ablation comparison.
    """
    def __init__(self, num_classes=5):
        super().__init__()
        self.gaze_proj = GazeProjection()
        
        # Concatenated dims: 64 (EMG) + 64 (Gaze) = 128
        self.classifier = nn.Sequential(
            nn.Linear(128, 64),
            nn.ReLU(),
            nn.Linear(64, num_classes)
        )

    def forward(self, emg_embedding, gaze_vector):
        """
        emg_embedding: shape (B, 1, 64) or (B, 64)
        gaze_vector: shape (B, 6)
        """
        # Squeeze dim 1 if shape is (B, 1, 64)
        if emg_embedding.dim() == 3 and emg_embedding.size(1) == 1:
            emg_embedding = emg_embedding.squeeze(1)
            
        # Project gaze to 64 dim: (B, 64)
        gaze_proj = self.gaze_proj(gaze_vector)
        
        # Concatenate: (B, 128)
        combined = torch.cat([emg_embedding, gaze_proj], dim=1)
        
        # Classification
        logits = self.classifier(combined)
        return logits
