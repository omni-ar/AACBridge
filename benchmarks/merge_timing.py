"""
merge_timing.py — Post-benchmark merge of native TIMING data with BENCH CSV.

Usage:
  1. Run benchmark on device
  2. Extract logs:
       adb logcat -d -s LatencyProfiler > bench_raw.txt
       adb logcat -d -s AACBridgeJNI > timing_raw.txt
  3. Run:
       python merge_timing.py bench_raw.txt timing_raw.txt > ttft_enriched.csv

The script joins BENCH and TIMING lines by sequential order.
Each BENCH trial produces exactly one TIMING line (they are synchronous).

BENCH format (from LatencyProfiler):
  BENCH,trial,mode,stateId,prompt_token_target,inference_ms,cache_load_ms,gen_length

TIMING format (from llama_jni.cpp):
  TIMING,{runInference|resumeInference},prefill_ms=X,gen_ms=Y,prompt_tokens=Z,gen_tokens=W

Output CSV:
  trial,mode,stateId,prompt_token_target,inference_ms,cache_load_ms,gen_length,
  prefill_ms,gen_ms,prompt_tokens,gen_tokens
"""
import sys
import re
import csv

def parse_bench_lines(filepath):
    """Extract BENCH data lines (skip header)."""
    results = []
    with open(filepath, 'r') as f:
        for line in f:
            m = re.search(
                r'BENCH,(\d+),(ZERO_CONTEXT|RAG_INLINE|CAP_KVC),'
                r'([^,]+),(\d+),([0-9.]+),([0-9.]+),(\d+)',
                line
            )
            if m:
                results.append({
                    'trial': int(m.group(1)),
                    'mode': m.group(2),
                    'stateId': m.group(3),
                    'prompt_token_target': int(m.group(4)),
                    'inference_ms': float(m.group(5)),
                    'cache_load_ms': float(m.group(6)),
                    'gen_length': int(m.group(7)),
                })
    return results

def parse_timing_lines(filepath):
    """Extract TIMING data lines."""
    results = []
    with open(filepath, 'r') as f:
        for line in f:
            m = re.search(
                r'TIMING,(runInference|resumeInference),'
                r'prefill_ms=([0-9.]+),'
                r'gen_ms=([0-9.]+),'
                r'prompt_tokens=(\d+),'
                r'gen_tokens=(\d+)',
                line
            )
            if m:
                results.append({
                    'function': m.group(1),
                    'prefill_ms': float(m.group(2)),
                    'gen_ms': float(m.group(3)),
                    'prompt_tokens': int(m.group(4)),
                    'gen_tokens': int(m.group(5)),
                })
    return results

def main():
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} bench_raw.txt timing_raw.txt",
              file=sys.stderr)
        sys.exit(1)

    bench = parse_bench_lines(sys.argv[1])
    timing = parse_timing_lines(sys.argv[2])

    n_bench = len(bench)
    n_timing = len(timing)

    if n_timing < n_bench:
        print(f"ERROR: {n_timing} TIMING lines < {n_bench} BENCH lines",
              file=sys.stderr)
        sys.exit(1)

    if n_timing > n_bench:
        skip = n_timing - n_bench
        print(f"WARNING: {n_timing} TIMING lines > {n_bench} BENCH lines. "
              f"Skipping first {skip} TIMING entries (warmup/validation).",
              file=sys.stderr)
        timing = timing[skip:]

    writer = csv.writer(sys.stdout)
    writer.writerow([
        'trial', 'mode', 'stateId', 'prompt_token_target',
        'inference_ms', 'cache_load_ms', 'gen_length',
        'prefill_ms', 'gen_ms', 'prompt_tokens', 'gen_tokens'
    ])

    for b, t in zip(bench, timing):
        writer.writerow([
            b['trial'], b['mode'], b['stateId'],
            b['prompt_token_target'],
            f"{b['inference_ms']:.2f}",
            f"{b['cache_load_ms']:.2f}",
            b['gen_length'],
            f"{t['prefill_ms']:.2f}",
            f"{t['gen_ms']:.2f}",
            t['prompt_tokens'],
            t['gen_tokens'],
        ])

    import statistics
    for mode in ['ZERO_CONTEXT', 'RAG_INLINE', 'CAP_KVC']:
        indices = [i for i, b in enumerate(bench) if b['mode'] == mode]
        if not indices:
            continue

        targets = sorted(set(bench[i]['prompt_token_target'] for i in indices))
        for target in targets:
            sub_idx = [i for i in indices
                       if bench[i]['prompt_token_target'] == target]
            sub_prefill = [timing[i]['prefill_ms'] for i in sub_idx]
            sub_gen = [timing[i]['gen_ms'] for i in sub_idx]
            sub_prompt = [timing[i]['prompt_tokens'] for i in sub_idx]

            print(f"\n{mode} (target={target}):", file=sys.stderr)
            print(f"  prefill_ms: mean={statistics.mean(sub_prefill):.2f} "
                  f"std={statistics.stdev(sub_prefill):.2f} "
                  f"n={len(sub_prefill)}", file=sys.stderr)
            print(f"  gen_ms:     mean={statistics.mean(sub_gen):.2f} "
                  f"std={statistics.stdev(sub_gen):.2f}", file=sys.stderr)
            print(f"  prompt_tokens: mean={statistics.mean(sub_prompt):.1f}",
                  file=sys.stderr)

if __name__ == '__main__':
    main()
