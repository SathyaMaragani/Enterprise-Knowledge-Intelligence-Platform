"""
Sentence-embedding encoders for the two models selected in phase 1.7B-1.

The only substantive difference between them is the query instruction prefix.
BGE was trained with an instruction prepended to *queries* but not to
*documents*; omitting it does not raise an error, it just retrieves worse. That
asymmetry is the single most likely way to get quietly wrong numbers here, so
it lives in one place and is covered by an executable test rather than a note.

No Qdrant writes and no Spring integration happen here -- that is 1.7B-3.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

MODELS_DIR = Path(__file__).resolve().parents[2] / "models"


@dataclass(frozen=True)
class ModelSpec:
    """Everything that differs between the candidate models."""

    key: str
    hf_id: str
    license: str
    # Prepended to queries only. Empty for models trained without one.
    query_prefix: str = ""
    # Prepended to documents only. Both selected models use none.
    document_prefix: str = ""
    expected_dimension: int = 384


MINILM = ModelSpec(
    key="minilm-l6",
    hf_id="sentence-transformers/all-MiniLM-L6-v2",
    license="Apache-2.0",
)

BGE_SMALL = ModelSpec(
    key="bge-small",
    hf_id="BAAI/bge-small-en-v1.5",
    license="MIT",
    query_prefix="Represent this sentence for searching relevant passages: ",
)

SPECS = {spec.key: spec for spec in (MINILM, BGE_SMALL)}


@dataclass
class EncodeStats:
    """Resource measurements, so model choice can be justified on cost too."""

    texts: int = 0
    seconds: float = 0.0
    peak_rss_mb: float = 0.0
    extras: dict = field(default_factory=dict)

    @property
    def per_second(self) -> float:
        return self.texts / self.seconds if self.seconds else 0.0


def _peak_rss_mb() -> float:
    """Current process RSS in MB, without taking a psutil dependency."""
    try:
        import ctypes
        import ctypes.wintypes

        class PROCESS_MEMORY_COUNTERS(ctypes.Structure):
            _fields_ = [
                ("cb", ctypes.wintypes.DWORD),
                ("PageFaultCount", ctypes.wintypes.DWORD),
                ("PeakWorkingSetSize", ctypes.c_size_t),
                ("WorkingSetSize", ctypes.c_size_t),
                ("QuotaPeakPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPagedPoolUsage", ctypes.c_size_t),
                ("QuotaPeakNonPagedPoolUsage", ctypes.c_size_t),
                ("QuotaNonPagedPoolUsage", ctypes.c_size_t),
                ("PagefileUsage", ctypes.c_size_t),
                ("PeakPagefileUsage", ctypes.c_size_t),
            ]

        counters = PROCESS_MEMORY_COUNTERS()
        counters.cb = ctypes.sizeof(counters)
        ctypes.windll.psapi.GetProcessMemoryInfo(
            ctypes.windll.kernel32.GetCurrentProcess(), ctypes.byref(counters), counters.cb
        )
        return counters.PeakWorkingSetSize / 1e6
    except Exception:
        try:
            import resource

            return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss / 1e3
        except Exception:
            return 0.0


class Encoder:
    """
    Thin wrapper over SentenceTransformer that owns the prefix rules.

    Callers must use `encode_queries` / `encode_documents` rather than a single
    generic `encode`, because the correct behaviour differs between the two and
    a generic method would make it easy to apply the wrong one.
    """

    def __init__(self, spec: ModelSpec, device: str = "cpu", cache_dir: Path | None = None):
        from sentence_transformers import SentenceTransformer

        self.spec = spec
        self.device = device
        started = time.time()
        self.model = SentenceTransformer(
            spec.hf_id,
            device=device,
            cache_folder=str(cache_dir or MODELS_DIR),
        )
        self.load_seconds = time.time() - started

        # Renamed in sentence-transformers 5.x; support both so the pinned
        # range in requirements.txt stays valid.
        get_dimension = getattr(
            self.model, "get_embedding_dimension", None
        ) or self.model.get_sentence_embedding_dimension
        dimension = get_dimension()
        if dimension != spec.expected_dimension:
            raise ValueError(
                f"{spec.hf_id} produced {dimension} dimensions, expected "
                f"{spec.expected_dimension}. The Qdrant collection is "
                f"{spec.expected_dimension}-dimensional; using this model would "
                f"require a collection migration."
            )
        self.dimension = dimension

    def _encode(self, texts: list[str], prefix: str, batch_size: int) -> tuple[np.ndarray, EncodeStats]:
        prepared = [prefix + text for text in texts] if prefix else texts
        started = time.time()
        vectors = self.model.encode(
            prepared,
            batch_size=batch_size,
            convert_to_numpy=True,
            # L2-normalized so a dot product is cosine similarity -- the same
            # metric the Qdrant collection uses.
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        stats = EncodeStats(
            texts=len(texts),
            seconds=time.time() - started,
            peak_rss_mb=_peak_rss_mb(),
        )
        return vectors.astype(np.float32), stats

    def encode_documents(self, texts: list[str], batch_size: int = 128):
        return self._encode(texts, self.spec.document_prefix, batch_size)

    def encode_queries(self, texts: list[str], batch_size: int = 128):
        return self._encode(texts, self.spec.query_prefix, batch_size)


def model_disk_mb(spec: ModelSpec, cache_dir: Path | None = None) -> float:
    """Size of the downloaded weights on disk, for the resource comparison."""
    root = Path(cache_dir or MODELS_DIR)
    stem = spec.hf_id.replace("/", "_")
    total = 0
    for path in root.rglob("*"):
        if path.is_file() and stem.lower() in str(path).lower().replace("--", "_"):
            total += path.stat().st_size
    return total / 1e6
