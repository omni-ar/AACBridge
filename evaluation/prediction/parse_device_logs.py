"""Turn on-device SWEEP / INTERACTION logcat lines into CSVs and rates.

Collect on the phone (app running normally, benchmark NOT requested):

    adb logcat -c
    # ... use the app / leave the daemon running ...
    adb logcat -d -s ActiveSweep:I AACBridge:I > device_log.txt

Then:

    python evaluation/prediction/parse_device_logs.py device_log.txt out_dir/

Writes out_dir/sweeps.csv, out_dir/interactions.csv and prints outcome
rates (HIT_TOP1 / HIT_RESIDENT_NOT_TOP1 / COLD_MISS / FALLBACK_*) and
per-sweep load/evict counts. No ground truth is logged, so wrong-context
rate cannot be computed from device logs alone.
"""

import csv
import os
import re
import sys
from collections import Counter

LINE = re.compile(r"\b(SWEEP|INTERACTION),(.*)$")


def parse(path):
    sweeps, interactions = [], []
    with open(path, encoding="utf-8", errors="replace") as f:
        for raw in f:
            m = LINE.search(raw.rstrip())
            if not m:
                continue
            kind, body = m.groups()
            row = dict(kv.split("=", 1) for kv in body.split(",") if "=" in kv)
            (sweeps if kind == "SWEEP" else interactions).append(row)
    return sweeps, interactions


def write(rows, path):
    if not rows:
        return
    keys = sorted({k for r in rows for k in r})
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(rows)


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    sweeps, interactions = parse(sys.argv[1])
    os.makedirs(sys.argv[2], exist_ok=True)
    write(sweeps, os.path.join(sys.argv[2], "sweeps.csv"))
    write(interactions, os.path.join(sys.argv[2], "interactions.csv"))

    print(f"sweeps: {len(sweeps)}")
    if sweeps:
        loads = sum(len([x for x in s.get("loaded", "").split("|") if x]) for s in sweeps)
        evicts = sum(len([x for x in s.get("evicted", "").split("|") if x]) for s in sweeps)
        gps = sum(s.get("gps") == "true" for s in sweeps)
        print(f"  loads: {loads}  evictions: {evicts}  gps_available: {gps / len(sweeps):.3f}")

    print(f"interactions: {len(interactions)}")
    counts = Counter(r.get("outcome", "?") for r in interactions)
    for outcome, n in counts.most_common():
        print(f"  {outcome}: {n} ({n / len(interactions):.3f})")


if __name__ == "__main__":
    main()
