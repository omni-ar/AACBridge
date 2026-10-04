"""Prefill / generation / restore statistics for the paper (2026-10-04 run).

Input:  benchmarks/results/canonical_benchmark.csv (180 trials, 30 per condition)
Output: evaluation/latency/prefill_results.csv
        evaluation/latency/prefill_tests.csv

Why end-to-end totals are NOT used:
In this run, gaze-triggered interaction inference (resumeInference on slot 0,
45 calls in benchmarks/results/new_run.log) held the engine lock between
trials, and the trial timer started before lock acquisition. total_ms for
RAG@50/100/200 therefore includes lock-wait time (median gap
total - prefill - gen of 3.8-8.1 s). prefill_ms, gen_ms (native std::chrono
around llama_decode) and cache_load_ms (measured inside the lock) are not
affected by lock waiting.

Usage: python evaluation/latency/prefill_analysis.py
"""

import csv
from collections import defaultdict
from pathlib import Path

import numpy as np
from scipy import stats

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "benchmarks" / "results" / "canonical_benchmark.csv"
OUT_SUMMARY = ROOT / "evaluation" / "latency" / "prefill_results.csv"
OUT_TESTS = ROOT / "evaluation" / "latency" / "prefill_tests.csv"

ORDER = [("ZERO_CONTEXT", 0), ("RAG_INLINE", 50), ("RAG_INLINE", 100),
         ("RAG_INLINE", 200), ("RAG_INLINE", 500), ("CAP_KVC", 50)]


def describe(x):
    x = np.asarray(x, dtype=float)
    q1, med, q3 = np.percentile(x, [25, 50, 75])
    return dict(mean=x.mean(), sd=x.std(ddof=1), median=med, q1=q1, q3=q3)


def main():
    groups = defaultdict(list)
    with open(SRC, newline="") as f:
        for r in csv.DictReader(f):
            groups[(r["mode"], int(r["prompt_token_target"]))].append(r)

    col = lambda g, c: np.array([float(r[c]) for r in groups[g]])

    with open(OUT_SUMMARY, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["mode", "target", "n", "prompt_tokens", "metric",
                    "mean", "sd", "median", "q1", "q3"])
        for g in ORDER:
            toks = sorted({r["prompt_tokens"] for r in groups[g]})
            for metric in ("prefill_ms", "gen_ms", "cache_load_ms", "gen_tokens"):
                d = describe(col(g, metric))
                w.writerow([g[0], g[1], len(groups[g]), "|".join(toks), metric]
                           + [f"{d[k]:.3f}" for k in ("mean", "sd", "median", "q1", "q3")])

    cap = col(("CAP_KVC", 50), "prefill_ms")
    rows = []
    for g in ORDER[1:5]:
        rag = col(g, "prefill_ms")
        t = stats.ttest_ind(rag, cap, equal_var=False)
        u = stats.mannwhitneyu(rag, cap, alternative="two-sided")
        pooled = np.sqrt((rag.var(ddof=1) + cap.var(ddof=1)) / 2)
        rows.append([
            f"RAG@{g[1]} vs CAP_KVC", g[1] == 50,
            f"{rag.mean() / cap.mean():.3f}", f"{np.median(rag) / np.median(cap):.3f}",
            f"{t.statistic:.3f}", f"{t.df:.1f}", f"{t.pvalue:.3e}",
            f"{u.statistic:.1f}", f"{u.pvalue:.3e}", f"{(rag.mean() - cap.mean()) / pooled:.3f}",
        ])

    # RAG prefill cost per prompt token (OLS over all RAG trials).
    xs, ys = [], []
    for g in ORDER[1:5]:
        xs += [float(r["prompt_tokens"]) for r in groups[g]]
        ys += [float(r["prefill_ms"]) for r in groups[g]]
    fit = stats.linregress(xs, ys)

    with open(OUT_TESTS, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["comparison", "context_matched", "ratio_of_means", "ratio_of_medians",
                    "welch_t", "welch_df", "welch_p", "mannwhitney_U", "mannwhitney_p",
                    "cohens_d_pooled"])
        w.writerows(rows)
        w.writerow([])
        w.writerow(["rag_prefill_ols_ms_per_token", f"{fit.slope:.3f}",
                    "intercept_ms", f"{fit.intercept:.1f}", "r2", f"{fit.rvalue ** 2:.4f}"])

    print(OUT_SUMMARY.read_text())
    print(OUT_TESTS.read_text())


if __name__ == "__main__":
    main()
