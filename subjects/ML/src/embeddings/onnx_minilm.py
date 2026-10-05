"""
MiniLM document embeddings computed exactly the way the platform computes them.

The backend embeds with ONNX Runtime and the pinned `Xenova/all-MiniLM-L6-v2`
files in `models/minilm/` (see `subjects/DBE-DSD/backend/model/fetch-model.sh`),
then mean-pools over the attention mask and L2-normalizes
(`MiniLmOnnxEncoder.java`). This module does the same with the same files, so a
model trained on these vectors sees the space the platform searches in.

It needs only `onnxruntime` and `tokenizers`; `src/embeddings/encoder.py` stays
the sentence-transformers path used for the 1.7B-2 model comparison.
"""

from __future__ import annotations

from pathlib import Path

import numpy as np

# Repository root: subjects/ML/src/embeddings -> four levels up.
MODEL_DIR = Path(__file__).resolve().parents[4] / "models" / "minilm"
DIMENSION = 384


class OnnxMiniLm:
    def __init__(self, model_dir: Path = MODEL_DIR):
        import onnxruntime as ort
        from tokenizers import Tokenizer

        model, tokenizer = model_dir / "onnx" / "model.onnx", model_dir / "tokenizer.json"
        if not model.exists() or not tokenizer.exists():
            raise FileNotFoundError(
                f"MiniLM files not found in {model_dir}; run "
                "subjects/DBE-DSD/backend/model/fetch-model.sh models/minilm from the repository root"
            )
        self.tokenizer = Tokenizer.from_file(str(tokenizer))
        self.session = ort.InferenceSession(str(model), providers=["CPUExecutionProvider"])

    def encode(self, texts: list[str]) -> np.ndarray:
        """One L2-normalized 384-d vector per text, float32."""
        vectors = np.empty((len(texts), DIMENSION), dtype=np.float32)
        for i, text in enumerate(texts):
            encoding = self.tokenizer.encode(text)
            mask = np.array([encoding.attention_mask], dtype=np.int64)
            hidden = self.session.run(None, {
                "input_ids": np.array([encoding.ids], dtype=np.int64),
                "attention_mask": mask,
                "token_type_ids": np.array([encoding.type_ids], dtype=np.int64),
            })[0][0]
            pooled = (hidden * mask[0][:, None]).sum(axis=0) / mask[0].sum()
            vectors[i] = pooled / max(np.linalg.norm(pooled), 1e-9)
        return vectors
