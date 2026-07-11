"""
verify_all_stats.py — Complete statistical verification against manuscript.

Reads from canonical_benchmark.csv ONLY.
Computes every statistic used in the manuscript.
Reports computed vs. manuscript value with match status.
"""
from scipy import stats
import statistics
import math
import csv
import os

CANONICAL = os.path.join(os.path.dirname(__file__), "results", "canonical_benchmark.csv")

# Parse canonical dataset
data = {}
with open(CANONICAL) as f:
    reader = csv.DictReader(f)
    for r in reader:
        key = f"{r['mode']}@{r['prompt_token_target']}"
        if key not in data:
            data[key] = {"prefill": [], "gen": [], "inference": [], "cache_load": []}
        data[key]["prefill"].append(float(r["prefill_ms"]))
        data[key]["gen"].append(float(r["gen_ms"]))
        data[key]["inference"].append(float(r["inference_ms"]))
        data[key]["cache_load"].append(float(r["cache_load_ms"]))

# Compute E2E for CAP_KVC (includes cache_load)
cap = data["CAP_KVC@50"]
cap_e2e = [cap["inference"][i] + cap["cache_load"][i] for i in range(len(cap["inference"]))]

rag50 = data["RAG_INLINE@50"]
rag100 = data["RAG_INLINE@100"]
rag200 = data["RAG_INLINE@200"]
rag500 = data["RAG_INLINE@500"]
zero = data["ZERO_CONTEXT@0"]

# For non-CAP conditions, cache_load is always 0
rag50_e2e = rag50["inference"]
rag100_e2e = rag100["inference"]
rag200_e2e = rag200["inference"]
rag500_e2e = rag500["inference"]
zero_e2e = zero["inference"]


def check(name, computed, manuscript, tolerance=0.015):
    """Check if computed value matches manuscript within tolerance."""
    match = abs(computed - manuscript) <= tolerance * max(abs(manuscript), 1.0)
    status = "MATCH" if match else "MISMATCH"
    return f"  {name:40s} computed={computed:12.2f}  manuscript={manuscript:12.2f}  [{status}]"


results = []
print("=" * 90)
print("COMPLETE STATISTICAL VERIFICATION")
print(f"Source: {CANONICAL}")
print("=" * 90)

# === TABLE II: E2E MEANS ===
print("\n--- TABLE II: End-to-End Latency (ms) ---")
results.append(check("ZERO E2E mean", statistics.mean(zero_e2e), 3823.78))
results.append(check("ZERO E2E sd", statistics.stdev(zero_e2e), 569.76))
results.append(check("RAG@50 E2E mean", statistics.mean(rag50_e2e), 6547.13))
results.append(check("RAG@50 E2E sd", statistics.stdev(rag50_e2e), 8392.58))
results.append(check("RAG@100 E2E mean", statistics.mean(rag100_e2e), 7900.92))
results.append(check("RAG@100 E2E sd", statistics.stdev(rag100_e2e), 3857.18))
results.append(check("RAG@200 E2E mean", statistics.mean(rag200_e2e), 12122.30))
results.append(check("RAG@200 E2E sd", statistics.stdev(rag200_e2e), 2740.12))
results.append(check("RAG@500 E2E mean", statistics.mean(rag500_e2e), 19408.89))
results.append(check("RAG@500 E2E sd", statistics.stdev(rag500_e2e), 2113.04))
results.append(check("CAP E2E mean", statistics.mean(cap_e2e), 4574.70))
results.append(check("CAP E2E sd", statistics.stdev(cap_e2e), 373.49))

# === TABLE II: PREFILL ===
print("\n--- TABLE II: Prefill Latency (ms) ---")
results.append(check("ZERO prefill mean", statistics.mean(zero["prefill"]), 274.79))
results.append(check("RAG@50 prefill mean", statistics.mean(rag50["prefill"]), 2021.46))
results.append(check("RAG@100 prefill mean", statistics.mean(rag100["prefill"]), 3015.09))
results.append(check("RAG@200 prefill mean", statistics.mean(rag200["prefill"]), 6673.53))
results.append(check("RAG@500 prefill mean", statistics.mean(rag500["prefill"]), 14001.46))
results.append(check("CAP prefill mean", statistics.mean(cap["prefill"]), 343.35))
results.append(check("CAP prefill sd", statistics.stdev(cap["prefill"]), 45.00))

# === TABLE II: GENERATION ===
print("\n--- TABLE II: Generation Latency (ms) ---")
results.append(check("ZERO gen mean", statistics.mean(zero["gen"]), 3544.58))
results.append(check("RAG@50 gen mean", statistics.mean(rag50["gen"]), 4508.53))
results.append(check("RAG@100 gen mean", statistics.mean(rag100["gen"]), 4877.95))
results.append(check("RAG@200 gen mean", statistics.mean(rag200["gen"]), 5439.96))
results.append(check("RAG@500 gen mean", statistics.mean(rag500["gen"]), 5390.76))
results.append(check("CAP gen mean", statistics.mean(cap["gen"]), 4226.74))

# === TABLE II: TOKENS ===
print("\n--- TABLE II: Prompt Tokens ---")
results.append(check("ZERO tokens", statistics.mean([float(r["prompt_tokens"]) for r in csv.DictReader(open(CANONICAL)) if r["mode"] == "ZERO_CONTEXT"]), 9))
results.append(check("CAP tokens", statistics.mean([float(r["prompt_tokens"]) for r in csv.DictReader(open(CANONICAL)) if r["mode"] == "CAP_KVC"]), 9))
results.append(check("RAG@500 tokens", statistics.mean([float(r["prompt_tokens"]) for r in csv.DictReader(open(CANONICAL)) if r["mode"] == "RAG_INLINE" and r["prompt_token_target"] == "500"]), 439))

# === CACHE LOAD ===
print("\n--- Cache Restoration ---")
results.append(check("Cache load mean", statistics.mean(cap["cache_load"]), 2.74))
results.append(check("Cache load sd", statistics.stdev(cap["cache_load"]), 1.35))

# === CAP_KVC inference (without cache_load) ===
print("\n--- CAP_KVC Inference (without cache_load) ---")
results.append(check("CAP inference mean", statistics.mean(cap["inference"]), 4571.97))
results.append(check("CAP inference sd", statistics.stdev(cap["inference"]), 373.65))

# === SPEEDUPS ===
print("\n--- Speedups ---")
e2e_speedup_500 = statistics.mean(rag500_e2e) / statistics.mean(cap_e2e)
prefill_speedup_500 = statistics.mean(rag500["prefill"]) / statistics.mean(cap["prefill"])
results.append(check("E2E speedup @500", e2e_speedup_500, 4.24))
results.append(check("Prefill speedup @500", prefill_speedup_500, 40.78))

# === WELCH T-TESTS ===
print("\n--- Welch t-tests (prefill) ---")
t50, p50 = stats.ttest_ind(cap["prefill"], rag50["prefill"], equal_var=False)
t500, p500 = stats.ttest_ind(cap["prefill"], rag500["prefill"], equal_var=False)

pooled_50 = math.sqrt((statistics.variance(cap["prefill"]) + statistics.variance(rag50["prefill"])) / 2)
pooled_500 = math.sqrt((statistics.variance(cap["prefill"]) + statistics.variance(rag500["prefill"])) / 2)
d50 = (statistics.mean(rag50["prefill"]) - statistics.mean(cap["prefill"])) / pooled_50
d500 = (statistics.mean(rag500["prefill"]) - statistics.mean(cap["prefill"])) / pooled_500

results.append(check("Cohen's d vs RAG@50", d50, 0.70))
results.append(check("Cohen's d vs RAG@500", d500, 10.44))

print(f"  t-test vs RAG@50:  t={t50:.2f}, p={p50:.2e}")
print(f"  t-test vs RAG@500: t={t500:.2f}, p={p500:.2e}")

# === E2E WELCH T-TESTS ===
print("\n--- Welch t-tests (E2E) ---")
te50, pe50 = stats.ttest_ind(cap_e2e, rag50_e2e, equal_var=False)
te500, pe500 = stats.ttest_ind(cap_e2e, rag500_e2e, equal_var=False)
pooled_e50 = math.sqrt((statistics.variance(cap_e2e) + statistics.variance(rag50_e2e)) / 2)
pooled_e500 = math.sqrt((statistics.variance(cap_e2e) + statistics.variance(rag500_e2e)) / 2)
de50 = (statistics.mean(rag50_e2e) - statistics.mean(cap_e2e)) / pooled_e50
de500 = (statistics.mean(rag500_e2e) - statistics.mean(cap_e2e)) / pooled_e500
results.append(check("E2E Cohen's d vs RAG@50", de50, 0.33))
results.append(check("E2E Cohen's d vs RAG@500", de500, 9.76))

# === PRINT ALL RESULTS ===
print("\n" + "=" * 90)
print("VERIFICATION SUMMARY")
print("=" * 90)
matches = 0
mismatches = 0
for r in results:
    print(r)
    if "MATCH" in r and "MISMATCH" not in r:
        matches += 1
    else:
        mismatches += 1

print(f"\nTotal checks: {len(results)}")
print(f"Matches: {matches}")
print(f"Mismatches: {mismatches}")

if mismatches == 0:
    print("\n=== ALL STATISTICS VERIFIED ===")
else:
    print(f"\n=== {mismatches} MISMATCHES FOUND — INVESTIGATE ===")
