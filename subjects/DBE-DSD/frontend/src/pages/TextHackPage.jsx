import { useRef, useState } from 'react';
import { request } from '../api/client.js';
import { useApi } from '../api/useApi.js';

const SAMPLE_TEXT = 'she sells sea shells by the sea shore; he hears the ushers hush.';
const SAMPLE_PATTERNS = 'he\nshe\nhers';
// Two edge-disjoint chains from 0 to 5 (0-1-3-5 and 0-2-4-5).
const SAMPLE_CITATIONS = '0 1\n0 2\n1 3\n2 3\n2 4\n3 5\n4 5';

/**
 * Splits `text` into plain and highlighted runs. Overlapping or touching
 * matches merge into one highlight, since a character is either marked or not.
 */
export function highlightSegments(text, matches) {
  const merged = [];
  for (const { start, end } of [...matches].sort((a, b) => a.start - b.start)) {
    const last = merged.at(-1);
    if (last && start <= last.end) {
      last.end = Math.max(last.end, end);
    } else {
      merged.push({ start, end });
    }
  }
  const segments = [];
  let cursor = 0;
  for (const { start, end } of merged) {
    if (start > cursor) segments.push({ text: text.slice(cursor, start), marked: false });
    segments.push({ text: text.slice(start, end), marked: true });
    cursor = end;
  }
  if (cursor < text.length) segments.push({ text: text.slice(cursor), marked: false });
  return segments;
}

/** One citation per line, "0 1" or "0 -> 1". Returns { citations } or { error }. */
export function parseCitations(input) {
  const citations = [];
  const lines = input.split('\n').map((line) => line.trim()).filter(Boolean);
  for (const [index, line] of lines.entries()) {
    const match = line.match(/^(\d+)(?:\s*(?:->|→)\s*|\s+)(\d+)$/);
    if (!match) {
      return { error: `Line ${index + 1}: write each citation as “from to”, for example “0 1”.` };
    }
    citations.push({ from: Number(match[1]), to: Number(match[2]) });
  }
  return { citations };
}

/** POSTs to `path` and keeps the latest result or error. */
function useRun(path) {
  const [state, setState] = useState({ result: null, error: null, busy: false });
  async function run(body) {
    setState((previous) => ({ ...previous, error: null, busy: true }));
    try {
      setState({ result: await request(path, { method: 'POST', body }), error: null, busy: false });
    } catch (err) {
      setState({ result: null, error: err.message, busy: false });
    }
  }
  const fail = (error) => setState({ result: null, error, busy: false });
  return [state, run, fail];
}

const percent = (value) => `${Math.round(value * 100)}%`;

function ErrorLine({ error }) {
  return error ? (
    <p className="form-error" role="alert">
      {error}
    </p>
  ) : null;
}

function PatternDemo() {
  const [text, setText] = useState(SAMPLE_TEXT);
  const [patterns, setPatterns] = useState(SAMPLE_PATTERNS);
  const [{ result, error, busy }, run, fail] = useRun('/api/texthack/pattern');
  const [searched, setSearched] = useState('');

  function handleSubmit(event) {
    event.preventDefault();
    const list = patterns.split('\n').filter((pattern) => pattern !== '');
    if (list.length === 0) {
      fail('Enter at least one pattern.');
      return;
    }
    setSearched(text);
    run({ text, patterns: list });
  }

  return (
    <section className="glass-panel section" aria-labelledby="pattern-title">
      <h2 id="pattern-title" className="section__title">
        Pattern search
      </h2>
      <p className="muted texthack-intro">
        One pattern runs KMP. Several run Aho-Corasick, which finds every pattern in a single pass. The longest
        repeated substring comes from the suffix array and its LCP array. Matching is
        case-sensitive.
      </p>
      <form className="texthack-form" onSubmit={handleSubmit}>
        <label htmlFor="pattern-text">Text</label>
        <textarea id="pattern-text" className="text-input" rows={2} value={text} onChange={(e) => setText(e.target.value)} />
        <label htmlFor="pattern-list">Patterns, one per line</label>
        <textarea
          id="pattern-list"
          className="text-input"
          rows={3}
          value={patterns}
          onChange={(e) => setPatterns(e.target.value)}
        />
        <ErrorLine error={error} />
        <button type="submit" className="btn btn--primary texthack-form__submit" disabled={busy}>
          {busy ? 'Searching…' : 'Find matches'}
        </button>
      </form>

      {result && (
        <div className="texthack-result" aria-live="polite">
          <p>
            <strong>{result.algorithm}</strong>: {result.matches.length}{' '}
            {result.matches.length === 1 ? 'match' : 'matches'}
          </p>
          <div className="doc-content texthack-text" data-testid="highlighted-text">
            {highlightSegments(searched, result.matches).map((segment, index) =>
              segment.marked ? <mark key={index}>{segment.text}</mark> : <span key={index}>{segment.text}</span>,
            )}
          </div>
          {result.matches.length > 0 && (
            <ol className="texthack-matches" aria-label="Matches">
              {result.matches.map((match, index) => (
                <li key={index}>
                  “{match.pattern}” at {match.start}–{match.end}
                </li>
              ))}
            </ol>
          )}
          <p className="muted">
            Longest repeated substring:{' '}
            {result.longestRepeated ? <code>“{result.longestRepeated}”</code> : 'none (no substring occurs twice)'}
          </p>
        </div>
      )}
    </section>
  );
}

function AlignmentBlock({ title, alignment }) {
  return (
    <div className="texthack-alignment">
      <h3>{title}</h3>
      <pre aria-label={`${title} alignment`}>
        {alignment.alignedFirst}
        {'\n'}
        {alignment.alignedSecond}
      </pre>
      <p className="muted">
        Score {alignment.score} · identity {percent(alignment.identity)} · first {alignment.firstStart}–
        {alignment.firstEnd}, second {alignment.secondStart}–{alignment.secondEnd}
      </p>
    </div>
  );
}

function SimilarityDemo() {
  const [first, setFirst] = useState('recieve the report');
  const [second, setSecond] = useState('receive the reports');
  const [{ result, error, busy }, run] = useRun('/api/texthack/similarity');

  function handleSubmit(event) {
    event.preventDefault();
    run({ first, second });
  }

  return (
    <section className="glass-panel section" aria-labelledby="similarity-title">
      <h2 id="similarity-title" className="section__title">
        Similarity and alignment
      </h2>
      <p className="muted texthack-intro">
        Edit distance by Levenshtein and Damerau-Levenshtein, which counts a swapped pair as one edit. Needleman-Wunsch
        aligns the whole of both texts; Smith-Waterman finds the best matching region.
      </p>
      <form className="texthack-form" onSubmit={handleSubmit}>
        <div className="form-row">
          <div>
            <label htmlFor="similarity-first">First text</label>
            <input
              id="similarity-first"
              className="text-input"
              maxLength={1000}
              value={first}
              onChange={(e) => setFirst(e.target.value)}
            />
          </div>
          <div>
            <label htmlFor="similarity-second">Second text</label>
            <input
              id="similarity-second"
              className="text-input"
              maxLength={1000}
              value={second}
              onChange={(e) => setSecond(e.target.value)}
            />
          </div>
        </div>
        <ErrorLine error={error} />
        <button type="submit" className="btn btn--primary texthack-form__submit" disabled={busy}>
          {busy ? 'Comparing…' : 'Compare'}
        </button>
      </form>

      {result && (
        <div className="texthack-result" aria-live="polite">
          <dl className="detail-list texthack-facts">
            <div>
              <dt>Levenshtein</dt>
              <dd>{result.levenshteinDistance} edits</dd>
            </div>
            <div>
              <dt>Damerau</dt>
              <dd>{result.damerauDistance} edits</dd>
            </div>
            <div>
              <dt>Similarity</dt>
              <dd>{percent(result.similarity)}</dd>
            </div>
          </dl>
          <AlignmentBlock title="Global" alignment={result.global} />
          <AlignmentBlock title="Local" alignment={result.local} />
        </div>
      )}
    </section>
  );
}

function CitationDemo() {
  const [documents, setDocuments] = useState('6');
  const [source, setSource] = useState('0');
  const [sink, setSink] = useState('5');
  const [citations, setCitations] = useState(SAMPLE_CITATIONS);
  const [{ result, error, busy }, run, fail] = useRun('/api/texthack/citations');

  function handleSubmit(event) {
    event.preventDefault();
    const parsed = parseCitations(citations);
    if (parsed.error) {
      fail(parsed.error);
      return;
    }
    run({ documents: Number(documents), source: Number(source), sink: Number(sink), citations: parsed.citations });
  }

  const numberField = (id, label, value, setValue) => (
    <div>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        className="text-input"
        type="number"
        min={0}
        max={100}
        value={value}
        onChange={(e) => setValue(e.target.value)}
      />
    </div>
  );

  return (
    <section className="glass-panel section" aria-labelledby="citation-title">
      <h2 id="citation-title" className="section__title">
        Citation flow
      </h2>
      <p className="muted texthack-intro">
        Documents are numbered from 0, and each citation points from one document to another. Dinic’s maximum flow
        counts the separate citation chains from source to sink. Edmonds-Karp’s minimum cut shows the citations every
        chain depends on.
      </p>
      <form className="texthack-form" onSubmit={handleSubmit}>
        <div className="form-row texthack-numbers">
          {numberField('citation-documents', 'Documents', documents, setDocuments)}
          {numberField('citation-source', 'Source', source, setSource)}
          {numberField('citation-sink', 'Sink', sink, setSink)}
        </div>
        <label htmlFor="citation-list">Citations, one “from to” per line</label>
        <textarea
          id="citation-list"
          className="text-input"
          rows={5}
          value={citations}
          onChange={(e) => setCitations(e.target.value)}
        />
        <ErrorLine error={error} />
        <button type="submit" className="btn btn--primary texthack-form__submit" disabled={busy}>
          {busy ? 'Analysing…' : 'Analyse'}
        </button>
      </form>

      {result && (
        <div className="texthack-result" aria-live="polite">
          <p>
            <strong>Influence {result.influence}</strong>: {result.influence} separate citation{' '}
            {result.influence === 1 ? 'chain' : 'chains'} from {source} to {sink}.
          </p>
          {result.bottleneck.length > 0 ? (
            <>
              <p className="muted">Bottleneck citations (the minimum cut):</p>
              <ul className="texthack-matches" aria-label="Bottleneck citations">
                {result.bottleneck.map((citation, index) => (
                  <li key={index}>
                    {citation.from} → {citation.to}
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <p className="muted">No citation chain reaches the sink.</p>
          )}
          <p className="muted">Source side of the cut: {result.sourceSide.join(', ')}</p>
        </div>
      )}
    </section>
  );
}

function ComplexityTable() {
  const { status, data, error } = useApi('/api/texthack/complexity');
  return (
    <section className="glass-panel section" aria-labelledby="complexity-title">
      <h2 id="complexity-title" className="section__title">
        Complexity
      </h2>
      {status === 'error' && <ErrorLine error={error.message} />}
      {status === 'loading' && !data && <p className="muted">Loading…</p>}
      {data && (
        <div className="table-scroll">
          <table className="doc-table">
            <thead>
              <tr>
                <th scope="col">Algorithm</th>
                <th scope="col">Category</th>
                <th scope="col">Time</th>
                <th scope="col">Space</th>
                <th scope="col">Note</th>
              </tr>
            </thead>
            <tbody>
              {data.map((entry) => (
                <tr key={entry.name}>
                  <td>{entry.name}</td>
                  <td>{entry.category}</td>
                  <td>
                    <code>{entry.time}</code>
                  </td>
                  <td>
                    <code>{entry.space}</code>
                  </td>
                  <td className="muted">{entry.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

const TOOLS = [
  { id: 'pattern', label: 'Pattern search', Panel: PatternDemo },
  { id: 'similarity', label: 'Similarity', Panel: SimilarityDemo },
  { id: 'citations', label: 'Citation flow', Panel: CitationDemo },
  { id: 'complexity', label: 'Complexity', Panel: ComplexityTable },
];

export default function TextHackPage() {
  const [active, setActive] = useState('pattern');
  const tabs = useRef([]);

  // Arrow keys move between tabs, as the ARIA tabs pattern expects.
  function onKeyDown(event, index) {
    const step = { ArrowRight: 1, ArrowLeft: -1 }[event.key];
    if (!step) return;
    event.preventDefault();
    const next = (index + step + TOOLS.length) % TOOLS.length;
    setActive(TOOLS[next].id);
    tabs.current[next]?.focus();
  }

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">TextHack workbench</h1>
        <p className="page-subtitle">
          Run the DSA-3 text algorithms on your own input. The same engine scores keyword and fuzzy search.
        </p>
      </header>

      <div className="tabs" role="tablist" aria-label="TextHack tools">
        {TOOLS.map(({ id, label }, index) => (
          <button
            key={id}
            ref={(element) => {
              tabs.current[index] = element;
            }}
            type="button"
            role="tab"
            id={`tool-tab-${id}`}
            aria-controls={`tool-panel-${id}`}
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

      {/* Panels stay mounted while hidden, so inputs and results survive switching tabs. */}
      {TOOLS.map(({ id, Panel }) => (
        <div key={id} role="tabpanel" id={`tool-panel-${id}`} aria-labelledby={`tool-tab-${id}`} hidden={active !== id}>
          <Panel />
        </div>
      ))}
    </div>
  );
}
