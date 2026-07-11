"""
regenerate_canonical.py — Regenerate canonical benchmark dataset from raw log.

This script is the SINGLE source of truth for all benchmark data.
It reads run4_complete.log and produces canonical_benchmark.csv.

All downstream consumers (final_stats.py, create_clean_csv.py,
generate_paper_figures.py) must read from canonical_benchmark.csv.
"""
import sys
import os
import re
import csv

RESULTS_DIR = os.path.join(os.path.dirname(__file__), "results")
RAW_LOG = os.path.join(RESULTS_DIR, "run4_complete.log")
CANONICAL_CSV = os.path.join(RESULTS_DIR, "canonical_benchmark.csv")

def main():
    with open(RAW_LOG, "r", encoding="utf-8", errors="replace") as f:
        lines = f.readlines()

    bench_re = re.compile(
        r"BENCH,(\d+),(ZERO_CONTEXT|RAG_INLINE|CAP_KVC),"
        r"([^,]+),(\d+),([0-9.]+),([0-9.]+),(\d+)"
    )
    timing_re = re.compile(
        r"TIMING,(runInference|resumeInference),"
        r"prefill_ms=([0-9.]+),"
        r"gen_ms=([0-9.]+),"
        r"prompt_tokens=(\d+),"
        r"gen_tokens=(\d+)"
    )

    timing_entries = []
    bench_entries = []
    for i, line in enumerate(lines):
        tm = timing_re.search(line)
        if tm:
            timing_entries.append((i, {
                "function": tm.group(1),
                "prefill_ms": float(tm.group(2)),
                "gen_ms": float(tm.group(3)),
                "prompt_tokens": int(tm.group(4)),
                "gen_tokens": int(tm.group(5)),
            }))
        bm = bench_re.search(line)
        if bm:
            bench_entries.append((i, {
                "trial": int(bm.group(1)),
                "mode": bm.group(2),
                "stateId": bm.group(3),
                "prompt_token_target": int(bm.group(4)),
                "inference_ms": float(bm.group(5)),
                "cache_load_ms": float(bm.group(6)),
                "gen_length": int(bm.group(7)),
            }))

    print(f"Raw log: {RAW_LOG}")
    print(f"Found {len(bench_entries)} BENCH and {len(timing_entries)} TIMING entries")

    # Pair each BENCH with its closest preceding TIMING
    rows = []
    timing_pool = list(timing_entries)  # copy for consumption
    paired = 0
    unpaired = 0
    for bench_lineno, b in bench_entries:
        best_timing = None
        for t_lineno, t in timing_pool:
            if t_lineno < bench_lineno:
                best_timing = (t_lineno, t)
            else:
                break

        if best_timing is None:
            print(f"WARNING: No preceding TIMING for BENCH trial {b['trial']} "
                  f"{b['mode']} at line {bench_lineno}")
            unpaired += 1
            continue

        # Remove used TIMING entry
        timing_pool = [(ln, t) for ln, t in timing_pool
                       if not (t is best_timing[1])]

        rows.append({
            "trial": b["trial"],
            "mode": b["mode"],
            "stateId": b["stateId"],
            "prompt_token_target": b["prompt_token_target"],
            "inference_ms": b["inference_ms"],
            "cache_load_ms": b["cache_load_ms"],
            "gen_length": b["gen_length"],
            "prefill_ms": best_timing[1]["prefill_ms"],
            "gen_ms": best_timing[1]["gen_ms"],
            "prompt_tokens": best_timing[1]["prompt_tokens"],
            "gen_tokens": best_timing[1]["gen_tokens"],
        })
        paired += 1

    print(f"Paired: {paired}, Unpaired: {unpaired}")

    # Write canonical CSV
    fieldnames = [
        "trial", "mode", "stateId", "prompt_token_target",
        "inference_ms", "cache_load_ms", "gen_length",
        "prefill_ms", "gen_ms", "prompt_tokens", "gen_tokens"
    ]
    with open(CANONICAL_CSV, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        for r in rows:
            writer.writerow({
                "trial": r["trial"],
                "mode": r["mode"],
                "stateId": r["stateId"],
                "prompt_token_target": r["prompt_token_target"],
                "inference_ms": f"{r['inference_ms']:.2f}",
                "cache_load_ms": f"{r['cache_load_ms']:.2f}",
                "gen_length": r["gen_length"],
                "prefill_ms": f"{r['prefill_ms']:.2f}",
                "gen_ms": f"{r['gen_ms']:.2f}",
                "prompt_tokens": r["prompt_tokens"],
                "gen_tokens": r["gen_tokens"],
            })

    print(f"\nWrote {len(rows)} rows to {CANONICAL_CSV}")

    # Integrity verification
    conditions = {}
    for r in rows:
        key = f"{r['mode']}@{r['prompt_token_target']}"
        conditions.setdefault(key, []).append(r)

    EXPECTED = {
        "ZERO_CONTEXT@0": 30,
        "RAG_INLINE@50": 30,
        "RAG_INLINE@100": 30,
        "RAG_INLINE@200": 30,
        "RAG_INLINE@500": 30,
        "CAP_KVC@50": 30,
    }

    print("\n=== CONDITION COUNTS ===")
    print(f"{'Condition':20s} {'Expected':>8s} {'Observed':>8s} {'Status':>8s}")
    all_ok = True
    for key in sorted(EXPECTED.keys()):
        expected = EXPECTED[key]
        observed = len(conditions.get(key, []))
        status = "OK" if expected == observed else "MISMATCH"
        if status == "MISMATCH":
            all_ok = False
        print(f"{key:20s} {expected:8d} {observed:8d} {status:>8s}")

    total_expected = sum(EXPECTED.values())
    print(f"\nTotal: {total_expected} expected, {len(rows)} observed")

    # Duplicate check
    seen = set()
    duplicates = 0
    for r in rows:
        key = (r["trial"], r["mode"], r["prompt_token_target"])
        if key in seen:
            duplicates += 1
            print(f"DUPLICATE: trial={r['trial']}, mode={r['mode']}, "
                  f"target={r['prompt_token_target']}")
        seen.add(key)
    print(f"Duplicate rows: {duplicates}")

    if all_ok and duplicates == 0:
        print("\n=== CANONICAL DATASET INTEGRITY: PASSED ===")
    else:
        print("\n=== CANONICAL DATASET INTEGRITY: FAILED ===")
        sys.exit(1)


if __name__ == "__main__":
    main()
