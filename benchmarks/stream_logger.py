"""
Robust live benchmark monitor and logger for AACBridge.
Continuously syncs logcat from the phone directly into new_run.log every 5 seconds.
When suite completes, automatically extracts canonical_benchmark.csv and runs analysis.
"""
import subprocess
import sys
import os
import time

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
LOG_PATH = os.path.join(SCRIPT_DIR, "results", "new_run.log")
CANONICAL_CSV = os.path.join(SCRIPT_DIR, "results", "canonical_benchmark.csv")
EXTRACT_SCRIPT = os.path.join(SCRIPT_DIR, "extract_enriched.py")
ANALYZE_SCRIPT = os.path.join(SCRIPT_DIR, "..", "evaluation", "latency", "analyze_benchmarks.py")

def main():
    print(f"[*] Starting robust live benchmark sync to: {LOG_PATH}", flush=True)

    suite_finished = False
    last_trial_seen = ""

    while not suite_finished:
        try:
            res = subprocess.run(
                ["adb", "logcat", "-d", "-v", "time", "-s", "LatencyProfiler:V", "AACBridgeJNI:V"],
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace"
            )

            if res.stdout:
                with open(LOG_PATH, "w", encoding="utf-8") as f:
                    f.write(res.stdout)

                # Check for progress lines to print to console
                for line in res.stdout.splitlines():
                    if "TRIAL_RESULT:" in line and line != last_trial_seen:
                        last_trial_seen = line
                        print(line, flush=True)

                if "=== BENCHMARK SUITE COMPLETE ===" in res.stdout:
                    print("\n[*] Detected === BENCHMARK SUITE COMPLETE ===!", flush=True)
                    suite_finished = True
                    break

        except Exception as e:
            print(f"[!] Warning: sync loop error: {e}", flush=True)

        time.sleep(5)

    print("\n[*] Benchmark complete! Extracting canonical_benchmark.csv...", flush=True)
    try:
        with open(CANONICAL_CSV, "w", encoding="utf-8") as out_f:
            res = subprocess.run([sys.executable, EXTRACT_SCRIPT, LOG_PATH], stdout=out_f, text=True, check=True)
        print(f"[+] Canonical dataset saved to: {CANONICAL_CSV}", flush=True)

        print("\n[*] Running analyze_benchmarks.py...", flush=True)
        subprocess.run([sys.executable, ANALYZE_SCRIPT], check=True)
        print("\n[+] All benchmark numbers and analysis complete!", flush=True)
    except Exception as e:
        print(f"[-] Error processing results: {e}", flush=True)

if __name__ == "__main__":
    main()
