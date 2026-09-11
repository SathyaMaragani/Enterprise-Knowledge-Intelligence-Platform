"""
Normalization and chunking into the canonical chunk format.

The chunk record deliberately reuses the field names already used by the
platform, so ingestion in a later phase is a mapping rather than a redesign:

    canonical            MongoDB chunks[i]      Qdrant payload
    -----------------    -------------------    ----------------------
    chunk_id             chunk_id               chunk_id
    text                 text                   (not stored)
    position             position               chunk_position
    token_count          token_count            (not stored)
    doc_id               (parent document)      postgres_document_id
    title                (parent title)         title
    language             content.language       language

`doc_id` is the source corpus identifier and is a string. Mapping it to the
integer `postgres_document_id` that Qdrant expects is an ingestion concern for
a later phase, not something this module invents.
"""

from __future__ import annotations

import re
import unicodedata

# ponytail: word-count windows, not model tokens. A real tokenizer belongs with
# the model in 1.7B-2; until one is loaded, words are a stable proxy and keep
# this module dependency-free. Rule of thumb: ~0.75 words per token for English.
DEFAULT_CHUNK_WORDS = 180
DEFAULT_OVERLAP_WORDS = 40

_WHITESPACE = re.compile(r"\s+")
_CONTROL = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")


def normalize(text: str) -> str:
    """
    Collapse a raw document into clean, single-spaced text.

    NFKC folds typographic variants (curly quotes, ligatures, full-width forms)
    onto their ASCII equivalents so that the same word embeds identically
    regardless of how the source encoded it.
    """
    if not text:
        return ""
    text = unicodedata.normalize("NFKC", text)
    text = _CONTROL.sub(" ", text)
    return _WHITESPACE.sub(" ", text).strip()


def chunk_text(
    text: str,
    chunk_words: int = DEFAULT_CHUNK_WORDS,
    overlap_words: int = DEFAULT_OVERLAP_WORDS,
) -> list[str]:
    """
    Split text into overlapping windows.

    Overlap exists so a passage that straddles a boundary still appears whole in
    at least one chunk; without it, the sentence carrying the answer can be cut
    in half and retrieved by neither side.
    """
    if chunk_words <= 0:
        raise ValueError("chunk_words must be positive")
    if not 0 <= overlap_words < chunk_words:
        raise ValueError("overlap_words must be >= 0 and smaller than chunk_words")

    words = normalize(text).split()
    if not words:
        return []

    stride = chunk_words - overlap_words
    chunks = []
    for start in range(0, len(words), stride):
        window = words[start : start + chunk_words]
        if window:
            chunks.append(" ".join(window))
        if start + chunk_words >= len(words):
            break
    return chunks


def chunk_document(
    document: dict,
    chunk_words: int = DEFAULT_CHUNK_WORDS,
    overlap_words: int = DEFAULT_OVERLAP_WORDS,
) -> list[dict]:
    """Turn one canonical document into canonical chunk records."""
    doc_id = str(document["doc_id"])
    title = normalize(document.get("title", ""))

    # The title is prepended to the first chunk rather than stored separately:
    # in FiQA many documents are a bare answer whose subject only appears in the
    # title, and a chunk that loses it embeds as an orphaned fragment.
    body = document.get("text", "")
    combined = f"{title}. {body}" if title else body

    records = []
    for position, text in enumerate(chunk_text(combined, chunk_words, overlap_words)):
        records.append(
            {
                "doc_id": doc_id,
                "chunk_id": f"{document.get('source_dataset', 'doc')}-{doc_id}-{position}",
                "text": text,
                "position": position,
                "token_count": len(text.split()),
                "title": title,
                "language": document.get("language", "en"),
                "source_dataset": document.get("source_dataset", ""),
            }
        )
    return records


def chunk_corpus(
    documents: list[dict],
    chunk_words: int = DEFAULT_CHUNK_WORDS,
    overlap_words: int = DEFAULT_OVERLAP_WORDS,
) -> list[dict]:
    chunks: list[dict] = []
    for document in documents:
        chunks.extend(chunk_document(document, chunk_words, overlap_words))
    return chunks
