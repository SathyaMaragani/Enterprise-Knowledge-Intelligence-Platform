# Document classification and clustering (phase 1.7D)

Feature engineering, category classification and unsupervised clustering on the
platform's demo corpus, each with an evaluation. The platform's **ML insights**
page (`subjects/DBE-DSD/frontend`, route `/insights`) displays these results.

```bash
python -m src.insights --frontend ../DBE-DSD/frontend/src/data/mlInsights.json
python tests/test_document_insights.py
```

The first command takes about 2 minutes on a laptop CPU. It writes
`results/document_insights.json` and a copy for the frontend. Rerunning it gives
the same numbers; only the timing fields change.

| Module | Purpose |
|---|---|
| `src/embeddings/onnx_minilm.py` | MiniLM embeddings with ONNX Runtime and the pinned model files, exactly as the backend computes them |
| `src/features/documents.py` | Text assembly and normalization; the four feature sets as sklearn transformers |
| `src/classification/categories.py` | Locked test set, tuning, test evaluation, leave-one-topic-out, leakage check, calibration |
| `src/clustering/documents.py` | Choosing k, initialisation, algorithm comparison, DBSCAN, PCA, themes, t-SNE map |
| `src/insights.py` | Runs everything and writes the JSON |

## The corpus, and why it decides the method

The demo corpus has 315 documents in 6 categories (HR, Finance, Technical,
Research, Legal, Administration). It is generated from 21 topics, each written
15 times with different figures such as years, limits and amounts. After
normalization, the word overlap (Jaccard) between two documents is:
- **0.99** within a topic;
- **0.09** across topics.

So two documents of the same topic are near-copies. That has two consequences:
- **Classification:** any split that puts versions of one topic on both sides
  measures memorisation. Every split here is by topic.
- **Clustering:** recovering the 21 topics is easy. The informative question is
  whether clusters line up with the 6 categories.

## Feature engineering

Every document becomes title + description + body. Normalization drops the
"(A)" to "(O)" version marker and maps every number to `0`, because figures say
nothing about a category.

| Feature set | What it captures | Dimensions |
|---|---|---|
| `tfidf-word` | TF-IDF over word 1-2-grams, English stop words removed, sublinear TF | 2,527 |
| `tfidf-char` | TF-IDF over character 3-5-grams inside word boundaries | 10,346 |
| `minilm` | 384-d MiniLM embedding of the whole document | 384 |
| `combined` | `tfidf-char` reduced to 100 LSA dimensions (truncated SVD), next to `minilm` | 484 |

Documents enter every pipeline as row indices. Each feature set is a
transformer that turns indices into features, so vectorizers are fitted inside
each training fold only, and no held-out topic's words leak into the vocabulary.

**The embeddings match the platform's.** The Python encoder uses the same ONNX
model and tokenizer files and the same mean pooling as the backend's
`MiniLmOnnxEncoder`. On a test sentence the two vectors had cosine 1.0, with a
largest element difference of 3 × 10⁻⁸.

## Classification

**Protocol** (the train / validation / test discipline):
1. Lock away one topic per category as the test set: `admin-facilities`,
   `fin-expenses`, `hr-performance`, `legal-nda`, `res-ranking` and
   `tech-incident`, 90 documents in all.
2. On the other 15 topics, tune each feature set × model with leave-one-topic-out
   validation.
   - Grid search, except the random forest, which uses randomized search.
   - Each fold is one unseen topic, scored by accuracy.
   - Macro-F1 isn't used for tuning: a one-topic fold contains a single category,
     so the F1 of every other category is undefined.
3. Refit the best pair on all 15 topics, and evaluate it **once** on the locked
   test set.
4. Repeat leave-one-topic-out over all 21 topics, because 6 test topics are a
   small sample.
5. Run the same model under an ordinary stratified 5-fold split, to show the leak.
6. Compare the calibration of four model types out of fold.

**Validation accuracy on unseen topics** (mean over the 15 training topics):

| Feature set | Logistic regression | Linear SVM | k-NN | Naive Bayes | Random forest |
|---|---|---|---|---|---|
| Word TF-IDF | 0.25 | 0.27 | 0.47 | 0.53 | 0.27 |
| Character TF-IDF | 0.25 | 0.27 | 0.53 | **0.60** | 0.27 |
| MiniLM | 0.47 | 0.48 | 0.41 | — | 0.33 |
| Combined | 0.33 | 0.49 | 0.51 | — | 0.23 |

Naive Bayes needs non-negative counts, so it runs on TF-IDF only. The fold-to-fold
standard deviation is large (about 0.4–0.5) because each fold is one topic that
is mostly all right or all wrong.

**Chosen model:** character TF-IDF with multinomial Naive Bayes, α = 0.1.

| Evaluation | Accuracy | Macro-F1 |
|---|---|---|
| Ordinary stratified 5-fold split (leaks) | 1.00 | — |
| **Locked test set, 6 unseen topics** | **0.83** | **0.78** |
| Leave one topic out, all 21 topics | 0.57 | 0.55 |
| Chance (largest category) | 0.19 | — |

On the locked test set:
- Every Finance document (the expenses policy) was filed under Administration.
  That gives Finance an F1 of 0, and the macro-F1 of 0.78.
- The other five categories were perfect.

Across all 21 topics, 12 were filed correctly. The 9 misses all go to a
neighbouring business area, for example:
- the budget-planning and procurement policies filed as HR;
- the information-security awareness standard filed as Legal.

**Reading the numbers:**
- The 1.00 from a random split is memorisation of near-copies, not skill.
- The honest score is well above chance (0.57 against 0.19), but far from perfect.
- The six categories are organisational, not topical: an expenses policy and a
  facilities guide use similar language.
- The locked test score (0.83) is higher than the 21-topic score (0.57), which
  shows how much one small test set can vary.

**Hyperparameter tuning: grid against random search**, on the chosen model:

| Search | Candidates | Fits | Best | Validation accuracy |
|---|---|---|---|---|
| Grid, α ∈ {0.01, 0.1, 1} | 3 | 45 | α = 0.1 | 0.60 |
| Random, α ~ log-uniform(0.001, 10) | 10 | 150 | α = 0.151 | 0.60 |

Random search found an equally good value in a continuous range, but needed more
fits. With one hyperparameter, a small grid is enough.

**Calibration.** These are out-of-fold probabilities, with each topic held out.
The table compares the top answer's confidence with how often it was right:

| Model (features) | Accuracy | Mean confidence | ECE | Brier | Log loss |
|---|---|---|---|---|---|
| Logistic regression (MiniLM) | 0.49 | 0.51 | 0.198 | 0.643 | 1.241 |
| Naive Bayes (word TF-IDF) | 0.48 | 0.69 | 0.217 | 0.632 | 1.109 |
| Random forest (MiniLM) | 0.31 | 0.29 | 0.063 | 0.796 | 1.661 |
| Linear SVM + Platt scaling (MiniLM) | 0.40 | 0.49 | 0.147 | 0.775 | 1.525 |

- **Naive Bayes is overconfident:** 0.69 confidence for 0.48 accuracy, the usual
  result for its independence assumption.
- **The random forest is the best calibrated** (ECE 0.063), but also the least
  accurate.
- **The SVM** has no probabilities of its own, so Platt scaling fits a sigmoid on
  grouped folds of the training topics.

## Clustering

Clustering never sees the labels. Afterwards, the clusters are scored against
the 6 categories and the 21 topics with ARI and NMI, plus homogeneity and
completeness. Silhouette is reported as an internal measure.

**Choosing k on the MiniLM embeddings** (k = 2 to 30, k-means++):
- **Elbow** of the within-cluster sum of squares: k = 17.
- **Best silhouette:** k = 24 (0.971). That's near the 21 topics; a few topics
  split by version.

**Random against k-means++ initialisation** (20 single-start runs each):

| k | Initialisation | Mean inertia | Iterations | Topic ARI |
|---|---|---|---|---|
| 6 | random | 157.3 ± 7.3 | 3.0 | 0.31 |
| 6 | k-means++ | 146.5 ± 2.6 | 2.9 | 0.33 |
| 24 | random | 30.6 ± 10.1 | 4.2 | 0.79 |
| 24 | k-means++ | 4.1 ± 0.3 | 2.0 | 0.98 |

With many small clusters, random starts often put two centroids in one topic and
none in another. k-means++ spreads the first centroids out, so it avoids that.

**Algorithms at k = 6** (do clusters match the categories?):

| Algorithm | ARI categories | NMI categories | Silhouette |
|---|---|---|---|
| K-means on PCA (17 components, 90% variance) | **0.60** | 0.72 | 0.41 |
| K-means, k-means++ | 0.58 | 0.71 | 0.36 |
| Mini-batch k-means | 0.49 | 0.68 | 0.36 |
| K-means on LSA (word TF-IDF) | 0.45 | 0.61 | 0.28 |
| Agglomerative, Ward | 0.41 | 0.60 | 0.38 |
| Agglomerative, complete / average link | 0.36 | 0.62 | 0.39 |
| K-means, random init | 0.32 | 0.60 | 0.35 |
| Agglomerative, single link | 0.29 | 0.56 | 0.37 |

At k = 24, every method except random-init and mini-batch k-means recovers the
topics (topic ARI 0.97–0.98).

**DBSCAN** (`min_samples = 5`, cosine distance):
- eps comes from the knee of the sorted 5th-neighbour distance: eps = 0.030.
- With no k given, DBSCAN found **21 clusters**, one per topic.
- It left 11 documents as noise, and scored topic ARI 0.98.

**Themes.** Each cluster is described by the words whose mean TF-IDF in the
cluster most exceeds the corpus mean. Two of the six k = 6 clusters are pure
(Technical, Research). The others mix categories, for example:
- leave, budget and calibration documents together;
- two HR topics (onboarding, remote working) with an Administration topic (office
  facilities), around words such as access, workstation and pass.

That matches the classification finding: the categories are not topical groups.

**Dimensionality reduction:**
- PCA needs 17 of 384 dimensions to keep 90% of the embeddings' variance.
- PCA's first two components keep only 25%, so the insights page draws its map
  with t-SNE (perplexity 30, cosine).

## Limitations

- **Small sample:** 21 topics is a small number of independent examples, and the
  per-topic results are close to all-or-nothing. Treat differences of a few
  points between models as noise.
- **Synthetic corpus:** the numbers show the methods and the evaluation working
  honestly, not how they would score on a company's real documents.
- **UMAP was not used:** it needs `umap-learn`, which is not installed. PCA and
  t-SNE cover the visualisation.

## Tests

`python tests/test_document_insights.py` runs six checks:
- text normalization;
- the locked test topics are one per category and reproducible;
- no topic spans a training and a validation fold;
- elbow detection;
- the encoder gives unit vectors that rank paraphrases closer (skipped without
  the model files);
- the results file is internally consistent (skipped until it has been
  generated).

Among the consistency checks: the confusion matrix sums to the test set's size,
and the leaky split must beat the grouped split, which must beat chance.
