import torch.nn as nn
from fusion.cross_attention import CrossAttentionFusion
from fusion.late_fusion import LateFusion

class FusionModel(nn.Module):
    """
    Unified wrapper routing to the selected architecture.
    """
    def __init__(self, mode="cross_attention", num_classes=5):
        super().__init__()
        self.mode = mode
        if mode == "cross_attention":
            self.model = CrossAttentionFusion(num_classes=num_classes)
        elif mode == "late_fusion":
            self.model = LateFusion(num_classes=num_classes)
        else:
            raise ValueError(f"Unknown fusion mode: {mode}")

    def forward(self, emg_embedding, gaze_vector):
        return self.model(emg_embedding, gaze_vector)
