"""
final_stats.py — Complete statistical analysis for the paper.
Uses the correctly paired run4_enriched_v2.csv.
"""
from scipy import stats
import statistics

INPUT = r"d:\AACBridge\benchmarks\results\canonical_benchmark.csv"

data = {}
with open(INPUT) as f:
    for line in f:
        parts = line.strip().split(",")
        if len(parts) == 11 and parts[0].strip().isdigit():
            mode = parts[1].strip()
            target = int(parts[3])
            key = f"{mode}@{target}"
            if key not in data:
                data[key] = {"prefill": [], "gen": [], "inference": [], "cache_load": []}
            data[key]["prefill"].append(float(parts[7]))
            data[key]["gen"].append(float(parts[8]))
            data[key]["inference"].append(float(parts[4]))
            data[key]["cache_load"].append(float(parts[5]))

ORDER = ["ZERO_CONTEXT@0", "RAG_INLINE@50", "RAG_INLINE@100",
         "RAG_INLINE@200", "RAG_INLINE@500", "CAP_KVC@50"]

# E2E totals
cap = [data["CAP_KVC@50"]["inference"][i] + data["CAP_KVC@50"]["cache_load"][i]
       for i in range(30)]

print("=" * 80)
print("E2E MEANS (all 30 trials per condition)")
print("=" * 80)
for key in ORDER:
    d = data[key]
    e2e = [d["inference"][i] + d["cache_load"][i] for i in range(len(d["inference"]))]
    m = statistics.mean(e2e)
    s = statistics.stdev(e2e)
    print(f"  {key:20s}  mean={m:10.2f}  sd={s:8.2f}  n={len(e2e)}")

cap_m = statistics.mean(cap)
rag50_m = statistics.mean(data["RAG_INLINE@50"]["inference"])
rag500_m = statistics.mean(data["RAG_INLINE@500"]["inference"])
print(f"\n  Speedup vs RAG@50:  {rag50_m/cap_m:.2f}x")
print(f"  Speedup vs RAG@500: {rag500_m/cap_m:.2f}x")

# E2E t-tests
print("\n" + "=" * 80)
print("E2E WELCH T-TESTS (alpha=0.05, two-tailed)")
print("=" * 80)
for name, rag_key in [("RAG@50", "RAG_INLINE@50"), ("RAG@500", "RAG_INLINE@500")]:
    rag = data[rag_key]["inference"]
    t, p = stats.ttest_ind(cap, rag, equal_var=False)
    s1, s2 = statistics.stdev(cap), statistics.stdev(rag)
    n1, n2 = len(cap), len(rag)
    df = (s1**2/n1 + s2**2/n2)**2 / ((s1**2/n1)**2/(n1-1) + (s2**2/n2)**2/(n2-1))
    pooled = ((s1**2 + s2**2)/2)**0.5
    d = abs(statistics.mean(cap) - statistics.mean(rag)) / pooled
    print(f"\n  CAP_KVC vs {name}:")
    print(f"    t({df:.1f}) = {t:.2f}")
    print(f"    p = {p:.2e}")
    print(f"    Cohen's d = {d:.2f}")

# Prefill
print("\n" + "=" * 80)
print("PREFILL DECOMPOSITION")
print("=" * 80)
cap_pf_m = statistics.mean(data["CAP_KVC@50"]["prefill"])
cap_pf_s = statistics.stdev(data["CAP_KVC@50"]["prefill"])
print(f"  CAP_KVC prefill:  {cap_pf_m:.2f} +/- {cap_pf_s:.2f} ms")
print()
for key in ORDER:
    d = data[key]
    pf_m = statistics.mean(d["prefill"])
    pf_s = statistics.stdev(d["prefill"])
    gn_m = statistics.mean(d["gen"])
    gn_s = statistics.stdev(d["gen"])
    if key != "CAP_KVC@50" and key != "ZERO_CONTEXT@0":
        speedup = pf_m / cap_pf_m
        print(f"  {key:20s}  prefill={pf_m:10.2f}+/-{pf_s:7.2f}  gen={gn_m:10.2f}+/-{gn_s:7.2f}  => {speedup:.2f}x prefill speedup")
    else:
        print(f"  {key:20s}  prefill={pf_m:10.2f}+/-{pf_s:7.2f}  gen={gn_m:10.2f}+/-{gn_s:7.2f}")

# Prefill t-tests
print("\n" + "=" * 80)
print("PREFILL-ONLY WELCH T-TESTS")
print("=" * 80)
cap_pf = data["CAP_KVC@50"]["prefill"]
for name, key in [("RAG@50", "RAG_INLINE@50"), ("RAG@500", "RAG_INLINE@500")]:
    rag_pf = data[key]["prefill"]
    t, p = stats.ttest_ind(cap_pf, rag_pf, equal_var=False)
    s1, s2 = statistics.stdev(cap_pf), statistics.stdev(rag_pf)
    n1, n2 = len(cap_pf), len(rag_pf)
    df = (s1**2/n1 + s2**2/n2)**2 / ((s1**2/n1)**2/(n1-1) + (s2**2/n2)**2/(n2-1))
    pooled = ((s1**2 + s2**2)/2)**0.5
    d = abs(statistics.mean(cap_pf) - statistics.mean(rag_pf)) / pooled
    print(f"\n  CAP_KVC prefill vs {name} prefill:")
    print(f"    t({df:.1f}) = {t:.2f}")
    print(f"    p = {p:.2e}")
    print(f"    Cohen's d = {d:.2f}")

# Gen phase comparison
print("\n" + "=" * 80)
print("GENERATION PHASE ANALYSIS")
print("=" * 80)
for key in ORDER:
    gn = data[key]["gen"]
    print(f"  {key:20s}  gen_ms = {statistics.mean(gn):10.2f} +/- {statistics.stdev(gn):8.2f}")

print("\nDONE.")
