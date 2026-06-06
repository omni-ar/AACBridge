import os
import torch
import torch.nn as nn
import torch.optim as optim
import sys

# Ensure fusion package is importable
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from fusion.fusion_model import FusionModel
from gaze.gaze_dataset import get_dataloaders

def train_model(model, train_loader, val_loader, num_epochs=50, lr=1e-3, save_path="best_model.pth"):
    criterion = nn.CrossEntropyLoss()
    optimizer = optim.Adam(model.parameters(), lr=lr)
    
    best_val_acc = 0.0
    
    for epoch in range(num_epochs):
        model.train()
        train_loss = 0.0
        correct = 0
        total = 0
        
        for emg, gaze, labels in train_loader:
            optimizer.zero_grad()
            
            outputs = model(emg, gaze)
            loss = criterion(outputs, labels)
            
            loss.backward()
            optimizer.step()
            
            train_loss += loss.item()
            _, predicted = outputs.max(1)
            total += labels.size(0)
            correct += predicted.eq(labels).sum().item()
            
        train_acc = 100. * correct / total
        
        model.eval()
        val_loss = 0.0
        correct = 0
        total = 0
        
        with torch.no_grad():
            for emg, gaze, labels in val_loader:
                outputs = model(emg, gaze)
                loss = criterion(outputs, labels)
                
                val_loss += loss.item()
                _, predicted = outputs.max(1)
                total += labels.size(0)
                correct += predicted.eq(labels).sum().item()
                
        val_acc = 100. * correct / total
        
        if val_acc > best_val_acc:
            best_val_acc = val_acc
            torch.save(model.state_dict(), save_path)
            
        if (epoch + 1) % 10 == 0:
            print(f"Epoch [{epoch+1}/{num_epochs}] - Train Loss: {train_loss/len(train_loader):.4f}, "
                  f"Train Acc: {train_acc:.2f}% - Val Loss: {val_loss/len(val_loader):.4f}, Val Acc: {val_acc:.2f}%")
            
    print(f"Training completed. Best Validation Accuracy: {best_val_acc:.2f}%")
    return best_val_acc

def main():
    os.makedirs("fusion_model/results", exist_ok=True)
    
    train_loader, val_loader, test_loader = get_dataloaders(total_samples=2000, batch_size=32)
    
    print("--- Training Cross-Attention Model ---")
    cross_model = FusionModel(mode="cross_attention")
    train_model(cross_model, train_loader, val_loader, 
                save_path="fusion_model/results/cross_attention_best.pth")
    
    print("\n--- Training Late Fusion Model ---")
    late_model = FusionModel(mode="late_fusion")
    train_model(late_model, train_loader, val_loader, 
                save_path="fusion_model/results/late_fusion_best.pth")

if __name__ == "__main__":
    main()
