"""
generate_paper_figures.py
Generates publication-quality figures for the AACBridge IEEE Access paper
from repository benchmark data.

Outputs:
  paper/figures/latency_comparison.png
  paper/figures/memory_budget.png
  paper/figures/drift_k_ablation.png
  paper/figures/fusion_ablation.png
"""

import csv
import os
from collections import defaultdict
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker
import numpy as np

# ── Paths ────────────────────────────────────────────────────────────────────
REPO = Path(r"d:\AACBridge")
OUT = REPO / "paper" / "figures"
OUT.mkdir(parents=True, exist_ok=True)

# ── Style ────────────────────────────────────────────────────────────────────
plt.rcParams.update({
    "font.family": "serif",
    "font.size": 9,
    "axes.labelsize": 10,
    "axes.titlesize": 11,
    "xtick.labelsize": 8,
    "ytick.labelsize": 8,
    "legend.fontsize": 8,
    "figure.dpi": 300,
    "savefig.dpi": 300,
    "savefig.bbox": "tight",
    "savefig.pad_inches": 0.05,
})

# IEEE-safe grayscale-friendly palette
COLORS = {
    "zero": "#2c3e50",
    "rag":  "#7f8c8d",
    "cap":  "#e74c3c",
}
HATCH_RAG = "///"
HATCH_CAP = "xxx"


# ═══════════════════════════════════════════════════════════════════════════════
# Figure A: Latency Comparison
# ═══════════════════════════════════════════════════════════════════════════════
def generate_latency_figure():
    data = defaultdict(list)
    cache_loads = []

    with open(REPO / "benchmarks" / "results" / "ttft_clean.csv", "r") as f:
        reader = csv.DictReader(f)
        for row in reader:
            mode = row["mode"]
            target = int(row["prompt_token_target"])
            key = (mode, target)
            data[key].append(float(row["inference_ms"]))
            if mode == "CAP_KVC":
                cache_loads.append(float(row["cache_load_ms"]))

    # Compute means and stds
    labels = []
    means = []
    stds = []
    colors = []
    hatches = []

    conditions = [
        (("ZERO_CONTEXT", 0), "No\nContext", COLORS["zero"], ""),
        (("RAG_INLINE", 50), "RAG\nN≈50", COLORS["rag"], HATCH_RAG),
        (("RAG_INLINE", 100), "RAG\nN≈100", COLORS["rag"], HATCH_RAG),
        (("RAG_INLINE", 200), "RAG\nN≈200", COLORS["rag"], HATCH_RAG),
        (("RAG_INLINE", 500), "RAG\nN≈500", COLORS["rag"], HATCH_RAG),
    ]

    for key, label, color, hatch in conditions:
        vals = data[key]
        labels.append(label)
        means.append(np.mean(vals))
        stds.append(np.std(vals, ddof=1))
        colors.append(color)
        hatches.append(hatch)

    # CAP_KVC: total = inference + cache load
    cap_totals = [data[("CAP_KVC", 50)][i] + cache_loads[i]
                  for i in range(len(cache_loads))]
    labels.append("CAP-\nKVC")
    means.append(np.mean(cap_totals))
    stds.append(np.std(cap_totals, ddof=1))
    colors.append(COLORS["cap"])
    hatches.append(HATCH_CAP)

    fig, ax = plt.subplots(figsize=(5.5, 3.2))
    x = np.arange(len(labels))
    bars = ax.bar(x, means, yerr=stds, width=0.65,
                  color=colors, edgecolor="black", linewidth=0.6,
                  capsize=3, error_kw={"linewidth": 0.8})
    for bar, h in zip(bars, hatches):
        bar.set_hatch(h)

    ax.set_xticks(x)
    ax.set_xticklabels(labels)
    ax.set_ylabel("End-to-End Inference Latency (ms)")
    ax.set_title("Inference Latency Across Pipeline Conditions")
    ax.yaxis.set_major_formatter(ticker.FuncFormatter(lambda v, _: f"{v:,.0f}"))

    # Annotate CAP-KVC speedup
    rag500_mean = np.mean(data[("RAG_INLINE", 500)])
    cap_mean = np.mean(cap_totals)
    speedup = rag500_mean / cap_mean
    ax.annotate(
        f"{speedup:.2f}\u00d7 speedup",
        xy=(5, cap_mean + stds[-1] + 200),
        fontsize=7.5, ha="center", color=COLORS["cap"],
        fontweight="bold"
    )

    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.set_xlim(-0.5, len(labels) - 0.5)

    plt.tight_layout()
    fig.savefig(OUT / "latency_comparison.png")
    fig.savefig(OUT / "latency_comparison.pdf")
    plt.close(fig)
    print(f"  [OK] latency_comparison.png/pdf")


# ═══════════════════════════════════════════════════════════════════════════════
# Figure A2: Latency Breakdown (Prefill vs Generation)
# ═══════════════════════════════════════════════════════════════════════════════
def generate_latency_breakdown():
    """Stacked bar chart showing prefill and generation decomposition."""
    enriched = REPO / "benchmarks" / "results" / "ttft_enriched.csv"
    data = defaultdict(lambda: {"prefill": [], "gen": [], "cache_load": []})

    with open(enriched, "r") as f:
        reader = csv.DictReader(f)
        for row in reader:
            mode = row["mode"]
            target = int(row["prompt_token_target"])
            key = (mode, target)
            data[key]["prefill"].append(float(row["prefill_ms"]))
            data[key]["gen"].append(float(row["gen_ms"]))
            if mode == "CAP_KVC":
                data[key]["cache_load"].append(float(row.get("cache_load_ms", 0)))

    conditions = [
        (("ZERO_CONTEXT", 0), "No\nContext"),
        (("RAG_INLINE", 50), "RAG\nN\u224850"),
        (("RAG_INLINE", 100), "RAG\nN\u2248100"),
        (("RAG_INLINE", 200), "RAG\nN\u2248200"),
        (("RAG_INLINE", 500), "RAG\nN\u2248500"),
        (("CAP_KVC", 50), "CAP-\nKVC"),
    ]

    labels = [c[1] for c in conditions]
    prefill_means = []
    gen_means = []
    prefill_stds = []
    gen_stds = []

    for key, _ in conditions:
        d = data[key]
        prefill_means.append(np.mean(d["prefill"]))
        gen_means.append(np.mean(d["gen"]))
        prefill_stds.append(np.std(d["prefill"], ddof=1))
        gen_stds.append(np.std(d["gen"], ddof=1))

    fig, ax = plt.subplots(figsize=(5.5, 3.2))
    x = np.arange(len(labels))
    width = 0.65

    # Stacked: prefill on bottom, gen on top
    bar_colors_pf = [COLORS["zero"]] + [COLORS["rag"]] * 4 + [COLORS["cap"]]
    bar_colors_gen = ["#5d6d7e"] + ["#b2bec3"] * 4 + ["#f1948a"]

    bars_pf = ax.bar(x, prefill_means, width, label="Prefill",
                     color=bar_colors_pf, edgecolor="black", linewidth=0.6)
    bars_gen = ax.bar(x, gen_means, width, bottom=prefill_means,
                      label="Generation", color=bar_colors_gen,
                      edgecolor="black", linewidth=0.6)

    # Error bars on total
    totals = [p + g for p, g in zip(prefill_means, gen_means)]
    total_stds = [np.sqrt(ps**2 + gs**2) for ps, gs in zip(prefill_stds, gen_stds)]
    ax.errorbar(x, totals, yerr=total_stds, fmt="none", ecolor="black",
               capsize=3, linewidth=0.8)

    ax.set_xticks(x)
    ax.set_xticklabels(labels)
    ax.set_ylabel("Latency (ms)")
    ax.set_title("Latency Breakdown: Prefill vs Generation")
    ax.yaxis.set_major_formatter(ticker.FuncFormatter(lambda v, _: f"{v:,.0f}"))
    ax.legend(loc="upper left", framealpha=0.9)

    # Annotate prefill speedup
    cap_pf = prefill_means[-1]
    rag500_pf = prefill_means[4]
    speedup = rag500_pf / cap_pf
    ax.annotate(
        f"{speedup:.1f}\u00d7 prefill\nspeedup",
        xy=(5, totals[-1] + total_stds[-1] + 200),
        fontsize=7.5, ha="center", color=COLORS["cap"],
        fontweight="bold"
    )

    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.set_xlim(-0.5, len(labels) - 0.5)

    plt.tight_layout()
    fig.savefig(OUT / "latency_breakdown.png")
    fig.savefig(OUT / "latency_breakdown.pdf")
    plt.close(fig)
    print(f"  [OK] latency_breakdown.png/pdf")

# ═══════════════════════════════════════════════════════════════════════════════
# Figure B: Memory Budget
# ═══════════════════════════════════════════════════════════════════════════════
def generate_memory_figure():
    idle_kb = 18856
    loaded_kb = 140420
    delta_kb = loaded_kb - idle_kb

    fig, ax = plt.subplots(figsize=(3.5, 3.0))
    x = [0, 1]
    vals = [idle_kb / 1024, loaded_kb / 1024]  # Convert to MB
    bar_colors = ["#2c3e50", "#e74c3c"]

    bars = ax.bar(x, vals, width=0.55, color=bar_colors,
                  edgecolor="black", linewidth=0.6)

    ax.set_xticks(x)
    ax.set_xticklabels(["Idle\n(0 states)", "Loaded\n(3 states)"])
    ax.set_ylabel("Native Heap (MB)")
    ax.set_title("Memory Budget: KV Cache Residency")

    # Annotate delta
    ax.annotate(
        f"Δ = {delta_kb/1024:.1f} MB",
        xy=(0.5, (idle_kb/1024 + loaded_kb/1024) / 2),
        fontsize=8, ha="center", fontweight="bold", color="#555"
    )

    # Value labels on bars
    for bar, val in zip(bars, vals):
        ax.text(bar.get_x() + bar.get_width() / 2, bar.get_height() + 2,
                f"{val:.1f}", ha="center", va="bottom", fontsize=8)

    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)
    ax.set_ylim(0, max(vals) * 1.15)

    plt.tight_layout()
    fig.savefig(OUT / "memory_budget.png")
    fig.savefig(OUT / "memory_budget.pdf")
    plt.close(fig)
    print(f"  [OK] memory_budget.png/pdf")


# ═══════════════════════════════════════════════════════════════════════════════
# Figure C: Drift k-Ablation
# ═══════════════════════════════════════════════════════════════════════════════
def generate_drift_figure():
    ks = []
    f1s = []
    precs = []
    recs = []

    csv_path = REPO / "emg_pipeline" / "drift_ablation" / "results" / "k_ablation_results.csv"
    with open(csv_path, "r") as f:
        reader = csv.DictReader(f)
        for row in reader:
            ks.append(int(row["k"]))
            f1s.append(float(row["f1"]))
            precs.append(float(row["precision"]))
            recs.append(float(row["recall"]))

    fig, ax = plt.subplots(figsize=(4.0, 2.8))

    ax.plot(ks, f1s, "o-", color="#2c3e50", linewidth=1.5, markersize=5, label="F1")
    ax.plot(ks, precs, "s--", color="#7f8c8d", linewidth=1.0, markersize=4, label="Precision")
    ax.plot(ks, recs, "^--", color="#95a5a6", linewidth=1.0, markersize=4, label="Recall")

    ax.set_xlabel("Window Size $k$")
    ax.set_ylabel("Score")
    ax.set_title("Drift Detection: k-Ablation on DailyDialog")
    ax.set_xticks(ks)
    ax.set_ylim(-0.02, 0.45)
    ax.legend(loc="upper right", framealpha=0.9)

    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)

    plt.tight_layout()
    fig.savefig(OUT / "drift_k_ablation.png")
    fig.savefig(OUT / "drift_k_ablation.pdf")
    plt.close(fig)
    print(f"  [OK] drift_k_ablation.png/pdf")


# ═══════════════════════════════════════════════════════════════════════════════
# Figure D: Fusion Ablation
# ═══════════════════════════════════════════════════════════════════════════════
def generate_fusion_figure():
    csv_path = REPO / "fusion_model" / "results" / "fusion_ablation.csv"
    models = []
    accs = []
    f1s = []

    with open(csv_path, "r") as f:
        reader = csv.DictReader(f)
        for row in reader:
            models.append(row["Model"].replace("_", "\n"))
            accs.append(float(row["Accuracy"]))
            f1s.append(float(row["F1 (Macro)"]))

    fig, ax = plt.subplots(figsize=(4.0, 2.8))
    x = np.arange(len(models))
    width = 0.3

    bars1 = ax.bar(x - width/2, accs, width, label="Accuracy",
                   color="#2c3e50", edgecolor="black", linewidth=0.6)
    bars2 = ax.bar(x + width/2, f1s, width, label="F1 (Macro)",
                   color="#e74c3c", edgecolor="black", linewidth=0.6)

    ax.set_xticks(x)
    ax.set_xticklabels(models, fontsize=8)
    ax.set_ylabel("Score")
    ax.set_title("Fusion Architecture Ablation (Synthetic Data)")
    ax.set_ylim(0.90, 1.005)
    ax.legend(loc="lower left", framealpha=0.9)

    # Value annotations
    for bar in bars1:
        ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.002,
                f"{bar.get_height():.3f}", ha="center", va="bottom", fontsize=7)
    for bar in bars2:
        ax.text(bar.get_x() + bar.get_width()/2, bar.get_height() + 0.002,
                f"{bar.get_height():.3f}", ha="center", va="bottom", fontsize=7)

    ax.spines["top"].set_visible(False)
    ax.spines["right"].set_visible(False)

    plt.tight_layout()
    fig.savefig(OUT / "fusion_ablation.png")
    fig.savefig(OUT / "fusion_ablation.pdf")
    plt.close(fig)
    print(f"  [OK] fusion_ablation.png/pdf")


# ═══════════════════════════════════════════════════════════════════════════════
if __name__ == "__main__":
    print("Generating paper figures...")
    generate_latency_figure()
    generate_latency_breakdown()
    generate_memory_figure()
    generate_drift_figure()
    generate_fusion_figure()
    print("\nAll figures generated in:", OUT)
