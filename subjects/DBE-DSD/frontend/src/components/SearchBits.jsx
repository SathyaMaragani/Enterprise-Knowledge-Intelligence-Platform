import { Link } from 'react-router';
import { MATCH_LABELS } from '../pages/dashboardData.js';
import { CategoryTag, StatusPill } from './DocumentBits.jsx';
import { LayersIcon, SparkleIcon, TypeIcon, WavesIcon } from './icons.jsx';

export const SEARCH_MODES = [
  { id: 'hybrid', title: 'Hybrid', text: 'Matches meaning and keywords together.', Icon: LayersIcon },
  { id: 'semantic', title: 'Semantic', text: 'Matches by meaning, even without shared words.', Icon: SparkleIcon },
  { id: 'keyword', title: 'Keyword', text: 'Matches the exact words only.', Icon: TypeIcon },
  { id: 'fuzzy', title: 'Fuzzy', text: 'Matches words even when they are misspelled.', Icon: WavesIcon },
];

/** Display name for a mode as the API reports it (`FUZZY`), including the legacy `TEXTHACK`. */
export function modeLabel(apiMode) {
  const mode = SEARCH_MODES.find(({ id }) => id === apiMode?.toLowerCase());
  return mode ? mode.title : apiMode === 'TEXTHACK' ? 'TextHack' : apiMode;
}

/** A mode from untrusted input (the URL), falling back to hybrid. */
export function parseSearchMode(value) {
  return SEARCH_MODES.some(({ id }) => id === value) ? value : 'hybrid';
}

/** The API's `mode` value. Hybrid is the backend default, so it is left out. */
export function modeParam(mode) {
  return mode === 'hybrid' ? undefined : mode.toUpperCase();
}

/** A segmented radio control choosing how the next search runs, with the chosen mode explained. */
export function SearchModePicker({ value, onChange }) {
  const active = SEARCH_MODES.find(({ id }) => id === value);
  return (
    <div className="mode-picker">
      <fieldset className="segmented">
        <legend className="visually-hidden">Search mode</legend>
        {SEARCH_MODES.map(({ id, title, Icon }) => (
          <label key={id} className={`segmented__option${value === id ? ' is-active' : ''}`}>
            <input
              type="radio"
              className="visually-hidden"
              name="search-mode"
              value={id}
              checked={value === id}
              onChange={() => onChange(id)}
            />
            <Icon size={15} />
            {title}
          </label>
        ))}
      </fieldset>
      {active && <p className="mode-picker__hint">{active.text}</p>}
    </div>
  );
}

/** Hybrid quietly degrades to keywords when semantic search cannot run; say so. */
export function KeywordFallbackNotice({ mode, sources }) {
  if (mode !== 'hybrid' || !sources || sources.includes('VECTOR')) {
    return null;
  }
  return <p className="notice">Semantic search is unavailable right now, so these results match keywords only.</p>;
}

const percent = (value) => `${Math.round((value ?? 0) * 100)}%`;

/** Ranked hits, each linking to the document viewer. `detailed` adds per-signal scores. */
export function SearchHitList({ hits, detailed = false }) {
  return (
    <ol className="hit-list">
      {hits.map((hit) => (
        <li key={hit.documentId} className="hit">
          <div className="hit__body">
            <Link className="hit__title doc-link" to={`/documents/${hit.documentId}`}>
              {hit.title}
            </Link>
            {hit.description && <p className="hit__description">{hit.description}</p>}
            <p className="hit__meta">
              {hit.category && <CategoryTag category={hit.category} />}
              {detailed && hit.status && <StatusPill status={hit.status} />}
              {(hit.matchedBy ?? []).map((signal) => (
                <span key={signal} className={`match match--${signal.toLowerCase()}`}>
                  {MATCH_LABELS[signal] ?? signal}
                </span>
              ))}
            </p>
            {detailed && (
              <p className="hit__details">
                {hit.owner && <span>Owner: {hit.owner}</span>}
                <span>Keyword: {hit.keywordScore == null ? '—' : percent(hit.keywordScore)}</span>
                <span>Semantic: {hit.vectorScore == null ? '—' : hit.vectorScore.toFixed(2)}</span>
                {hit.chunkId && <span>Best chunk: {hit.chunkId}</span>}
              </p>
            )}
          </div>
          <div className="hit__score" title="Relevance">
            <span>{percent(hit.score)}</span>
            <span className="score-bar">
              <span style={{ width: percent(hit.score) }} />
            </span>
          </div>
        </li>
      ))}
    </ol>
  );
}
