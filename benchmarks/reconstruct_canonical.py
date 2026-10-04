"""
Reconstructs canonical_benchmark.csv from the complete 180-trial task log.
"""
import re
import csv
import os

regex = re.compile(
    r'TRIAL_RESULT:\s+mode=(\w+)\s+target=(\d+)\s+trial=(\d+)\s+=>\s+'
    r'total_ms=([0-9,.]+)\s+prefill_ms=([0-9,.]+)\s+gen_ms=([0-9,.]+)\s+'
    r'cache_load_ms=([0-9,.]+)\s+prompt_tokens=(\d+)\s+gen_tokens=(\d+)'
)

entries = {}
log_path = r'C:\Users\arjit\.gemini\antigravity-ide\brain\b521c9f7-1f35-433d-b818-a674eea18c9e\.system_generated\tasks\task-1454.log'

with open(log_path, 'r', encoding='utf-8', errors='replace') as f:
    for line in f:
        m = regex.search(line)
        if m:
            mode, target, trial, total_ms, prefill_ms, gen_ms, cache_load_ms, prompt_tokens, gen_tokens = m.groups()
            key = (mode, int(target), int(trial))
            entries[key] = {
                'trial': int(trial),
                'mode': mode,
                'stateId': 'none' if mode == 'ZERO_CONTEXT' else 'home_morning',
                'prompt_token_target': int(target),
                'inference_ms': float(total_ms.replace(',', '')),
                'cache_load_ms': float(cache_load_ms.replace(',', '')),
                'gen_length': int(gen_tokens),
                'prefill_ms': float(prefill_ms.replace(',', '')),
                'gen_ms': float(gen_ms.replace(',', '')),
                'prompt_tokens': int(prompt_tokens),
                'gen_tokens': int(gen_tokens),
            }

csv_path = r'D:\AACBridge\benchmarks\results\canonical_benchmark.csv'
fieldnames = ['trial', 'mode', 'stateId', 'prompt_token_target', 'inference_ms', 'cache_load_ms', 'gen_length', 'prefill_ms', 'gen_ms', 'prompt_tokens', 'gen_tokens']

mode_order = {'ZERO_CONTEXT': 0, 'RAG_INLINE': 1, 'CAP_KVC': 2}
sorted_entries = sorted(entries.values(), key=lambda r: (mode_order[r['mode']], r['prompt_token_target'], r['trial']))

with open(csv_path, 'w', newline='', encoding='utf-8') as f:
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    for row in sorted_entries:
        writer.writerow({
            'trial': row['trial'],
            'mode': row['mode'],
            'stateId': row['stateId'],
            'prompt_token_target': row['prompt_token_target'],
            'inference_ms': f"{row['inference_ms']:.2f}",
            'cache_load_ms': f"{row['cache_load_ms']:.2f}",
            'gen_length': row['gen_length'],
            'prefill_ms': f"{row['prefill_ms']:.2f}",
            'gen_ms': f"{row['gen_ms']:.2f}",
            'prompt_tokens': row['prompt_tokens'],
            'gen_tokens': row['gen_tokens'],
        })

print(f"Successfully generated {csv_path} with {len(sorted_entries)} rows.")
