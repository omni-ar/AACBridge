"""
extract_enriched.py — Extract paired BENCH+TIMING data from a single logcat dump.
Pairs each BENCH line with its immediately preceding TIMING line.
Only processes measured trials (skips warmup/validation).

Usage:
    python extract_enriched.py run4_complete.log > ttft_enriched.csv
"""
import sys
import re
import csv

def main():
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} logcat_dump.log", file=sys.stderr)
        sys.exit(1)

    with open(sys.argv[1], "r", encoding="utf-8", errors="replace") as f:
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

    # Build list of all TIMING and BENCH entries with line numbers
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

    print(f"Found {len(bench_entries)} BENCH and {len(timing_entries)} TIMING entries",
          file=sys.stderr)

    # For each BENCH entry, find the closest preceding TIMING entry
    writer = csv.writer(sys.stdout)
    writer.writerow([
        "trial", "mode", "stateId", "prompt_token_target",
        "inference_ms", "cache_load_ms", "gen_length",
        "prefill_ms", "gen_ms", "prompt_tokens", "gen_tokens"
    ])

    timing_idx = 0
    paired = 0
    unpaired = 0
    for bench_lineno, b in bench_entries:
        # Find the latest TIMING entry before this BENCH entry
        best_timing = None
        for t_lineno, t in timing_entries:
            if t_lineno < bench_lineno:
                best_timing = t
            else:
                break

        if best_timing is None:
            print(f"WARNING: No preceding TIMING for BENCH trial {b['trial']} "
                  f"{b['mode']} at line {bench_lineno}", file=sys.stderr)
            unpaired += 1
            continue

        # Remove the used TIMING entry to prevent reuse
        timing_entries = [(ln, t) for ln, t in timing_entries
                          if not (t is best_timing)]

        writer.writerow([
            b["trial"], b["mode"], b["stateId"],
            b["prompt_token_target"],
            f"{b['inference_ms']:.2f}",
            f"{b['cache_load_ms']:.2f}",
            b["gen_length"],
            f"{best_timing['prefill_ms']:.2f}",
            f"{best_timing['gen_ms']:.2f}",
            best_timing["prompt_tokens"],
            best_timing["gen_tokens"],
        ])
        paired += 1

    print(f"Paired: {paired}, Unpaired: {unpaired}", file=sys.stderr)

if __name__ == "__main__":
    main()
