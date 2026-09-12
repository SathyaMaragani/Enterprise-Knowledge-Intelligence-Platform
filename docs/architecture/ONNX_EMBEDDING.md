# ONNX Embedding Architecture

This document describes the in-process Java ONNX embedding architecture used for semantic search.

## Overview

The Enterprise Knowledge Intelligence Platform uses `sentence-transformers/all-MiniLM-L6-v2` (384 dimensions) for its embedding model. To minimize operational costs and avoid running a separate GPU/Python microservice, we run the model *in-process* within the Spring Boot backend using ONNX Runtime.

## Architecture

1.  **Tokenizer**: We use `ai.djl.huggingface:tokenizers` to load the Hugging Face `tokenizer.json` directly. It produces `input_ids`, `attention_mask`, and `token_type_ids`.
2.  **Inference**: We use `ai.onnxruntime:onnxruntime` to execute `model.onnx`. The model outputs `last_hidden_state`.
3.  **Mean Pooling**: We apply attention-mask-aware mean pooling to average the embeddings over tokens where the attention mask is `1`.
4.  **Normalization**: We apply L2 normalization to ensure that the dot product is exactly equal to cosine similarity (the metric configured in our Qdrant instance).

## Verification

The Java ONNX embedding implementation has been rigorously tested against a Python `sentence-transformers` reference.

-   **Numerical Compatibility**: The absolute maximum element-wise difference between Python PyTorch FP32 and Java ONNX output is `~1.5e-7`. Cosine similarity between the two outputs is strictly `1.000000`.
-   **Retrieval Equivalence**: A 32-query regression suite tested against a synthetic corpus of 705 vectors verified that the top-5 retrieval outcomes from Qdrant are **100% identical**, yielding the same documents in the exact same order for every test query.

## Performance

Local tests indicate:
-   **Cold start**: ~130ms per encode on CPU.
-   Memory overhead is minimal and safely contained within the JVM heap footprint.

This performance is more than sufficient for user-facing, real-time query encoding in the current phase.
