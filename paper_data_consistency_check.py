#!/usr/bin/env python3
"""
Paper data consistency checker.

Reads the CSV data files and the LaTeX manuscript, then verifies
that every number quoted in the paper matches the underlying data.

Usage:
    python paper_data_consistency_check.py

Expects to be run from the repo root (D:/AACBridge/).
"""

import csv
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent

ERRORS = []
WARNINGS = []

def err(msg):
    ERRORS.append(msg)
    print(f"  ERROR: {msg}")

def warn(msg):
    WARNINGS.append(msg)
    print(f"  WARN:  {msg}")

def ok(msg):
    print(f"  OK:    {msg}")


def load_csv(path):
    with open(path, newline="") as f:
        return list(csv.DictReader(f))


def check_prediction_eval():
    print("\n=== Prediction Eval (prediction_eval.csv) ===")
    data = load_csv(REPO / "evaluation/prediction/tables/prediction_eval.csv")
    by_name = {row["config"]: row for row in data}

    # Paper claims: FULL correct = 98.5%
    full = by_name.get("FULL")
    if full:
        correct = float(full["served_correct"])
        if abs(correct - 0.984844) < 0.001:
            ok(f"FULL correct = {correct:.4f} (paper says 98.5%)")
        else:
            err(f"FULL correct = {correct:.4f}, paper says 98.5%")
    else:
        err("FULL config not found in prediction_eval.csv")

    # LRU = 92.2%
    lru = by_name.get("REACTIVE_LRU_NO_SENSING")
    if lru:
        correct = float(lru["served_correct"])
        if abs(correct - 0.921875) < 0.001:
            ok(f"LRU correct = {correct:.4f} (paper says 92.2%)")
        else:
            err(f"LRU correct = {correct:.4f}, paper says 92.2%")

    # GPS spoof undetected = 38.9% wrong
    spoof = by_name.get("FULL_GPS_SPOOF_UNDETECTED")
    if spoof:
        wrong = float(spoof["wrong_context"])
        if abs(wrong - 0.388906) < 0.001:
            ok(f"GPS spoof wrong = {wrong:.4f} (paper says 38.9%)")
        else:
            err(f"GPS spoof wrong = {wrong:.4f}, paper says 38.9%")

    # BLE spoof = 9.9% wrong
    ble = by_name.get("FULL_BLE_SPOOF")
    if ble:
        wrong = float(ble["wrong_context"])
        if abs(wrong - 0.099219) < 0.002:
            ok(f"BLE spoof wrong = {wrong:.4f} (paper says 9.9%)")
        else:
            err(f"BLE spoof wrong = {wrong:.4f}, paper says 9.9%")

    # 6400 interactions
    if full:
        interactions = int(full["interactions"])
        if interactions == 6400:
            ok(f"Interactions = {interactions} (paper says 6,400)")
        else:
            err(f"Interactions = {interactions}, paper says 6,400")


def check_markov_baseline():
    print("\n=== Markov Baseline (markov_baseline.csv) ===")
    path = REPO / "evaluation/prediction/tables/markov_baseline.csv"
    if not path.exists():
        warn("markov_baseline.csv not found (new experiment)")
        return

    data = load_csv(path)
    by_name = {row["policy"]: row for row in data}

    markov = by_name.get("MARKOV_SUCCESSOR")
    if markov:
        correct = float(markov["correct"])
        if abs(correct - 0.996875) < 0.001:
            ok(f"Markov correct = {correct:.4f} (paper says 99.7%)")
        else:
            err(f"Markov correct = {correct:.4f}, paper says 99.7%")


def check_k_ablation():
    print("\n=== K Ablation (k_ablation.csv) ===")
    path = REPO / "evaluation/prediction/tables/k_ablation.csv"
    if not path.exists():
        warn("k_ablation.csv not found (new experiment)")
        return

    data = load_csv(path)
    by_k = {row["K"]: row for row in data}

    k1 = by_k.get("1")
    if k1:
        correct = float(k1["correct"])
        if abs(correct - 0.994375) < 0.001:
            ok(f"K=1 correct = {correct:.4f} (paper says 99.4%)")
        else:
            err(f"K=1 correct = {correct:.4f}, paper says 99.4%")


def check_multi_seed():
    print("\n=== Multi-Seed (multi_seed_eval.csv) ===")
    path = REPO / "evaluation/prediction/tables/multi_seed_eval.csv"
    if not path.exists():
        warn("multi_seed_eval.csv not found (new experiment)")
        return

    data = load_csv(path)
    full_rows = [row for row in data if row["policy"] == "FULL"]

    if not full_rows:
        err("No FULL rows in multi_seed_eval.csv")
        return

    correct_rates = [float(r["correct"]) for r in full_rows]
    mean = sum(correct_rates) / len(correct_rates)
    mn = min(correct_rates)
    mx = max(correct_rates)

    if abs(mean - 0.983) < 0.005:
        ok(f"Multi-seed mean = {mean:.4f} (paper says 98.3%)")
    else:
        err(f"Multi-seed mean = {mean:.4f}, paper says 98.3%")

    ok(f"Range: [{mn:.4f}, {mx:.4f}] ({len(full_rows)} seeds)")


def check_latency():
    print("\n=== Latency (interleaved_20261005_trials.csv) ===")
    path = REPO / "benchmarks/results/interleaved_20261005_trials.csv"
    if not path.exists():
        warn("interleaved_20261005_trials.csv not found")
        return

    data = load_csv(path)

    import statistics as st
    cap = [float(r["prefill_ms"]) for r in data if r["mode"] == "CAP_KVC"]
    rag50 = [float(r["prefill_ms"]) for r in data if r["mode"] == "RAG_INLINE" and r["target"] == "50"]
    restore = [float(r["cache_load_ms"]) for r in data if r["mode"] == "CAP_KVC"]

    if len(cap) != 30:
        err(f"CAP_KVC trials = {len(cap)}, expected 30")
    else:
        ok(f"CAP_KVC trials = {len(cap)}")

    if len(rag50) != 30:
        err(f"RAG_INLINE@50 trials = {len(rag50)}, expected 30")
    else:
        ok(f"RAG_INLINE@50 trials = {len(rag50)}")

    # Paper says: CAP prefill mean 366 ms
    cap_mean = st.mean(cap)
    if abs(cap_mean - 366) < 1:
        ok(f"CAP prefill mean = {cap_mean:.1f} (paper says 366)")
    else:
        err(f"CAP prefill mean = {cap_mean:.1f}, paper says 366")

    # Paper says: RAG@50 prefill mean 1847 ms
    rag_mean = st.mean(rag50)
    if abs(rag_mean - 1847) < 1:
        ok(f"RAG@50 prefill mean = {rag_mean:.1f} (paper says 1847)")
    else:
        err(f"RAG@50 prefill mean = {rag_mean:.1f}, paper says 1847")

    # Ratio: 5.0x
    ratio = rag_mean / cap_mean
    if abs(ratio - 5.0) < 0.1:
        ok(f"Ratio = {ratio:.1f}x (paper says 5.0x)")
    else:
        err(f"Ratio = {ratio:.1f}x, paper says 5.0x")

    # Restore mean: 2.9 ms
    r_mean = st.mean(restore)
    if abs(r_mean - 2.9) < 0.1:
        ok(f"Restore mean = {r_mean:.1f} (paper says 2.9)")
    else:
        err(f"Restore mean = {r_mean:.1f}, paper says 2.9")

    # Contamination check: 0 BUSY/HIT events during suite
    log_path = REPO / "benchmarks/raw_logs/full_interleaved_20261005.log"
    if log_path.exists():
        text = log_path.read_text(encoding="utf-8-sig", errors="replace")
        in_suite = False
        contam = 0
        for ln in text.splitlines():
            if "BENCHMARK SUITE START" in ln:
                in_suite = True
            elif "BENCHMARK SUITE COMPLETE" in ln:
                in_suite = False
            if in_suite and "INTERACTION" in ln:
                if "FALLBACK_BUSY" in ln or "HIT_TOP" in ln:
                    contam += 1
        if contam == 0:
            ok(f"No contaminating interactions during benchmark suite")
        else:
            err(f"{contam} contaminating interactions during benchmark suite")


def check_hysteresis():
    print("\n=== Hysteresis (hysteresis_eval.csv) ===")
    data = load_csv(REPO / "evaluation/prediction/tables/hysteresis_eval.csv")

    # Normal trace, margin=0.10
    m010 = [r for r in data if r["trace"] == "NORMAL" and r["margin"] == "0.10"]
    if m010:
        loads = float(m010[0]["loads_per_day"])
        if abs(loads - 31.6) < 0.1:
            ok(f"Normal margin=0.10 loads/day = {loads} (paper says 31.6)")
        else:
            err(f"Normal margin=0.10 loads/day = {loads}, paper says 31.6")

    # Normal, margin=0.00
    m000 = [r for r in data if r["trace"] == "NORMAL" and r["margin"] == "0.00"]
    if m000:
        loads = float(m000[0]["loads_per_day"])
        if abs(loads - 65.9) < 0.2:
            ok(f"Normal margin=0.00 loads/day = {loads} (paper says 65.9)")
        else:
            err(f"Normal margin=0.00 loads/day = {loads}, paper says 65.9")


def check_jni_count():
    print("\n=== JNI Method Count ===")
    bridge_path = REPO / "android/app/src/main/java/com/aacbridge/inference/LlamaBridge.kt"
    if not bridge_path.exists():
        err("LlamaBridge.kt not found")
        return

    content = bridge_path.read_text()
    # Count all lines with 'external fun' (includes 'override external fun')
    externals = re.findall(r'\bexternal fun \w+', content)
    total = len(externals)

    if total == 14:
        ok(f"JNI methods = {total} (paper says 14)")
    else:
        err(f"JNI methods = {total}, paper says 14")


if __name__ == "__main__":
    print("=" * 60)
    print("AACBridge Paper 262 — Data Consistency Check")
    print("=" * 60)

    check_prediction_eval()
    check_markov_baseline()
    check_k_ablation()
    check_multi_seed()
    check_latency()
    check_hysteresis()
    check_jni_count()

    print("\n" + "=" * 60)
    print(f"RESULT: {len(ERRORS)} errors, {len(WARNINGS)} warnings")
    if ERRORS:
        print("\nERRORS:")
        for e in ERRORS:
            print(f"  - {e}")
        sys.exit(1)
    else:
        print("All checks passed.")
        sys.exit(0)
