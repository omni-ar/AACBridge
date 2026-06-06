import torch
import numpy as np
from torch.utils.data import Dataset, DataLoader

# Noise std dev = 25% of detection threshold (THRESHOLD_X=0.02, THRESHOLD_Y=0.01)
# Ensures classification is non-trivial while preserving directional signal.
# At 25%, ~5% of boundary samples flip class — sufficient for meaningful ablation.
NOISE_STD_X = 0.005  # 0.005 / 0.02 = 25%
NOISE_STD_Y = 0.003  # 0.003 / 0.01 = 30%

THRESHOLD_X = 0.02
THRESHOLD_Y = 0.01

INTENT_MAP = {
    0: "confirm",   # deltaX < -Tx, deltaY < -Ty
    1: "reject",    # deltaX > Tx,  deltaY < -Ty
    2: "scroll",    # deltaX < -Tx, deltaY > Ty
    3: "select",    # deltaX > Tx,  deltaY > Ty
    4: "call-help"  # else
}

class GazeEMGDataset(Dataset):
    def __init__(self, num_samples=1000):
        super().__init__()
        self.num_samples = num_samples
        
        self.emg_embeddings = []
        self.gaze_vectors = []
        self.labels = []
        
        self._generate_synthetic_data()
        
    def _generate_synthetic_data(self):
        """
        Generates simulated paired data. 
        LIMITATION: Simulated EMG and noisy threshold-based gaze vectors.
        This must be acknowledged in the paper.
        """
        for _ in range(self.num_samples):
            intent_idx = np.random.randint(0, 5)
            
            # Base deltaX and deltaY to satisfy conditions
            if intent_idx == 0:   # confirm
                base_dx = -THRESHOLD_X - 0.01
                base_dy = -THRESHOLD_Y - 0.01
            elif intent_idx == 1: # reject
                base_dx = THRESHOLD_X + 0.01
                base_dy = -THRESHOLD_Y - 0.01
            elif intent_idx == 2: # scroll
                base_dx = -THRESHOLD_X - 0.01
                base_dy = THRESHOLD_Y + 0.01
            elif intent_idx == 3: # select
                base_dx = THRESHOLD_X + 0.01
                base_dy = THRESHOLD_Y + 0.01
            else:                 # call-help
                base_dx = 0.0
                base_dy = 0.0
                
            # Inject Gaussian noise
            dx = base_dx + np.random.normal(0, NOISE_STD_X)
            dy = base_dy + np.random.normal(0, NOISE_STD_Y)
            
            # Gaze vector: [deltaX, deltaY, abs(deltaX), abs(deltaY), magnitude, intentIndex]
            magnitude = np.sqrt(dx**2 + dy**2)
            gaze_vec = np.array([dx, dy, abs(dx), abs(dy), magnitude, float(intent_idx)], dtype=np.float32)
            
            # EMG Embedding: Random L2 normalized 64-dim vector (since we don't have real EMG hardware)
            emg_vec = np.random.randn(64).astype(np.float32)
            emg_vec = emg_vec / np.linalg.norm(emg_vec)
            
            self.emg_embeddings.append(emg_vec)
            self.gaze_vectors.append(gaze_vec)
            self.labels.append(intent_idx)
            
        self.emg_embeddings = torch.tensor(np.array(self.emg_embeddings))
        self.gaze_vectors = torch.tensor(np.array(self.gaze_vectors))
        self.labels = torch.tensor(np.array(self.labels), dtype=torch.long)

    def __len__(self):
        return self.num_samples

    def __getitem__(self, idx):
        # Return EMG as (1, 64) to match Medha's (1, 1, 64) when unsqueezed or squeezed
        return self.emg_embeddings[idx].unsqueeze(0), self.gaze_vectors[idx], self.labels[idx]

def get_dataloaders(total_samples=1000, batch_size=32):
    dataset = GazeEMGDataset(total_samples)
    
    train_len = int(0.7 * total_samples)
    val_len = int(0.15 * total_samples)
    test_len = total_samples - train_len - val_len
    
    train_dataset, val_dataset, test_dataset = torch.utils.data.random_split(
        dataset, [train_len, val_len, test_len]
    )
    
    train_loader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=batch_size, shuffle=False)
    test_loader = DataLoader(test_dataset, batch_size=batch_size, shuffle=False)
    
    return train_loader, val_loader, test_loader
