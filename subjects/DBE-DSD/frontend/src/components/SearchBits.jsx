import { Link } from 'react-router';
import { MATCH_LABELS, searchMode } from '../pages/dashboardData.js';
import { CategoryTag, StatusPill } from './DocumentBits.jsx';
import { LayersIcon, SparkleIcon, TypeIcon } from './icons.jsx';

const SEARCH_MODES = [
  { id: 'keyword', title: 'Keyword', text: 'Exact & fuzzy match', Icon: TypeIcon },
  { id: 'semantic', title: 'Semantic', text: 'AI-powered meaning', Icon: SparkleIcon },
  { id: 'hybrid', title: 'Hybrid', text: 'Best of both worlds', Icon: LayersIcon },
];

/**
 * The backend chooses the mode from what it can run (a query always runs
 * keyword search; semantic runs when embedding is available). These chips report
 * which mode the last search used; they are not a selector.
 */
export function SearchModeChips({ sources }) {
  const active = sources ? searchMode(sources) : null;
  return (
    <ul className="mode-chips" aria-label="Search modes">
      {SEARCH_MODES.map(({ id, title, text, Icon }) => (
        <li key={id} className={`mode-chip${active === id ? ' is-active' : ''}`}>
          <span className="mode-chip__icon">
            <Icon size={18} />
          </span>
          <span>
            <strong>{title}</strong>
            <span className="mode-chip__text">{text}</span>
          </span>
        </li>
      ))}
    </ul>
  );
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
