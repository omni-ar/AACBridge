"""
validate_latex.py — Structural LaTeX validation for final_main.tex.

Since no TeX distribution is available in this environment,
this script performs automated checks for common errors:
  1. Brace balance
  2. Environment balance (begin/end)
  3. Missing figure files
  4. Broken \ref{} and \cite{} references
  5. Orphaned \bibitem entries
  6. Package requirements
  7. Key statistics cross-check
"""
import re
import os

TEX_FILE = r"d:\AACBridge\paper\final_main.tex"
FIGURES_DIR = r"d:\AACBridge\paper\figures"

with open(TEX_FILE, "r", encoding="utf-8") as f:
    content = f.read()
    lines = content.split("\n")

errors = []
warnings = []

# 1. Brace balance
depth = 0
for i, line in enumerate(lines, 1):
    # Skip comments
    clean = re.sub(r'(?<!\\)%.*$', '', line)
    for ch in clean:
        if ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth < 0:
                errors.append(f"L{i}: Unmatched closing brace")
                depth = 0
if depth != 0:
    errors.append(f"Unbalanced braces: {depth} unclosed")
else:
    print("[OK] Brace balance: balanced")

# 2. Environment balance
env_stack = []
env_re = re.compile(r'\\(begin|end)\{(\w+)\}')
for i, line in enumerate(lines, 1):
    clean = re.sub(r'(?<!\\)%.*$', '', line)
    for m in env_re.finditer(clean):
        action, name = m.group(1), m.group(2)
        if action == 'begin':
            env_stack.append((name, i))
        elif action == 'end':
            if env_stack and env_stack[-1][0] == name:
                env_stack.pop()
            else:
                expected = env_stack[-1][0] if env_stack else "none"
                errors.append(f"L{i}: \\end{{{name}}} but expected \\end{{{expected}}}")
if env_stack:
    for name, line in env_stack:
        errors.append(f"L{line}: \\begin{{{name}}} never closed")
else:
    print("[OK] Environment balance: all matched")

# 3. Figure file references
fig_re = re.compile(r'\\includegraphics(?:\[.*?\])?\{([^}]+)\}')
for m in fig_re.finditer(content):
    fig_path = m.group(1)
    # Try with and without extension
    candidates = [
        os.path.join(FIGURES_DIR, fig_path),
        os.path.join(FIGURES_DIR, fig_path + ".png"),
        os.path.join(FIGURES_DIR, fig_path + ".pdf"),
        os.path.join(os.path.dirname(TEX_FILE), fig_path),
    ]
    found = any(os.path.exists(c) for c in candidates)
    if not found:
        errors.append(f"Missing figure: {fig_path}")
    else:
        print(f"[OK] Figure: {fig_path}")

# 4. Label/Ref consistency
label_re = re.compile(r'\\label\{([^}]+)\}')
ref_re = re.compile(r'\\ref\{([^}]+)\}')
labels = set(label_re.findall(content))
refs = set(ref_re.findall(content))
broken_refs = refs - labels
if broken_refs:
    for r in broken_refs:
        errors.append(f"Broken \\ref{{{r}}}: no matching \\label")
else:
    print(f"[OK] References: {len(refs)} refs, {len(labels)} labels, 0 broken")

# 5. Citation/Bibitem consistency
cite_re = re.compile(r'\\cite\{([^}]+)\}')
bibitem_re = re.compile(r'\\bibitem\{([^}]+)\}')
cites = set()
for m in cite_re.finditer(content):
    for key in m.group(1).split(","):
        cites.add(key.strip())
bibitems = set(bibitem_re.findall(content))
broken_cites = cites - bibitems
orphan_bibs = bibitems - cites
if broken_cites:
    for c in broken_cites:
        errors.append(f"Broken \\cite{{{c}}}: no matching \\bibitem")
else:
    print(f"[OK] Citations: {len(cites)} cited, {len(bibitems)} defined, 0 broken")
if orphan_bibs:
    for b in orphan_bibs:
        warnings.append(f"Orphan \\bibitem{{{b}}}: never cited")

# 6. Required packages
used_packages = set(re.findall(r'\\usepackage(?:\[.*?\])?\{([^}]+)\}', content))
print(f"[INFO] Packages used: {', '.join(sorted(used_packages))}")

# 7. IEEEtran document class
if r'\documentclass' in content:
    dc_match = re.search(r'\\documentclass(?:\[.*?\])?\{(\w+)\}', content)
    if dc_match:
        print(f"[OK] Document class: {dc_match.group(1)}")
    else:
        errors.append("Cannot parse \\documentclass")

# 8. Check for common LaTeX errors
# Unescaped underscores in text mode (not in math or command)
# This is complex to check properly, so just warn about bare _ outside $
underscore_re = re.compile(r'(?<![\\$])_(?![}$])')
# Skip this check as it's too noisy with Kotlin/C++ code in the paper

# 9. Key numbers verification
key_numbers = {
    "2.74": "cache load mean",
    "1.35": "cache load SD",
    "4574.70": "CAP E2E mean",
    "19408.89": "RAG@500 E2E mean",
    "4.24": "E2E speedup",
    "40.78": "prefill speedup",
    "343": "CAP prefill",
    "14001": "RAG@500 prefill",
    "10.44": "Cohen's d @500",
    "0.70": "Cohen's d @50",
}
for num, desc in key_numbers.items():
    if num in content:
        count = content.count(num)
        print(f"[OK] {desc} ({num}): found {count} occurrence(s)")
    else:
        warnings.append(f"Number {num} ({desc}) not found in manuscript")

# Summary
print(f"\n{'='*60}")
print(f"VALIDATION SUMMARY")
print(f"{'='*60}")
print(f"Errors:   {len(errors)}")
print(f"Warnings: {len(warnings)}")
for e in errors:
    print(f"  ERROR: {e}")
for w in warnings:
    print(f"  WARNING: {w}")

if len(errors) == 0:
    print("\n=== STRUCTURAL VALIDATION PASSED ===")
else:
    print(f"\n=== {len(errors)} ERRORS FOUND ===")
