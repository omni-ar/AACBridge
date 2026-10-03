
# VELLORE INSTITUTE OF TECHNOLOGY, VELLORE

---

## B.Tech Project – I Report

**Title:**
**Predictive Amortized KV-Caching for Low-Latency Edge LLMs in Augmentative and Alternative Communication**

**Short Title:** AACBridge

---

**Team Members:**

| Name | Registration No. | Email |
|------|-----------------|-------|
| Arjit Tripathi | — | arjit.tripathi2023@vitstudent.ac.in |
| Heer Shah | — | shah.heer2023@vitstudent.ac.in |
| Medha Sriram | — | medha.sriram2023@vitstudent.ac.in |

**Guide:** Dr. Shalini L (l.shalini@vit.ac.in)

**Domain:** Artificial Intelligence / Healthcare / Embedded Systems / Edge Computing

**Duration:** May 15, 2026 – July 15, 2026

---

\newpage

## DECLARATION

We hereby declare that the project entitled **"Predictive Amortized KV-Caching for Low-Latency Edge LLMs in Augmentative and Alternative Communication"** submitted to the School of Computer Science and Engineering, Vellore Institute of Technology, Vellore, is a record of an original work done by us under the guidance of **Dr. Shalini L**, Assistant Professor, School of Computer Science and Engineering, during the period May–July 2026, and this project work has not formed the basis for the award of any degree/diploma/associateship/fellowship or other similar title to any candidate of any university.

**Place:** Vellore

| Name | Signature |
|------|-----------|
| Arjit Tripathi | |
| Heer Shah | |
| Medha Sriram | |

---

\newpage

## CERTIFICATE

This is to certify that the project entitled **"Predictive Amortized KV-Caching for Low-Latency Edge LLMs in Augmentative and Alternative Communication"** submitted by Arjit Tripathi, Heer Shah, and Medha Sriram to the School of Computer Science and Engineering, Vellore Institute of Technology, Vellore, for the partial fulfillment of the requirements for the award of the degree of B.Tech in Computer Science and Engineering is a bonafide record of the project work carried out by them under my guidance.

**Guide:**

Dr. Shalini L
Assistant Professor
School of Computer Science and Engineering
VIT, Vellore

**Date:**

**Internal Examiner Signature:**

**External Examiner Signature:**

---

\newpage

## ACKNOWLEDGEMENT

We express our sincere gratitude to our project guide, **Dr. Shalini L**, Assistant Professor, School of Computer Science and Engineering, VIT Vellore, for her constant support, valuable suggestions, and constructive criticism throughout the course of this project.

We are thankful to the School of Computer Science and Engineering, VIT Vellore, for providing us with the necessary infrastructure and computational resources to carry out this work.

We also extend our thanks to the open-source communities behind llama.cpp, ONNX Runtime, MediaPipe, and the Qwen2.5 model family, whose work forms the backbone of our inference pipeline. The NinaPro project team deserves acknowledgement for maintaining publicly available EMG datasets that enabled our intent classification research.

Finally, we thank our families for their encouragement and patience during the intensive development period of this project.

---

\newpage

## TABLE OF CONTENTS

| Section | Title | Page |
|---------|-------|------|
| | Declaration | ii |
| | Certificate | iii |
| | Acknowledgement | iv |
| | Table of Contents | v |
| | List of Figures | vii |
| | List of Tables | viii |
| | List of Abbreviations | ix |
| | Symbols | x |
| | Abstract | xi |
| **1** | **Introduction** | **1** |
| 1.1 | Background | 1 |
| 1.2 | Motivation | 2 |
| 1.3 | Problem Statement | 3 |
| 1.4 | Objectives | 4 |
| 1.5 | Scope | 4 |
| 1.6 | Contributions | 5 |
| 1.7 | Report Organization | 6 |
| **2** | **Project Description and Goals** | **7** |
| 2.1 | Literature Review | 7 |
| 2.2 | Research Gap | 12 |
| 2.3 | Project Objectives | 13 |
| 2.4 | Problem Definition | 14 |
| 2.5 | Project Planning and Timeline | 15 |
| **3** | **Technical Specifications** | **17** |
| 3.1 | Functional Requirements | 17 |
| 3.2 | Non-Functional Requirements | 18 |
| 3.3 | Feasibility Analysis | 19 |
| 3.4 | Hardware Specifications | 19 |
| 3.5 | Software Specifications | 20 |
| 3.6 | Development Environment | 21 |
| **4** | **Design Approach** | **23** |
| 4.1 | Overall Architecture | 23 |
| 4.2 | Subsystem Architecture | 25 |
| 4.3 | Android Application Architecture | 26 |
| 4.4 | JNI Boundary Architecture | 28 |
| 4.5 | LLM Inference Pipeline | 29 |
| 4.6 | KV Cache Lifecycle Pipeline | 30 |
| 4.7 | Drift Detection Pipeline | 31 |
| 4.8 | Database Design | 32 |
| 4.9 | Class Diagram | 33 |
| 4.10 | Data Flow Diagram | 34 |
| 4.11 | Sequence Diagrams | 35 |
| 4.12 | State Diagrams | 36 |
| 4.13 | Algorithm Flow | 37 |
| **5** | **Methodology and Testing** | **39** |
| 5.1 | Implementation Methodology | 39 |
| 5.2 | Module Descriptions | 40 |
| 5.3 | Testing Strategy | 47 |
| 5.4 | Benchmark Methodology | 49 |
| 5.5 | Experimental Setup | 50 |
| 5.6 | Memory Profiling | 51 |
| **6** | **Project Demonstration** | **52** |
| 6.1 | Application Workflow | 52 |
| 6.2 | Benchmark Execution Flow | 53 |
| 6.3 | Use Cases | 54 |
| **7** | **Results and Discussion** | **55** |
| 7.1 | Latency Results | 55 |
| 7.2 | Memory Results | 58 |
| 7.3 | Fusion and EMG Results | 59 |
| 7.4 | Analysis and Discussion | 60 |
| 7.5 | Advantages | 62 |
| 7.6 | Limitations | 62 |
| **8** | **Conclusion** | **64** |
| 8.1 | Summary | 64 |
| 8.2 | Future Scope | 65 |
| | **References** | **66** |
| | **Appendix A: Code Snippets** | **69** |

---

\newpage

## LIST OF FIGURES

| Figure No. | Caption |
|------------|---------|
| Figure 1.1 | Communication latency breakdown in conventional AAC systems |
| Figure 4.1 | CAP-KVC overall system architecture |
| Figure 4.2 | Android package structure and dependency graph |
| Figure 4.3 | JNI boundary contract — scalar-only data flow |
| Figure 4.4 | KV cache priming lifecycle (prefill → save → load) |
| Figure 4.5 | Drift detection pipeline with hysteresis gating |
| Figure 4.6 | Class diagram — core backend subsystems |
| Figure 4.7 | Data flow diagram — sensor acquisition to cache residency |
| Figure 4.8 | Sequence diagram — CAP-KVC inference path |
| Figure 4.9 | Sequence diagram — background priming path |
| Figure 4.10 | State diagram — CacheState lifecycle |
| Figure 4.11 | Context scoring algorithm flowchart |
| Figure 5.1 | Benchmark data pipeline — raw log to statistics |
| Figure 7.1 | End-to-end latency comparison across all conditions |
| Figure 7.2 | Latency decomposition — prefill vs. generation breakdown |
| Figure 7.3 | Native heap memory budget — 0 states vs. 3 states |
| Figure 7.4 | Fusion ablation — cross-attention vs. late fusion |
| Figure 7.5 | Drift detection k-ablation on DailyDialog |
| Figure 7.6 | EMG training curves (loss and accuracy) |
| Figure 7.7 | EMG confusion matrix (LOSO evaluation) |

---

\newpage

## LIST OF TABLES

| Table No. | Caption |
|-----------|---------|
| Table 2.1 | Comparison of KV cache optimization approaches |
| Table 2.2 | Comparison of edge LLM deployment approaches |
| Table 2.3 | Project timeline — phased development plan |
| Table 3.1 | Functional requirements specification |
| Table 3.2 | Non-functional requirements specification |
| Table 3.3 | Hardware specifications — benchmark device |
| Table 3.4 | Software specifications and dependencies |
| Table 3.5 | Development environment configuration |
| Table 4.1 | JNI exported functions and signatures |
| Table 4.2 | Context scoring function parameters |
| Table 5.1 | Unit test suite summary |
| Table 5.2 | Benchmark configuration — six pipeline conditions |
| Table 5.3 | Experimental setup parameters |
| Table 7.1 | Latency decomposition across all conditions (n=30 each) |
| Table 7.2 | Statistical significance — Welch's t-test results |
| Table 7.3 | Native heap memory profile |
| Table 7.4 | Fusion ablation results |
| Table 7.5 | EMG CNN-LSTM classification report (LOSO) |
| Table 7.6 | Drift detection k-ablation results |

---

\newpage

## LIST OF ABBREVIATIONS

| Abbreviation | Full Form |
|-------------|-----------|
| AAC | Augmentative and Alternative Communication |
| ABI | Application Binary Interface |
| ALS | Amyotrophic Lateral Sclerosis |
| APK | Android Package Kit |
| ART | Android Runtime |
| BLE | Bluetooth Low Energy |
| BOS | Beginning of Sequence |
| CAP-KVC | Context-Aware Predictive KV-Cache Priming |
| CNN | Convolutional Neural Network |
| CSV | Comma-Separated Values |
| DI | Dependency Injection |
| EOS | End of Sequence |
| EMG | Electromyography |
| ext4 | Fourth Extended Filesystem |
| FSM | Finite State Machine |
| GC | Garbage Collection |
| GGUF | GGML General Unified Format (llama.cpp model file format) |
| GPS | Global Positioning System |
| JIT | Just-In-Time (compilation) |
| JNI | Java Native Interface |
| JVM | Java Virtual Machine |
| KV | Key-Value |
| LOSO | Leave-One-Subject-Out |
| LRU | Least Recently Used |
| LSTM | Long Short-Term Memory |
| NDK | Native Development Kit |
| NEON | ARM Advanced SIMD Extension |
| NPU | Neural Processing Unit |
| ONNX | Open Neural Network Exchange |
| PSS | Proportional Set Size |
| RAG | Retrieval-Augmented Generation |
| RSSI | Received Signal Strength Indicator |
| sEMG | Surface Electromyography |
| SIMD | Single Instruction Multiple Data |
| SoC | System on Chip |
| TFLite | TensorFlow Lite |
| TTFT | Time To First Token |
| TTS | Text-to-Speech |
| UFS | Universal Flash Storage |
| vDSO | Virtual Dynamic Shared Object |

---

\newpage

## SYMBOLS

| Symbol | Description |
|--------|-------------|
| S(c_i) | Composite scoring function for context state c_i |
| α | Dynamic weight for temporal similarity |
| β | Dynamic weight for spatial (GPS) similarity |
| γ | Dynamic weight for BLE topology similarity |
| σ | Gaussian kernel bandwidth for temporal decay (2.0 hours) |
| λ | GPS characteristic distance for spatial decay (0.1 km) |
| d_circ | Circular distance on 24-hour clock |
| d_haversine | Haversine distance between GPS coordinates |
| S_time | Temporal anchor similarity score |
| S_gps | Spatial anchor similarity score |
| S_ble | BLE topology similarity score |
| w_t | Time sensor baseline weight (0.4) |
| w_g | GPS sensor reliability weight |
| w_b | BLE sensor reliability weight |
| Δ | Hysteresis margin for drift detection (0.10) |
| k | Cosine similarity turn window size |
| θ | Cosine similarity threshold for drift detection |
| N | Number of context prompt tokens |
| n_past | Token count of restored KV cache session |
| n_ctx | Maximum context window size (2048) |
| R_ble | BLE reliability measure |

---

\newpage

## ABSTRACT

Augmentative and alternative communication (AAC) devices powered by large language models can generate context-aware responses tailored to a user's environment, but the computational overhead of assembling and processing contextual prompts at inference time introduces latency that is incompatible with real-time conversation. On mobile hardware, the prefill phase — where the model ingests a prompt of N tokens — scales as O(N) with prompt length and can take several seconds for prompts spanning hundreds of tokens. For AAC users mid-conversation, delays of this magnitude are functionally unacceptable.

This project presents Context-Aware Predictive KV-Cache Priming (CAP-KVC), a system that decouples the prefill computation from the inference path. CAP-KVC uses environmental sensor signals — GPS coordinates, Bluetooth Low Energy device proximity, and time of day — to predict the most relevant semantic context for the user. During idle periods, a background daemon executes the prefill for the predicted context and serializes the resulting key-value cache tensors to persistent storage. When the user initiates a communication request, the pre-computed cache is restored from disk and only the short intent tokens are processed by the language model, bypassing the full context prefill entirely.

The system is implemented as a complete Android application targeting arm64-v8a devices. The inference backend uses llama.cpp accessed through a C++/JNI bridge with a strict scalar-only boundary contract. A Kotlin orchestration layer manages a bounded LRU cache supporting three simultaneous KV states, a deterministic scoring function for context routing with reliability-adaptive sensor weighting, and hysteresis-gated drift detection to prevent cache thrashing.

Evaluated on a OnePlus 11R (Snapdragon 8+ Gen 1, 8 GB LPDDR5X) with Qwen2.5-0.5B-Instruct (Q4_K_M quantization), native-side std::chrono instrumentation confirms a 40.78× prefill-specific speedup (343 ms vs. 14,001 ms at N ≈ 500 tokens) and a 4.24× end-to-end latency reduction (4,574 ms vs. 19,408 ms). Cache restoration completes in 2.74 ms on average. The system operates within a 118.7 MB native heap budget for three simultaneous cache states.

A proof-of-concept multimodal intent classification pipeline combining surface electromyography (sEMG) via a CNN-LSTM classifier and gaze tracking via MediaPipe is included. Late fusion was selected over cross-attention based on a pre-specified ablation criterion.

---

\newpage

# CHAPTER 1: INTRODUCTION

## 1.1 Background

Augmentative and alternative communication encompasses a broad set of strategies, tools, and technologies used by individuals with complex communication needs. People living with conditions such as amyotrophic lateral sclerosis (ALS), cerebral palsy, locked-in syndrome, and severe aphasia often rely on AAC devices as their primary means of interaction with caregivers, medical staff, and family members [1]. Traditional AAC systems employ grid-based symbol boards or switch-scanning interfaces, where users navigate hierarchical menus to construct utterances one symbol at a time [2]. While functional, these interfaces impose a significant cognitive and temporal burden. Communication rates for experienced AAC users typically range between 8 and 15 words per minute — roughly an order of magnitude slower than natural speech [3].

The emergence of sub-billion-parameter language models that can run on mobile system-on-chip (SoC) hardware has opened a fundamentally different design space for AAC. Rather than requiring users to manually compose messages symbol by symbol, a language model can generate contextually appropriate full-sentence responses given a brief intent signal. If the model knows, for instance, that the user is in a hospital ward during morning rounds, a simple "water" intent can be expanded into "Could I please have some water? I'm feeling quite thirsty this morning." This kind of context-aware response generation dramatically reduces the number of user actions required to produce a meaningful utterance.

Post-training quantization techniques such as GPTQ [4] and LLM.int8() [5], combined with efficient CPU-based inference runtimes like llama.cpp [6] and MLC-LLM [7], have made it practically possible to run quantized 0.5B–1.5B parameter models entirely on a smartphone. The Qwen2.5-0.5B-Instruct model [8], for example, fits within the memory constraints of modern Android devices when quantized to Q4_K_M format.

The critical bottleneck, however, lies not in whether the model can run on the device, but in how quickly it can produce its first output token after receiving a contextual prompt.

**Figure 1.1: Communication Latency Breakdown in Conventional AAC Systems**

[Insert communication_latency_breakdown.png — diagram showing the breakdown of user-facing latency in a conventional AAC pipeline: symbol navigation time, prompt assembly, prefill computation, and generation]

## 1.2 Motivation

When a language model is used for context-aware AAC inference, the typical approach follows a retrieval-augmented generation (RAG) pattern: a system prompt describing the user's current environment is concatenated with the user's intent, and the combined prompt is submitted to the model. The model must then process every token in this combined prompt during its prefill phase before generating the first output token. This prefill phase is the single forward pass that transforms the entire input sequence into key-value cache tensors within the transformer's attention layers.

On server-class GPUs, prefill for a few hundred tokens takes milliseconds. On a mobile CPU, the same operation can take several seconds. Our measurements on a Snapdragon 8+ Gen 1 SoC show that prefilling 439 tokens of context takes approximately 14 seconds through llama.cpp. For an AAC user in the middle of a conversation — perhaps trying to ask a nurse for pain medication — a 14-second wait is not merely inconvenient; it fundamentally defeats the purpose of the device.

The central observation is that the environmental context of an AAC user changes slowly relative to the speed of conversation. A user who is in a hospital ward at 11 AM will almost certainly still be in the same hospital ward at 11:05 AM. If the system can predict which context is needed before the user initiates a request, it can perform the expensive prefill computation in advance, during idle periods when the device is not being actively used for communication.

Server-side inference systems have exploited KV cache reuse for years. PagedAttention [9] manages cache memory efficiently across concurrent requests. SGLang [10] introduces RadixAttention for prefix sharing across multiple requests with common prefixes. CacheGen [11] compresses and streams KV caches between machines. Prompt Cache [12] caches attention states for reusable prompt modules. All of these techniques, however, assume a multi-tenant serving environment with shared request streams — a scenario that does not exist on a single-user edge device.

No existing system uses environmental sensor signals to predictively prime KV caches on mobile hardware. This gap is what AACBridge addresses.

## 1.3 Problem Statement

Given a set of M pre-configured semantic contexts C = {c₁, c₂, ..., c_M}, each associated with a system prompt p_i of N_i tokens, and a mobile edge device running a quantized language model, the problem is to minimize the inference-time latency experienced by the user when requesting context-aware text generation.

In standard RAG, the prompt p_i is concatenated with the user's intent tokens at inference time, incurring an O(N_i) prefill cost that scales linearly with the context prompt length. For prompts exceeding a few hundred tokens, this cost dominates the total inference latency on mobile CPUs.

The system must:

1. Predict which context the user is most likely to need, using passively acquired environmental signals.
2. Execute the computationally expensive prefill for the predicted context during idle periods.
3. Persist the resulting KV cache tensors across application restarts.
4. Restore the pre-computed cache at inference time in a fraction of the time required for a full prefill.
5. Operate entirely offline, without any cloud connectivity, to protect user privacy and ensure availability.
6. Fit within the memory and power constraints of a commercially available Android smartphone.

## 1.4 Objectives

The project objectives are:

1. Design and implement a predictive KV cache priming architecture (CAP-KVC) that amortizes context prefill cost by exploiting idle-time pre-computation.

2. Develop a deterministic context routing engine that scores candidate semantic contexts using a convex combination of temporal, spatial (GPS), and topological (BLE) similarity signals, with reliability-adaptive sensor weighting.

3. Implement a bounded KV cache residency manager with LRU eviction, supporting three simultaneous cache states within a mobile native heap budget.

4. Build a complete Android application with a C++/JNI inference backend based on llama.cpp, targeting arm64-v8a devices.

5. Design and resolve concurrency hazards at the JNI boundary to prevent native memory crashes (SIGBUS) when background cache operations and foreground inference execute concurrently.

6. Implement a hysteresis-gated drift detector to detect when the user's environmental context has shifted sufficiently to warrant cache replacement, preventing cache thrashing from transient sensor noise.

7. Develop a proof-of-concept multimodal intent classification pipeline combining sEMG and gaze tracking through late fusion.

8. Benchmark the system on real mobile hardware and report reproducible latency and memory measurements.

## 1.5 Scope

This project covers:

- The complete software implementation of the CAP-KVC architecture, from sensor acquisition to LLM response generation.
- A single-device benchmark on a OnePlus 11R (Snapdragon 8+ Gen 1) with the Qwen2.5-0.5B-Instruct Q4_K_M model.
- The EMG intent classification pipeline validated on the NinaPro DB5 dataset using a CNN-LSTM architecture with Leave-One-Subject-Out cross-validation.
- Gaze tracking via MediaPipe FaceLandmarker with a 400 ms dwell threshold.
- Late fusion of EMG and gaze modalities evaluated on synthetic paired data.

Out of scope:

- Clinical trials with actual AAC users.
- Multi-device benchmarking across different SoC families.
- Real-time BLE wearable hardware integration (mock embeddings are used in the current implementation).
- Power consumption measurement and battery impact analysis.
- Production-grade accessibility UI design.

## 1.6 Contributions

The technical contributions of this project are:

1. **A predictive KV cache architecture for edge devices**, achieving a 40.78× prefill-specific speedup and 4.24× end-to-end speedup relative to standard RAG on a OnePlus 11R, with 2.74 ms cache restoration.

2. **An edge-native Android implementation** with a scalar-only JNI boundary (9 native functions), reentrant-lock concurrency across the JVM-native boundary, and an LRU cache manager bounded to 3 KV states within 118.7 MB native heap.

3. **A deterministic sensor-driven context routing engine** with reliability-adaptive weighting that gracefully degrades when GPS or BLE sensors are unavailable, and hysteresis-gated drift detection that prevents cache thrashing under noisy sensor conditions.

4. **A proof-of-concept multimodal intent pipeline** combining CNN-LSTM sEMG classification (NinaPro DB5) with MediaPipe gaze tracking through late fusion, with a structured ablation study comparing late fusion against cross-attention.

5. **Complete reproducibility artifacts**, including the raw benchmark log, canonical CSV dataset, statistical verification scripts, and figure generation code.

## 1.7 Report Organization

The remainder of this report is organized as follows:

**Chapter 2** reviews the relevant literature on KV cache optimization, edge LLM deployment, AAC systems, multimodal sensor fusion, and semantic drift detection. It identifies the research gap motivating CAP-KVC and defines the project objectives formally.

**Chapter 3** specifies the functional and non-functional requirements, hardware and software dependencies, and feasibility considerations.

**Chapter 4** presents the system design, including the overall architecture, subsystem decomposition, JNI boundary contract, scoring function mathematics, and UML diagrams.

**Chapter 5** describes the implementation methodology, module-level details, testing strategy, and the benchmark methodology used for experimental evaluation.

**Chapter 6** demonstrates the application workflow, benchmark execution, and representative use cases.

**Chapter 7** reports the latency, memory, and fusion results with statistical analysis, followed by a discussion of advantages and limitations.

**Chapter 8** concludes the report with a summary and directions for future work.

---

\newpage

# CHAPTER 2: PROJECT DESCRIPTION AND GOALS

## 2.1 Literature Review

This section reviews the five areas of prior work most relevant to AACBridge: KV cache optimization for language models, on-device LLM deployment, augmentative and alternative communication, multimodal sensor fusion, and semantic drift detection.

### 2.1.1 KV Cache Optimization for Large Language Models

The key-value cache is a fundamental data structure in transformer-based language models. During autoregressive generation, each new token attends to all previously generated tokens. Rather than recomputing the key and value projections of all prior tokens at every step, the model stores them in the KV cache and appends to it incrementally. This turns the attention computation from O(n²) to O(n) per generated token, but creates a memory management challenge as context lengths grow.

**Token-level compression.** Scissorhands [13] exploits the empirical observation that a small subset of tokens disproportionately influence attention patterns, and prunes tokens with consistently low attention scores. H₂O (Heavy-Hitter Oracle) [14] takes a similar approach, identifying "heavy hitter" tokens that accumulate high attention mass and evicting the rest. StreamingLLM [15] observes that keeping the initial "attention sink" tokens plus a sliding window of recent tokens enables stable generation over arbitrarily long contexts. SnapKV [16] clusters KV entries based on attention patterns and retains only representative entries per cluster. KVQuant [17] applies quantization specifically to KV cache entries, reducing their memory footprint while maintaining generation quality. All of these methods operate within a single inference sequence and do not address the initial prefill cost of ingesting a prompt.

**Server-side prefix reuse.** In cloud serving environments, multiple user requests frequently share common system prompt prefixes. SGLang's RadixAttention [10] organizes cached KV states in a radix tree, enabling efficient prefix matching and reuse across requests. PagedAttention [9], implemented in vLLM, manages KV cache memory in fixed-size pages analogous to virtual memory, eliminating internal fragmentation and enabling efficient memory sharing across requests with common prefixes. Prompt Cache [12] precomputes and stores attention states for reusable prompt modules that can be composed at serving time. CacheBlend [18] blends precomputed cached states with dynamically generated ones to handle partially overlapping contexts. CacheGen [11] compresses KV caches and streams them between serving nodes for cross-session reuse.

On a single-user edge device, however, none of these sharing mechanisms apply. There is no request stream — each inference call starts cold. CAP-KVC addresses this gap by substituting environmental sensor prediction for multi-tenant request analysis, enabling pre-computation of caches that no other caller will ever share.

### 2.1.2 On-Device Edge LLM Deployment

Running language models on mobile hardware requires aggressive optimization across multiple dimensions: model size, arithmetic precision, memory bandwidth, and runtime efficiency.

**Post-training quantization.** LLM.int8() [5] demonstrated that 8-bit quantization could preserve model quality while halving memory requirements. GPTQ [4] pushed this further with 4-bit and 3-bit quantization using second-order weight rounding. These methods enable sub-billion-parameter models to fit within the DRAM budget of mobile devices. The GGUF format used by llama.cpp [6] packages quantized weights in a self-contained file that can be memory-mapped directly, avoiding the need for explicit deserialization.

**Inference runtimes.** llama.cpp [6] provides a pure C/C++ inference engine optimized for CPU execution, with NEON SIMD acceleration on ARM processors. MLC-LLM [7] takes a compiler-based approach, using Apache TVM to generate device-specific optimized kernels. PowerInfer-2 [19] extends on-device inference to 47B parameter models through heterogeneous NPU/CPU scheduling and intelligent neuron placement. Alizadeh et al. [20] demonstrate LLM inference from flash memory for models exceeding DRAM capacity, using windowed key-value caching and row-column bundling to minimize flash reads.

**Architecture-aware models.** MobileLLM [21] designs transformer architectures specifically for mobile deployment, incorporating techniques like weight sharing across layers and deep-narrow architectural choices that improve quality per parameter within tight memory budgets.

These advances reduce the per-token compute cost, but the linear scaling with prompt length persists. An AAC prompt of 500 tokens still incurs 500 tokens of prefill — it simply runs faster per token. CAP-KVC targets a different axis: it eliminates the prompt-length dependence at inference time entirely, and this benefit compounds on top of whatever runtime acceleration the underlying engine provides.

### 2.1.3 Augmentative and Alternative Communication

Modern AAC devices have evolved from static symbol boards to sophisticated software systems running on tablets and smartphones. Grid-based systems like Proloquo2Go, TouchChat, and GRID organize symbols in hierarchical page sets, where users navigate through multiple levels to find the desired symbol. Beukelman and Light [1] provide a comprehensive survey of AAC approaches, noting that the fundamental tension in AAC design is between vocabulary size and navigation speed — systems offering rich vocabulary impose longer navigation paths.

Predictive text systems, adapted from mainstream keyboard research, apply language models to suggest likely next words based on the user's partial input [3]. These systems reduce keystrokes but still require the user to construct messages character by character or word by word.

The potential of large language models for AAC lies in a qualitative shift: rather than predicting the next word, the model generates an entire contextually appropriate response from a brief intent signal. This shift reduces the user's cognitive and motor burden. Current LLM-based AAC prototypes treat the contextual prompt as a runtime input that must be re-ingested on every interaction. The prefill latency this creates has not been treated as a distinct system-design problem in the AAC literature. AACBridge is, to our knowledge, the first system to apply predictive KV cache priming specifically for AAC applications.

### 2.1.4 Multimodal Sensor Fusion for Intent Classification

Multimodal intent classification for assistive technology combines signals from different sensing modalities to infer user intent with higher accuracy and robustness than any single modality alone.

**Surface EMG.** The NinaPro database [22] is the standard benchmark for sEMG-based gesture recognition, providing recordings from multiple subjects performing standardized hand and wrist movements using 16-channel electrode arrays at 2 kHz. CNN-LSTM architectures have proven effective for temporal sEMG pattern recognition, where the CNN extracts local spatial features across channels and the LSTM captures temporal dependencies within the gesture window.

**Gaze tracking.** MediaPipe [23] provides a pre-trained face mesh model that detects 478 facial landmarks in real-time from a single camera feed. Gaze direction can be estimated from the relative displacement between the nose tip (landmark 4) and the midpoint of the eye inner corners (landmarks 33 and 362). Dwell-time selection — where a sustained gaze at a target for a threshold duration triggers selection — is a well-established interaction paradigm for gaze-based AAC [24].

**Fusion strategies.** Late fusion (also called decision-level fusion) concatenates feature vectors or prediction scores from independent modality-specific classifiers before a final classification layer [25]. Cross-attention fusion uses attention mechanisms to model interactions between modalities. Late fusion is simpler and often performs comparably to more complex fusion architectures when modality-specific classifiers are strong [26].

### 2.1.5 Semantic Drift Detection in Dialogue Systems

In dialogue systems, semantic drift refers to the gradual shift in conversation topic over multiple turns. Detecting when a conversation has drifted sufficiently from the cached context is essential for maintaining response quality.

Sentence-BERT (SBERT) [27] and its distilled variant MiniLM [28] generate semantically meaningful fixed-dimensional sentence embeddings that enable efficient cosine similarity computation. By comparing the centroid embedding of recent conversation turns against an anchor embedding representing the cached context, the system can estimate whether the conversation has moved outside the scope of the cached context.

Dialogue state tracking (DST) provides more structured approaches to tracking conversation context, but traditional DST methods require slot-filling ontologies and substantial training data specific to the target domain — resources not available for AAC communication patterns [29].

## 2.2 Research Gap

Table 2.1 summarizes how existing KV cache optimization approaches compare to CAP-KVC.

**Table 2.1: Comparison of KV Cache Optimization Approaches**

| Approach | Scope | Target | Sensor-Driven | Edge-Native |
|----------|-------|--------|---------------|-------------|
| Scissorhands [13] | Token-level compression | Intra-sequence | No | No |
| H₂O [14] | Token-level eviction | Intra-sequence | No | No |
| StreamingLLM [15] | Sliding window + sink | Long contexts | No | No |
| SnapKV [16] | Attention-based clustering | Intra-sequence | No | No |
| KVQuant [17] | Cache quantization | Memory reduction | No | No |
| PagedAttention [9] | Memory management | Multi-tenant serving | No | No |
| SGLang RadixAttention [10] | Prefix sharing | Multi-tenant serving | No | No |
| Prompt Cache [12] | Module-level caching | Server-side composition | No | No |
| CacheGen [11] | Cache streaming | Cross-session reuse | No | No |
| CacheBlend [18] | Partial cache reuse | Server-side RAG | No | No |
| **CAP-KVC (ours)** | **Predictive priming** | **Single-user edge** | **Yes** | **Yes** |

Table 2.2 similarly compares edge LLM deployment approaches, highlighting that no existing runtime eliminates the prompt-length-dependent prefill cost.

**Table 2.2: Comparison of Edge LLM Deployment Approaches**

| Approach | Technique | Hardware Target | Addresses Prefill Cost |
|----------|-----------|----------------|------------------------|
| llama.cpp [6] | CPU-optimized C++ inference | ARM64 mobile CPU | No — reduces per-token cost |
| MLC-LLM [7] | TVM compiler-generated kernels | Heterogeneous (CPU/GPU/NPU) | No — reduces per-token cost |
| PowerInfer-2 [19] | Heterogeneous NPU/CPU neuron scheduling | Mobile SoC with NPU | No — reduces per-token cost |
| Alizadeh et al. [20] | Flash memory inference with windowed KV | Memory-constrained devices | Partially — windows reduce KV size |
| MobileLLM [21] | Architecture-aware model design | Sub-1B parameter mobile | No — reduces model footprint |
| **CAP-KVC (ours)** | **Predictive idle-time priming** | **ARM64 mobile CPU** | **Yes — amortizes to idle time** |

The fundamental gap is that no existing system uses environmental context signals to predictively pre-compute and persist KV cache states on a single-user mobile device. Server-side prefix reuse systems require multi-tenant request streams that do not exist on edge devices. Token-level compression methods reduce memory within a sequence but do not address the initial prefill cost. AACBridge bridges this gap by reformulating cache management as a prediction problem driven by environmental sensors.

## 2.3 Project Objectives

Building on the identified research gap, and refining the high-level objectives stated in §1.4, the project objectives are formalized with measurable criteria:

**O1.** Demonstrate that predictive KV cache priming reduces inference-time latency on mobile hardware relative to standard RAG, with statistically significant improvements measured via native-side instrumentation.

**O2.** Implement a complete, buildable, testable Android application that encapsulates the entire CAP-KVC pipeline from sensor acquisition through LLM response generation.

**O3.** Design a concurrency model that prevents native memory crashes at the JNI boundary when background priming and foreground inference operate concurrently.

**O4.** Validate a multimodal intent classification pipeline combining sEMG and gaze modalities as a proof-of-concept input layer for the AAC system.

**O5.** Produce reproducible benchmark results with a documented data pipeline from raw measurement logs to the reported statistics.

## 2.4 Problem Definition

**Formal problem statement.** Let C = {c₁, c₂, ..., c_M} be a set of pre-configured semantic contexts, each associated with a system prompt p_i of N_i tokens. In standard retrieval-augmented generation, p_i is concatenated with the user's intent tokens at inference time, incurring an O(N_i) prefill cost. CAP-KVC reformulates this as a scheduling problem: predict the most likely context ĉ from environmental signals, execute the prefill of p_ĉ during idle periods, and persist the resulting KV cache tensors. At inference time, the system restores the pre-computed cache and appends only the user's intent tokens, reducing the inference-time cost from a prompt-length-dependent prefill to a fixed-cost cache restoration operation.

**Input space.** Environmental signals: GPS coordinates with accuracy metadata, BLE device topology with RSSI values, system clock time. User intent signals: sEMG embeddings from a 16-channel facial wearable, gaze fixation vectors from a front-facing camera.

**Output space.** Natural language responses generated by Qwen2.5-0.5B-Instruct, contextualized to the user's predicted environment.

**Constraint space.** The system must operate entirely offline on a single Android device with 8 GB RAM, minimizing user-facing inference latency for cached contexts and fitting three simultaneous KV cache states within the native heap budget.

## 2.5 Project Planning and Timeline

The project followed a phased development plan spanning approximately two months.

**Table 2.3: Project Timeline**

| Phase | Period | Focus | Status |
|-------|--------|-------|--------|
| Phase 1 | May 15 – May 31, 2026 | JNI integration, llama.cpp ARM64 build, GGUF model loading, on-device inference verification, OpenMP runtime fix | Complete |
| Phase 2 | Jun 1 – Jun 8, 2026 | State router and scoring function, KV cache manager with LRU eviction, cold start recovery (BootReceiver + ActiveSweep), two-tier fallback system, async drift detector, concurrency stabilization (engineLock), EMG pipeline (preprocessing, CNN-LSTM, ONNX/TFLite export), gaze tracking (MediaPipe, dwell system), fusion model (cross-attention vs. late fusion ablation) | Complete |
| Phase 3 | Jun 9 – Jul 15, 2026 | LatencyProfiler implementation, TTFT benchmarking (30 trials × 6 conditions), memory profiling (dumpsys meminfo), statistical analysis, figure generation, manuscript preparation | Complete |

**Build and test status at project closure:**
- Build status: PASS (assembleDebug succeeds with 0 errors)
- Test status: 41/41 unit tests PASS
- Native crash rate: 0 (SIGBUS crashes eliminated)

---

\newpage

# CHAPTER 3: TECHNICAL SPECIFICATIONS

## 3.1 Functional Requirements

**Table 3.1: Functional Requirements Specification**

| ID | Requirement | Description | Priority |
|----|-------------|-------------|----------|
| FR-01 | Model Loading | Load a GGUF-quantized language model from device storage and initialize the native llama.cpp context | High |
| FR-02 | Context Prompt Prefill | Execute a forward pass over a context system prompt to populate the KV cache with attention tensors | High |
| FR-03 | KV Cache Serialization | Serialize in-memory KV cache tensors to a persistent binary file on the Android filesystem | High |
| FR-04 | KV Cache Restoration | Deserialize a previously saved KV cache binary file back into native memory | High |
| FR-05 | Context Scoring | Evaluate candidate context states using temporal, spatial, and BLE proximity signals and rank them by relevance | High |
| FR-06 | Cache Residency Management | Maintain at most 3 KV cache states simultaneously resident in native memory with LRU eviction | High |
| FR-07 | Background Priming | Autonomously pre-compute KV caches for predicted contexts during idle periods | High |
| FR-08 | Inference with Cached Context | Resume inference from a loaded KV cache state, processing only intent tokens | High |
| FR-09 | Boot Recovery | Trigger context cache population on device boot or application reinstall | Medium |
| FR-10 | Drift Detection | Periodically re-evaluate context relevance and trigger cache replacement when the environment shifts | Medium |
| FR-11 | Gaze-Based Interaction | Detect gaze fixation targets using camera-based face mesh tracking with 400 ms dwell threshold | Medium |
| FR-12 | Multimodal Fusion | Classify user intent from combined EMG embedding and gaze feature vectors | Medium |
| FR-13 | Fallback Response | Return a generic pre-configured response when no cached context is available | High |
| FR-14 | Text-to-Speech Output | Speak generated responses aloud using the Android TTS engine | Low |

## 3.2 Non-Functional Requirements

**Table 3.2: Non-Functional Requirements Specification**

| ID | Requirement | Specification |
|----|-------------|---------------|
| NFR-01 | Cache Restoration Latency | < 10 ms for KV cache file restoration from persistent storage |
| NFR-02 | End-to-End Latency Target | < 5000 ms for cached-context inference (intent-only prefill + generation) |
| NFR-03 | Memory Budget | Three simultaneous KV cache states within ~120 MB native heap |
| NFR-04 | Offline Operation | 100% offline execution — no network API calls permitted |
| NFR-05 | Concurrency Safety | Zero native crashes (SIGBUS) under concurrent cache and inference operations |
| NFR-06 | Platform Compatibility | Android 8.0+ (API 26 minimum SDK), arm64-v8a ABI only |
| NFR-07 | Build Reproducibility | Deterministic Gradle build with pinned dependency versions |
| NFR-08 | Benchmark Reproducibility | Complete data pipeline from raw logs to reported statistics |
| NFR-09 | Startup Overhead | Manual dependency injection to minimize initialization latency |
| NFR-10 | Privacy | No user data transmitted off-device; KV cache files use Android sandbox |

## 3.3 Feasibility Analysis

**Technical feasibility.** The llama.cpp inference engine has demonstrated stable execution on ARM64 mobile processors. The Qwen2.5-0.5B-Instruct model at Q4_K_M quantization produces a ~2.2 GB GGUF file that fits within device storage. The llama.cpp API exposes `llama_state_seq_save_file` and `llama_state_seq_load_file` functions for KV cache serialization, making the core cache persistence mechanism available without custom modifications to the inference engine.

**Hardware feasibility.** The Snapdragon 8+ Gen 1 SoC provides 1 Cortex-X2 prime core (3.0 GHz), 3 Cortex-A710 performance cores (2.5 GHz), and 4 Cortex-A510 efficiency cores (1.8 GHz) with NEON SIMD support, offering sufficient computational throughput for quantized matrix operations. The 8 GB LPDDR5X memory can accommodate the model weights, three KV cache states, and Android OS overhead simultaneously. UFS 3.1 storage provides the sequential read bandwidth needed for fast KV cache restoration.

**Operational feasibility.** GPS, BLE, and system clock signals are available through standard Android APIs. MediaPipe FaceLandmarker runs in real-time on mobile GPUs. ONNX Runtime for Android supports the small fusion model with negligible overhead. All required dependencies (AndroidX, WorkManager, CameraX, MediaPipe, ONNX Runtime) are available as production-quality Android libraries.

## 3.4 Hardware Specifications

**Table 3.3: Hardware Specifications — Benchmark Device**

| Component | Specification |
|-----------|---------------|
| Device | OnePlus 11R (CPH2487) |
| SoC | Snapdragon 8+ Gen 1 |
| CPU Architecture | 1× Cortex-X2 (3.0 GHz) + 3× Cortex-A710 (2.5 GHz) + 4× Cortex-A510 (1.8 GHz) |
| ABI | arm64-v8a |
| RAM | 8 GB LPDDR5X |
| Storage | UFS 3.1 |
| Operating System | Android 14 |
| Benchmark Configuration | Airplane mode, 50% brightness, no other foreground applications |

## 3.5 Software Specifications

**Table 3.4: Software Specifications and Dependencies**

| Component | Version | Purpose |
|-----------|---------|---------|
| Android SDK (compileSdk) | 34 | Build target |
| Android SDK (minSdk) | 26 | Minimum supported API level |
| NDK | r27c (27.2.12479018) | Native C++ compilation for ARM64 |
| CMake | 3.22.1 | Native build system |
| Java | JDK 17 | Kotlin/JVM compilation target |
| Kotlin | 1.9.x | Application language |
| C++ Standard | C++17 | JNI bridge implementation |
| llama.cpp | Latest (prebuilt libllama.so) | LLM inference engine |
| Qwen2.5-0.5B-Instruct | Q4_K_M GGUF | Evaluation model (~2.2 GB) |
| AndroidX Core-KTX | 1.12.0 | Kotlin extensions |
| AndroidX AppCompat | 1.6.1 | Backward-compatible UI |
| Material Design 3 | 1.11.0 | UI components |
| WorkManager | 2.9.0 | Periodic background task scheduling |
| Kotlinx Coroutines | 1.7.3 | Asynchronous programming |
| Play Services Location | 21.2.0 | GPS coordinate acquisition |
| MediaPipe Vision Tasks | 0.10.14 | FaceLandmarker gaze tracking |
| CameraX | 1.3.1 | Camera frame acquisition |
| ONNX Runtime Android | 1.17.0 | Fusion model inference |
| JUnit | 4.13.2 | Unit testing |
| Python | 3.11+ | Benchmark analysis scripts |
| matplotlib | 3.11+ | Figure generation |
| NumPy | 2.4+ | Numerical computation |
| SciPy | 1.17+ | Statistical analysis |

## 3.6 Development Environment

**Table 3.5: Development Environment Configuration**

| Tool | Version / Configuration |
|------|------------------------|
| IDE | Android Studio (Ladybug or later) |
| Build System | Gradle with Android Gradle Plugin |
| Version Control | Git |
| Testing Framework | JUnit 4 + kotlinx-coroutines-test |
| Benchmark Data Extraction | adb logcat with structured CSV parsing |
| Memory Profiling | adb shell dumpsys meminfo |
| Model Deployment | adb push to /data/local/tmp/models/ |
| Native Library | Prebuilt libllama.so in jniLibs/arm64-v8a/ |
| CI | Manual build verification (41/41 tests passing) |

---

\newpage

# CHAPTER 4: DESIGN APPROACH

## 4.1 Overall Architecture

CAP-KVC amortizes the cost of contextual prompt ingestion by decoupling the prefill computation from the user-facing inference path. The system operates as a complete Android application with four architectural layers and five execution stages.

**Figure 4.1: CAP-KVC Overall System Architecture**

[Insert system_architecture_new.png — programmatically generated architecture diagram showing the four-layer decomposition: Sensor & Intent Layer, Deterministic State Router, Predictive KV Cache Manager, and LLM Inference Engine, with data flow between layers]

### Layer 1: Sensor and Intent Layer

GPS coordinates come from cached `getLastKnownLocation()` calls via Play Services. BLE topology is sampled through a 3-second `BluetoothLeScanner` window — long enough to detect nearby devices without draining the battery on every poll. The system clock rounds to a decimal hour-of-day representation for the temporal scorer. On the intent side, MediaPipe's FaceLandmarker extracts gaze vectors in `LIVE_STREAM` mode from CameraX frames, and a CNN-LSTM classifier produces 64-dimensional sEMG embeddings (mock `FloatArray(64)` in the current implementation, pending physical armband integration). The `ContextDaemon` foreground service orchestrates sensor polling on a 60-second sweep interval, balancing context freshness against thermal and battery budgets.

### Layer 2: Deterministic State Router

The router scores all registered context states deterministically — no machine learning model is involved. The scoring function computes a convex combination:

**S(c_i) = α · S_time + β · S_gps + γ · S_ble**

where α + β + γ = 1. The weights are dynamically computed from real-time sensor reliability. Time maintains a fixed baseline weight w_t = 0.4, guaranteeing a positive normalization denominator even when both GPS and BLE are unavailable. GPS reliability decays exponentially with reported accuracy: w_g = exp(−accuracy_meters / 50.0). BLE reliability derives from the mean sigmoid-transformed RSSI centered at −70 dBm.

Contexts scoring below 0.01 (the `DEAD_STATE_THRESHOLD`) are pruned. The top-k surviving states (k = 3 by default, matching `MAX_ACTIVE_KV_STATES`) are forwarded to the cache manager.

### Layer 3: Predictive KV Cache Manager

The cache manager maintains at most k = 3 KV cache states simultaneously resident in native memory. Native memory slots are managed through a `seqId` pool (a `ConcurrentLinkedQueue` of integers [0, 1, 2]). When all slots are occupied, the LRU state is evicted. Eviction is protected by reference counting: a state under active inference holds a reference blocking eviction until inference completes. The eviction sequence marks the victim inactive, polls until its reference count drains to zero, and returns the native slot to the available pool.

An invariant is maintained at all times: `activeStates.size + availableSeqIds.size == MAX_ACTIVE_KV_STATES`.

### Layer 4: LLM Inference Engine

The inference engine is llama.cpp accessed through a JNI bridge (`llama_jni.cpp`, 801 lines). The bridge exposes nine native functions organized into three groups: lifecycle (`initializeBackend`, `initializeModel`, `release`), KV cache operations (`saveKVCache`, `loadKVCache`, `clearKVCache`), and inference (`runInference`, `prefillOnly`, `resumeInference`).

### Five-Stage Execution Pipeline

The pipeline operates in five stages, with the first three running asynchronously during idle periods:

1. **Sensor Acquisition:** ActiveSweep concurrently polls GPS coordinates and BLE topology, constructs an immutable `SensorSnapshot`.

2. **Context Routing:** StateRouter evaluates all registered context states via the convex scoring function and selects the top-k for cache residency.

3. **Background Priming:** ContextPrimerImpl executes `prefillOnly()` for each selected context under the engine lock, serializes the KV tensors via `saveKVCache()` to a temporary file, and atomically renames to the final `.bin` path after lock release.

4. **Resident Inference:** At interaction time, `loadKVCache()` restores the pre-computed cache and `resumeInference()` appends only the intent tokens. Both operations occur within a single engine lock acquisition.

5. **Drift Correction:** A `DriftDetector` CoroutineWorker registered with WorkManager runs every 15 minutes. It re-evaluates scores using a lightweight BLE-blind snapshot and triggers cache replacement only when a candidate exceeds the weakest resident state's score by at least Δ = 0.10.

## 4.2 Subsystem Architecture

The Android application is organized into seven Kotlin packages under `com.aacbridge`:

**Figure 4.2: Android Package Structure**

```
com.aacbridge/
├── inference/      # LlamaBridge, LlamaBridgeAdapter, LatencyProfiler, MockIntentGenerator
├── cache/          # KVCacheManager, ContextPrimerImpl, CacheState, StateRepository,
│                   # SeededStateRepository, CacheMutexRegistry, InMemoryStateRepository
├── router/         # StateRouter, TimeScorer, GPSScorer, BLEScorer, HardwareConfig,
│                   # ContextState, SensorSnapshot, GpsLocation, Rssi
├── daemon/         # ContextDaemon, DriftDetector, ActiveSweep, BootReceiver
├── fusion/         # FusionInference, FusionInput, FusionOutput
├── gaze/           # GazeTracker, CalibrationManager, DwellOverlayView
├── fallback/       # FallbackRepository, FallbackRouter, InMemoryFallbackRepository
├── AACBridgeApplication.kt    # Application subclass, DI root
├── AppContainer.kt            # Manual dependency injection container
├── MainActivity.kt            # UI, lifecycle, camera binding, benchmark trigger
├── FallbackRouter.kt          # Staged fallback routing for testing
├── IntentPayload.kt           # Data class for intent signals
└── SpeechOutputManager.kt     # Android TTS wrapper
```

The `AppContainer` class implements manual dependency injection, deliberately avoiding frameworks like Hilt or Dagger to minimize startup latency and memory overhead. All dependencies are constructed eagerly at application launch, with the exception of `FusionInference` which is lazy-initialized to defer ONNX model loading until first use.

## 4.3 Android Application Architecture

The application follows a single-Activity architecture with `MainActivity` serving as the primary entry point. The activity coordinates camera binding, UI rendering, TTS initialization, and dynamic permission requests. A `ContextDaemon` runs as a foreground service with `START_STICKY` lifecycle semantics, ensuring OS resurrection after process death.

**Permissions.** The AndroidManifest declares permissions for fine and coarse location, Bluetooth scanning and connection (including Android 12+ granular permissions), boot completion reception, foreground service execution (location and connected device types), and camera access.

**Camera pipeline.** CameraX `ImageAnalysis` feeds non-blocking `ImageProxy` frames directly to MediaPipe's FaceLandmarker. No `PreviewView` is rendered — bypassing preview reduces GPU load and thermal throttling during sustained inference sessions.

**TTS integration.** The `SpeechOutputManager` wraps Android's `TextToSpeech` engine for spoken response output, supporting the hands-free communication modality required for AAC users with severe motor impairment.

## 4.4 JNI Boundary Architecture

The JNI boundary is governed by a strict design principle: **all arguments and return values crossing the boundary must be JNI scalar types** — `jstring`, `jint`, `jboolean`. No Java objects, arrays, or callbacks cross the boundary. This eliminates GC root pinning issues that would arise if the garbage collector attempted to move or collect objects while native code held references to them.

**Figure 4.3: JNI Boundary Contract — Scalar-Only Data Flow**

[Insert jni_boundary_contract.png — diagram showing the Kotlin/JVM side passing only scalar types (String, Int, Boolean) across the JNI boundary to the C++ side, with tensor data remaining entirely native-side]

**Table 4.1: JNI Exported Functions and Signatures**

| JNI Function | Kotlin Signature | Group |
|-------------|------------------|-------|
| `Java_com_aacbridge_inference_LlamaBridge_initializeBackend` | `external fun initializeBackend()` | Lifecycle |
| `Java_com_aacbridge_inference_LlamaBridge_initializeModel` | `external fun initializeModel(modelPath: String): Boolean` | Lifecycle |
| `Java_com_aacbridge_inference_LlamaBridge_release` | `external fun release()` | Lifecycle |
| `Java_com_aacbridge_inference_LlamaBridge_saveKVCache` | `external fun saveKVCache(filepath: String, seqId: Int): Boolean` | KV Cache |
| `Java_com_aacbridge_inference_LlamaBridge_loadKVCache` | `external fun loadKVCache(filepath: String, seqId: Int): Boolean` | KV Cache |
| `Java_com_aacbridge_inference_LlamaBridge_clearKVCache` | `external fun clearKVCache()` | KV Cache |
| `Java_com_aacbridge_inference_LlamaBridge_runInference` | `external fun runInference(prompt: String): String` | Inference |
| `Java_com_aacbridge_inference_LlamaBridge_prefillOnly` | `external fun prefillOnly(prompt: String): Boolean` | Inference |
| `Java_com_aacbridge_inference_LlamaBridge_resumeInference` | `external fun resumeInference(prompt: String): String` | Inference |

**Critical distinction:** `runInference()` clears `session_tokens` and processes from position zero. `resumeInference()` preserves the loaded KV cache state by tokenizing the intent prompt without a BOS token (`add_special=false`) and appending at position `n_past = session_tokens.size()`. Confusing these two functions would cause position-ID collisions that corrupt the attention computation.

**Why tensors are not passed through JNI.** The `ggml` library maintains its own tensor memory arenas. Exposing raw tensor pointers to the JVM garbage collector risks memory corruption. Copying multi-megabyte tensor arrays between the C++ heap and the JVM heap would violate the latency budget. Instead, KV cache data remains entirely native-side — the JVM orchestrates load/save operations by passing only file paths and integer slot identifiers.

## 4.5 LLM Inference Pipeline

The native inference pipeline, implemented in `llama_jni.cpp`, follows this sequence for both `runInference` and `resumeInference`:

1. **Tokenization.** The prompt string is tokenized using `llama_tokenize()` with the Qwen2.5 vocabulary. The function is called twice: first with a null output buffer to determine the required token count, then with a properly sized buffer to store the tokens.

2. **Batch construction.** `llama_batch_get_one()` constructs a batch containing all prompt tokens for a single-shot prefill decode.

3. **Prefill.** `llama_decode()` executes the forward pass over the entire batch, computing key-value projections for all prompt tokens and storing them in the KV cache. This is the single most expensive operation and is instrumented with `std::chrono::high_resolution_clock` timestamps.

4. **Generation loop.** The loop iterates for up to 64 tokens. At each step: `llama_sampler_sample()` with greedy sampling selects the highest-probability next token. If the token is an end-of-generation token (checked via `llama_vocab_is_eog()`), the loop terminates. Otherwise, the token is detokenized via `llama_token_to_piece()`, appended to the output string, and decoded via `llama_decode()` with a single-token batch.

5. **Timing emission.** Native-side timing for prefill and generation phases is emitted via `LOGI("TIMING,...")` in structured CSV format for extraction via `adb logcat`.

## 4.6 KV Cache Lifecycle Pipeline

The cache lifecycle proceeds through distinct phases orchestrated by `ContextPrimerImpl`:

**Figure 4.4: KV Cache Priming Lifecycle**

```
Step 1: Resolve prompt text from StateRepository
          ↓
Step 2: Acquire engineLock (ReentrantLock)
          ↓
Step 3: prefillOnly(prompt) → populates KV cache in native memory
          ↓
Step 4: saveKVCache(tmpPath, seqId=0) → serializes to .tmp file
          ↓
Step 5: Release engineLock
          ↓
Step 6: Atomic rename .tmp → .bin (ext4 guarantee)
```

**Why steps 3 and 4 must be atomic under the lock.** The C++ layer maintains a global `session_tokens` vector that is written by `prefillOnly()` and read by `saveKVCache()`. If the lock were released between these two calls, another thread could invoke `runInference()` or `loadKVCache()`, which would clear or overwrite `session_tokens`, causing `saveKVCache()` to serialize incorrect token associations.

**Sequence slot semantics.** `prefillOnly()` always decodes into native sequence slot 0 via `llama_batch_get_one()`. Therefore, `saveKVCache()` must always save slot 0 regardless of which `seqId` the KVCacheManager intends to load the file into later. The `.bin` file is sequence-ID-agnostic on disk — `loadKVCache()` can restore it into any slot.

**Crash consistency.** Writing to a temporary file and performing an atomic `rename()` ensures that the `.bin` file is either fully written or does not exist. On ext4 (the standard Android filesystem), `rename()` is an atomic metadata operation. This guarantees that a crash during the write phase will not leave a corrupt partial `.bin` file.

## 4.7 Drift Detection Pipeline

The `DriftDetector` is implemented as a `CoroutineWorker` registered with Android's `WorkManager` for 15-minute periodic execution using `ExistingPeriodicWorkPolicy.KEEP` to ensure only a single worker instance survives app restarts and Doze mode.

**BLE-bypass strategy.** The drift detector intentionally uses a lightweight BLE-blind snapshot (setting `detectedBleDevices = emptyMap()`) to conserve battery during periodic polling. Both candidate and resident states are evaluated against the same BLE-blind snapshot, so the relative score deltas — which drive the hysteresis decision — remain mathematically valid even though absolute scores may be temporarily lower for BLE-rich contexts.

**Hysteresis gating.** A cache swap is triggered only when the best non-resident candidate's score exceeds the weakest resident state's score by more than `HYSTERESIS_MARGIN = 0.10`. This margin prevents thrashing caused by trivial score fluctuations from noisy GPS readings or transient BLE device appearances.

**Figure 4.5: Drift Detection Pipeline**

```
Every 15 minutes (WorkManager):
  1. Acquire lightweight snapshot (time + GPS, no BLE)
  2. Score all registered states via StateRouter
  3. If best candidate is already resident → no action
  4. Compute delta = bestCandidateScore - weakestResidentScore
  5. If delta > 0.10 → trigger loadTopStates([bestCandidate])
     else → no action (hysteresis suppresses swap)
```

## 4.8 Database Design

The current implementation uses `SeededStateRepository`, a hardcoded in-memory repository providing five predefined semantic context states for benchmarking:

| State ID | Expected Time | Latitude | Longitude | BLE Devices |
|----------|--------------|----------|-----------|-------------|
| home_morning | 8.0 | 28.6139 | 77.2090 | 2 devices |
| home_evening | 19.0 | 28.6139 | 77.2090 | 2 devices |
| hospital_ward | 11.0 | 28.5672 | 77.2100 | 2 devices |
| therapy_room | 14.0 | 28.5672 | 77.2105 | 1 device |
| caregiver_visit | 16.5 | 28.6139 | 77.2090 | 2 devices |

Each state maps to a context prompt text that establishes the semantic environment for the LLM. KV cache binary files are stored at `/data/data/com.aacbridge/files/kv_cache/{stateId}.bin`.

The `StateRepository` interface isolates all consumers from this implementation decision. Migrating to Room-backed persistence requires changing a single line in `AppContainer`. The interface exposes three operations: `getFilePath(stateId)`, `getAllContextStates()`, and `getPromptText(stateId)`.

## 4.9 Class Diagram

**Figure 4.6: Class Diagram — Core Backend Subsystems**

[Insert class diagram showing relationships between: AppContainer (creates all), StateRouter (uses TimeScorer, GPSScorer, BLEScorer), KVCacheManager (uses LlamaBridgeAdapter, StateRepository, ContextPrimerImpl, CacheMutexRegistry), DriftDetector (uses StateRouter, KVCacheManager, StateRepository), ActiveSweep (uses LocationManager, BluetoothAdapter, StateRouter, KVCacheManager), ContextPrimerImpl (uses LlamaBridgeAdapter, StateRepository, ReentrantLock), GazeTracker (emits to FusionInference), FusionInference (uses OrtEnvironment, OrtSession)]

Key relationships:
- `AppContainer` owns all component instances and provides the shared `engineLock` and `modelReady` gate.
- `KVCacheManager` depends on `LlamaBridgeAdapter` (interface) implemented by `LlamaBridge` (singleton with JNI externals).
- `ContextPrimerImpl` holds a reference to the `engineLock` and acquires it for the duration of the prefill→save sequence.
- `DriftDetector` resolves its dependencies from `AACBridgeApplication.appContainer` at worker execution time.

## 4.10 Data Flow Diagram

**Figure 4.7: Data Flow Diagram — Sensor Acquisition to Cache Residency**

```
[GPS Provider] ──→ getLastKnownLocation() ──→ GpsLocation
[BLE Scanner]  ──→ 3s scan window ──→ Map<String, Rssi>           ──→ SensorSnapshot
[System Clock] ──→ Calendar.HOUR_OF_DAY + MINUTE/60 ──→ Double    ──↗

SensorSnapshot ──→ StateRouter.getScoredStates() ──→ List<Pair<String, Double>>
                                                          ↓
                                                   top-k filter (k=3)
                                                          ↓
                                            KVCacheManager.loadTopStates()
                                                          ↓
                                          ┌────── .bin exists? ──────┐
                                          │ YES                      │ NO
                                          ↓                          ↓
                            loadKVCache(filePath, seqId)    ContextPrimer.primeAndSave()
                                          ↓                          ↓
                                    CacheState registered    prefillOnly() → saveKVCache()
                                    in activeStates map      → atomic rename → .bin created
```

## 4.11 Sequence Diagrams

**Figure 4.8: Sequence Diagram — CAP-KVC Inference Path (Cache Hit)**

```
User ──→ MainActivity: trigger intent ("water")
         MainActivity ──→ engineLock: acquire
                          engineLock ──→ LlamaBridge: loadKVCache(path, seqId)
                                        LlamaBridge ──→ llama.cpp: llama_state_seq_load_file()
                                                       ← session_tokens restored
                          engineLock ──→ LlamaBridge: resumeInference(intentPrompt)
                                        LlamaBridge ──→ llama.cpp: tokenize (no BOS)
                                                       llama.cpp: llama_decode (intent)
                                                       llama.cpp: generation loop (≤64 tokens)
                                                       ← generated text
                          engineLock: release
         MainActivity ──→ SpeechOutputManager: speak(response)
         MainActivity ──→ UI: display response
```

**Figure 4.9: Sequence Diagram — Background Priming Path**

```
BootReceiver ──→ ActiveSweep: executeSweep()
                 ActiveSweep ──→ LocationManager: getLastKnownLocation()
                 ActiveSweep ──→ BluetoothLeScanner: startScan() [3s window]
                 ActiveSweep ──→ StateRouter: getTopContextIds(snapshot, states)
                 ActiveSweep ──→ KVCacheManager: loadTopStates(topIds)
                                 KVCacheManager ──→ ContextPrimerImpl: primeAndSave(stateId, seqId, filePath)
                                                    ContextPrimerImpl ──→ engineLock: acquire
                                                    ContextPrimerImpl ──→ LlamaBridge: prefillOnly(prompt)
                                                    ContextPrimerImpl ──→ LlamaBridge: saveKVCache(tmpPath, 0)
                                                    ContextPrimerImpl ──→ engineLock: release
                                                    ContextPrimerImpl ──→ File: rename(.tmp → .bin)
```

## 4.12 State Diagrams

**Figure 4.10: State Diagram — CacheState Lifecycle**

```
                        ┌──────────────┐
                        │  UNLOADED    │
                        │ (no seqId)   │
                        └──────┬───────┘
                               │ loadTopStates() allocates seqId
                               ↓
                        ┌──────────────┐
                        │   ACTIVE     │
                        │ isActive=true│ ←──── acquireStateForInference()
                        │ refCount≥0   │       increments refCount
                        └──────┬───────┘
                               │ evictVictim() sets isActive=false
                               ↓
                        ┌──────────────┐
                        │  DRAINING    │
                        │ isActive=false│
                        │ refCount > 0 │ ←──── waiting for active inference to complete
                        └──────┬───────┘
                               │ refCount drops to 0
                               ↓
                        ┌──────────────┐
                        │   EVICTED    │
                        │ seqId returned│
                        │ to pool      │
                        └──────────────┘
```

## 4.13 Algorithm Flow

### Context Scoring Algorithm

**Table 4.2: Context Scoring Function Parameters**

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Time kernel bandwidth (σ) | 2.0 hours | A 2-hour caregiver delay drops temporal score to ~60%, appropriate for AAC routines |
| GPS decay constant (λ) | 0.1 km | 100 m represents a meaningful contextual shift (e.g., hospital room vs. cafeteria) |
| Time baseline weight (w_t) | 0.4 | Guarantees positive normalization denominator when GPS and BLE both fail |
| GPS accuracy decay | exp(−accuracy/50.0) | 50 m indoor GPS accuracy retains 36% confidence |
| BLE sigmoid center | −70 dBm | Standard Bluetooth proximity threshold |
| Dead state threshold | 0.01 | Prunes asymptotically zero-score states from ranking |
| Hysteresis margin (Δ) | 0.10 | Prevents cache thrashing from transient sensor noise |

**Figure 4.11: Context Scoring Algorithm Flowchart**

```
Input: SensorSnapshot, List<ContextState>

For each state c_i:
  1. Compute S_time = exp(−d_circ(t, t_i)² / 2σ²)
     where d_circ = min(|t₁−t₂|, 24−|t₁−t₂|)

  2. If GPS available:
       Compute S_gps = exp(−d_haversine / λ)
       Compute w_g = exp(−accuracy / 50.0)
     Else:
       S_gps = 0, w_g = 0

  3. If BLE devices detected (M > 0):
       Compute S_ble = Σ(w_i · x_i) / Σ(w_i)
       Compute R_ble = (1/M) · Σ sigmoid(rssi_i + 70)
     Else:
       S_ble = 0, w_b = 0

  4. Normalize: α = w_t/(w_t+w_g+w_b), β = w_g/(w_t+w_g+w_b), γ = w_b/(w_t+w_g+w_b)

  5. S(c_i) = α·S_time + β·S_gps + γ·S_ble

  6. If S(c_i) < 0.01: prune state

Sort surviving states by score (descending)
Return top-k state IDs
```

---

\newpage

# CHAPTER 5: METHODOLOGY AND TESTING

## 5.1 Implementation Methodology

Development followed a phased approach with clear deliverables at each stage. The team was organized by domain ownership: Arjit handled the backend/LLM infrastructure (JNI bridge, KV cache manager, state router, scoring function, concurrency model, drift detector, benchmarking), Medha handled the input/ML pipeline (NinaPro EMG preprocessing, CNN-LSTM classifier, model export, k-ablation study), and Heer handled the application layer and paper (Android UI, camera/gaze pipeline, fusion model, ONNX integration, manuscript writing).

The codebase uses manual dependency injection through the `AppContainer` class, avoiding heavy DI frameworks. This was a deliberate engineering choice: Hilt's annotation processing adds build time and startup latency that conflict with the project's edge-device constraints. All dependencies are wired explicitly, making the object graph traceable through a single file.

## 5.2 Module Descriptions

### 5.2.1 Backend: JNI Bridge (llama_jni.cpp)

The JNI bridge is a single C++ source file of 801 lines that mediates between the Kotlin application layer and the llama.cpp inference engine. It maintains three pieces of shared global state:

- `static llama_model * model` — the loaded model weights.
- `static llama_context * ctx` — the active inference context (KV cache, sampling state).
- `static std::vector<llama_token> session_tokens` — the token history of the current session.

The bridge is loaded via `System.loadLibrary("aacbridge-jni")` in an `init` block. The CMake build system links the bridge against the prebuilt `libllama.so` and the Android log library. Build configuration specifies C++17, arm64-v8a ABI filters, and NDK r27c.

**Native timing instrumentation.** Both `runInference()` and `resumeInference()` wrap the `llama_decode()` prefill call and the generation loop with `std::chrono::high_resolution_clock` timestamps. On Android's NDK runtime, `high_resolution_clock` maps to the monotonic `CLOCK_MONOTONIC` source (vDSO-accelerated, approximately 10–50 ns overhead), immune to NTP synchronization artifacts. JNI boundary crossing and log serialization occur strictly outside the timed regions.

**Memory management rationale.** The llama.cpp `ggml` library manages its own tensor memory arenas internally. Raw tensors are never exposed to the JVM. This prevents the garbage collector from interfering with native memory layout and avoids the prohibitive overhead of copying multi-megabyte tensor arrays across the JNI boundary.

### 5.2.2 Backend: Concurrency Model

The concurrency model addresses a specific crash scenario identified during Phase 2 development. If the StateRouter decided to evict a cached state, deleting the corresponding `.bin` file at the exact moment llama.cpp was executing `llama_state_seq_load_file()` on that same file, the JVM would throw an `IOException` or the native library would crash with a `SIGBUS` signal caused by an invalid memory mapping.

The solution is a single shared `engineLock` (`ReentrantLock`) that serializes all JNI operations across three primary callers:

1. **MainActivity** — foreground inference via `runInference()` or `loadKVCache()` + `resumeInference()`.
2. **KVCacheManager** — cache restoration via `loadKVCache()` during state loading.
3. **ContextPrimerImpl** — background priming via `prefillOnly()` + `saveKVCache()`.

The lock is held across both `loadKVCache()` and `resumeInference()` within a single acquisition, because releasing between calls would allow another thread to mutate `session_tokens`. Per-state coroutine `Mutex` instances from `CacheMutexRegistry` separately protect cache residency metadata without blocking unrelated cache operations.

After implementing this concurrency model, SIGBUS crashes were completely eliminated. The Phase 2 closure report records a native crash rate of zero across all subsequent testing.

### 5.2.3 Backend: State Router and Scoring Function

The `StateRouter` class is intentionally synchronous, deterministic, side-effect free, and Android-independent. It does not load KV caches, access JNI, access databases, trigger BLE scans, or perform async operations. This design makes it trivially unit-testable.

**Temporal scoring** (`TimeScorer.kt`): Uses circular distance on a 24-hour clock with Gaussian decay:

S_time = exp(−d² / 2σ²), where d = min(|t₁ − t₂|, 24 − |t₁ − t₂|)

The circular distance handles midnight wraparound seamlessly. Malformed inputs like hour 25.5 are safely normalized to 1.5 via modulo arithmetic.

**Spatial scoring** (`GPSScorer.kt`): Uses Haversine distance with exponential decay:

S_gps = exp(−d / λ), where λ = 0.1 km (the characteristic distance)

At 100 m separation, the score falls to 0.367. At 1 km, it collapses to near-zero. GPS coordinates are validated atomically inside the `GpsLocation` data class initialization.

**BLE scoring** (`BLEScorer.kt`): Uses weighted device-topology overlap:

S_ble = Σ(w_i · x_i) / Σ(w_i)

where x_i = 1 if the device is detected, 0 if absent, and w_i is the static importance weight of the device (e.g., caregiver's phone has a higher weight than a generic room speaker). An empty expected registry evaluates to 0.0.

**Dynamic weight normalization** redistributes sensor weights based on real-time reliability. The time baseline weight of 0.4 ensures the normalization denominator is always positive. If no BLE devices are detected (M = 0), `calculateReliability()` returns 0.0, setting γ = 0. The remaining weight is redistributed proportionally to α and β, maintaining the convex property α + β + γ = 1.

### 5.2.4 Backend: KV Cache Manager

`KVCacheManager` orchestrates the bounded residency of cached contexts using a `ConcurrentHashMap` for active states and a `ConcurrentLinkedQueue` for available native sequence slots. The `loadTopStates()` method:

1. Computes the set difference between requested states and already-resident states.
2. For each missing state, evicts the LRU victim if RAM is full.
3. Allocates a native `seqId` from the pool.
4. Attempts restoration from disk via `loadKVCache()` if the `.bin` file exists, or delegates to `ContextPrimerImpl.primeAndSave()` if it does not.
5. On failure, returns the `seqId` to the pool to prevent slot leakage.

The eviction mechanism follows a safe lifecycle: acquire mutex → mark inactive → wait for refCount drain (polling with 10 ms delay) → return seqId to pool → remove from activeStates → release mutex. The `releaseState()` method intentionally does not acquire the mutex because `refCount` decrement is atomic and, once `isActive` is set to false, refCount becomes monotonically decreasing.

### 5.2.5 Frontend: Gaze Tracking

`GazeTracker` uses MediaPipe's FaceLandmarker with the `face_landmarker.task` model in `LIVE_STREAM` mode. The gaze direction is computed from the displacement vector between the nose tip (landmark 4) and the midpoint of the left eye inner corner (landmark 33) and right eye inner corner (landmark 362):

```
eyeCenterX = (leftEye.x + rightEye.x) / 2
eyeCenterY = (leftEye.y + rightEye.y) / 2
deltaX = noseTip.x - eyeCenterX
deltaY = noseTip.y - eyeCenterY
```

The displacement is mapped to one of five AAC intent targets: confirm (top-left), reject (top-right), scroll (bottom-left), select (bottom-right), call-help (center). Thresholds of THRESHOLD_X = 0.02 and THRESHOLD_Y = 0.01 were empirically selected. A 400 ms dwell fixation, enforced through a finite state machine with `hasFired` debounce, prevents intent spam.

The gaze feature vector for fusion is 5-dimensional: `[deltaX, deltaY, abs(deltaX), abs(deltaY), magnitude]`. During development, a label leakage bug was discovered where the original 6-dimensional vector inadvertently included `intentIndex` as the sixth element, causing 100% classification accuracy. This was corrected by removing the leaked label.

All dwell evaluations are serialized through `Executors.newSingleThreadExecutor()` to prevent race conditions in the FSM state transitions.

### 5.2.6 Frontend: Multimodal Fusion

`FusionInference` loads the `gaze_emg_fusion.onnx` model (45 KB) from the application's assets directory using ONNX Runtime for Android. The late fusion architecture:

1. EMG embedding (1, 1, 64) → squeeze → (1, 64)
2. Gaze vector (1, 5) → project → (1, 64)
3. Concatenate → (1, 128)
4. MLP → logits (1, 5)
5. Argmax → predicted class, Softmax → confidence

If ONNX Runtime fails to load the model or encounters an inference error, the system falls back to returning the gaze target directly with confidence 0.0. Log emissions are throttled to 1-second intervals to prevent logcat ring buffer saturation.

The fusion model was exported using the legacy `torch.onnx.export` with `dynamo=False` because the PyTorch Dynamo exporter generated an external `.onnx.data` file that ONNX Runtime for Android could not seamlessly load. The legacy exporter inlined all weights into a single contiguous 45 KB file.

### 5.2.7 Input ML: EMG CNN-LSTM Classifier

The EMG preprocessing pipeline (`emg_pipeline/src/preprocess.py`) processes raw `.mat` files from NinaPro DB5 through: 4th-order Butterworth bandpass filtering (20–450 Hz), full-wave rectification, non-overlapping 200 ms window segmentation (400 samples at 2 kHz), and per-channel z-score normalization.

The `NinaProEMGDataset` (`dataset.py`) filters the 50+ NinaPro gestures to the 5 AAC intent classes: confirm, reject, scroll, select, and call-help.

The CNN-LSTM model (`model.py`) takes input shape (1, 400, 16) — 400 time samples × 16 EMG channels — and produces two outputs: (1, 5) logits for classification and (1, 64) L2-normalized embeddings for fusion. The model has approximately 500K parameters.

**Model export pipeline.** The original TFLite export via `onnx-tf` was abandoned because that library is unmaintained and incompatible with ONNX ≥ 1.14. A direct PyTorch → Keras → TFLite export script (`export_tflite_direct.py`) was written, performing layer-by-layer weight transfer including Conv1d transpose, BatchNorm parameter mapping, LSTM gate transposition, and Dense layer transfer. Numerical equivalence was validated: `np.testing.assert_allclose(pytorch_logits, tflite_logits, atol=1e-3)` passes with a maximum absolute error of 9.30e-04.

**Exported model artifacts:**

| Artifact | Format | Size |
|----------|--------|------|
| emg_classifier.onnx | ONNX opset 14 | 1.22 MB |
| emg_classifier.tflite | TFLite float32 | 1.26 MB |
| emg_classifier_int8.tflite | TFLite INT8 dynamic-range | 385 KB |

### 5.2.8 Benchmarking: LatencyProfiler

The `LatencyProfiler` class (959 lines) is the Phase 3 instrumentation layer that produces all benchmark data. It measures wall-clock latency using `System.nanoTime()` at the Kotlin layer and relies on native `std::chrono` instrumentation for phase decomposition.

Three pipeline configurations are compared:

1. **ZERO_CONTEXT** — intent tokens only, establishing the hardware latency floor.
2. **RAG_INLINE** — full context + intent via `runInference()`, with N ∈ {50, 100, 200, 500} token tiers.
3. **CAP_KVC** — restored cache via `loadKVCache()` + intent via `resumeInference()`.

Each configuration runs 32 sequential trials (2 warmup + 30 measured). The warmup phase absorbs Kotlin/ART JIT compilation and initial thermal settling.

Results are emitted as structured CSV via `Log.i(TAG, ...)` and extracted post-run via `adb logcat`. The extraction script (`regenerate_canonical.py`) matches JNI timing entries to Kotlin bench entries by strict proximity, asserts alignment of trial indices and token counts, and aborts on any mismatch.

## 5.3 Testing Strategy

### 5.3.1 Unit Testing

The project includes 41 unit tests organized into the following test suites:

**Table 5.1: Unit Test Suite Summary**

| Test Suite | Tests | Focus |
|-----------|-------|-------|
| StateRouterTest | Multiple | Scoring function correctness, edge cases (M=0 BLE, null GPS, midnight wraparound) |
| SeededStateRepositoryContractTest | Multiple | Repository contract compliance, file path resolution, prompt text resolution |
| KVCacheManagerTest | Multiple | Slot allocation, LRU eviction, invariant preservation (active + available = MAX) |
| KVCacheManagerPrimingTest | Multiple | Prime-on-miss behavior, failure recovery (seqId return to pool) |
| ContextPrimerImplTest | Multiple | Prefill → save lifecycle, model readiness gate, atomic rename |

All tests use JUnit 4 with `kotlinx-coroutines-test` for coroutine testing. The `LlamaBridgeAdapter` interface enables mock injection for tests that cannot execute actual native code.

### 5.3.2 Integration Testing

Integration testing was performed on the target device (OnePlus 11R) through the following verification procedures:

- **JNI boundary stability:** Multi-threaded operations across the JNI boundary with the `engineLock` were verified to produce zero SIGBUS crashes.
- **Cache I/O readiness:** The filesystem successfully wrote and loaded multiple MB-scale `.bin` cache files simultaneously (3/3 successful cache loads verified).
- **Daemon orchestration:** The `BootReceiver` → `ActiveSweep` startup ordering was verified to correctly populate caches on device boot.
- **Build verification:** `assembleDebug` succeeds with 0 errors.

## 5.4 Benchmark Methodology

### 5.4.1 Measurement Methodology

End-to-end wall-clock latency is captured via `System.nanoTime()` at the Kotlin layer. To decompose latency into constituent phases, `std::chrono::high_resolution_clock` timestamps are inserted immediately before and after each `llama_decode()` call within both `runInference()` and `resumeInference()`. This isolates the prefill phase — the single `llama_decode()` call that processes all prompt tokens — from the autoregressive generation loop.

On the Android NDK runtime, `high_resolution_clock` maps to the monotonic `CLOCK_MONOTONIC` source (vDSO-accelerated, approximately 10–50 ns overhead), preventing corruption from NTP synchronization. JNI boundaries and log serialization occur strictly outside the timed regions.

For the CAP_KVC condition, three sub-components are instrumented separately:
- `cache_load_ms` — Kotlin layer, measuring the complete `loadKVCache()` round trip including JNI marshalling, native file I/O, and KV tensor deserialization.
- `prefill_ms` — native layer, measuring the intent-only `llama_decode()`.
- `gen_ms` — native layer, measuring the generation loop.

### 5.4.2 Data Pipeline

**Figure 5.1: Benchmark Data Pipeline**

```
run4_complete.log (raw logcat, 85 KB)
       │
       └─ regenerate_canonical.py
              │
              ▼
   canonical_benchmark.csv (180 rows, single source of truth)
       │
       ├─ final_stats.py ──────────────→ Table statistics (means, Welch t-tests, Cohen's d)
       │
       ├─ verify_stats.py ─────────────→ Verification report (computed vs. manuscript)
       │
       └─ create_clean_csv.py
              │
              ├─→ ttft_clean.csv ──────→ Latency comparison figure
              └─→ ttft_enriched.csv ───→ Latency breakdown figure
```

The canonical dataset (`canonical_benchmark.csv`, 180 rows) is the single source of truth for all statistics and figures.

## 5.5 Experimental Setup

**Table 5.2: Benchmark Conditions — Six Pipeline Configurations**

| Condition | Description | Prompt Tokens (approx.) |
|-----------|-------------|------------------------|
| ZERO_CONTEXT | Intent tokens only (hardware floor) | 9 |
| RAG_INLINE @ N≈50 | Full context + intent via runInference | 52 |
| RAG_INLINE @ N≈100 | Full context + intent via runInference | 89 |
| RAG_INLINE @ N≈200 | Full context + intent via runInference | 206 |
| RAG_INLINE @ N≈500 | Full context + intent via runInference | 439 |
| CAP_KVC | Cached context + intent via resumeInference | 9 (intent only) |

**Table 5.3: Experimental Setup Parameters**

| Parameter | Value |
|-----------|-------|
| Device | OnePlus 11R (Snapdragon 8+ Gen 1) |
| RAM | 8 GB LPDDR5X |
| Storage | UFS 3.1 |
| OS | Android 14 |
| Model | Qwen2.5-0.5B-Instruct Q4_K_M (GGUF) |
| Context window (n_ctx) | 2048 |
| Batch size (n_batch) | 512 |
| Threads (n_threads) | 4 |
| Sampling | Greedy |
| Max output tokens | 64 |
| Warmup trials | 2 per condition |
| Measured trials | 30 per condition |
| Total trials | 180 (6 conditions × 30 trials) |
| Benchmark isolation | Airplane mode, 50% brightness, no other foreground apps |
| Benchmark duration | ~30 minutes |

**Important limitation.** CAP_KVC caches were derived from approximately 50-token prompts. Comparisons against longer RAG_INLINE contexts characterize inference-time amortization behavior rather than response quality under equivalent context richness.

## 5.6 Memory Profiling

Memory profiles were captured using `adb shell dumpsys meminfo com.aacbridge` in two states: idle (0 KV cache states loaded) and loaded (3 KV cache states resident). The native heap and total PSS values were recorded for analysis.

---

\newpage

# CHAPTER 6: PROJECT DEMONSTRATION

## 6.1 Application Workflow

The AACBridge application follows this end-to-end workflow in normal operation:

**Step 1: Application Launch.** `AACBridgeApplication.onCreate()` initializes the `AppContainer`, creating all dependency instances. The `DriftDetector` is scheduled with WorkManager. The `modelReady` flag remains false.

**Step 2: Model Initialization.** `MainActivity` calls `LlamaBridge.initializeBackend()` followed by `LlamaBridge.initializeModel("/data/local/tmp/models/qwen2.5-0.5b-instruct-q4_k_m.gguf")`. On success, `modelReady` is set to true.

**Step 3: Initial Context Sweep.** With the model ready, `ActiveSweep.executeSweep()` is invoked. GPS and BLE signals are acquired concurrently. The `StateRouter` scores all five predefined contexts and returns the top 3. `KVCacheManager.loadTopStates()` loads or primes each state.

**Step 4: Camera and Gaze Initialization.** CameraX binds the front-facing camera. `GazeTracker` initializes MediaPipe's FaceLandmarker. The `ImageAnalysis` use case begins feeding frames for gaze processing.

**Step 5: User Interaction.** When the user fixates on a gaze target for 400 ms, the dwell system fires an intent event. The gaze feature vector and mock EMG embedding are passed to `FusionInference.fuse()` to produce the final intent classification.

**Step 6: Context-Aware Inference.** The classified intent is mapped to a prompt. If a cached context is available, the system acquires the engine lock, loads the KV cache, and calls `resumeInference()` with only the intent tokens. The generated response is displayed in the UI and spoken via TTS.

**Step 7: Fallback Path.** If no cached context is available (cold miss), the `FallbackRouter` returns a generic pre-configured response immediately (Tier 1, <50 ms). A Tier 2 asynchronous RAG prefill is initiated in the background so subsequent intents can use contextual inference.

**Step 8: Periodic Drift Monitoring.** Every 15 minutes, the `DriftDetector` re-evaluates sensor conditions and triggers cache swaps if the hysteresis margin is exceeded.

## 6.2 Benchmark Execution Flow

The benchmark runs automatically on application launch when triggered by `LatencyProfiler.runBenchmarkSuite()` in `MainActivity`.

1. The profiler validates that the model is loaded and the `home_morning` cache file exists on device.
2. A semantic validation pass confirms that the cached context produces contextually appropriate responses.
3. Six conditions are executed sequentially: ZERO_CONTEXT, RAG@50, RAG@100, RAG@200, RAG@500, CAP_KVC.
4. For each condition: 2 warmup trials are discarded, then 30 measured trials are logged.
5. Between trials, `clearKVCache()` resets the native state to prevent context exhaustion.
6. Structured CSV output is emitted via `Log.i("LatencyProfiler", "BENCH,...")`.
7. The benchmark completes in approximately 30 minutes.
8. Post-run extraction via `adb logcat -d -s LatencyProfiler` produces the raw log.

## 6.3 Use Cases

**Use Case 1: Morning routine at home.** The user wakes up. The `BootReceiver` triggers `ActiveSweep`, which detects the home GPS coordinates and the caregiver's BLE-emitting phone. The `StateRouter` scores `home_morning` highest. The daemon primes its KV cache. When the user looks at the "confirm" gaze target, the system generates: "Good morning! Could I please have some breakfast? I'm feeling hungry." Total latency: ~4.5 seconds (cached).

**Use Case 2: Hospital visit with context drift.** The user is transported from home to a hospital ward at 11 AM. After 15 minutes, the `DriftDetector` notices that `hospital_ward` now scores significantly higher than `home_morning` (delta > 0.10). The system evicts the lowest-scoring home state and primes the hospital context. Subsequent "call-help" intents generate ward-appropriate responses: "Nurse, I could use some assistance. I'm in some discomfort right now."

**Use Case 3: Cold start after device reboot.** The device reboots while the user is already in the therapy room. Standard geofencing APIs would never fire an entry event. The `BootReceiver` intercepts `ACTION_BOOT_COMPLETED`, uses `goAsync()` to extend the receiver's lifetime, and launches `ActiveSweep` which detects the therapy room context and primes the cache before the user needs to communicate.

---

\newpage

# CHAPTER 7: RESULTS AND DISCUSSION

## 7.1 Latency Results

### 7.1.1 Inference Latency Decomposition

Table 7.1 reports latency measurements with native-side prefill decomposition. Each condition comprises n = 30 measured trials (180 trials total).

**Table 7.1: Latency Decomposition Across All Conditions (ms)**

| Condition | E2E Mean (ms) | Prefill Mean (ms) | Gen Mean (ms) | E2E σ (ms) | Prompt Tokens |
|-----------|---------------|-------------------|---------------|------------|---------------|
| ZERO_CONTEXT | 3,823.78 | 274.79 | 3,544.58 | 569.76 | 9 |
| RAG (N≈50) | 6,547.13 | 2,021.46 | 4,508.53 | 8,392.58 | 52 |
| RAG (N≈100) | 7,900.92 | 3,015.09 | 4,877.95 | 3,857.18 | 89 |
| RAG (N≈200) | 12,122.30 | 6,673.53 | 5,439.96 | 2,740.12 | 206 |
| RAG (N≈500) | 19,408.89 | 14,001.46 | 5,390.76 | 2,113.04 | 439 |
| CAP_KVC (cache load) | 2.74 | — | — | 1.35 | — |
| CAP_KVC (inference) | 4,571.97 | 343.35 | 4,226.74 | 373.65 | 9 |
| CAP_KVC (total) | 4,574.70 | — | — | 373.49 | — |

**Figure 7.1: End-to-End Latency Comparison**

[Insert latency_comparison_new.png — box plot showing E2E latency distribution across all six conditions]

**Figure 7.2: Latency Decomposition — Prefill vs. Generation**

[Insert latency_breakdown_new.png — stacked bar chart showing prefill and generation components for each condition]

### 7.1.2 Cache Restoration Latency

The mean cache load time is 2.74 ms (σ = 1.35 ms), representing less than 0.1% of total CAP_KVC latency. This measures the complete Kotlin-side `loadKVCache()` round trip including JNI marshalling, native file I/O, and KV tensor deserialization. Cache restoration is effectively negligible in the overall latency budget.

### 7.1.3 Prefill Amortization

RAG_INLINE prefill scales linearly from 2,021 ms (N ≈ 52 tokens) to 14,001 ms (N ≈ 439 tokens), a 6.93× increase. CAP_KVC prefill is 343.35 ms (σ = 45.00 ms) for 9 intent tokens regardless of the cached context size. This yields:

- **Prefill-specific speedup at N≈500:** 14,001 / 343 = **40.78×**
- **End-to-end speedup at N≈500:** 19,408 / 4,574 = **4.24×**

The generation phase (approximately 3,500–5,400 ms across conditions) masks the prefill improvement in the end-to-end ratio. The generation time varies because the KV cache contains different numbers of entries depending on the condition, and per-token attention cost scales with KV cache occupancy. Additionally, system-level thermal effects accumulated over the ~30-minute benchmark session contribute to generation variance.

### 7.1.4 Statistical Significance

Welch's two-sample t-tests (α = 0.05, two-tailed) were applied to prefill-only latency measurements:

**Table 7.2: Statistical Significance — Welch's t-test Results**

| Comparison | t-statistic | df | p-value | Cohen's d | Interpretation |
|-----------|-------------|-----|---------|-----------|---------------|
| CAP_KVC vs. RAG@500 (prefill) | −40.42 | 29.0 | < 10⁻²⁶ | 10.44 | Extremely significant |
| CAP_KVC vs. RAG@50 (prefill) | — | — | 0.011 | 0.70 | Medium-to-large effect |
| CAP_KVC vs. RAG@500 (E2E) | −37.86 | 30.8 | < 10⁻²⁶ | 9.78 | Extremely significant |
| CAP_KVC vs. RAG@50 (E2E) | — | — | 0.209 | — | Not significant |

The end-to-end comparison at N ≈ 50 is not statistically significant because the high generation-phase variance at that condition (σ = 8,392 ms) overwhelms the prefill difference. Welch's t-test is robust to unequal variances, but the extreme skewness of RAG latency distributions violates normality assumptions. The reported statistics should be interpreted as general trends rather than precise probability statements.

**Outliers.** Five trials were flagged by Tukey's criterion (Q₃ + 3 × IQR). No thermal telemetry or CPU frequency logs were captured, so the specific mechanisms causing these outliers cannot be attributed. All outliers were retained in reported statistics to preserve operational realism.

## 7.2 Memory Results

**Table 7.3: Native Heap Memory Profile**

| State | Native Heap (KB) | Total PSS (KB) |
|-------|-----------------|----------------|
| Idle (0 KV cache states) | 18,856 | 623,488 |
| Loaded (3 KV cache states) | 140,420 | 612,940 |
| **Delta** | **+121,564 (≈ 118.7 MB)** | — |

**Figure 7.3: Native Heap Memory Budget**

[Insert memory_budget_new.png — bar chart comparing native heap and PSS between idle and loaded states]

Three-state residency adds approximately 118.7 MB of native heap, corresponding to roughly 40 MB per state for the Qwen2.5-0.5B Q4_K_M model. This is a feasible budget on 8 GB devices. The slight PSS decrease between the idle and loaded states is attributed to measurement variability in shared memory attribution by the Android memory accounting system.

## 7.3 Fusion and EMG Results

### 7.3.1 Fusion Ablation

The late fusion model combining CNN-LSTM sEMG embeddings (ℝ⁶⁴) with MediaPipe gaze features (ℝ⁵) was evaluated on a synthetic dataset (2,000 paired samples, 60/20/20 train/val/test split). A pre-specified decision criterion required the cross-attention model to exceed the late fusion F1 by more than 3 percentage points (absolute).

**Table 7.4: Fusion Ablation Results**

| Model | Accuracy | F1 (Macro) | Precision | Recall | Latency (ms) |
|-------|----------|------------|-----------|--------|-------------|
| Cross-Attention | 0.9467 | 0.9437 | 0.9459 | 0.9450 | 0.076 |
| Late Fusion | 0.9967 | 0.9963 | 0.9956 | 0.9972 | 0.049 |

**Winner: Late Fusion.** The cross-attention model did not meet the >3% F1 advantage criterion — in fact, the difference was 5.26 percentage points in the reversed direction (late fusion outperformed cross-attention). Late fusion was selected as the simpler, faster, and higher-performing architecture.

**Figure 7.4: Fusion Ablation Comparison**

[Insert fusion_ablation_new.png — grouped bar chart comparing accuracy, F1, precision, and recall between architectures]

### 7.3.2 EMG Classification

The standalone CNN-LSTM EMG classifier was evaluated using Leave-One-Subject-Out (LOSO) cross-validation on NinaPro DB5.

**Table 7.5: EMG CNN-LSTM Classification Report (LOSO)**

| Class | Precision | Recall | F1-Score | Support |
|-------|-----------|--------|----------|---------|
| confirm | 0.2500 | 0.1111 | 0.1538 | 18 |
| reject | 0.0000 | 0.0000 | 0.0000 | 18 |
| scroll | 0.4194 | 0.7222 | 0.5306 | 18 |
| select | 0.7692 | 0.5000 | 0.6061 | 20 |
| call-help | 0.4516 | 0.7778 | 0.5714 | 18 |
| **Overall accuracy** | | | **0.4239** | **92** |

The 42.4% accuracy under LOSO evaluation with 0% recall on the reject class is consistent with the known difficulty of cross-subject EMG classification, where inter-subject variability in muscle activation patterns causes models trained on one subject to generalize poorly to unseen subjects. With only 808 training windows per fold, the model capacity exceeds the available training data for robust cross-subject generalization. Subject-specific fine-tuning is expected to improve deployment performance significantly.

NinaPro DB5 captures hand/wrist sEMG, not facial sEMG. The pipeline therefore validates the architecture and export pathway rather than deployment-level accuracy for AAC facial gesture recognition.

### 7.3.3 Drift Detection k-Ablation

An offline Sentence-BERT k-ablation study was conducted on the DailyDialog dataset [29] to evaluate the drift detector's turn window size parameter.

**Table 7.6: Drift Detection k-Ablation Results (θ = 0.15, 150 conversations)**

| k | Precision | Recall | F1 | Predicted Shifts |
|---|-----------|--------|----|-----------------|
| 1 | 0.3667 | 0.3667 | 0.3667 | 150 |
| 2 | 0.3406 | 0.3133 | 0.3264 | 138 |
| 3 | 0.1368 | 0.1067 | 0.1199 | 117 |
| 4 | 0.0714 | 0.0400 | 0.0513 | 84 |
| 5 | 0.0385 | 0.0133 | 0.0198 | 52 |

Maximum F1 of 0.367 occurs at k = 1, with performance degrading monotonically as the window size increases. Despite this, the deployed system uses k = 3 rather than k = 1. This is because the DailyDialog results are methodologically distinct from the deployed system's behavior: the ablation measures Sentence-BERT centroid distances on conversational text, while the deployed drift detector uses sensor-score hysteresis with a BLE-blind snapshot. The k = 3 setting provides smoothing against transient sensor fluctuations without requiring the system to react to every momentary score change.

**Figure 7.5: Drift Detection k-Ablation**

[Insert drift_k_ablation_new.png — line plot showing F1 score across k values]

**Figure 7.6: EMG Training Curves**

[Insert training_curves.png — loss and accuracy curves across training epochs]

**Figure 7.7: EMG Confusion Matrix**

[Insert emg_confusion_matrix.png — confusion matrix showing per-class classification performance]

## 7.4 Analysis and Discussion

### 7.4.1 Prefill Cost Dominance

The results confirm the motivating hypothesis: prefill cost dominates end-to-end latency for context-rich prompts on mobile hardware. At N ≈ 439 tokens, the prefill phase consumes 14,001 ms out of a 19,408 ms total — 72% of end-to-end latency. The remaining 28% is the autoregressive generation loop, which produces up to 64 output tokens. By amortizing the prefill to an idle-time background operation, CAP-KVC reduces the user-facing latency from 19,408 ms to 4,574 ms.

### 7.4.2 Amortization Characteristics

The term "amortized" is precise: the O(N) prefill cost is not eliminated but shifted to idle time. The total computational work remains unchanged. What changes is when the work is performed relative to the user's communication request. This temporal decoupling transforms a synchronous latency penalty into an asynchronous background cost that does not impact user-perceived responsiveness.

The amortization is most effective when:
- Contexts change slowly relative to usage frequency (enabling primed caches to be reused across multiple interactions).
- The device has meaningful idle periods during which background priming can execute without thermal or battery concerns.
- The number of relevant contexts is small enough to fit within the cache residency budget (k = 3 in our implementation).

### 7.4.3 Generation Phase Variance

Generation time varies across conditions: ZERO_CONTEXT at 3,544 ms, RAG@500 at 5,390 ms, and CAP_KVC at 4,226 ms. This variance is consistent with differing per-token attention costs when the KV cache contains different numbers of entries. A larger KV cache means each generated token must attend to more key-value pairs, increasing per-step latency. Since prefill and generation are decomposed independently via native instrumentation, these differences do not confound the prefill speedup measurement.

### 7.4.4 Memory Feasibility

The 118.7 MB native heap increase for three states (approximately 40 MB per state) represents a feasible budget on 8 GB devices. Modern Android devices running the base OS and typical background services consume 3–4 GB, leaving approximately 4 GB available for application use. The model weights (~2.2 GB for Q4_K_M) plus three cache states (~120 MB) plus OS overhead remain well within this budget.

## 7.5 Advantages

1. The 4.24× end-to-end speedup makes contextual inference practical on mobile hardware. At the N≈500 token tier, RAG latency approaches 20 seconds — beyond the tolerance of any real-time conversation. CAP-KVC brings this under 5 seconds.

2. Cache restoration at 2.74 ms adds negligible overhead. The disk-to-native restoration path is over 5,000× faster than re-computing the same KV tensors from scratch.

3. When GPS or BLE signals degrade or disappear entirely, the scoring function does not crash or produce degenerate rankings. The fixed time baseline weight guarantees a valid denominator, and sensor weights collapse gracefully to zero.

4. The single-lock concurrency model eliminated all SIGBUS crashes — a class of failure that was frequent enough during Phase 2 to block integration testing entirely.

5. The system runs fully offline. No API calls, no cloud dependencies, no data leaves the device. For medical AAC users, this is a privacy requirement, not a feature.

6. The `StateRepository` interface provides a clean seam between the benchmarking-phase in-memory implementation and a future Room-backed persistence layer. Swapping requires changing one line in `AppContainer`.

7. Every reported number can be independently reproduced from the raw `run4_complete.log` through the published analysis scripts.

## 7.6 Limitations

1. **Single-device evaluation.** All benchmarks use a single device (OnePlus 11R, Snapdragon 8+ Gen 1) and a single model (Qwen2.5-0.5B Q4_K_M) with n = 30 trials. Results may differ on other SoCs, other model architectures, or different quantization levels.

2. **Synthetic fusion data.** The fusion evaluation uses synthetically paired EMG and gaze data (2,000 samples) and lacks clinical validation. No gaze-only baseline was evaluated, making it impossible to isolate the contribution of EMG embeddings.

3. **No thermal telemetry.** CPU frequency, thermal throttling state, and die temperature were not logged during benchmarking. Outlier trials may be caused by thermal management policies, but this cannot be confirmed.

4. **No power measurement.** Battery impact of the `ContextDaemon` (60-second sweep cycle) and `DriftDetector` (15-minute WorkManager poll) has not been characterized. For a battery-constrained AAC device, this is a significant gap.

5. **Static context configurations.** The evaluation uses five predefined contexts rather than dynamically learned profiles. Real-world deployment would require mechanisms for caregiver-configurable or automatically discovered contexts.

6. **Unencrypted cache files.** Serialized KV cache files are stored unencrypted on the filesystem, relying on Android's application sandbox for protection. For medical AAC applications, this may require additional encryption.

7. **Mock EMG input.** Physical BLE armband integration remains pending. The current implementation uses `FloatArray(64)` mock embeddings.

8. **Tokenization overhead excluded.** Prefill timing measures the single `llama_decode()` call. Tokenization overhead (<1 ms) is excluded from reported prefill times.

9. **Cross-subject EMG accuracy.** The 42.4% LOSO accuracy is insufficient for deployment. Subject-specific calibration or significantly larger training datasets would be needed.

---

\newpage

# CHAPTER 8: CONCLUSION

## 8.1 Summary

This project presented Context-Aware Predictive KV-Cache Priming (CAP-KVC), a system that addresses the latency bottleneck of contextual prompt processing in mobile AAC inference. The core insight is that the environmental context of an AAC user changes slowly relative to conversation speed, making it possible to predict the needed context and pre-compute the expensive prefill during idle periods.

The system was implemented as a complete Android application with:

- A C++/JNI inference backend (801 lines of native code) based on llama.cpp, with nine exported JNI functions following a strict scalar-only boundary contract.
- A Kotlin orchestration layer managing sensor acquisition (`ActiveSweep` with concurrent GPS and BLE polling), deterministic context routing (`StateRouter` with convex scoring and reliability-adaptive weighting), bounded cache residency (`KVCacheManager` with 3-slot LRU eviction and reference-counted safe eviction), and hysteresis-gated drift detection (`DriftDetector` with Δ = 0.10 margin).
- A concurrency model using a single shared `ReentrantLock` that completely eliminated SIGBUS native crashes caused by concurrent JNI access.
- A proof-of-concept multimodal intent pipeline combining CNN-LSTM sEMG classification (NinaPro DB5) and MediaPipe gaze tracking through late fusion (selected via structured ablation).

Evaluated on a OnePlus 11R (Snapdragon 8+ Gen 1) with Qwen2.5-0.5B-Instruct Q4_K_M:

- **Prefill speedup:** 40.78× (343 ms vs. 14,001 ms at N ≈ 500 tokens)
- **End-to-end speedup:** 4.24× (4,574 ms vs. 19,408 ms)
- **Cache restoration:** 2.74 ms average
- **Memory budget:** 118.7 MB native heap for three simultaneous cache states
- **Build status:** 41/41 tests passing, 0 native crashes

All benchmark data, analysis scripts, and figure generation code are included in the repository for independent reproducibility verification.

## 8.2 Future Scope

Several directions can extend this work:

1. **Multi-device benchmarking.** Evaluating CAP-KVC on devices with different SoCs (MediaTek Dimensity, Google Tensor, Apple A-series) and larger models (1.5B–3B parameters) would establish the generalizability of the approach.

2. **Dynamic context learning.** Replacing the static `SeededStateRepository` with a Room-backed persistence layer supporting caregiver-configurable context profiles and automatic context discovery from usage patterns.

3. **BLE wearable integration.** Connecting the CNN-LSTM classifier to a physical sEMG armband for real-time facial gesture classification, replacing the current mock embeddings.

4. **Adaptive hysteresis tuning.** Replacing the fixed Δ = 0.10 hysteresis margin with a data-driven threshold that adapts to the user's daily context transition patterns.

5. **Power profiling.** Measuring the battery impact of background priming, sensor polling, and drift detection to optimize duty cycles for all-day AAC use.

6. **Thermal-aware scheduling.** Deferring background priming operations when the device is thermally throttled, and capturing CPU frequency/temperature telemetry during benchmarks for more rigorous analysis.

7. **Cache encryption.** Adding transparent encryption to serialized KV cache files for compliance with medical data protection requirements.

8. **Clinical evaluation.** Conducting usability studies with actual AAC users to measure communication rate improvements and subjective satisfaction compared to traditional grid-based systems.

9. **Larger context windows.** Evaluating the amortization benefit with models supporting 4K–8K context windows and correspondingly longer system prompts.

---

\newpage

# REFERENCES

[1] D. R. Beukelman and J. C. Light, "Augmentative and alternative communication: Supporting children and adults with complex communication needs," *Paul H. Brookes Publishing*, 5th ed., 2020.

[2] D. McNaughton and J. Light, "The iPad and mobile technology revolution: Benefits and challenges for individuals who require augmentative and alternative communication," *Augmentative and Alternative Communication*, vol. 29, no. 2, pp. 107–116, 2013.

[3] K. Vertanen and P. O. Kristensson, "Complementing text entry evaluations with a composition task," *ACM Transactions on Computer-Human Interaction*, vol. 21, no. 5, pp. 1–33, 2015.

[4] E. Frantar, S. Ashkboos, T. Hoefler, and D. Alistarh, "GPTQ: Accurate post-training quantization for generative pre-trained transformers," in *Proc. ICLR*, 2023.

[5] T. Dettmers, M. Lewis, Y. Belkada, and L. Zettlemoyer, "LLM.int8(): 8-bit matrix multiplication for transformers at scale," in *Proc. NeurIPS*, 2022.

[6] G. Gerganov, "llama.cpp: LLM inference in C/C++," 2023. [Online]. Available: https://github.com/ggerganov/llama.cpp

[7] MLC Team, "MLC LLM: Universal LLM deployment engine," 2023. [Online]. Available: https://github.com/mlc-ai/mlc-llm

[8] A. Yang et al., "Qwen2.5 technical report," *arXiv:2412.15115*, 2024.

[9] W. Kwon et al., "Efficient memory management for large language model serving with PagedAttention," in *Proc. 29th SOSP*, 2023.

[10] L. Zheng et al., "SGLang: Efficient execution of structured language model programs," in *Proc. NeurIPS*, 2024.

[11] Y. Liu et al., "CacheGen: KV cache compression and streaming for fast large language model serving," in *Proc. ACM SIGCOMM*, 2024.

[12] I. Gim et al., "Prompt Cache: Modular attention reuse for low-latency inference," in *Proc. MLSys*, 2024.

[13] Z. Liu et al., "Scissorhands: Exploiting the persistence of importance hypothesis for LLM KV cache compression at test time," in *Proc. NeurIPS*, 2023.

[14] Z. Zhang et al., "H₂O: Heavy-hitter oracle for efficient generative inference of large language models," in *Proc. NeurIPS*, 2023.

[15] G. Xiao, Y. Tian, B. Chen, S. Han, and M. Lewis, "Efficient streaming language models with attention sinks," *arXiv:2309.17453*, 2023.

[16] Y. Li et al., "SnapKV: LLM knows what you are looking for before generation," *arXiv:2404.14469*, 2024.

[17] C. Hooper, S. Kim, H. Mohammadzadeh, M. W. Mahoney, Y. S. Shao, K. Keutzer, and A. Gholami, "KVQuant: Towards 10 million context length LLM inference with KV cache quantization," in *Proc. NeurIPS*, 2024.

[18] J. Yao et al., "CacheBlend: Fast large language model serving for RAG with cached knowledge fusion," *arXiv:2405.16444*, 2024.

[19] Z. Xue et al., "PowerInfer-2: Fast large language model inference on a smartphone," *arXiv:2406.06282*, 2024.

[20] K. Alizadeh, I. Mirzadeh, D. Belenko, K. Khatamifard, M. Cho, C. C. Del Mundo, M. Rastegari, and M. Farajtabar, "LLM in a flash: Efficient large language model inference with limited memory," *arXiv:2312.11514*, 2023.

[21] Z. Liu, J. Zhao, T. Cai, L. Zhuang, Z. Lv, J. Shang, and B. Zoph, "MobileLLM: Optimizing sub-billion parameter language models for on-device use cases," in *Proc. ICML*, 2024.

[22] M. Atzori et al., "Electromyography data for non-invasive naturally-controlled robotic hand prostheses," *Scientific Data*, vol. 1, p. 140053, 2014.

[23] C. Lugaresi et al., "MediaPipe: A framework for building perception pipelines," 2019. [Online]. Available: https://mediapipe.dev

[24] P. Majaranta and A. Bulling, "Eye tracking and eye-based human-computer interaction," in *Advances in Physiological Computing*, Springer, pp. 39–65, 2014.

[25] J. Ngiam, A. Khosla, M. Kim, J. Nam, H. Lee, and A. Y. Ng, "Multimodal deep learning," in *Proc. ICML*, 2011.

[26] T. Baltrušaitis, C. Ahuja, and L.-P. Morency, "Multimodal machine learning: A survey and taxonomy," *IEEE Transactions on Pattern Analysis and Machine Intelligence*, vol. 41, no. 2, pp. 423–443, 2019.

[27] N. Reimers and I. Gurevych, "Sentence-BERT: Sentence embeddings using Siamese BERT-networks," in *Proc. EMNLP-IJCNLP*, 2019.

[28] W. Wang, F. Wei, L. Dong, H. Bao, N. Yang, and M. Zhou, "MiniLM: Deep self-attention distillation for task-agnostic compression of pre-trained transformers," in *Proc. NeurIPS*, 2020.

[29] Y. Li et al., "DailyDialog: A manually labelled multi-turn dialogue dataset," *arXiv:1710.03957*, 2017.

---

\newpage

# APPENDIX A: CODE SNIPPETS

## A.1 JNI Bridge — resumeInference() (llama_jni.cpp)

This function is the critical path for CAP-KVC inference. It continues generation from a previously loaded KV cache state, appending only the user's intent tokens at position `n_past`.

```cpp
extern "C"
JNIEXPORT jstring JNICALL
Java_com_aacbridge_inference_LlamaBridge_resumeInference(
        JNIEnv *env, jobject thiz, jstring prompt) {

    int n_past = (int) session_tokens.size();

    if (n_past == 0) {
        LOGE("resumeInference: session_tokens is empty.");
        return env->NewStringUTF("No KV cache loaded");
    }

    // Tokenize intent WITHOUT BOS (add_special=false)
    // to avoid corrupting the token stream.
    int token_count = llama_tokenize(vocab,
        input_text.c_str(), input_text.length(),
        nullptr, 0, false, true);

    // Prefill: single llama_decode() for intent tokens
    auto prefill_start = std::chrono::high_resolution_clock::now();
    int decode_result = llama_decode(ctx, batch);
    auto prefill_end = std::chrono::high_resolution_clock::now();

    // Generation loop (greedy, max 64 tokens)
    for (int i = 0; i < max_generation_tokens; i++) {
        llama_token new_token = llama_sampler_sample(smpl, ctx, -1);
        if (llama_vocab_is_eog(vocab, new_token)) break;
        // ... decode and accumulate output
    }
}
```

Key design decisions:
- `add_special=false` prevents a second BOS token that would corrupt attention.
- `llama_batch_get_one()` uses the internal position tracker updated by `llama_state_seq_load_file()`.
- Native timing is inserted around `llama_decode()`, outside JNI marshalling.

## A.2 Context Scoring — Dynamic Weight Normalization (StateRouter.kt)

```kotlin
internal fun calculateStateScore(
    snapshot: SensorSnapshot, state: ContextState
): Double {
    val wt = HardwareConfig.TIME_BASELINE_WEIGHT  // 0.4
    val sTime = timeScorer.score(snapshot.currentHourDecimal, state.expectedTime)

    val wg: Double
    val sGps: Double
    if (snapshot.location != null) {
        wg = gpsScorer.calculateReliability(snapshot.location.accuracyMeters)
        sGps = gpsScorer.score(snapshot.location, state.lat, state.lng)
    } else {
        wg = 0.0; sGps = 0.0
    }

    val wb = bleScorer.calculateReliability(snapshot.detectedBleDevices)
    val sBle = bleScorer.score(snapshot.detectedBleDevices, state.bleDevices)

    val totalWeight = wt + wg + wb  // Always > 0 because wt = 0.4
    val alpha = wt / totalWeight
    val beta = wg / totalWeight
    val gamma = wb / totalWeight

    return (alpha * sTime + beta * sGps + gamma * sBle).coerceIn(0.0, 1.0)
}
```

## A.3 Cache Priming Lifecycle — Atomic Save (ContextPrimerImpl.kt)

```kotlin
suspend fun primeAndSave(stateId: String, seqId: Int, filePath: String): Boolean {
    if (!modelReady.get()) return false
    val prompt = repository.getPromptText(stateId) ?: return false

    val tmpFile = File("$filePath.tmp")

    // Steps 2-5: Engine lock held across prefill + save
    val saveSuccess = engineLock.withLock {
        val prefillSuccess = bridge.prefillOnly(prompt)
        if (!prefillSuccess) return@withLock false
        bridge.saveKVCache(tmpFile.absolutePath, PRIMING_SEQ_ID)
    }

    if (!saveSuccess) { tmpFile.delete(); return false }

    // Step 6: Atomic rename (ext4 guarantee)
    val renamed = tmpFile.renameTo(File(filePath))
    if (!renamed) { tmpFile.delete(); return false }
    return true
}
```

## A.4 Hysteresis-Gated Drift Detection (DriftDetector.kt)

```kotlin
// BLE-bypass snapshot for battery conservation
val snapshot = SensorSnapshot(
    currentHourDecimal = currentHourDecimal,
    location = location,
    detectedBleDevices = emptyMap()  // Intentional BLE bypass
)

val scoredStates = router.getScoredStates(snapshot, availableStates)
val bestCandidate = scoredStates.firstOrNull() ?: return Result.success()

// Already resident — no action needed
if (cacheManager.isStateResident(bestCandidate.first))
    return Result.success()

// Hysteresis: swap only if delta exceeds margin
val weakestScore = residentScored.minOf { it.second }
if ((bestCandidate.second - weakestScore) > HYSTERESIS_MARGIN) {
    cacheManager.loadTopStates(listOf(bestCandidate.first))
}
```

## A.5 Build Configuration (build.gradle)

```groovy
android {
    compileSdk 34
    ndkVersion "27.2.12479018"
    defaultConfig {
        minSdk 26
        targetSdk 34
        externalNativeBuild { cmake { cppFlags "-std=c++17" } }
        ndk { abiFilters "arm64-v8a" }
    }
    externalNativeBuild {
        cmake { path "src/main/cpp/CMakeLists.txt"; version "3.22.1" }
    }
}
dependencies {
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.17.0'
    implementation 'com.google.mediapipe:tasks-vision:0.10.14'
    implementation "androidx.work:work-runtime-ktx:2.9.0"
}
```

---

*End of Report*

