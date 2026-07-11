"""
analyze_run4.py
Computes statistics from the enriched benchmark CSV (run4).
Outputs: means, stdevs, overhead check, prefill-only speedup.
"""
import statistics
import csv
from scipy import stats

INPUT = r"d:\AACBridge\benchmarks\results\run4_enriched_v2.csv"

# Parse enriched CSV
data = {}
with open(INPUT, "r") as f:
    for line in f:
        parts = line.strip().split(",")
        if len(parts) == 11 and parts[0].strip().isdigit():
            mode = parts[1].strip()
            target = int(parts[3].strip())
            key = f"{mode}@{target}"
            if key not in data:
                data[key] = {
                    "prefill": [], "gen": [], "prompt_tokens": [],
                    "gen_tokens": [], "inference": [], "cache_load": []
                }
            data[key]["prefill"].append(float(parts[7].strip()))
            data[key]["gen"].append(float(parts[8].strip()))
            data[key]["prompt_tokens"].append(int(parts[9].strip()))
            data[key]["gen_tokens"].append(int(parts[10].strip()))
            data[key]["inference"].append(float(parts[4].strip()))
            data[key]["cache_load"].append(float(parts[5].strip()))

ORDER = [
    "ZERO_CONTEXT@0", "RAG_INLINE@50", "RAG_INLINE@100",
    "RAG_INLINE@200", "RAG_INLINE@500", "CAP_KVC@50"
]

# ── Summary Table ────────────────────────────────────────────────────────
print("=" * 120)
print("SUMMARY TABLE (Run 4 - Clean Single Suite)")
print("=" * 120)
hdr = f"{'Condition':20s} {'n':>3s}  {'prefill_ms':>11s} {'sd':>8s}  {'gen_ms':>10s} {'sd':>8s}  {'e2e_ms':>10s} {'sd':>8s}  {'ptok':>5s} {'gtok':>5s}"
print(hdr)
print("-" * 120)
for key in ORDER:
    d = data[key]
    n = len(d["prefill"])
    pm = statistics.mean(d["prefill"])
    ps = statistics.stdev(d["prefill"])
    gm = statistics.mean(d["gen"])
    gs = statistics.stdev(d["gen"])
    im = statistics.mean(d["inference"])
    is_ = statistics.stdev(d["inference"])
    cl = statistics.mean(d["cache_load"])
    total = im + cl
    pt = statistics.mean(d["prompt_tokens"])
    gt = statistics.mean(d["gen_tokens"])
    print(f"{key:20s} {n:3d}  {pm:11.2f} {ps:8.2f}  {gm:10.2f} {gs:8.2f}  {total:10.2f} {is_:8.2f}  {pt:5.0f} {gt:5.0f}")

# ── Overhead Check ───────────────────────────────────────────────────────
print()
print("=" * 80)
print("OVERHEAD CHECK: e2e_inference - prefill - gen (should be < 10ms)")
print("=" * 80)
for key in ORDER:
    d = data[key]
    overheads = []
    for i in range(len(d["prefill"])):
        oh = d["inference"][i] - d["prefill"][i] - d["gen"][i]
        overheads.append(oh)
    om = statistics.mean(overheads)
    os_ = statistics.stdev(overheads)
    print(f"  {key:20s}  overhead = {om:8.2f} ms +/- {os_:6.2f}")

# ── Prefill-only Speedup ────────────────────────────────────────────────
print()
print("=" * 80)
print("PREFILL-ONLY SPEEDUP (CAP_KVC prefill vs RAG prefill)")
print("=" * 80)
cap_prefill = statistics.mean(data["CAP_KVC@50"]["prefill"])
cap_prefill_sd = statistics.stdev(data["CAP_KVC@50"]["prefill"])
print(f"  CAP_KVC prefill mean = {cap_prefill:.2f} ms +/- {cap_prefill_sd:.2f}")
print()
for key in ["RAG_INLINE@50", "RAG_INLINE@100", "RAG_INLINE@200", "RAG_INLINE@500"]:
    rag_prefill = statistics.mean(data[key]["prefill"])
    speedup = rag_prefill / cap_prefill
    print(f"  {key:20s} prefill = {rag_prefill:10.2f} ms => {speedup:.2f}x prefill speedup vs CAP_KVC")

# ── E2E Speedup ──────────────────────────────────────────────────────────
print()
print("=" * 80)
print("END-TO-END SPEEDUP (CAP_KVC total vs RAG total)")
print("=" * 80)
cap_e2e = statistics.mean(data["CAP_KVC@50"]["inference"]) + statistics.mean(data["CAP_KVC@50"]["cache_load"])
for key in ["RAG_INLINE@50", "RAG_INLINE@100", "RAG_INLINE@200", "RAG_INLINE@500"]:
    rag_e2e = statistics.mean(data[key]["inference"])
    speedup = rag_e2e / cap_e2e
    print(f"  {key:20s} e2e = {rag_e2e:10.2f} ms vs CAP = {cap_e2e:.2f} ms => {speedup:.2f}x")

# ── Welch's t-test ───────────────────────────────────────────────────────
print()
print("=" * 80)
print("STATISTICAL TESTS (Welch's t-test, alpha=0.05, two-tailed)")
print("=" * 80)

cap_inf = [data["CAP_KVC@50"]["inference"][i] + data["CAP_KVC@50"]["cache_load"][i]
           for i in range(len(data["CAP_KVC@50"]["inference"]))]

for key in ["RAG_INLINE@50", "RAG_INLINE@500"]:
    rag_inf = data[key]["inference"]
    t_stat, p_val = stats.ttest_ind(cap_inf, rag_inf, equal_var=False)
    n1, n2 = len(cap_inf), len(rag_inf)
    pooled_sd = ((statistics.stdev(cap_inf)**2 + statistics.stdev(rag_inf)**2) / 2) ** 0.5
    cohens_d = abs(statistics.mean(cap_inf) - statistics.mean(rag_inf)) / pooled_sd

    # Welch-Satterthwaite df
    s1 = statistics.stdev(cap_inf)
    s2 = statistics.stdev(rag_inf)
    df_num = (s1**2/n1 + s2**2/n2)**2
    df_den = (s1**2/n1)**2/(n1-1) + (s2**2/n2)**2/(n2-1)
    df = df_num / df_den

    print(f"\n  CAP_KVC vs {key}:")
    print(f"    t({df:.1f}) = {t_stat:.2f}")
    print(f"    p = {p_val:.2e}")
    print(f"    Cohen's d = {cohens_d:.2f}")

# ── Prefill-only t-tests ─────────────────────────────────────────────────
print()
print("=" * 80)
print("PREFILL-ONLY STATISTICAL TESTS")
print("=" * 80)

cap_pf = data["CAP_KVC@50"]["prefill"]
for key in ["RAG_INLINE@50", "RAG_INLINE@500"]:
    rag_pf = data[key]["prefill"]
    t_stat, p_val = stats.ttest_ind(cap_pf, rag_pf, equal_var=False)
    n1, n2 = len(cap_pf), len(rag_pf)
    pooled_sd = ((statistics.stdev(cap_pf)**2 + statistics.stdev(rag_pf)**2) / 2) ** 0.5
    cohens_d = abs(statistics.mean(cap_pf) - statistics.mean(rag_pf)) / pooled_sd
    s1 = statistics.stdev(cap_pf)
    s2 = statistics.stdev(rag_pf)
    df_num = (s1**2/n1 + s2**2/n2)**2
    df_den = (s1**2/n1)**2/(n1-1) + (s2**2/n2)**2/(n2-1)
    df = df_num / df_den
    print(f"\n  CAP_KVC prefill vs {key} prefill:")
    print(f"    t({df:.1f}) = {t_stat:.2f}")
    print(f"    p = {p_val:.2e}")
    print(f"    Cohen's d = {cohens_d:.2f}")

print("\n\nDONE.")
