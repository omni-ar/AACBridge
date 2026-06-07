# Related Work (Skeleton Outline)

## 1. KV Cache Optimization for LLMs
- [CITE: pagedattention_vllm] 
  - Summary: PagedAttention manages KV cache memory efficiently by splitting it into blocks, inspiring vLLM.
  - AACBridge context: AACBridge focuses on *predictive priming* rather than request batching, but borrows the concept of native ring buffer memory slots.
- [CITE: flexgen]
  - Summary: FlexGen focuses on offloading LLM attention calculations to disk/CPU for high-throughput batch generation.
  - AACBridge context: AACBridge cannot offload to disk during inference due to latency requirements (<500ms), relying entirely on in-RAM resident state.
- [CITE: scissorhands]
  - Summary: Explores sparse attention mechanisms to drop uninformative tokens from the KV cache.
  - AACBridge context: AACBridge takes a macroscopic approach, dropping entire semantic context states rather than individual tokens.
- [CITE: sglang_radixattention]
  - Summary: SGLang introduces RadixAttention for reusing KV caches across multiple requests with shared prefixes.
  - AACBridge context: AACBridge leverages a similar concept but pre-computes prefixes based on environmental sensor routing rather than previous prompts.

## 2. On-Device / Edge LLMs
- [CITE: mlc_llm]
  - Summary: MLC-LLM compiles language models into native applications using TVM for diverse edge hardware.
  - AACBridge context: AACBridge uses `llama.cpp` instead of TVM for bare-metal ARM64 optimization and custom JNI memory control.
- [CITE: llama_cpp]
  - Summary: The core `llama.cpp` implementation for efficient CPU inference of quantized models.
  - AACBridge context: Forms the backbone of AACBridge's native execution engine.
- [CITE: powerinfer]
  - Summary: PowerInfer exploits high locality in LLM activations to run fast inference on consumer hardware with CPU/GPU hybrid execution.
  - AACBridge context: Highlights the challenge of edge execution, which AACBridge mitigates via prefill amortization.
- [CITE: tinyllama]
  - Summary: TinyLlama explores the capabilities of highly compact (1.1B) models trained on vast token counts.
  - AACBridge context: AACBridge relies on small models (like Qwen2.5-1.5B or Phi-3) to fit within edge device RAM budgets.

## 3. Augmentative and Alternative Communication (AAC)
- [CITE: grid_aac_review]
  - Summary: Reviews traditional grid-based AAC systems which rely on hierarchical navigation, causing significant communication delays.
  - AACBridge context: AACBridge seeks to replace deep menu navigation with zero-shot contextual inference.
- [CITE: bci_aac_survey]
  - Summary: Surveys Brain-Computer Interface (BCI) approaches to AAC for severe motor impairment.
  - AACBridge context: AACBridge uses non-invasive multimodal sensors (EMG/Gaze) to achieve high-bandwidth intent generation without BCI surgical requirements.
- [CITE: predictive_aac_text]
  - Summary: Discusses n-gram and early neural network predictive text systems for AAC typing speedup.
  - AACBridge context: AACBridge moves beyond next-word prediction to full conversational turn generation based on environmental context.

## 4. Multimodal Sensor Fusion for Intent Classification
- [CITE: ninapro_emg]
  - Summary: The NinaPro database and baseline models for sEMG-based hand gesture recognition.
  - AACBridge context: Justifies the use of EMG embeddings for reliable, low-latency discrete intent classification.
- [CITE: gaze_hci_survey]
  - Summary: Surveys gaze-based Human-Computer Interaction, emphasizing dwell-time selection mechanisms.
  - AACBridge context: AACBridge implements a robust 400ms dwell-filter mechanism for face-mesh-derived gaze vectors.
- [CITE: cross_modal_attention]
  - Summary: Proposes cross-modal attention mechanisms for fusing disparate sensor modalities.
  - AACBridge context: AACBridge utilizes a Late Fusion architecture to concatenate high-dimensional EMG with low-dimensional gaze features.
- [CITE: late_fusion_comparison]
  - Summary: Compares early, late, and hybrid fusion strategies for multimodal classification.
  - AACBridge context: AACBridge ablates cross-attention against a late-fusion fallback, ultimately selecting late fusion as cross-attention failed to meet the required performance margin.

## 5. Semantic Drift Detection in Dialogue Systems
- [CITE: sentence_transformers]
  - Summary: Sentence-BERT (SBERT) and the use of siamese networks to generate semantically meaningful sentence embeddings.
  - AACBridge context: AACBridge utilizes quantized `all-MiniLM-L6-v2` to generate semantic vectors of recent conversation turns.
- [CITE: minilm]
  - Summary: MiniLM presents a highly distilled, efficient transformer model suitable for edge deployments.
  - AACBridge context: Allows the AACBridge drift detector daemon to run background checks without exhausting battery life.
- [CITE: dialogue_state_tracking]
  - Summary: Reviews traditional and neural dialogue state tracking (DST) methodologies.
  - AACBridge context: AACBridge uses a localized cosine similarity heuristic with a hysteresis margin instead of heavy generative DST.
