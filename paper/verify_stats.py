"""
Reproducibility verification: Welch's t-test on ttft_clean.csv
This script reads the ACTUAL benchmark data and computes the exact
statistical values that appear in final_main.tex.
"""
import csv
import math
from scipy import stats

# 1. Read raw data
rows = []
with open(r'd:\AACBridge\benchmarks\results\ttft_clean.csv', 'r') as f:
    for r in csv.DictReader(f):
        rows.append(r)

print(f"Total rows in CSV: {len(rows)}")

# 2. Extract per-condition arrays
cap_total = [float(r['inference_ms']) + float(r['cache_load_ms'])
             for r in rows if r['mode'] == 'CAP_KVC']
rag50 = [float(r['inference_ms'])
         for r in rows if r['mode'] == 'RAG_INLINE' and r['prompt_token_target'] == '50']
rag500 = [float(r['inference_ms'])
          for r in rows if r['mode'] == 'RAG_INLINE' and r['prompt_token_target'] == '500']

print(f"CAP_KVC trials: {len(cap_total)}")
print(f"RAG@50 trials:  {len(rag50)}")
print(f"RAG@500 trials: {len(rag500)}")

# 3. Verify means match Table I
import numpy as np
print(f"\nCAP_KVC mean: {np.mean(cap_total):.2f} ms (Table I says 3403.92)")
print(f"RAG@50  mean: {np.mean(rag50):.2f} ms  (Table I says 4505.82)")
print(f"RAG@500 mean: {np.mean(rag500):.2f} ms (Table I says 15632.06)")

# 4. Run Welch's t-tests
t1, p1 = stats.ttest_ind(cap_total, rag50, equal_var=False)
t2, p2 = stats.ttest_ind(cap_total, rag500, equal_var=False)

# 5. Degrees of freedom (Welch-Satterthwaite)
def welch_df(a, b):
    n1, n2 = len(a), len(b)
    v1, v2 = np.var(a, ddof=1), np.var(b, ddof=1)
    num = (v1/n1 + v2/n2)**2
    den = (v1/n1)**2/(n1-1) + (v2/n2)**2/(n2-1)
    return num / den

df1 = welch_df(cap_total, rag50)
df2 = welch_df(cap_total, rag500)

# 6. Cohen's d
def cohens_d(a, b):
    na, nb = len(a), len(b)
    va, vb = np.var(a, ddof=1), np.var(b, ddof=1)
    pooled = math.sqrt(((na-1)*va + (nb-1)*vb) / (na+nb-2))
    return abs(np.mean(a) - np.mean(b)) / pooled

d1 = cohens_d(cap_total, rag50)
d2 = cohens_d(cap_total, rag500)

# 7. Print results
print("\n" + "="*60)
print("TEST 1: CAP_KVC vs RAG@50")
print(f"  t({df1:.1f}) = {t1:.2f}")
print(f"  p = {p1:.2e}")
print(f"  Cohen's d = {d1:.2f}")
print(f"  Paper says: t(46.9)=-18.23, p<10^-22, d=4.71")

print("\nTEST 2: CAP_KVC vs RAG@500")
print(f"  t({df2:.1f}) = {t2:.2f}")
print(f"  p = {p2:.2e}")
print(f"  Cohen's d = {d2:.2f}")
print(f"  Paper says: t(36.2)=-78.60, p<10^-41, d=20.29")

print("\nVERDICT:")
print(f"  Test 1 matches paper: {abs(t1 - (-18.23)) < 0.1 and abs(d1 - 4.71) < 0.01}")
print(f"  Test 2 matches paper: {abs(t2 - (-78.60)) < 0.1 and abs(d2 - 20.29) < 0.01}")
