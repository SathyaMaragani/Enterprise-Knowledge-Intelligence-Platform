"""
Document category classification (HR, Finance, Technical, Research, Legal,
Administration) on the demo corpus, evaluated so the score means something.

The corpus has 21 topics with 15 near-identical variants each (word-set Jaccard
about 0.95 within a topic, 0.08 across). Any split that puts variants of one
topic on both sides scores about 100% by matching near-copies. So every split
here is by topic. The question being answered is: given a kind of document the
model has never seen, does it file it under the right category?

Protocol, following the train / validation / test discipline:
1. Lock away one topic per category as the test set (90 documents).
2. On the other 15 topics, tune every feature set x model pair with
   leave-one-topic-out validation (grid search; randomized search for the
   forest). Each validation fold is one unseen topic, scored by accuracy.
   Macro-F1 is not used here: a one-topic fold holds a single category, so its
   F1 for every other category is undefined.
3. Pick the best pair by mean validation accuracy, refit it on all 15 topics,
   and evaluate it once on the locked test topics.
4. Check robustness with leave-one-topic-out over all 21 topics.
5. Show the leak: the same model under an ordinary stratified 5-fold split.
6. Compare probability calibration of four model types out of fold.
"""

from __future__ import annotations

import time

import numpy as np
from scipy.stats import loguniform, randint
from sklearn.base import clone
from sklearn.calibration import CalibratedClassifierCV
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (accuracy_score, classification_report, confusion_matrix,
                             f1_score, log_loss)
from sklearn.model_selection import (GridSearchCV, GroupKFold, LeaveOneGroupOut,
                                     RandomizedSearchCV, StratifiedKFold, cross_val_predict)
from sklearn.naive_bayes import MultinomialNB
from sklearn.neighbors import KNeighborsClassifier
from sklearn.pipeline import Pipeline
from sklearn.svm import LinearSVC

SEED = 20261005
SPARSE_ONLY = {"naive-bayes"}  # multinomial NB needs non-negative counts, so TF-IDF only

MODELS = {
    "logistic-regression": (lambda: LogisticRegression(max_iter=5000),
                            {"clf__C": [0.1, 1, 10, 100]}),
    "linear-svm": (lambda: LinearSVC(),
                   {"clf__C": [0.01, 0.1, 1, 10]}),
    "knn": (lambda: KNeighborsClassifier(metric="cosine"),
            {"clf__n_neighbors": [1, 3, 5, 9, 15], "clf__weights": ["uniform", "distance"]}),
    "naive-bayes": (lambda: MultinomialNB(),
                    {"clf__alpha": [0.01, 0.1, 1.0]}),
    "random-forest": (lambda: RandomForestClassifier(random_state=0),
                      {"clf__n_estimators": [100, 200], "clf__max_depth": [None, 8, 16],
                       "clf__max_features": ["sqrt", "log2", 0.2], "clf__min_samples_leaf": [1, 2, 4]}),
}
RANDOMIZED = {"random-forest": 6}  # model -> sampled candidates; the rest use the full grid
# Continuous ranges for comparing random search against the grid on the winning model.
CONTINUOUS = {
    "logistic-regression": {"clf__C": loguniform(1e-3, 1e3)},
    "linear-svm": {"clf__C": loguniform(1e-3, 1e3)},
    "naive-bayes": {"clf__alpha": loguniform(1e-3, 10)},
    "knn": {"clf__n_neighbors": randint(1, 30), "clf__weights": ["uniform", "distance"]},
}
RANDOM_BUDGET = 10


def locked_test_topics(docs: list[dict], seed: int = SEED) -> list[str]:
    """One topic per category, chosen reproducibly."""
    rng = np.random.RandomState(seed)
    by_category: dict[str, list[str]] = {}
    for d in docs:
        by_category.setdefault(d["category"], [])
        if d["topic"] not in by_category[d["category"]]:
            by_category[d["category"]].append(d["topic"])
    return [rng.choice(sorted(topics)) for _, topics in sorted(by_category.items())]


def _pipeline(features, model):
    return Pipeline([("features", features), ("clf", model)])


def _search(name, features, X, y, groups):
    factory, grid = MODELS[name]
    pipe = _pipeline(features, factory())
    cv = LeaveOneGroupOut()
    if name in RANDOMIZED:
        return RandomizedSearchCV(pipe, grid, n_iter=RANDOMIZED[name], scoring="accuracy", cv=cv,
                                  random_state=0, n_jobs=-1).fit(X, y, groups=groups)
    return GridSearchCV(pipe, grid, scoring="accuracy", cv=cv, n_jobs=-1).fit(X, y, groups=groups)


def _summary(search, seconds):
    i = search.best_index_
    return {
        "candidates": len(search.cv_results_["params"]),
        "fits": len(search.cv_results_["params"]) * search.n_splits_,
        "bestParams": {k.removeprefix("clf__"): v for k, v in search.best_params_.items()},
        "cvAccuracy": round(float(search.cv_results_["mean_test_score"][i]), 3),
        "cvStd": round(float(search.cv_results_["std_test_score"][i]), 3),
        "seconds": round(seconds, 1),
    }


def _top_label_calibration(proba, y_true, classes, bins=10):
    confidence = proba.max(axis=1)
    correct = classes[proba.argmax(axis=1)] == y_true
    edges = np.linspace(0, 1, bins + 1)
    rows, ece = [], 0.0
    for lo, hi in zip(edges[:-1], edges[1:]):
        inside = (confidence > lo) & (confidence <= hi) if lo > 0 else (confidence >= lo) & (confidence <= hi)
        if inside.any():
            conf, acc = float(confidence[inside].mean()), float(correct[inside].mean())
            ece += inside.mean() * abs(acc - conf)
            rows.append({"confidence": round(conf, 3), "accuracy": round(acc, 3), "count": int(inside.sum())})
    onehot = (y_true[:, None] == classes[None, :]).astype(float)
    return {
        "bins": rows,
        "ece": round(float(ece), 3),
        "brier": round(float(((proba - onehot) ** 2).sum(axis=1).mean()), 3),
        "logLoss": round(float(log_loss(y_true, proba, labels=classes)), 3),
        "meanConfidence": round(float(confidence.mean()), 3),
        "accuracy": round(float(correct.mean()), 3),
    }


def evaluate(docs: list[dict], features: dict) -> dict:
    y = np.array([d["category"] for d in docs])
    topics = np.array([d["topic"] for d in docs])
    X = np.arange(len(docs)).reshape(-1, 1)
    categories = sorted(set(y))

    test_topics = locked_test_topics(docs)
    test = np.isin(topics, test_topics)
    train = ~test
    for tr, va in LeaveOneGroupOut().split(X[train], y[train], topics[train]):
        assert not set(topics[train][tr]) & set(topics[train][va]), "a topic leaked across a fold"

    # 2. Tune every feature set x model on the training topics only.
    selection, searches = [], {}
    for feature_name, (make_features, _) in features.items():
        for model_name in MODELS:
            if model_name in SPARSE_ONLY and not feature_name.startswith("tfidf"):
                continue
            start = time.perf_counter()
            search = _search(model_name, make_features(), X[train], y[train], topics[train])
            row = {"feature": feature_name, "model": model_name,
                   "search": "random" if model_name in RANDOMIZED else "grid",
                   **_summary(search, time.perf_counter() - start)}
            selection.append(row)
            searches[(feature_name, model_name)] = search
            print(f"  {feature_name:10s} {model_name:20s} cv accuracy {row['cvAccuracy']:.3f} +- {row['cvStd']:.3f}  {row['bestParams']}")
    selection.sort(key=lambda r: (-r["cvAccuracy"], r["cvStd"]))
    best = selection[0]
    best_search = searches[(best["feature"], best["model"])]

    # Grid versus random search on the winning pair: the grid's fixed values against
    # RANDOM_BUDGET samples from a continuous range.
    tuning = {"grid": {k: best[k] for k in ("candidates", "fits", "cvAccuracy", "seconds", "bestParams")}}
    if best["model"] in CONTINUOUS:
        start = time.perf_counter()
        rand = RandomizedSearchCV(_pipeline(features[best["feature"]][0](), MODELS[best["model"]][0]()),
                                  CONTINUOUS[best["model"]], n_iter=RANDOM_BUDGET,
                                  scoring="accuracy", cv=LeaveOneGroupOut(), random_state=0, n_jobs=-1)
        rand.fit(X[train], y[train], groups=topics[train])
        tuning["random"] = {k: v for k, v in _summary(rand, time.perf_counter() - start).items() if k != "cvStd"}
        tuning["random"]["bestParams"] = {k: round(float(v), 4) if isinstance(v, float) else v
                                          for k, v in tuning["random"]["bestParams"].items()}

    # 3. One evaluation on the locked test topics.
    predicted = best_search.predict(X[test])
    report = classification_report(y[test], predicted, labels=categories, output_dict=True, zero_division=0)
    test_result = {
        "topics": sorted(test_topics),
        "documents": int(test.sum()),
        "accuracy": round(accuracy_score(y[test], predicted), 3),
        "macroF1": round(f1_score(y[test], predicted, average="macro", zero_division=0), 3),
        "weightedF1": round(f1_score(y[test], predicted, average="weighted", zero_division=0), 3),
        "report": [{"label": c, **{m: round(report[c][m], 3) for m in ("precision", "recall", "f1-score")},
                    "support": int(report[c]["support"])} for c in categories],
        "confusion": confusion_matrix(y[test], predicted, labels=categories).tolist(),
    }

    # 4. Leave one topic out, over all 21 topics, with the chosen configuration.
    final = clone(best_search.best_estimator_)
    loto = cross_val_predict(final, X, y, groups=topics, cv=LeaveOneGroupOut(), n_jobs=-1)
    per_topic = []
    for t in sorted(set(topics)):
        mask = topics == t
        values, counts = np.unique(loto[mask], return_counts=True)
        per_topic.append({"topic": t, "category": y[mask][0], "predicted": values[counts.argmax()],
                          "accuracy": round(float((loto[mask] == y[mask]).mean()), 3)})
    loto_result = {
        "accuracy": round(accuracy_score(y, loto), 3),
        "macroF1": round(f1_score(y, loto, average="macro"), 3),
        "topicsCorrect": sum(r["predicted"] == r["category"] for r in per_topic),
        "topics": len(per_topic),
        "perTopic": per_topic,
        "confusion": confusion_matrix(y, loto, labels=categories).tolist(),
    }

    # 5. The leak: an ordinary stratified split over documents.
    leaky = cross_val_predict(final, X, y, cv=StratifiedKFold(5, shuffle=True, random_state=0), n_jobs=-1)
    leakage = {"randomSplitAccuracy": round(accuracy_score(y, leaky), 3),
               "groupedAccuracy": loto_result["accuracy"],
               "chanceAccuracy": round(max(np.unique(y, return_counts=True)[1]) / len(y), 3)}

    return {
        "categories": categories,
        "bestModel": {"feature": best["feature"], "model": best["model"], "params": best["bestParams"]},
        "selection": selection,
        "tuning": tuning,
        "test": test_result,
        "leaveOneTopicOut": loto_result,
        "leakage": leakage,
        "calibration": calibration(X, y, topics, features),
    }


def calibration(X, y, topics, features) -> list[dict]:
    """Out-of-fold (leave one topic out) probabilities for four model types, as in a reliability diagram."""
    def platt_svm(train_groups):
        return CalibratedClassifierCV(LinearSVC(C=0.1), method="sigmoid", cv=GroupKFold(3).split(
            np.zeros(len(train_groups)), np.zeros(len(train_groups)), train_groups))

    candidates = [
        ("Logistic regression", "minilm", lambda g: LogisticRegression(C=10, max_iter=5000)),
        ("Naive Bayes", "tfidf-word", lambda g: MultinomialNB(alpha=0.1)),
        ("Random forest", "minilm", lambda g: RandomForestClassifier(300, random_state=0, n_jobs=-1)),
        ("Linear SVM + Platt scaling", "minilm", platt_svm),
    ]
    classes = np.array(sorted(set(y)))
    results = []
    for label, feature_name, make_model in candidates:
        proba = np.zeros((len(y), len(classes)))
        for tr, te in LeaveOneGroupOut().split(X, y, topics):
            model = _pipeline(features[feature_name][0](), make_model(topics[tr])).fit(X[tr], y[tr])
            fold = model.predict_proba(X[te])
            proba[np.ix_(te, np.searchsorted(classes, model.classes_))] = fold
        results.append({"model": label, "feature": feature_name, **_top_label_calibration(proba, y, classes)})
        print(f"  calibration {label:28s} acc {results[-1]['accuracy']:.3f} conf {results[-1]['meanConfidence']:.3f} ECE {results[-1]['ece']:.3f} Brier {results[-1]['brier']:.3f}")
    return results
