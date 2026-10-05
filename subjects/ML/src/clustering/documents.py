"""
Unsupervised grouping of the demo corpus' documents, scored against the labels
the clustering never saw: the 6 categories and the 21 topics.

Covers:
- choosing k with the elbow (within-cluster sum of squares) and silhouette;
- k-means with random versus k-means++ initialisation, and mini-batch k-means;
- agglomerative clustering with single, complete, average and Ward linkage;
- DBSCAN, with eps read off the k-distance curve;
- PCA before clustering, and t-SNE / PCA maps for display;
- the themes each cluster stands for, as its most distinctive words.

Every algorithm runs at two levels: k = 6 (does it recover the categories?) and
the silhouette-chosen k (does it find the topics?).
"""

from __future__ import annotations

import time

import numpy as np
from sklearn.cluster import DBSCAN, AgglomerativeClustering, KMeans, MiniBatchKMeans
from sklearn.decomposition import PCA
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.manifold import TSNE
from sklearn.metrics import (adjusted_rand_score, completeness_score, homogeneity_score,
                             normalized_mutual_info_score, silhouette_score)
from sklearn.neighbors import NearestNeighbors
from sklearn.preprocessing import normalize

from src.features.documents import _VARIANT, lsa

K_RANGE = range(2, 31)
MIN_SAMPLES = 5  # DBSCAN core-point threshold; each topic has 15 documents


def knee(values) -> int:
    """Index of the point farthest from the chord joining the curve's ends (the elbow)."""
    y = np.asarray(values, dtype=float)
    x = np.linspace(0, 1, len(y))
    y = (y - y.min()) / (y.max() - y.min() or 1)
    chord = np.array([x[-1] - x[0], y[-1] - y[0]])
    chord /= np.linalg.norm(chord)
    distance = np.abs((x - x[0]) * chord[1] - (y - y[0]) * chord[0])
    return int(distance.argmax())


def scores(labels, features, categories, topics) -> dict:
    clustered = labels >= 0  # DBSCAN marks noise with -1
    found = len(set(labels[clustered]))
    sil = (silhouette_score(features[clustered], labels[clustered], metric="cosine")
           if 1 < found < clustered.sum() else None)
    return {
        "clusters": found,
        "noise": int((~clustered).sum()),
        "silhouette": None if sil is None else round(float(sil), 3),
        "category": {"ari": round(adjusted_rand_score(categories, labels), 3),
                     "nmi": round(normalized_mutual_info_score(categories, labels), 3),
                     "homogeneity": round(homogeneity_score(categories, labels), 3),
                     "completeness": round(completeness_score(categories, labels), 3)},
        "topic": {"ari": round(adjusted_rand_score(topics, labels), 3),
                  "nmi": round(normalized_mutual_info_score(topics, labels), 3)},
    }


def _timed(fit):
    start = time.perf_counter()
    labels = fit()
    return labels, round(time.perf_counter() - start, 3)


def evaluate(docs: list[dict], texts: list[str], embeddings: np.ndarray) -> dict:
    categories = np.array([d["category"] for d in docs])
    topics = np.array([d["topic"] for d in docs])
    E = embeddings

    # PCA: how many directions carry the embeddings' variance?
    pca = PCA(random_state=0).fit(E)
    cumulative = np.cumsum(pca.explained_variance_ratio_)
    components_90 = int(np.searchsorted(cumulative, 0.90) + 1)
    feature_sets = {
        "minilm": E,
        "minilm-pca": normalize(PCA(components_90, random_state=0).fit_transform(E)),
        "lsa": lsa(texts),
    }

    # Choosing k on the MiniLM embeddings: elbow and silhouette.
    sweep = []
    for k in K_RANGE:
        km = KMeans(k, n_init=10, random_state=0).fit(E)
        sweep.append({"k": k, "inertia": round(float(km.inertia_), 3),
                      "silhouette": round(float(silhouette_score(E, km.labels_, metric="cosine")), 3)})
    best_k = max(sweep, key=lambda r: r["silhouette"])["k"]
    elbow_k = sweep[knee([r["inertia"] for r in sweep])]["k"]
    print(f"  k sweep: silhouette best k={best_k}, elbow k={elbow_k}")

    # Random versus k-means++ initialisation: 20 single-start runs each.
    initialisation = []
    for k in (6, best_k):
        runs = {init: [KMeans(k, init=init, n_init=1, random_state=s).fit(E) for s in range(20)]
                for init in ("random", "k-means++")}
        best_inertia = min(m.inertia_ for models in runs.values() for m in models)
        for init, models in runs.items():
            inertia = np.array([m.inertia_ for m in models])
            initialisation.append({
                "k": k, "init": init,
                "meanInertia": round(float(inertia.mean()), 3), "stdInertia": round(float(inertia.std()), 3),
                "meanIterations": round(float(np.mean([m.n_iter_ for m in models])), 1),
                "reachedBest": int((inertia <= best_inertia * (1 + 1e-6)).sum()), "runs": len(models),
                "meanTopicAri": round(float(np.mean([adjusted_rand_score(topics, m.labels_) for m in models])), 3),
            })

    # DBSCAN: eps at the knee of the sorted distance to each point's MIN_SAMPLES-th neighbour.
    distances = NearestNeighbors(n_neighbors=MIN_SAMPLES, metric="cosine").fit(E).kneighbors(E)[0][:, -1]
    k_distance = np.sort(distances)
    eps = float(k_distance[knee(k_distance)])

    # The algorithm comparison, at k = 6 and at the silhouette-chosen k.
    def runs_at(k):
        return [
            ("K-means (k-means++)", "minilm", lambda: KMeans(k, n_init=10, random_state=0).fit_predict(E)),
            ("K-means (random init)", "minilm", lambda: KMeans(k, init="random", n_init=10, random_state=0).fit_predict(E)),
            ("Mini-batch k-means", "minilm", lambda: MiniBatchKMeans(k, batch_size=64, n_init=10, random_state=0).fit_predict(E)),
            ("K-means on PCA", "minilm-pca", lambda: KMeans(k, n_init=10, random_state=0).fit_predict(feature_sets["minilm-pca"])),
            ("K-means on LSA (TF-IDF)", "lsa", lambda: KMeans(k, n_init=10, random_state=0).fit_predict(feature_sets["lsa"])),
            ("Agglomerative, single link", "minilm", lambda: AgglomerativeClustering(k, metric="cosine", linkage="single").fit_predict(E)),
            ("Agglomerative, complete link", "minilm", lambda: AgglomerativeClustering(k, metric="cosine", linkage="complete").fit_predict(E)),
            ("Agglomerative, average link", "minilm", lambda: AgglomerativeClustering(k, metric="cosine", linkage="average").fit_predict(E)),
            ("Agglomerative, Ward", "minilm", lambda: AgglomerativeClustering(k, linkage="ward").fit_predict(E)),
        ]

    comparison = []
    for k in (6, best_k):
        for name, feature_name, fit in runs_at(k):
            labels, seconds = _timed(fit)
            comparison.append({"algorithm": name, "features": feature_name, "k": k, "seconds": seconds,
                               **scores(labels, feature_sets[feature_name], categories, topics)})
    labels, seconds = _timed(lambda: DBSCAN(eps=eps, min_samples=MIN_SAMPLES, metric="cosine").fit_predict(E))
    comparison.append({"algorithm": "DBSCAN", "features": "minilm", "k": None, "seconds": seconds,
                       **scores(labels, E, categories, topics)})

    # Themes: what each k-means++ cluster is about.
    six = KMeans(6, n_init=10, random_state=0).fit_predict(E)
    fine = KMeans(best_k, n_init=10, random_state=0).fit_predict(E)
    themes, fine_themes = describe(six, docs, texts), describe(fine, docs, texts, terms=4)

    # 2-D maps for display: t-SNE keeps neighbourhoods, PCA keeps global variance.
    tsne = TSNE(2, perplexity=30, init="pca", metric="cosine", random_state=0).fit_transform(E)
    pca2 = PCA(2, random_state=0).fit(E)
    flat = pca2.transform(E)
    points = []
    for doc, (tx, ty), (px, py), cluster in zip(docs, tsne, flat, six):
        points.append({"tsne": [round(float(tx), 2), round(float(ty), 2)],
                       "pca": [round(float(px), 3), round(float(py), 3)],
                       "category": doc["category"], "topic": doc["topic"], "cluster": int(cluster),
                       "title": _VARIANT.sub("", doc["title"])})

    return {
        "features": {name: int(matrix.shape[1]) for name, matrix in feature_sets.items()},
        "pca": {"cumulativeVariance": [round(float(v), 3) for v in cumulative[:60]],
                "componentsFor90": components_90, "map2dVariance": [round(float(v), 3) for v in pca2.explained_variance_ratio_]},
        "kSweep": sweep, "bestK": best_k, "elbowK": elbow_k,
        "initialisation": initialisation,
        "dbscan": {"eps": round(eps, 4), "minSamples": MIN_SAMPLES,
                   "kDistance": [round(float(v), 4) for v in k_distance[::3]]},
        "comparison": comparison,
        "themes": themes, "fineThemes": fine_themes,
        "map": points,
    }


def describe(labels, docs, texts, terms=6) -> list[dict]:
    """Per cluster: size, dominant category and purity, topics, and the most distinctive words."""
    vectorizer = TfidfVectorizer(stop_words="english", min_df=2, token_pattern=r"(?u)\b[a-zA-Z][a-zA-Z-]+\b")
    tfidf = vectorizer.fit_transform(texts)
    vocabulary = np.array(vectorizer.get_feature_names_out())
    overall = np.asarray(tfidf.mean(axis=0)).ravel()
    themes = []
    for c in sorted(set(labels)):
        members = np.flatnonzero(labels == c)
        lift = np.asarray(tfidf[members].mean(axis=0)).ravel() - overall
        cats, counts = np.unique([docs[i]["category"] for i in members], return_counts=True)
        topic_titles = {}
        for i in members:
            topic_titles.setdefault(docs[i]["topic"], _VARIANT.sub("", docs[i]["title"]))
        themes.append({
            "cluster": int(c), "size": int(len(members)),
            "terms": vocabulary[np.argsort(lift)[::-1][:terms]].tolist(),
            "dominantCategory": str(cats[counts.argmax()]),
            "purity": round(float(counts.max() / len(members)), 3),
            "categories": {str(k): int(v) for k, v in zip(cats, counts)},
            "topics": len(topic_titles),
            "examples": list(topic_titles.values())[:3],
        })
    return sorted(themes, key=lambda t: (-t["size"], t["cluster"]))
