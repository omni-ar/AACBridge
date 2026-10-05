# Memory Profile — AACBridge on OnePlus 11R

**Captured:** 2026-10-05T20:00:05+05:30
**State:** App running, model loaded, 3 resident KV states

## Summary (from `dumpsys meminfo com.aacbridge`)

| Category | PSS (KB) | Private Dirty (KB) | RSS (KB) |
|----------|---------|-------------------|---------|
| Native Heap | 151,477 | 150,892 | 155,476 |
| Dalvik Heap | 5,605 | 5,492 | 14,160 |
| .so mmap | 3,026 | 452 | 57,512 |
| .apk mmap | 21,612 | 828 | 23,468 |
| Other mmap | 390,651 | 4 | 392,216 |
| **TOTAL** | **627,460** | **173,472** | **773,228** |

## App Summary

| Category | PSS (KB) | RSS (KB) |
|----------|---------|---------|
| Java Heap | 11,036 | 42,156 |
| Native Heap | 150,892 | 155,476 |
| Code | 34,436 | 169,840 |
| Stack | 1,588 | 1,628 |
| Graphics | 2,020 | 2,024 |
| Private Other | 396,596 | — |
| System | 30,892 | — |
| **TOTAL PSS** | **627,460** | — |
| **TOTAL RSS** | — | **773,228** |
| TOTAL SWAP PSS | 18,672 | — |

## Interpretation

- **Native Heap (151 MB)**: llama.cpp context, KV cache tensors for all 4 sequences (3 resident + 1 scratch), ggml backend allocations
- **Other mmap (391 MB)**: The GGUF model file memory-mapped by llama.cpp. This is file-backed and shared; the OS can evict and reload pages as needed
- **Dalvik Heap (5.6 MB)**: Kotlin runtime objects — minimal
- **Total PSS (627 MB)**: Proportional set size including mmap'd model. On an 8 GB device, this is significant but manageable
- **Heap Size reported (571 MB)**: Native heap allocator capacity (includes free space)
- **Heap Alloc (546 MB)**: Actually allocated native memory

## For the Paper

The model file (Q4_K_M, ~380 MB on disk) dominates memory via mmap. The native heap (151 MB) holds the llama.cpp context and KV caches for four sequences of 1024 tokens each. Total proportional memory (PSS) is 627 MB; on the 8 GB OnePlus 11R, this leaves approximately 7.4 GB for the OS and other apps. The app stays within Android's large-heap threshold.
