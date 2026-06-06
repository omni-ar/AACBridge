import torch
import torch.nn as nn

class GazeProjection(nn.Module):
    def __init__(self):
        super().__init__()
        # 6-dim gaze input: [deltaX, deltaY, abs(deltaX), abs(deltaY), magnitude, intentIndex]
        self.proj = nn.Sequential(
            nn.Linear(6, 32),
            nn.ReLU(),
            nn.Linear(32, 64)
        )
        
    def forward(self, x):
        return self.proj(x)

class CrossAttentionFusion(nn.Module):
    def __init__(self, num_classes=5):
        super().__init__()
        self.gaze_proj = GazeProjection()
        
        # Single-head cross-attention. Embed dim is 64.
        self.cross_attn = nn.MultiheadAttention(embed_dim=64, num_heads=1, batch_first=True)
        
        # Classifier
        self.classifier = nn.Sequential(
            nn.Linear(64, 32),
            nn.ReLU(),
            nn.Linear(32, num_classes)
        )

    def forward(self, emg_embedding, gaze_vector):
        """
        emg_embedding: shape (B, 1, 64) or (B, 64). 
                       Medha noted shape is (1, 1, 64) during inference, 
                       so we squeeze the middle dimension if it exists.
        gaze_vector: shape (B, 6)
        """
        # Squeeze dim 1 if shape is (B, 1, 64)
        if emg_embedding.dim() == 3 and emg_embedding.size(1) == 1:
            emg_embedding = emg_embedding.squeeze(1)
            
        # Add sequence dim for attention: (B, 1, 64)
        query = emg_embedding.unsqueeze(1)
        
        # Project gaze to 64 dim and add sequence dim: (B, 1, 64)
        gaze_proj = self.gaze_proj(gaze_vector)
        key_value = gaze_proj.unsqueeze(1)
        
        # Cross-attention: Q = EMG, K/V = Gaze
        attn_output, _ = self.cross_attn(query, key_value, key_value)
        
        # Remove sequence dim: (B, 64)
        attn_output = attn_output.squeeze(1)
        
        # Classification
        logits = self.classifier(attn_output)
        return logits
