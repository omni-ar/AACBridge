# Agent D — Authorship / Style Audit

**Date:** 2026-10-05
**Scope:** ieee_main.tex prose quality, AI-pattern detection, Pangram report analysis

---

## 1. Overall Assessment

The current `ieee_main.tex` is **substantially better** than a typical AI-generated draft. Many paragraphs already contain project-specific details and honest limitations. The "HUMAN-SCORED BLOCK" comments (lines 74, 77, 80, 83, 86, 243, 248, 251, 263) indicate that these sections were specifically written to sound author-specific.

**However**, Pangram flagged 64% AI-generated, suggesting structural patterns remain.

## 2. Structural Patterns Detected

### Pattern A: "Problem → However → Therefore" Template
This is the most common AI structural pattern. Examples:

**Line 78**: "However, feeding this information into the language model prior to submitting a query is very costly."
**Line 81**: "However, this approach relies heavily upon there being some number of users requesting similar queries."

### Pattern B: "The question is..." / "Three quantities..." framing
**Line 270**: "The question here is simple: how much prefill work can be removed..."
**Line 301**: "Three quantities matter and should be kept distinct"

### Pattern C: Explanatory hedging
**Line 266-267**: "One problem with this particular run..." — this is actually good, but the phrasing is borderline response-letter language
**Line 349**: "The comparison with request-driven LRU deserves some unpacking" — conversational transition

## 3. Sentence-Level AI Patterns

### Overused transitions (count):
| Phrase | Count | Assessment |
|--------|-------|------------|
| However, | 2 | Normal for a technical paper |
| Still, | 1 | Normal |
| Therefore, | 0 | Good |
| Moreover, | 0 | Good |
| Furthermore, | 0 | Good |
| Notably, | 0 | Good |

**The transition usage is actually restrained.** The paper doesn't suffer from excessive connective tissue.

### Buzzword scan:
| Phrase | Count | Assessment |
|--------|-------|------------|
| robust | 0 | Good |
| seamless | 0 | Good |
| scalable | 0 | Good |
| novel | 0 | Good |
| state-of-the-art | 0 | Good |
| leveraging | 0 | Good |
| significantly | 0 | Good |
| promising | 0 | Good |
| addresses the challenge | 0 | Good |

**The paper has already been cleaned of most buzzwords.** This is a strength.

## 4. Response-Letter Language Still in Paper

**Line 228**: "An earlier draft conflated the two and justified Δ with an unrelated text-similarity experiment; we drop that justification."
→ This is response-letter language. Rewrite as current-state description.

**Line 266**: "One problem with this particular run: the phone's gaze interface remained active during the benchmark..."
→ This acknowledges a confound but reads like a revision note. Rewrite as methodology limitation.

**Line 266**: "The app has since been updated to block interaction inference during benchmarks..."
→ "Since been updated" is response-letter. State current behavior.

## 5. Sections by AI-Risk Level

### A (Strongly Author-Specific) — Keep
- Introduction paragraphs (lines 75-89): Dense with project facts, honest about limitations
- Sequence slots paragraph (line 244): Implementation-specific detail
- KV memory calculation (line 249): Concrete technical fact
- Sensing paragraph (line 252): Specific parameters
- Prediction traces paragraph (line 311): Honest about circularity
- Limitations section (line 387): Plain-language limitations

### B (Normal Technical Writing) — Acceptable
- Scoring equations (lines 209-222): Standard mathematical exposition
- Table descriptions (lines 271-272, 347-353): Data-driven
- Hysteresis results (line 364): Measurement-driven

### C (Generic AI-Like Prose) — Needs Rewrite
- **Line 96**: "The approaches differ in granularity (prefix trees, shared blocks, precomputed modules, non-prefix chunks, per-document state, compressed transferable state), but they share a common assumption" — This reads like a survey paragraph enumerating features
- **Line 228** (partial): Response-letter language mixed with technical content
- **Line 270**: "The question here is simple" — unnecessary framing

## 6. Paragraph Statistics

| Section | Paragraphs | Avg sentences/para | Assessment |
|---------|------------|-------------------|------------|
| Abstract | 1 | 10 | Dense but acceptable for abstract |
| Introduction | 5 | 3-5 | Good length |
| Related Work | 3 | 3-6 | One long paragraph (line 96) |
| System Design | 6 | 2-4 | Good |
| Implementation | 5 | 2-4 | Good |
| Evaluation | ~10 | 2-5 | Good |
| Threat Model | 3 | 3-5 | Good |
| Limitations | 2 | 5-8 | Long but dense |

## 7. Specific Rewrite Recommendations

### Priority 1: Remove response-letter language
- Line 228: "An earlier draft conflated..." → State current definition
- Line 266-267: "One problem with this particular run..." → Methodology section with current-state description
- Line 266: "The app has since been updated..." → State current behavior

### Priority 2: Reduce survey-style enumeration
- Line 96: Related Work paragraph listing all server systems in one long sentence → Break into shorter factual statements

### Priority 3: Remove unnecessary framing
- Line 270: "The question here is simple" → Just state what was measured
- Line 301: "Three quantities matter" → Just list them
- Line 349: "The comparison with request-driven LRU deserves some unpacking" → Just explain it

### Priority 4: Strengthen concrete language
- Replace "roughly 30 ms per token" with the OLS result directly
- Replace "about 0.43 s" with the actual computation shown

## 8. Summary

The manuscript is already in **reasonably good shape** for AI prose. The main issues are:
1. A few response-letter phrases that must be removed
2. One survey-style paragraph in Related Work
3. Some unnecessary conversational framing
4. Some estimated numbers that could be computed precisely

The 64% Pangram score likely reflects **structural patterns** (paragraph templates, consistent framing) more than individual word choices. The vocabulary is clean.

**Estimated rewrite effort**: Medium. ~15-20 paragraphs need revision, most of them minor.
