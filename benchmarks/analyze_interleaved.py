#!/usr/bin/env python3
"""
Parse the interleaved (randomized-order) benchmark log into CSVs and
summary statistics.

Input : benchmarks/raw_logs/full_interleaved_20261005.log
Output: benchmarks/results/interleaved_20261005_trials.csv
        benchmarks/results/interleaved_20261005_summary.csv
        (summary also printed to stdout)

Checks:
  - every round has each condition exactly once (6 per round)
  - no interaction inference (HIT_*/FALLBACK_BUSY) during the suite
  - position effect: prefill vs. position-in-round (Spearman)
  - thermal: max-zone temperature range, correlation with prefill
"""
import csv
import re
import statistics as st
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
LOG = ROOT / "raw_logs" / "full_interleaved_20261005.log"
OUT = ROOT / "results"
OUT.mkdir(exist_ok=True)

ts_re = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)")


def ci95(xs):
    if len(xs) < 2:
        return 0.0
    # t(0.975, df=29) = 2.045
    t = 2.045 if len(xs) == 30 else 1.96
    return t * st.stdev(xs) / len(xs) ** 0.5


def spearman(x, y):
    def rank(v):
        order = sorted(range(len(v)), key=lambda i: v[i])
        r = [0.0] * len(v)
        i = 0
        while i < len(v):
            j = i
            while j + 1 < len(v) and v[order[j + 1]] == v[order[i]]:
                j += 1
            for k in range(i, j + 1):
                r[order[k]] = (i + j) / 2 + 1
            i = j + 1
        return r
    rx, ry = rank(x), rank(y)
    mx, my = st.mean(rx), st.mean(ry)
    num = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    den = (sum((a - mx) ** 2 for a in rx) * sum((b - my) ** 2 for b in ry)) ** 0.5
    return num / den if den else 0.0


def main():
    lines = LOG.read_text(encoding="utf-8-sig", errors="replace").splitlines()

    in_suite = False
    contaminating = []
    blocked = 0
    order = {}      # (round, mode, target) -> position
    thermal = {}    # (round, mode, target) -> (batt, zone, freq)
    trials = []

    for ln in lines:
        if "BENCHMARK SUITE START" in ln:
            in_suite = True
        elif "BENCHMARK SUITE COMPLETE" in ln:
            in_suite = False
        if in_suite and "INTERACTION," in ln:
            if "FALLBACK_BENCHMARK_ACTIVE" in ln:
                blocked += 1
            elif "outcome=HIT" in ln or "FALLBACK_BUSY" in ln or "WRONG" in ln:
                contaminating.append(ln)
        if in_suite and "resumeInference: slot=0" in ln:
            contaminating.append(ln)

        i = ln.find("ORDER,")
        if i >= 0:
            _, r, pos, mode, tgt = ln[i:].strip().split(",")
            order[(int(r), mode, int(tgt))] = int(pos)
            continue
        i = ln.find("THERMAL,")
        if i >= 0:
            p = ln[i:].strip().split(",")
            thermal[(int(p[1]), p[2], int(p[3]))] = (float(p[4]), float(p[5]), int(p[6]))
            continue
        i = ln.find("BENCH,")
        if i >= 0 and "BENCH,trial" not in ln:
            p = ln[i:].strip().split(",")
            trials.append(dict(
                round=int(p[1]), mode=p[2], state=p[3], target=int(p[4]),
                total_ms=float(p[5]), prefill_ms=float(p[6]), gen_ms=float(p[7]),
                cache_load_ms=float(p[8]), prompt_tokens=int(p[9]), gen_tokens=int(p[10]),
            ))

    for t in trials:
        k = (t["round"], t["mode"], t["target"])
        t["position"] = order.get(k, -1)
        b, z, f = thermal.get(k, (-1.0, -1.0, -1))
        t["battery_c"], t["max_zone_c"], t["cpu7_khz"] = b, z, f

    with open(OUT / "interleaved_20261005_trials.csv", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(trials[0].keys()))
        w.writeheader()
        w.writerows(trials)

    # ---- integrity ----
    per_round = defaultdict(set)
    for t in trials:
        per_round[t["round"]].add((t["mode"], t["target"]))
    rounds_ok = all(len(v) == 6 for v in per_round.values())
    print(f"Trials parsed: {len(trials)}  rounds: {len(per_round)}  all rounds complete: {rounds_ok}")
    print(f"Gaze events blocked during suite: {blocked}")
    print(f"Contaminating events during suite: {len(contaminating)}")
    for c in contaminating[:5]:
        print("   ", c[:160])

    # ---- summary ----
    groups = defaultdict(list)
    for t in trials:
        groups[(t["mode"], t["target"])].append(t)
    keys = sorted(groups, key=lambda k: (["ZERO_CONTEXT", "RAG_INLINE", "CAP_KVC"].index(k[0]), k[1]))

    rows = []
    print("\nmode           N    n  prompt_tok  prefill mean ± CI95 (median)     total mean     restore mean")
    for k in keys:
        g = groups[k]
        pf = [x["prefill_ms"] for x in g]
        tot = [x["total_ms"] for x in g]
        cl = [x["cache_load_ms"] for x in g]
        ptok = st.median([x["prompt_tokens"] for x in g])
        row = dict(
            mode=k[0], target=k[1], n=len(g), prompt_tokens=ptok,
            prefill_mean=round(st.mean(pf), 2), prefill_ci95=round(ci95(pf), 2),
            prefill_median=round(st.median(pf), 2), prefill_sd=round(st.stdev(pf), 2),
            total_mean=round(st.mean(tot), 2), total_ci95=round(ci95(tot), 2),
            gen_mean=round(st.mean([x["gen_ms"] for x in g]), 2),
            restore_mean=round(st.mean(cl), 3), restore_sd=round(st.stdev(cl), 3),
        )
        rows.append(row)
        print(f"{k[0]:<14}{k[1]:>4}{len(g):>5}{ptok:>10}   {row['prefill_mean']:>8.1f} ± {row['prefill_ci95']:<6.1f} ({row['prefill_median']:.1f})"
              f"   {row['total_mean']:>10.1f}   {row['restore_mean']:>8.3f}")

    with open(OUT / "interleaved_20261005_summary.csv", "w", newline="") as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    # ---- matched comparison ----
    rag50 = st.mean([x["prefill_ms"] for x in groups[("RAG_INLINE", 50)]])
    cap = st.mean([x["prefill_ms"] for x in groups[("CAP_KVC", 50)]])
    print(f"\nMatched prefill RAG@50 -> CAP_KVC: {rag50:.1f} -> {cap:.1f} ms  ({rag50 / cap:.2f}x, saved {rag50 - cap:.1f} ms)")

    # ---- position + thermal effects ----
    print("\nPosition-in-round effect (Spearman rho, prefill vs position):")
    for k in keys:
        g = groups[k]
        print(f"   {k[0]:<14}{k[1]:>4}: rho={spearman([x['position'] for x in g], [x['prefill_ms'] for x in g]):+.2f}")

    zones = [t["max_zone_c"] for t in trials if t["max_zone_c"] > 0]
    freqs = [t["cpu7_khz"] for t in trials if t["cpu7_khz"] > 0]
    batt = [t["battery_c"] for t in trials if t["battery_c"] > 0]
    print(f"\nThermal: max-zone {min(zones):.1f}-{max(zones):.1f} C (median {st.median(zones):.1f}), n={len(zones)}")
    print(f"cpu7 freq: {min(freqs)}-{max(freqs)} kHz (median {st.median(freqs)})")
    print(f"battery temp readable: {len(batt)}/{len(trials)}")
    print("Thermal vs prefill (Spearman rho) per condition:")
    for k in keys:
        g = [x for x in groups[k] if x["max_zone_c"] > 0]
        print(f"   {k[0]:<14}{k[1]:>4}: rho={spearman([x['max_zone_c'] for x in g], [x['prefill_ms'] for x in g]):+.2f}")
    early = [t["max_zone_c"] for t in trials if t["round"] <= 5 and t["max_zone_c"] > 0]
    late = [t["max_zone_c"] for t in trials if t["round"] >= 26 and t["max_zone_c"] > 0]
    print(f"Zone temp rounds 1-5 mean {st.mean(early):.1f} C, rounds 26-30 mean {st.mean(late):.1f} C")
    return 0 if rounds_ok and not contaminating else 1


if __name__ == "__main__":
    sys.exit(main())
