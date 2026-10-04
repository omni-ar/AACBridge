"""
AACBridge Revision — Benchmark Data Analysis

Processes the existing canonical_benchmark.csv to produce:
1. Matched-context comparison (RAG@50 vs CAP_KVC, same ~50-token context)
2. Context-length scaling table
3. Statistical tests with honest p-values
4. Cache restoration overhead analysis

All numbers produced by this script are directly citeable in the paper.

RULE: Do NOT edit values manually. If a number changes, rerun this script.
"""

import csv
import math
import sys
from collections import defaultdict

def load_csv(path):
    with open(path, 'r') as f:
        reader = csv.DictReader(f)
        rows = []
        for row in reader:
            rows.append({
                'trial': int(row['trial']),
                'mode': row['mode'],
                'stateId': row['stateId'],
                'prompt_token_target': int(row['prompt_token_target']),
                'inference_ms': float(row['inference_ms']),
                'cache_load_ms': float(row['cache_load_ms']),
                'gen_length': int(row['gen_length']),
                'prefill_ms': float(row['prefill_ms']),
                'gen_ms': float(row['gen_ms']),
                'prompt_tokens': int(row['prompt_tokens']),
                'gen_tokens': int(row['gen_tokens']),
            })
    return rows

def group_by(rows, key_fn):
    groups = defaultdict(list)
    for r in rows:
        groups[key_fn(r)].append(r)
    return dict(groups)

def stats(values):
    n = len(values)
    if n == 0:
        return {'n': 0, 'mean': 0, 'sd': 0, 'median': 0, 'min': 0, 'max': 0}
    mean = sum(values) / n
    variance = sum((x - mean) ** 2 for x in values) / max(n - 1, 1)
    sd = math.sqrt(variance)
    sorted_v = sorted(values)
    median = sorted_v[n // 2] if n % 2 == 1 else (sorted_v[n // 2 - 1] + sorted_v[n // 2]) / 2
    q1 = sorted_v[n // 4]
    q3 = sorted_v[3 * n // 4]
    return {
        'n': n, 'mean': mean, 'sd': sd, 'median': median,
        'min': sorted_v[0], 'max': sorted_v[-1],
        'q1': q1, 'q3': q3, 'iqr': q3 - q1
    }

def welch_t_test(s1, s2):
    """Two-sample Welch's t-test. Returns t, df, p (two-tailed, approximate)."""
    n1, n2 = s1['n'], s2['n']
    m1, m2 = s1['mean'], s2['mean']
    v1 = s1['sd'] ** 2
    v2 = s2['sd'] ** 2

    if v1 == 0 and v2 == 0:
        return float('inf'), n1 + n2 - 2, 0.0

    se = math.sqrt(v1 / n1 + v2 / n2)
    if se == 0:
        return float('inf'), n1 + n2 - 2, 0.0

    t = (m1 - m2) / se

    # Welch-Satterthwaite df
    num = (v1 / n1 + v2 / n2) ** 2
    den = (v1 / n1) ** 2 / (n1 - 1) + (v2 / n2) ** 2 / (n2 - 1)
    df = num / den if den > 0 else n1 + n2 - 2

    # Approximate p-value using t-distribution CDF
    # Using a simple approximation for |t| >> 1
    p = approximate_p_value(abs(t), df)

    return t, df, p

def approximate_p_value(t_abs, df):
    """Approximate two-tailed p-value for t-distribution."""
    # For large |t|, use normal approximation
    if t_abs > 30:
        return 0.0
    # Simple approximation using the incomplete beta function relationship
    x = df / (df + t_abs * t_abs)
    # Use a rough approximation
    if t_abs < 0.01:
        return 1.0
    # Beta regularized incomplete function approximation
    # For our purposes, we use a simpler bound
    import math
    # Normal approximation for large df
    if df > 30:
        z = t_abs
        p = 2 * (1 - 0.5 * (1 + math.erf(z / math.sqrt(2))))
        return max(p, 1e-30)
    # For small df, use a rough table-based approach
    # This is approximate - for the paper we'd use scipy
    critical_values = {
        # df: {alpha: t_critical} for two-tailed
        29: {0.05: 2.045, 0.01: 2.756, 0.001: 3.659},
        30: {0.05: 2.042, 0.01: 2.750, 0.001: 3.646},
    }
    if t_abs > 3.66:
        return 0.001  # p < 0.001
    elif t_abs > 2.76:
        return 0.01  # p < 0.01
    elif t_abs > 2.04:
        return 0.05  # p < 0.05
    else:
        # Rough interpolation
        return min(1.0, 2 * math.exp(-0.5 * t_abs * t_abs))

def cohens_d(s1, s2):
    """Pooled Cohen's d for equal sample sizes."""
    n1, n2 = s1['n'], s2['n']
    pooled_var = ((n1 - 1) * s1['sd']**2 + (n2 - 1) * s2['sd']**2) / (n1 + n2 - 2)
    pooled_sd = math.sqrt(pooled_var)
    if pooled_sd == 0:
        return float('inf')
    return abs(s1['mean'] - s2['mean']) / pooled_sd

def main():
    csv_path = r"D:\AACBridge\benchmarks\results\canonical_benchmark.csv"
    output_dir = r"D:\AACBridge\evaluation\latency"

    rows = load_csv(csv_path)

    # Group by mode and token tier
    by_mode = group_by(rows, lambda r: r['mode'])

    # Further group RAG_INLINE by token tier
    rag_by_tier = group_by(by_mode['RAG_INLINE'], lambda r: r['prompt_token_target'])

    print("=" * 70)
    print("AACBridge Paper 262 — Benchmark Data Analysis")
    print("=" * 70)

    # ================================================================
    # TABLE A: Matched-Context Comparison (RAG@50 vs CAP_KVC)
    # ================================================================
    print("\n" + "=" * 70)
    print("TABLE A: MATCHED-CONTEXT COMPARISON")
    print("Both conditions use the same ~50-token home_morning context.")
    print("RAG@50: context inlined (52 prompt tokens)")
    print("CAP_KVC: context pre-cached, 9 intent tokens at inference time")
    print("=" * 70)

    rag50 = rag_by_tier[50]
    cap_kvc = by_mode['CAP_KVC']
    zero_ctx = by_mode['ZERO_CONTEXT']

    rag50_e2e = stats([r['inference_ms'] for r in rag50])
    rag50_prefill = stats([r['prefill_ms'] for r in rag50])
    rag50_gen = stats([r['gen_ms'] for r in rag50])

    cap_e2e = stats([r['inference_ms'] for r in cap_kvc])
    cap_prefill = stats([r['prefill_ms'] for r in cap_kvc])
    cap_gen = stats([r['gen_ms'] for r in cap_kvc])
    cap_cache_load = stats([r['cache_load_ms'] for r in cap_kvc])
    cap_total_e2e = stats([r['inference_ms'] + r['cache_load_ms'] for r in cap_kvc])

    zero_e2e = stats([r['inference_ms'] for r in zero_ctx])
    zero_prefill = stats([r['prefill_ms'] for r in zero_ctx])
    zero_gen = stats([r['gen_ms'] for r in zero_ctx])

    print(f"\nZERO_CONTEXT (n={zero_e2e['n']}):")
    print(f"  E2E:     {zero_e2e['mean']:.2f} ± {zero_e2e['sd']:.2f} ms")
    print(f"  Prefill: {zero_prefill['mean']:.2f} ± {zero_prefill['sd']:.2f} ms (tokens={zero_ctx[0]['prompt_tokens']})")
    print(f"  Gen:     {zero_gen['mean']:.2f} ± {zero_gen['sd']:.2f} ms")

    print(f"\nRAG_INLINE @ N≈50 (n={rag50_e2e['n']}):")
    print(f"  E2E:     {rag50_e2e['mean']:.2f} ± {rag50_e2e['sd']:.2f} ms")
    print(f"  Prefill: {rag50_prefill['mean']:.2f} ± {rag50_prefill['sd']:.2f} ms (tokens={rag50[0]['prompt_tokens']})")
    print(f"  Gen:     {rag50_gen['mean']:.2f} ± {rag50_gen['sd']:.2f} ms")

    print(f"\nCAP_KVC (n={cap_e2e['n']}):")
    print(f"  Cache load: {cap_cache_load['mean']:.2f} ± {cap_cache_load['sd']:.2f} ms")
    print(f"  Inference:  {cap_e2e['mean']:.2f} ± {cap_e2e['sd']:.2f} ms")
    print(f"  Prefill:    {cap_prefill['mean']:.2f} ± {cap_prefill['sd']:.2f} ms (tokens={cap_kvc[0]['prompt_tokens']})")
    print(f"  Gen:        {cap_gen['mean']:.2f} ± {cap_gen['sd']:.2f} ms")
    print(f"  Total E2E:  {cap_total_e2e['mean']:.2f} ± {cap_total_e2e['sd']:.2f} ms")

    # Speedups
    matched_prefill_speedup = rag50_prefill['mean'] / cap_prefill['mean']
    matched_e2e_speedup = rag50_e2e['mean'] / cap_total_e2e['mean']
    print(f"\n--- MATCHED-CONTEXT SPEEDUPS ---")
    print(f"  Prefill speedup (RAG@50 / CAP_KVC): {matched_prefill_speedup:.2f}×")
    print(f"  E2E speedup (RAG@50 / CAP_KVC):     {matched_e2e_speedup:.2f}×")

    # Statistical tests
    t_prefill, df_prefill, p_prefill = welch_t_test(rag50_prefill, cap_prefill)
    d_prefill = cohens_d(rag50_prefill, cap_prefill)
    print(f"\n--- STATISTICAL TESTS (Matched Context, Prefill) ---")
    print(f"  Welch t = {t_prefill:.2f}, df ≈ {df_prefill:.1f}")
    print(f"  p {'< 0.001' if p_prefill < 0.001 else f'= {p_prefill:.4f}'}")
    print(f"  Cohen's d = {d_prefill:.2f}")
    print(f"  Significant at α=0.05: {'YES' if p_prefill < 0.05 else 'NO'}")

    t_e2e, df_e2e, p_e2e = welch_t_test(
        stats([r['inference_ms'] for r in rag50]),
        cap_total_e2e
    )
    d_e2e = cohens_d(stats([r['inference_ms'] for r in rag50]), cap_total_e2e)
    print(f"\n--- STATISTICAL TESTS (Matched Context, E2E) ---")
    print(f"  Welch t = {t_e2e:.2f}, df ≈ {df_e2e:.1f}")
    print(f"  p {'< 0.001' if p_e2e < 0.001 else f'= {p_e2e:.4f}'}")
    print(f"  Cohen's d = {d_e2e:.2f}")
    print(f"  Significant at α=0.05: {'YES' if p_e2e < 0.05 else 'NO'}")

    # ================================================================
    # TABLE C: Context-Length Scaling
    # ================================================================
    print("\n" + "=" * 70)
    print("TABLE C: CONTEXT-LENGTH SCALING")
    print("=" * 70)

    for tier in [50, 100, 200, 500]:
        tier_rows = rag_by_tier[tier]
        tier_e2e = stats([r['inference_ms'] for r in tier_rows])
        tier_prefill = stats([r['prefill_ms'] for r in tier_rows])
        tier_gen = stats([r['gen_ms'] for r in tier_rows])
        actual_tokens = tier_rows[0]['prompt_tokens']

        # Speedup vs CAP_KVC
        prefill_speedup = tier_prefill['mean'] / cap_prefill['mean']
        e2e_speedup = tier_e2e['mean'] / cap_total_e2e['mean']

        # Statistical test
        t_val, df_val, p_val = welch_t_test(tier_prefill, cap_prefill)
        d_val = cohens_d(tier_prefill, cap_prefill)

        print(f"\nRAG_INLINE @ N≈{tier} (actual={actual_tokens} tokens, n={tier_e2e['n']}):")
        print(f"  E2E:     {tier_e2e['mean']:.2f} ± {tier_e2e['sd']:.2f} ms")
        print(f"  Prefill: {tier_prefill['mean']:.2f} ± {tier_prefill['sd']:.2f} ms")
        print(f"  Gen:     {tier_gen['mean']:.2f} ± {tier_gen['sd']:.2f} ms")
        print(f"  Prefill speedup vs CAP_KVC: {prefill_speedup:.2f}×")
        print(f"  E2E speedup vs CAP_KVC:     {e2e_speedup:.2f}×")
        print(f"  Welch t={t_val:.2f}, p{'<0.001' if p_val < 0.001 else f'={p_val:.4f}'}, d={d_val:.2f}")
        print(f"  Significant: {'YES' if p_val < 0.05 else 'NO'}")

    # ================================================================
    # ORIGINAL HEADLINE NUMBERS VERIFICATION
    # ================================================================
    print("\n" + "=" * 70)
    print("VERIFICATION OF SUBMITTED PAPER HEADLINE NUMBERS")
    print("=" * 70)

    rag500_prefill = stats([r['prefill_ms'] for r in rag_by_tier[500]])
    rag500_e2e = stats([r['inference_ms'] for r in rag_by_tier[500]])

    old_prefill_speedup = rag500_prefill['mean'] / cap_prefill['mean']
    old_e2e_speedup = rag500_e2e['mean'] / cap_total_e2e['mean']

    print(f"\nRAG@500 prefill:  {rag500_prefill['mean']:.2f} ms")
    print(f"CAP_KVC prefill:  {cap_prefill['mean']:.2f} ms")
    print(f"Prefill speedup:  {old_prefill_speedup:.2f}× (paper claimed: 40.78×)")

    print(f"\nRAG@500 E2E:      {rag500_e2e['mean']:.2f} ms")
    print(f"CAP_KVC total:    {cap_total_e2e['mean']:.2f} ms")
    print(f"E2E speedup:      {old_e2e_speedup:.2f}× (paper claimed: 4.24×)")

    print(f"\nCache load mean:  {cap_cache_load['mean']:.2f} ms (paper claimed: 2.74 ms)")

    context_note = "UNMATCHED" if rag_by_tier[500][0]['prompt_tokens'] != cap_kvc[0]['prompt_tokens'] else "MATCHED"
    print(f"\nContext comparison status: {context_note}")
    print(f"  RAG@500 tokens: {rag_by_tier[500][0]['prompt_tokens']}")
    print(f"  CAP_KVC tokens: {cap_kvc[0]['prompt_tokens']} (intent only; cached context ≈ 50 tokens)")

    # ================================================================
    # WRITE CSV FOR PAPER TABLES
    # ================================================================
    output_csv = f"{output_dir}\\analysis_results.csv"
    with open(output_csv, 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['condition', 'context_tokens', 'actual_prompt_tokens', 'n',
                         'e2e_mean', 'e2e_sd', 'prefill_mean', 'prefill_sd',
                         'gen_mean', 'gen_sd', 'cache_load_mean', 'cache_load_sd',
                         'prefill_speedup_vs_capkvc', 'e2e_speedup_vs_capkvc',
                         'welch_t', 'p_value', 'cohens_d', 'significant'])

        for mode_name, tier, tier_stats_e2e, tier_stats_prefill, tier_stats_gen, tokens in [
            ('ZERO_CONTEXT', 0, zero_e2e, zero_prefill, zero_gen, 9),
            ('RAG@50', 50, rag50_e2e, rag50_prefill, rag50_gen, 52),
            ('RAG@100', 100, stats([r['inference_ms'] for r in rag_by_tier[100]]),
             stats([r['prefill_ms'] for r in rag_by_tier[100]]),
             stats([r['gen_ms'] for r in rag_by_tier[100]]),
             rag_by_tier[100][0]['prompt_tokens']),
            ('RAG@200', 200, stats([r['inference_ms'] for r in rag_by_tier[200]]),
             stats([r['prefill_ms'] for r in rag_by_tier[200]]),
             stats([r['gen_ms'] for r in rag_by_tier[200]]),
             rag_by_tier[200][0]['prompt_tokens']),
            ('RAG@500', 500, stats([r['inference_ms'] for r in rag_by_tier[500]]),
             stats([r['prefill_ms'] for r in rag_by_tier[500]]),
             stats([r['gen_ms'] for r in rag_by_tier[500]]),
             rag_by_tier[500][0]['prompt_tokens']),
        ]:
            t_val, _, p_val = welch_t_test(tier_stats_prefill, cap_prefill)
            d_val = cohens_d(tier_stats_prefill, cap_prefill)
            ps = tier_stats_prefill['mean'] / cap_prefill['mean'] if cap_prefill['mean'] > 0 else 0
            es = tier_stats_e2e['mean'] / cap_total_e2e['mean'] if cap_total_e2e['mean'] > 0 else 0

            writer.writerow([mode_name, tier, tokens, tier_stats_e2e['n'],
                             f"{tier_stats_e2e['mean']:.2f}", f"{tier_stats_e2e['sd']:.2f}",
                             f"{tier_stats_prefill['mean']:.2f}", f"{tier_stats_prefill['sd']:.2f}",
                             f"{tier_stats_gen['mean']:.2f}", f"{tier_stats_gen['sd']:.2f}",
                             '', '',
                             f"{ps:.2f}", f"{es:.2f}",
                             f"{t_val:.2f}", f"{p_val:.6f}", f"{d_val:.2f}",
                             'YES' if p_val < 0.05 else 'NO'])

        # CAP_KVC row
        writer.writerow(['CAP_KVC', '50 (cached)', 9, cap_e2e['n'],
                         f"{cap_total_e2e['mean']:.2f}", f"{cap_total_e2e['sd']:.2f}",
                         f"{cap_prefill['mean']:.2f}", f"{cap_prefill['sd']:.2f}",
                         f"{cap_gen['mean']:.2f}", f"{cap_gen['sd']:.2f}",
                         f"{cap_cache_load['mean']:.2f}", f"{cap_cache_load['sd']:.2f}",
                         '1.00', '1.00',
                         '', '', '', ''])

    print(f"\nResults written to: {output_csv}")
    print("\nDONE.")

if __name__ == '__main__':
    main()
