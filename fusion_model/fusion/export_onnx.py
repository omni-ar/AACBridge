import os
import sys
import torch

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fusion.fusion_model import FusionModel

def main():
    winner_file = "fusion_model/results/winner.txt"
    if not os.path.exists(winner_file):
        print("Winner file not found. Run ablation.py first.")
        return
        
    with open(winner_file, "r") as f:
        winner = f.read().strip()
        
    print(f"Loading winning model: {winner}")
    
    model = FusionModel(mode=winner)
    model.load_state_dict(torch.load(f"fusion_model/results/{winner}_best.pth", weights_only=True))
    model.eval()
    
    # Dummy inputs matching expected shapes
    # EMG embedding: shape (1, 1, 64) - explicitly to test the squeeze(1)
    dummy_emg = torch.randn(1, 1, 64)
    # Gaze vector: shape (1, 5)
    dummy_gaze = torch.randn(1, 5)
    
    onnx_path = "fusion_model/results/gaze_emg_fusion.onnx"
    
    print(f"Exporting ONNX to {onnx_path}")
    torch.onnx.export(
        model, 
        (dummy_emg, dummy_gaze), 
        onnx_path, 
        export_params=True,
        opset_version=14,
        do_constant_folding=True,
        input_names=['emg_embedding', 'gaze_vector'],
        output_names=['logits'],
        dynamic_axes={
            'emg_embedding': {0: 'batch_size'},
            'gaze_vector': {0: 'batch_size'},
            'logits': {0: 'batch_size'}
        },
        # Force legacy exporter — produces a single self-contained
        # .onnx file with inlined weights. The dynamo exporter
        # creates external .onnx.data files which Android's
        # assets.open() cannot resolve.
        dynamo=False
    )
    
    print("Export successful.")
    
    # Copy to assets
    import shutil
    assets_dir = "android/app/src/main/assets"
    os.makedirs(assets_dir, exist_ok=True)
    asset_path = os.path.join(assets_dir, "gaze_emg_fusion.onnx")
    shutil.copy2(onnx_path, asset_path)
    print(f"Copied ONNX to assets: {asset_path}")
    
    # ASSET TRAP EXEMPTION: This fusion model is <5MB.
    # The asset trap rule (MASTER_CONTEXT §6.2) applies ONLY to the 2.2GB
    # LLM GGUF model. Small ONNX models in assets/ are safe — identical
    # to the existing face_landmarker.task (3.7MB) already in assets/.

if __name__ == "__main__":
    main()
