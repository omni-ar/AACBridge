"""Vector (PDF) figures for ieee_main.tex, generated from repository data.

Inputs:
  benchmarks/results/canonical_benchmark.csv
  evaluation/prediction/tables/hysteresis_eval.csv
Outputs:
  paper/figures/prefill_scaling.pdf
  paper/figures/hysteresis_churn.pdf

Usage: python paper/make_figures.py
"""

import csv
from collections import defaultdict
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "paper" / "figures"

COL_W = 3.5  # IEEE single column, inches
BLUE, ORANGE, GREY = "#0072B2", "#D55E00", "#555555"  # Okabe-Ito

plt.rcParams.update({
    "font.family": "serif",
    "font.serif": ["Times New Roman", "Times", "DejaVu Serif"],
    "font.size": 8,
    "axes.labelsize": 8,
    "xtick.labelsize": 7,
    "ytick.labelsize": 7,
    "legend.fontsize": 7,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "pdf.fonttype": 42,
    "savefig.bbox": "tight",
    "savefig.pad_inches": 0.02,
})


def prefill_scaling():
    groups = defaultdict(list)
    with open(ROOT / "benchmarks/results/canonical_benchmark.csv", newline="") as f:
        for r in csv.DictReader(f):
            groups[(r["mode"], int(r["prompt_token_target"]))].append(r)

    def series(key):
        rows = groups[key]
        tok = int(rows[0]["prompt_tokens"])
        y = np.array([float(r["prefill_ms"]) / 1000 for r in rows])
        return tok, y

    fig, ax = plt.subplots(figsize=(COL_W, 1.9))

    rag = [series(("RAG_INLINE", n)) for n in (50, 100, 200, 500)]
    x = [t for t, _ in rag]
    med = [np.median(y) for _, y in rag]
    lo = [np.median(y) - np.percentile(y, 25) for _, y in rag]
    hi = [np.percentile(y, 75) - np.median(y) for _, y in rag]
    for t, y in rag:
        ax.scatter(np.full_like(y, t), y, s=4, color=BLUE, alpha=0.25, linewidths=0)
    ax.errorbar(x, med, yerr=[lo, hi], color=BLUE, marker="o", ms=3, lw=1, capsize=2,
                label="Inline context (prompt tokens on x)")

    t, y = series(("CAP_KVC", 50))
    ax.scatter(np.full_like(y, t), y, s=4, color=ORANGE, alpha=0.35, linewidths=0)
    ax.errorbar([t], [np.median(y)],
                yerr=[[np.median(y) - np.percentile(y, 25)], [np.percentile(y, 75) - np.median(y)]],
                color=ORANGE, marker="s", ms=3, lw=1, capsize=2,
                label="Restored 44-token context + intent")

    t, y = series(("ZERO_CONTEXT", 0))
    ax.errorbar([t], [np.median(y)],
                yerr=[[np.median(y) - np.percentile(y, 25)], [np.percentile(y, 75) - np.median(y)]],
                color=GREY, marker="^", ms=3, lw=1, capsize=2, label="Intent only, no context")

    cap_med = float(np.median(series(("CAP_KVC", 50))[1]))
    ax.annotate(f"restored context: {cap_med:.2f} s", xy=(9, cap_med), xytext=(45, 0.135),
                fontsize=7, color=ORANGE, arrowprops=dict(arrowstyle="-", color=ORANGE, lw=0.6))

    ax.set_xlabel("Tokens decoded in the prefill call")
    ax.set_ylabel("Prefill latency (s, log)")
    ax.set_yscale("log")
    ax.set_xlim(-10, 460)
    ax.set_ylim(0.1, 30)
    ax.legend(frameon=False, loc="center right", bbox_to_anchor=(1.0, 0.42))
    fig.savefig(OUT / "prefill_scaling.pdf")
    plt.close(fig)


def hysteresis_churn():
    data = defaultdict(list)
    with open(ROOT / "evaluation/prediction/tables/hysteresis_eval.csv", newline="") as f:
        for r in csv.DictReader(f):
            data[r["trace"]].append((float(r["margin"]), float(r["loads_per_day"]),
                                     float(r["served_correct"])))

    fig, ax = plt.subplots(figsize=(COL_W, 1.7))
    for trace, color, label in (("NORMAL", BLUE, "Normal noise"),
                                ("HIGH_NOISE", ORANGE, "High noise")):
        pts = sorted(data[trace])
        ax.plot([p[0] for p in pts], [p[1] for p in pts], marker="o", ms=3, lw=1,
                color=color, label=label)
    ax.axvline(0.10, color=GREY, lw=0.8, ls="--")
    ax.text(0.105, ax.get_ylim()[1] * 0.92, r"deployed $\Delta$", fontsize=7, color=GREY)
    ax.set_yscale("log")
    ax.set_xlabel(r"Hysteresis margin $\Delta$")
    ax.set_ylabel("KV states loaded per day")
    ax.legend(frameon=False, loc="upper right")
    fig.savefig(OUT / "hysteresis_churn.pdf")
    plt.close(fig)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    prefill_scaling()
    hysteresis_churn()
    print("wrote", OUT / "prefill_scaling.pdf", OUT / "hysteresis_churn.pdf")
