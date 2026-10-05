import { useRef, useState } from 'react';
import { BarList, ConfusionMatrix, LineChart, MapMultiples, SERIES } from '../components/InsightCharts.jsx';
import data from '../data/mlInsights.json';

// The numbers on this page are produced by `python -m src.insights` in subjects/ML
// (see subjects/ML/docs/DOCUMENT_CLASSIFICATION_AND_CLUSTERING.md) and copied here.

const FEATURE = {
  'tfidf-word': 'Word TF-IDF',
  'tfidf-char': 'Character TF-IDF',
  minilm: 'MiniLM embedding',
  combined: 'Char LSA + MiniLM',
  'minilm-pca': 'MiniLM after PCA',
  lsa: 'Word TF-IDF + LSA',
};
const MODEL = {
  'logistic-regression': 'Logistic regression',
  'linear-svm': 'Linear SVM',
  knn: 'k-nearest neighbours',
  'naive-bayes': 'Naive Bayes',
  'random-forest': 'Random forest',
};

// Reliability bins with fewer documents than this are too noisy to plot.
const MIN_BIN = 5;
const pct = (v) => `${Math.round(v * 100)}%`;
const num = (v, d = 2) => (v == null ? '—' : Number(v).toFixed(d));
const params = (p) => Object.entries(p).map(([k, v]) => `${k} = ${v}`).join(', ');
// The row with the largest (sign 1) or smallest (sign -1) value of `key`.
const extreme = (rows, key, sign) => rows.reduce((a, b) => (sign * (b[key] - a[key]) > 0 ? b : a));

const { corpus, classification: cls, clustering: clu } = data;
const topicTitle = Object.fromEntries(clu.map.map((p) => [p.topic, p.title.replace(/\s*(v?\d[\d.]*)$/, '')]));
const dbscan = clu.comparison.find((r) => r.algorithm === 'DBSCAN');
const bestCategoryRun = clu.comparison
  .filter((r) => r.k === 6)
  .reduce((a, b) => (b.category.ari > a.category.ari ? b : a));

function Section({ id, title, children, intro }) {
  return (
    <section className="glass-panel section insights__section" aria-labelledby={id}>
      <div className="section__header">
        <h2 id={id} className="section__title">{title}</h2>
      </div>
      {intro && <p className="muted insights__intro">{intro}</p>}
      {children}
    </section>
  );
}

function Headline() {
  const loto = cls.leaveOneTopicOut;
  const tiles = [
    { label: 'Filed correctly, unseen topics', value: pct(cls.test.accuracy), hint: `6 locked test topics · macro-F1 ${num(cls.test.macroF1)}` },
    { label: 'Across all 21 topics', value: pct(loto.accuracy), hint: `leave one topic out · ${loto.topicsCorrect} of ${loto.topics} topics right` },
    { label: 'Topics found without labels', value: dbscan.clusters, hint: `DBSCAN · ${dbscan.noise} documents left as noise` },
    { label: 'Category agreement, unsupervised', value: num(bestCategoryRun.category.ari), hint: `adjusted Rand index · ${bestCategoryRun.algorithm}, k = 6` },
  ];
  return (
    <dl className="kpi-grid">
      {tiles.map(({ label, value, hint }) => (
        <div key={label} className="glass-panel kpi">
          <dt className="kpi__label">{label}</dt>
          <dd className="kpi__value">{value}</dd>
          <dd className="kpi__hint">{hint}</dd>
        </div>
      ))}
    </dl>
  );
}

function Classification() {
  const best = cls.bestModel;
  const { grid, random } = cls.tuning;
  return (
    <>
      <Section
        id="leak-title"
        title="Why the split decides the score"
        intro={`Each topic appears in 15 near-identical versions. A random split puts copies of the same document on both sides, so it scores ${pct(cls.leakage.randomSplitAccuracy)} by recognising them. Holding out whole topics asks the real question: can the model file a kind of document it has never seen?`}
      >
        <BarList
          label="Accuracy by evaluation method"
          rows={[
            { label: 'Random 5-fold split (leaks)', value: cls.leakage.randomSplitAccuracy },
            { label: 'Whole topics held out', value: cls.leakage.groupedAccuracy, highlight: true },
            { label: 'Chance (largest category)', value: cls.leakage.chanceAccuracy },
          ]}
        />
      </Section>

      <Section
        id="selection-title"
        title="Feature sets and models"
        intro="Every combination was tuned on the 15 training topics only. Each validation fold is one unseen topic, scored by accuracy. The locked test topics played no part."
      >
        <BarList
          label="Validation accuracy per feature set and model"
          rows={cls.selection.map((r) => ({
            key: `${r.feature}-${r.model}`,
            label: `${FEATURE[r.feature]} · ${MODEL[r.model]}`,
            value: r.cvAccuracy,
            highlight: r.feature === best.feature && r.model === best.model,
          }))}
        />
        <p className="muted insights__note">
          Chosen: {FEATURE[best.feature]} with {MODEL[best.model]} ({params(best.params)}). Validation accuracy varies a lot
          between folds (± {num(cls.selection[0].cvStd)}) because each fold is a single topic that is mostly all right or all wrong.
        </p>
      </Section>

      <div className="insights__pair">
        <Section id="test-title" title="Locked test set" intro={`Evaluated once, after tuning: ${cls.test.documents} documents from ${cls.test.topics.length} topics, one per category.`}>
          <div className="table-scroll">
            <table className="doc-table">
              <thead>
                <tr><th>Category</th><th>Precision</th><th>Recall</th><th>F1</th><th>Documents</th></tr>
              </thead>
              <tbody>
                {cls.test.report.map((r) => (
                  <tr key={r.label}>
                    <td>{r.label}</td><td>{num(r.precision)}</td><td>{num(r.recall)}</td><td>{num(r['f1-score'])}</td><td>{r.support}</td>
                  </tr>
                ))}
                <tr className="insights__total">
                  <td>Accuracy {num(cls.test.accuracy)}</td><td colSpan="2">Macro F1 {num(cls.test.macroF1)}</td>
                  <td colSpan="2">Weighted F1 {num(cls.test.weightedF1)}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </Section>
        <Section id="confusion-title" title="Confusion matrix">
          <ConfusionMatrix labels={cls.categories} matrix={cls.test.confusion} caption="Locked test set, counts of documents" />
        </Section>
      </div>

      <Section
        id="loto-title"
        title="Every topic held out in turn"
        intro={`Six test topics are a small sample, so the chosen model was also run with each of the 21 topics held out once: ${pct(cls.leaveOneTopicOut.accuracy)} of documents and ${cls.leaveOneTopicOut.topicsCorrect} of 21 topics filed under the right category.`}
      >
        <div className="table-scroll">
          <table className="doc-table">
            <thead><tr><th>Held-out topic</th><th>Category</th><th>Filed as (most documents)</th><th>Accuracy</th></tr></thead>
            <tbody>
              {cls.leaveOneTopicOut.perTopic.map((r) => (
                <tr key={r.topic}>
                  <td>{topicTitle[r.topic] ?? r.topic}</td>
                  <td>{r.category}</td>
                  <td>{r.predicted === r.category ? r.predicted : <span className="insights__miss">{r.predicted}</span>}</td>
                  <td>{pct(r.accuracy)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <Section
        id="tuning-title"
        title="Hyperparameter tuning: grid against random search"
        intro="The chosen model tuned twice on the training topics: a fixed grid, then values sampled from a continuous range."
      >
        <div className="table-scroll">
          <table className="doc-table">
            <thead><tr><th>Search</th><th>Candidates</th><th>Fits</th><th>Best setting</th><th>Validation accuracy</th><th>Seconds</th></tr></thead>
            <tbody>
              {[['Grid search', grid], ['Random search', random]].filter(([, r]) => r).map(([name, r]) => (
                <tr key={name}>
                  <td>{name}</td><td>{r.candidates}</td><td>{r.fits}</td><td>{params(r.bestParams)}</td><td>{num(r.cvAccuracy)}</td><td>{num(r.seconds, 1)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <Section
        id="calibration-title"
        title="Calibration: can the confidence be trusted?"
        intro={`Out-of-fold predictions with each topic held out, grouped by the model's confidence in its top answer. A calibrated model sits on the diagonal: when it says 70%, it is right 70% of the time. Bins with fewer than ${MIN_BIN} documents are left off the chart.`}
      >
        <div className="insights__pair insights__pair--flush">
          <LineChart
            title="Reliability diagram"
            series={cls.calibration.map((m) => ({
              name: m.model,
              points: m.bins.filter((b) => b.count >= MIN_BIN).map((b) => [b.confidence, b.accuracy]),
              notes: m.bins.filter((b) => b.count >= MIN_BIN).map((b) => `${b.count} documents`),
            }))}
            xDomain={[0, 1]}
            yDomain={[0, 1]}
            xLabel="Confidence"
            yLabel="Accuracy"
            reference={{ kind: 'diagonal' }}
            xFormat={(v) => num(v, 2)}
            yFormat={(v) => num(v, 2)}
          />
          <div className="table-scroll">
            <table className="doc-table">
              <thead><tr><th>Model</th><th>Accuracy</th><th>Mean confidence</th><th>ECE</th><th>Brier</th></tr></thead>
              <tbody>
                {cls.calibration.map((m, i) => (
                  <tr key={m.model}>
                    <td><span className="chart__swatch" style={{ background: SERIES[i] }} />{m.model}</td>
                    <td>{num(m.accuracy)}</td><td>{num(m.meanConfidence)}</td><td>{num(m.ece, 3)}</td><td>{num(m.brier, 3)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="muted insights__note">
              ECE is the average gap between confidence and accuracy (lower is better). Brier is the squared error of the
              probabilities. Worst calibrated: {extreme(cls.calibration, 'ece', 1).model}; best calibrated:{' '}
              {extreme(cls.calibration, 'ece', -1).model}, which is also the{' '}
              {extreme(cls.calibration, 'accuracy', -1).model === extreme(cls.calibration, 'ece', -1).model ? 'least' : 'not the least'} accurate.
            </p>
          </div>
        </div>
      </Section>
    </>
  );
}

function Clustering() {
  const sweep = clu.kSweep;
  const kDomain = [sweep[0].k, sweep[sweep.length - 1].k];
  const inertia = sweep.map((r) => r.inertia);
  const levels = [6, clu.bestK];
  return (
    <>
      <div className="insights__pair">
        <Section id="elbow-title" title="Choosing k: the elbow" intro={`Within-cluster sum of squares falls as k grows; the bend is at k = ${clu.elbowK}.`}>
          <LineChart
            title="Within-cluster sum of squares by k"
            series={[{ name: 'Inertia', points: sweep.map((r) => [r.k, r.inertia]) }]}
            xDomain={kDomain}
            yDomain={[0, Math.ceil(Math.max(...inertia) / 40) * 40]}
            xLabel="k"
            yLabel="Inertia"
            reference={{ kind: 'x', value: clu.elbowK, label: `elbow k = ${clu.elbowK}` }}
            xFormat={(v) => num(v, 0)}
            yFormat={(v) => num(v, 0)}
          />
        </Section>
        <Section id="silhouette-title" title="Choosing k: silhouette" intro={`The silhouette peaks at k = ${clu.bestK}, close to the 21 topics the generator used.`}>
          <LineChart
            title="Silhouette score by k"
            series={[{ name: 'Silhouette', points: sweep.map((r) => [r.k, r.silhouette]) }]}
            xDomain={kDomain}
            yDomain={[0, 1]}
            xLabel="k"
            yLabel="Silhouette"
            reference={{ kind: 'x', value: clu.bestK, label: `best k = ${clu.bestK}`, at: 'bottom' }}
            xFormat={(v) => num(v, 0)}
            yFormat={(v) => num(v, 2)}
          />
        </Section>
      </div>

      <Section
        id="algorithms-title"
        title="Algorithms compared"
        intro={`Scored against labels the clustering never saw. At k = 6 the question is whether clusters match the categories; at k = ${clu.bestK}, whether they match the topics. ARI is 1 for a perfect match and about 0 for a random one.`}
      >
        <div className="table-scroll">
          <table className="doc-table insights__compact">
            <thead>
              <tr><th>Algorithm</th><th>Features</th><th>k</th><th>Clusters</th><th>Noise</th><th>ARI categories</th><th>ARI topics</th><th>NMI categories</th><th>Silhouette</th></tr>
            </thead>
            <tbody>
              {clu.comparison.map((r) => (
                <tr key={`${r.algorithm}-${r.k}`} className={r.k === levels[1] && r.algorithm === 'K-means (k-means++)' ? 'insights__group-start' : undefined}>
                  <td>{r.algorithm}</td><td>{FEATURE[r.features]}</td><td>{r.k ?? 'auto'}</td><td>{r.clusters}</td><td>{r.noise}</td>
                  <td>{num(r.category.ari)}</td><td>{num(r.topic.ari)}</td><td>{num(r.category.nmi)}</td><td>{num(r.silhouette)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <div className="insights__pair">
        <Section id="init-title" title="Random against k-means++ starts" intro="Twenty single-start runs each. k-means++ spreads the first centroids out, so runs land near the best solution far more often.">
          <div className="table-scroll">
            <table className="doc-table">
              <thead><tr><th>k</th><th>Initialisation</th><th>Mean inertia</th><th>Iterations</th><th>Topic ARI</th></tr></thead>
              <tbody>
                {clu.initialisation.map((r) => (
                  <tr key={`${r.k}-${r.init}`}>
                    <td>{r.k}</td><td>{r.init}</td><td>{num(r.meanInertia, 1)} ± {num(r.stdInertia, 1)}</td><td>{num(r.meanIterations, 1)}</td><td>{num(r.meanTopicAri)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Section>
        <Section id="dbscan-title" title="DBSCAN: picking eps" intro={`Sorted distance to each document's ${clu.dbscan.minSamples}th neighbour. eps = ${num(clu.dbscan.eps, 3)} sits at the knee; DBSCAN then found ${dbscan.clusters} clusters by itself.`}>
          <LineChart
            title="k-distance curve"
            series={[{ name: 'k-distance', markers: false, points: clu.dbscan.kDistance.map((v, i) => [i * 3, v]) }]}
            xDomain={[0, (clu.dbscan.kDistance.length - 1) * 3]}
            yDomain={[0, Math.max(...clu.dbscan.kDistance)]}
            xLabel="Documents, sorted"
            yLabel="Cosine distance"
            reference={{ kind: 'y', value: clu.dbscan.eps, label: `eps = ${num(clu.dbscan.eps, 3)}` }}
            xFormat={(v) => num(v, 0)}
            yFormat={(v) => num(v, 2)}
          />
        </Section>
      </div>

      <Section id="themes-title" title="Themes at k = 6" intro="The words that set each cluster apart from the rest of the corpus, and the categories inside it. Clusters do not line up exactly with categories: the corpus' categories are organisational, not topical.">
        <ul className="theme-grid">
          {clu.themes.map((t) => (
            <li key={t.cluster} className="theme">
              <p className="theme__terms">{t.terms.join(' · ')}</p>
              <p className="theme__meta">
                {t.size} documents · {t.topics} topics · mostly {t.dominantCategory} ({pct(t.purity)})
              </p>
              <p className="theme__mix">
                {Object.entries(t.categories).map(([c, n]) => `${c} ${n}`).join(', ')}
              </p>
            </li>
          ))}
        </ul>
      </Section>

      <Section
        id="map-title"
        title="Document map"
        intro={`Each panel is the same t-SNE map of the 315 MiniLM vectors, with one category's documents highlighted. PCA needs ${clu.pca.componentsFor90} of 384 dimensions to keep 90% of the variance; its first two keep only ${pct(clu.pca.map2dVariance[0] + clu.pca.map2dVariance[1])}, which is why t-SNE is used for the map. Each topic's 15 near-identical versions land on almost the same spot, so the map shows about 21 clusters of stacked dots.`}
      >
        <MapMultiples
          label="t-SNE map of documents by category"
          points={clu.map}
          groups={cls.categories}
          groupOf={(p) => p.category}
          project={(p) => p.tsne}
        />
      </Section>
    </>
  );
}

function Method() {
  return (
    <>
      <Section id="corpus-title" title="The corpus" intro={`The platform's demo corpus: ${corpus.documents} documents in ${corpus.topics} topics, about ${Math.round(corpus.meanWords)} words each. Every topic has 15 versions that differ only in figures such as years and limits.`}>
        <div className="insights__pair insights__pair--flush">
          <div className="table-scroll">
            <table className="doc-table">
              <thead><tr><th>Category</th><th>Documents</th><th>Topics</th></tr></thead>
              <tbody>
                {corpus.categories.map((c) => <tr key={c.name}><td>{c.name}</td><td>{c.documents}</td><td>{c.topics}</td></tr>)}
              </tbody>
            </table>
          </div>
          <BarList
            label="Word overlap between documents"
            rows={[
              { label: 'Same topic', value: corpus.similarity.withinTopic },
              { label: 'Different topics', value: corpus.similarity.acrossTopics },
            ]}
          />
        </div>
      </Section>

      <Section id="features-title" title="Feature engineering" intro="Text is the title, description and body. Normalisation drops the version marker and maps numbers to 0, since figures say nothing about a category. Vectorisers are fitted inside each training fold, so no held-out words leak into training.">
        <div className="table-scroll">
          <table className="doc-table">
            <thead><tr><th>Feature set</th><th>What it captures</th><th>Dimensions</th></tr></thead>
            <tbody>
              {data.features.map((f) => <tr key={f.name}><td>{FEATURE[f.name]}</td><td>{f.description}</td><td>{f.dimensions.toLocaleString()}</td></tr>)}
            </tbody>
          </table>
        </div>
      </Section>

      <Section id="protocol-title" title="Evaluation protocol">
        <ol className="insights__steps">
          <li>Lock away one topic per category ({cls.test.topics.length} topics, {cls.test.documents} documents) as the test set.</li>
          <li>Tune every feature set and model on the other 15 topics, validating on one unseen topic at a time.</li>
          <li>Refit the best on all 15 topics and evaluate it once on the locked test set.</li>
          <li>Repeat with each of the 21 topics held out, for a steadier estimate than six topics give.</li>
          <li>Cluster without labels, then score the clusters against the categories and the topics.</li>
        </ol>
        <p className="muted insights__note">
          Limitations: 21 topics is a small number of truly independent examples, and the corpus is synthetic. The
          numbers show the method working honestly, not how it would score on a company's real documents. Generated in{' '}
          {num(data.generated.seconds, 0)} s with scikit-learn {data.generated.sklearn} and {data.generated.embedding}.
        </p>
      </Section>
    </>
  );
}

const TABS = [
  { id: 'classification', label: 'Classification', Panel: Classification },
  { id: 'clustering', label: 'Clustering', Panel: Clustering },
  { id: 'method', label: 'Data & method', Panel: Method },
];

export default function InsightsPage() {
  const [active, setActive] = useState('classification');
  const tabs = useRef([]);

  function onKeyDown(event, index) {
    const step = { ArrowRight: 1, ArrowLeft: -1 }[event.key];
    if (!step) return;
    event.preventDefault();
    const next = (index + step + TABS.length) % TABS.length;
    setActive(TABS[next].id);
    tabs.current[next]?.focus();
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">ML insights</h1>
        <p className="page-subtitle">
          How well machine learning can file and group the platform&apos;s documents, measured on the demo corpus.
        </p>
      </header>

      <Headline />

      <div className="tabs" role="tablist" aria-label="Insight views">
        {TABS.map(({ id, label }, index) => (
          <button
            key={id}
            ref={(element) => {
              tabs.current[index] = element;
            }}
            type="button"
            role="tab"
            id={`insight-tab-${id}`}
            aria-controls={`insight-panel-${id}`}
            aria-selected={active === id}
            tabIndex={active === id ? 0 : -1}
            className={`tabs__tab${active === id ? ' is-active' : ''}`}
            onClick={() => setActive(id)}
            onKeyDown={(event) => onKeyDown(event, index)}
          >
            {label}
          </button>
        ))}
      </div>

      {TABS.map(({ id, Panel }) => (
        <div key={id} role="tabpanel" id={`insight-panel-${id}`} aria-labelledby={`insight-tab-${id}`} hidden={active !== id} className="insights__panel">
          <Panel />
        </div>
      ))}
    </div>
  );
}
