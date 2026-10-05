"""
Feature engineering for the demo corpus' documents.

Documents enter every pipeline as row indices, not text. Each feature set is an
sklearn transformer that turns indices into features, so vectorizers are fitted
inside each cross-validation fold on that fold's training documents only. A
TF-IDF vocabulary or IDF table fitted on all documents would leak the held-out
topics' words into training.

Feature sets:
    tfidf-word  word 1-2-grams, sublinear TF, English stop words removed
    tfidf-char  character 3-5-grams inside word boundaries (robust to inflection)
    minilm      384-d MiniLM sentence embedding, the platform's own vectors
    combined    tfidf-char reduced by truncated SVD (LSA), next to minilm
"""

from __future__ import annotations

import json
import re
from pathlib import Path

import numpy as np
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.decomposition import TruncatedSVD
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.pipeline import FeatureUnion, make_pipeline
from sklearn.preprocessing import Normalizer

DEMO_DOCUMENTS = Path(__file__).resolve().parents[2] / "data" / "demo" / "documents.json"

_VARIANT = re.compile(r"\s*\([A-Z]\)\s*$")  # "Annual Leave Policy 2025 (B)" -> variant marker
_DIGITS = re.compile(r"\d+")


def load_documents(path: Path = DEMO_DOCUMENTS) -> list[dict]:
    return json.loads(path.read_text(encoding="utf-8"))


def document_text(doc: dict, normalize: bool = True) -> str:
    """Title, description and body as one text.

    Normalization drops the "(A)".."(O)" variant marker and maps every number to
    "0". The generator fills each topic's template with different years, limits
    and amounts; those values say nothing about the category, and as distinct
    tokens they only add sparse noise.
    """
    title = _VARIANT.sub("", doc["title"]) if normalize else doc["title"]
    text = f"{title}. {doc['description']}\n{doc['body']}"
    return _DIGITS.sub("0", text) if normalize else text


class Texts(BaseEstimator, TransformerMixin):
    """Row indices -> document texts."""

    def __init__(self, texts=None):
        self.texts = texts

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        return [self.texts[i] for i in np.asarray(X).ravel()]


class Rows(BaseEstimator, TransformerMixin):
    """Row indices -> rows of a precomputed matrix (embeddings are not fitted)."""

    def __init__(self, matrix=None):
        self.matrix = matrix

    def fit(self, X, y=None):
        return self

    def transform(self, X):
        return self.matrix[np.asarray(X).ravel()]


def word_tfidf():
    return TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True, min_df=2, stop_words="english")


def char_tfidf():
    return TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), sublinear_tf=True, min_df=2)


def feature_sets(texts: list[str], embeddings: np.ndarray) -> dict:
    """name -> (factory returning a fresh unfitted transformer, short description)."""
    return {
        "tfidf-word": (lambda: make_pipeline(Texts(texts), word_tfidf()),
                       "TF-IDF over word 1-2-grams, stop words removed"),
        "tfidf-char": (lambda: make_pipeline(Texts(texts), char_tfidf()),
                       "TF-IDF over character 3-5-grams"),
        "minilm": (lambda: Rows(embeddings),
                   "384-d MiniLM embedding of the whole document (the platform's search model)"),
        "combined": (lambda: FeatureUnion([
                        ("lsa", make_pipeline(Texts(texts), char_tfidf(),
                                              TruncatedSVD(100, random_state=0), Normalizer())),
                        ("minilm", Rows(embeddings)),
                     ]),
                     "character TF-IDF reduced to 100 LSA dimensions, joined with MiniLM"),
    }


def lsa(texts: list[str], dimensions: int = 100) -> np.ndarray:
    """Word TF-IDF -> truncated SVD -> unit length, for clustering (no labels, so fitted on all)."""
    tfidf = word_tfidf().fit_transform(texts)
    reduced = TruncatedSVD(min(dimensions, tfidf.shape[1] - 1), random_state=0).fit_transform(tfidf)
    return Normalizer().fit_transform(reduced)


def near_duplicate_stats(docs: list[dict]) -> dict:
    """Mean word-set Jaccard similarity of documents within a topic versus across topics."""
    words = [set(document_text(d).lower().split()) for d in docs]
    topics = [d["topic"] for d in docs]
    within, across = [], []
    for i in range(len(docs)):
        for j in range(i + 1, len(docs)):
            score = len(words[i] & words[j]) / len(words[i] | words[j])
            (within if topics[i] == topics[j] else across).append(score)
    return {"withinTopic": round(float(np.mean(within)), 3), "acrossTopics": round(float(np.mean(across)), 3)}
