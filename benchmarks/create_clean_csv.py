"""
create_clean_csv.py — Produces the final ttft_clean.csv from run4 data.
Also produces ttft_enriched.csv with prefill/gen decomposition.
Reports outliers (>3 IQR from median) per condition.
"""
import csv
import statistics
import sys
import os

INPUT = r"d:\AACBridge\benchmarks\results\canonical_benchmark.csv"
OUT_DIR = r"d:\AACBridge\benchmarks\results"

# Parse
data = {}
rows = []
with open(INPUT, "r") as f:
    for line in f:
        parts = line.strip().split(",")
        if len(parts) == 11 and parts[0].strip().isdigit():
            row = {
                "trial": int(parts[0]),
                "mode": parts[1].strip(),
                "stateId": parts[2].strip(),
                "prompt_token_target": int(parts[3]),
                "inference_ms": float(parts[4]),
                "cache_load_ms": float(parts[5]),
                "gen_length": int(parts[6]),
                "prefill_ms": float(parts[7]),
                "gen_ms": float(parts[8]),
                "prompt_tokens": int(parts[9]),
                "gen_tokens": int(parts[10]),
            }
            rows.append(row)
            key = f"{row['mode']}@{row['prompt_token_target']}"
            data.setdefault(key, []).append(row)

# Outlier detection: flag rows where e2e > median + 3*IQR
outlier_flags = set()
for key, group in data.items():
    e2e_vals = sorted(r["inference_ms"] + r["cache_load_ms"] for r in group)
    q1 = e2e_vals[len(e2e_vals) // 4]
    q3 = e2e_vals[3 * len(e2e_vals) // 4]
    iqr = q3 - q1
    upper = q3 + 3 * iqr
    for r in group:
        total = r["inference_ms"] + r["cache_load_ms"]
        if total > upper:
            outlier_flags.add((key, r["trial"]))
            print(f"OUTLIER: {key} trial {r['trial']}: e2e={total:.1f} > {upper:.1f}",
                  file=sys.stderr)

# Write ttft_clean.csv (BENCH-only format, matching original)
clean_path = os.path.join(OUT_DIR, "ttft_clean.csv")
with open(clean_path, "w", newline="") as f:
    f.write("BENCH,trial,mode,stateId,prompt_token_target,inference_ms,cache_load_ms,gen_length\n")
    for r in rows:
        f.write(f"BENCH,{r['trial']},{r['mode']},{r['stateId']},"
                f"{r['prompt_token_target']},{r['inference_ms']:.2f},"
                f"{r['cache_load_ms']:.2f},{r['gen_length']}\n")
print(f"Wrote {len(rows)} rows to {clean_path}", file=sys.stderr)

# Write ttft_enriched.csv (full format with prefill/gen)
enriched_path = os.path.join(OUT_DIR, "ttft_enriched.csv")
with open(enriched_path, "w", newline="") as f:
    writer = csv.writer(f)
    writer.writerow([
        "trial", "mode", "stateId", "prompt_token_target",
        "inference_ms", "cache_load_ms", "gen_length",
        "prefill_ms", "gen_ms", "prompt_tokens", "gen_tokens"
    ])
    for r in rows:
        writer.writerow([
            r["trial"], r["mode"], r["stateId"],
            r["prompt_token_target"],
            f"{r['inference_ms']:.2f}",
            f"{r['cache_load_ms']:.2f}",
            r["gen_length"],
            f"{r['prefill_ms']:.2f}",
            f"{r['gen_ms']:.2f}",
            r["prompt_tokens"],
            r["gen_tokens"],
        ])
print(f"Wrote {len(rows)} rows to {enriched_path}", file=sys.stderr)

# Summary statistics (all data, no outlier removal)
ORDER = [
    "ZERO_CONTEXT@0", "RAG_INLINE@50", "RAG_INLINE@100",
    "RAG_INLINE@200", "RAG_INLINE@500", "CAP_KVC@50"
]

print("\n=== SUMMARY (all 180 trials) ===")
print(f"{'Condition':20s} {'n':>3s} {'prefill':>10s} {'sd':>8s} {'gen':>10s} {'sd':>8s} {'e2e':>10s} {'sd':>8s} {'tokens':>6s}")
for key in ORDER:
    group = data[key]
    n = len(group)
    pf = [r["prefill_ms"] for r in group]
    gn = [r["gen_ms"] for r in group]
    e2e = [r["inference_ms"] + r["cache_load_ms"] for r in group]
    pt = [r["prompt_tokens"] for r in group]
    print(f"{key:20s} {n:3d} {statistics.mean(pf):10.2f} {statistics.stdev(pf):8.2f} "
          f"{statistics.mean(gn):10.2f} {statistics.stdev(gn):8.2f} "
          f"{statistics.mean(e2e):10.2f} {statistics.stdev(e2e):8.2f} "
          f"{statistics.mean(pt):6.0f}")

# Summary without outliers
clean_data = {}
for key, group in data.items():
    clean_data[key] = [r for r in group if (key, r["trial"]) not in outlier_flags]

print(f"\n=== SUMMARY (outliers removed: {len(outlier_flags)} flagged) ===")
print(f"{'Condition':20s} {'n':>3s} {'prefill':>10s} {'sd':>8s} {'gen':>10s} {'sd':>8s} {'e2e':>10s} {'sd':>8s}")
for key in ORDER:
    group = clean_data[key]
    n = len(group)
    if n < 2:
        continue
    pf = [r["prefill_ms"] for r in group]
    gn = [r["gen_ms"] for r in group]
    e2e = [r["inference_ms"] + r["cache_load_ms"] for r in group]
    print(f"{key:20s} {n:3d} {statistics.mean(pf):10.2f} {statistics.stdev(pf):8.2f} "
          f"{statistics.mean(gn):10.2f} {statistics.stdev(gn):8.2f} "
          f"{statistics.mean(e2e):10.2f} {statistics.stdev(e2e):8.2f}")

print("\nDONE.")
