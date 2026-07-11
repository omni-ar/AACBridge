"""
generate_architecture.py — Generate publication-quality system architecture diagram.

This replaces the manually-created system_architecture.png with a
programmatically generated figure containing verified values:
  - 9 scalar-only native functions (verified from llama_jni.cpp)
  - 2.74 ms cache restoration (verified from canonical_benchmark.csv)

All component names are verified against the actual Kotlin/C++ source.
"""
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import numpy as np

# IEEE two-column figure: width ~7.16 inches, height ~5 inches
fig, ax = plt.subplots(1, 1, figsize=(10, 7.5), dpi=300)
ax.set_xlim(0, 100)
ax.set_ylim(0, 75)
ax.axis('off')
fig.patch.set_facecolor('white')

# Color scheme
BLUE = '#D6EAF8'       # Environmental Sensing
BLUE_DARK = '#2471A3'
YELLOW = '#FEF9E7'     # Context Routing
YELLOW_DARK = '#B7950B'
GREEN = '#D5F5E3'      # Background Priming
GREEN_DARK = '#1E8449'
PURPLE = '#E8DAEF'     # Multimodal Intent
PURPLE_DARK = '#7D3C98'
ORANGE = '#FDEBD0'     # Inference Engine
ORANGE_DARK = '#CA6F1E'
GRAY = '#F2F3F4'
DARK = '#2C3E50'

def add_section(x, y, w, h, color, border_color, title, items, fontsize=7):
    """Add a colored section with title and items."""
    rect = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.3",
                          facecolor=color, edgecolor=border_color, linewidth=1.5)
    ax.add_patch(rect)
    ax.text(x + w/2, y + h - 1.2, title, ha='center', va='top',
            fontsize=fontsize + 1, fontweight='bold', color=border_color,
            fontfamily='sans-serif')
    for i, item in enumerate(items):
        ax.text(x + w/2, y + h - 3.2 - i * 1.8, item, ha='center', va='top',
                fontsize=fontsize - 0.5, color=DARK, fontfamily='sans-serif')

def add_box(x, y, w, h, text, color=GRAY, border='#7F8C8D', fontsize=6.5, bold=False):
    """Add a small labeled box."""
    rect = FancyBboxPatch((x, y), w, h, boxstyle="round,pad=0.2",
                          facecolor=color, edgecolor=border, linewidth=1)
    ax.add_patch(rect)
    weight = 'bold' if bold else 'normal'
    ax.text(x + w/2, y + h/2, text, ha='center', va='center',
            fontsize=fontsize, color=DARK, fontweight=weight, fontfamily='sans-serif',
            wrap=True)

def add_arrow(x1, y1, x2, y2, label=None, color='#7F8C8D'):
    """Add an arrow with optional label."""
    ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle='->', color=color, lw=1.2))
    if label:
        mx, my = (x1+x2)/2, (y1+y2)/2
        ax.text(mx + 0.5, my + 0.5, label, fontsize=5.5, color=color,
                fontfamily='sans-serif', style='italic')

# ===== SECTION 1: Environmental Sensing (top-left) =====
add_section(1, 58, 45, 15, BLUE, BLUE_DARK,
            'Environmental Sensing',
            ['GpsCollector (Haversine distance)',
             'BleCollector (3 s scan window)',
             'TimeCollector (circular distance)',
             '→ SensorSnapshot (immutable data class)'])

# ===== SECTION 2: Context Routing & Drift (top-right) =====
add_section(52, 58, 46, 15, YELLOW, YELLOW_DARK,
            'Context Routing & Drift Detection',
            ['StateRouter: S(c) = α·S_time + β·S_gps + γ·S_ble',
             'Top-k = 3 contexts selected',
             'DriftDetector: WorkManager 15-min cycle',
             'Hysteresis gate: Δ ≥ 0.10'])

# ===== SECTION 3: Background Priming (middle) =====
add_section(1, 40, 97, 14, GREEN, GREEN_DARK,
            'Background Priming (Idle Time)',
            [])

# Add sub-boxes for priming pipeline
add_box(3, 42, 18, 5, 'prefillOnly(prompt)', GREEN, GREEN_DARK, 6.5)
add_arrow(21, 44.5, 24, 44.5, color=GREEN_DARK)
add_box(24, 42, 18, 5, 'saveKVCache(path)', GREEN, GREEN_DARK, 6.5)
add_arrow(42, 44.5, 45, 44.5, color=GREEN_DARK)
add_box(45, 42, 20, 5, 'KV Cache .bin\n(UFS 3.1 Storage)', GREEN, GREEN_DARK, 6.5)
add_arrow(65, 44.5, 68, 44.5, color=GREEN_DARK)
add_box(68, 42, 28, 5, 'KVCacheManager\n(3-slot LRU, ReentrantLock)', GREEN, GREEN_DARK, 6.5)

# Arrow: Routing → Priming
add_arrow(75, 58, 75, 51, 'select context', YELLOW_DARK)

# Arrow: Sensing → Routing
add_arrow(46, 65, 52, 65, color=BLUE_DARK)

# ===== SECTION 4: Multimodal Intent Pipeline (bottom-left) =====
add_section(1, 14, 45, 22, PURPLE, PURPLE_DARK,
            'Multimodal Intent Pipeline',
            [])

add_box(3, 27, 19, 5, 'sEMG 16-ch\n→ CNN-LSTM\n→ 64-dim embedding', PURPLE, PURPLE_DARK, 5.5)
add_box(3, 20, 19, 5, 'Front Camera\n→ MediaPipe Face Mesh\n→ 5-dim gaze', PURPLE, PURPLE_DARK, 5.5)
add_arrow(22, 29.5, 25, 24, color=PURPLE_DARK)
add_arrow(22, 22.5, 25, 24, color=PURPLE_DARK)
add_box(25, 21, 19, 7, 'Late Fusion\n(ONNX Runtime)\n45 KB model', PURPLE, PURPLE_DARK, 6)
ax.text(24, 17, 'Intent: confirm | reject |\nscroll | select | call-help',
        fontsize=5.5, color=PURPLE_DARK, fontfamily='sans-serif', style='italic')

# ===== SECTION 5: Inference Engine (bottom-right) =====
add_section(52, 14, 46, 22, ORANGE, ORANGE_DARK,
            'Inference Engine',
            [])

add_box(54, 27, 18, 5, 'loadKVCache()\n2.74 ms', ORANGE, ORANGE_DARK, 6.5, bold=True)
add_arrow(72, 29.5, 75, 29.5, color=ORANGE_DARK)
add_box(75, 27, 21, 5, 'resumeInference\n(intent tokens)', ORANGE, ORANGE_DARK, 6.5)
add_box(54, 19, 42, 5, 'llama.cpp / JNI Bridge (C++17)\nQwen2.5-0.5B-Instruct Q4_K_M\n9 scalar-only native functions', ORANGE, ORANGE_DARK, 6, bold=True)

# Arrow: Cache → Inference
add_arrow(82, 42, 72, 34, 'restore cached state', GREEN_DARK)

# Arrow: Intent → Inference
add_arrow(46, 24, 54, 29, 'intent class', PURPLE_DARK)

# Arrow: Inference → Response
add_box(68, 8, 28, 4.5, 'Generated AAC Response', '#E8F8F5', '#17A589', 7, bold=True)
add_arrow(86, 19, 82, 12.5, color=ORANGE_DARK)

# Title
ax.text(50, 74.5, 'CAP-KVC System Architecture', ha='center', va='top',
        fontsize=12, fontweight='bold', color=DARK, fontfamily='sans-serif')

plt.tight_layout(pad=0.5)
out_path = r'd:\AACBridge\paper\figures\system_architecture.png'
fig.savefig(out_path, dpi=300, bbox_inches='tight', facecolor='white',
            edgecolor='none', pad_inches=0.2)
print(f"Saved: {out_path}")

# Also save PDF
pdf_path = r'd:\AACBridge\paper\figures\system_architecture.pdf'
fig.savefig(pdf_path, bbox_inches='tight', facecolor='white',
            edgecolor='none', pad_inches=0.2)
print(f"Saved: {pdf_path}")

plt.close()
