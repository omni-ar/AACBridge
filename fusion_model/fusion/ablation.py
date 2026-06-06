import os
import torch
import csv
import sys
from sklearn.metrics import f1_score, accuracy_score, precision_score, recall_score
import time

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fusion.fusion_model import FusionModel
from gaze.gaze_dataset import get_dataloaders

def evaluate_model(model, test_loader):
    model.eval()
    all_preds = []
    all_labels = []
    
    start_time = time.time()
    with torch.no_grad():
        for emg, gaze, labels in test_loader:
            outputs = model(emg, gaze)
            _, predicted = outputs.max(1)
            all_preds.extend(predicted.numpy())
            all_labels.extend(labels.numpy())
            
    inference_time = (time.time() - start_time) / len(test_loader.dataset) * 1000 # ms per sample
            
    acc = accuracy_score(all_labels, all_preds)
    f1 = f1_score(all_labels, all_preds, average='macro')
    precision = precision_score(all_labels, all_preds, average='macro', zero_division=0)
    recall = recall_score(all_labels, all_preds, average='macro', zero_division=0)
    
    return acc, f1, precision, recall, inference_time

def main():
    _, _, test_loader = get_dataloaders(total_samples=2000, batch_size=32)
    
    # Evaluate Cross-Attention
    cross_model = FusionModel(mode="cross_attention")
    cross_model.load_state_dict(torch.load("fusion_model/results/cross_attention_best.pth", weights_only=True))
    cross_metrics = evaluate_model(cross_model, test_loader)
    
    # Evaluate Late Fusion
    late_model = FusionModel(mode="late_fusion")
    late_model.load_state_dict(torch.load("fusion_model/results/late_fusion_best.pth", weights_only=True))
    late_metrics = evaluate_model(late_model, test_loader)
    
    results = [
        {"Model": "Cross-Attention", "Accuracy": cross_metrics[0], "F1 (Macro)": cross_metrics[1], 
         "Precision": cross_metrics[2], "Recall": cross_metrics[3], "Latency (ms)": cross_metrics[4]},
        {"Model": "Late Fusion", "Accuracy": late_metrics[0], "F1 (Macro)": late_metrics[1], 
         "Precision": late_metrics[2], "Recall": late_metrics[3], "Latency (ms)": late_metrics[4]}
    ]
    
    print("\n--- Ablation Results ---")
    for r in results:
        print(f"{r['Model']}: F1={r['F1 (Macro)']:.4f}, Acc={r['Accuracy']:.4f}, Latency={r['Latency (ms)']:.2f}ms")
        
    # Write to CSV
    os.makedirs("fusion_model/results", exist_ok=True)
    with open("fusion_model/results/fusion_ablation.csv", "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=results[0].keys())
        writer.writeheader()
        writer.writerows(results)
        
    # Decision Logic
    cross_f1 = cross_metrics[1]
    late_f1 = late_metrics[1]
    
    if cross_f1 - late_f1 > 0.03:
        winner = "cross_attention"
        reason = f"Cross-attention F1 ({cross_f1:.4f}) exceeds Late-fusion F1 ({late_f1:.4f}) by > 3% margin."
    else:
        winner = "late_fusion"
        reason = f"Cross-attention F1 ({cross_f1:.4f}) did not exceed Late-fusion F1 ({late_f1:.4f}) by > 3%. Falling back to simpler model."
        
    print(f"\nDECISION: Exporting {winner} model.")
    print(f"Reason: {reason}")
    
    with open("fusion_model/results/winner.txt", "w") as f:
        f.write(winner)

if __name__ == "__main__":
    main()
