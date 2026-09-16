import { useRef, useState } from 'react';
import { Link } from 'react-router';
import { request } from '../api/client.js';
import { useApi } from '../api/useApi.js';
import { CategoryTag, FileBadge, StatusPill } from '../components/DocumentBits.jsx';
import GlassDocs from '../components/GlassDocs.jsx';
import Mountains from '../components/Mountains.jsx';
import ThreeBackdrop from '../components/ThreeBackdrop.jsx';
import {
  AlertIcon,
  BarChartIcon,
  CheckCircleIcon,
  DatabaseIcon,
  FileTextIcon,
  LayersIcon,
  PencilIcon,
  PlusIcon,
  SearchIcon,
  SparkleIcon,
  TagIcon,
  TypeIcon,
  UploadIcon,
  XIcon,
} from '../components/icons.jsx';
import {
  categoryNames,
  MATCH_LABELS,
  recentActivity,
  recentDocuments,
  relativeTime,
  searchMode,
  summarize,
} from './dashboardData.js';

const SEARCH_MODES = [
  { id: 'keyword', title: 'Keyword', text: 'Exact & fuzzy match', Icon: TypeIcon },
  { id: 'semantic', title: 'Semantic', text: 'AI-powered meaning', Icon: SparkleIcon },
  { id: 'hybrid', title: 'Hybrid', text: 'Best of both worlds', Icon: LayersIcon },
];

export default function DashboardPage() {
  const documents = useApi('/api/documents');
  const collection = useApi('/api/search/vector/collection-info');
  const searchInputRef = useRef(null);

  const docs = documents.status === 'ready' ? documents.data ?? [] : [];

  return (
    <div className="dashboard">
      <div className="dashboard__main">
        <section className="hero">
          <div className="hero__art">
            <ThreeBackdrop scene="nebula" className="hero__three" />
            <GlassDocs />
          </div>
          <div className="hero__copy">
            <p className="eyebrow eyebrow--spaced">Enterprise Intelligence Platform</p>
            <h1 className="hero-title hero-title--dashboard">
              Find what <span className="gradient-text">matters</span>
            </h1>
            <p className="hero-lead hero-lead--muted">
              Search across documents, knowledge and insights — powered by hybrid AI search.
            </p>
          </div>
        </section>

        <SearchPanel categories={categoryNames(docs)} inputRef={searchInputRef} />

        <section className="glass-panel section" aria-labelledby="quick-actions-title">
          <h2 id="quick-actions-title" className="section__title">
            Quick Actions
          </h2>
          <div className="quick-actions">
            <QuickAction tone="blue" Icon={UploadIcon} title="Upload Document" />
            <QuickAction
              tone="violet"
              Icon={SearchIcon}
              title="Advanced Search"
              text="Refine your results"
              onClick={() => searchInputRef.current?.focus()}
            />
            <QuickAction tone="green" Icon={BarChartIcon} title="View Analytics" />
            <QuickAction tone="amber" Icon={TagIcon} title="Manage Categories" />
          </div>
        </section>

        <RecentDocuments state={documents} />
      </div>

      <aside className="dashboard__aside">
        <ul className="deco-words" aria-hidden="true">
          <li>Documents</li>
          <li>People</li>
          <li>Knowledge</li>
          <li>Possibilities</li>
          <li className="deco-words__accent">Connected</li>
        </ul>

        <SystemOverview documents={documents} collection={collection} />
        <RecentActivity state={documents} />

        <figure className="scenic-card aside__card">
          <Mountains className="scenic-card__art" />
          <blockquote>A more connected and informed tomorrow.</blockquote>
          <figcaption>EIP</figcaption>
        </figure>
      </aside>
    </div>
  );
}

function SearchPanel({ categories, inputRef }) {
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('');
  const [search, setSearch] = useState({ status: 'idle' });

  async function handleSubmit(event) {
    event.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) {
      inputRef.current?.focus();
      return;
    }
    setSearch({ status: 'loading', query: trimmed });
    try {
      const data = await request('/api/search', {
        method: 'POST',
        body: { query: trimmed, category: category || undefined, page: 0, size: 10 },
      });
      setSearch({ status: 'ready', query: trimmed, data });
    } catch (error) {
      setSearch({ status: 'error', query: trimmed, error });
    }
  }

  const activeMode = search.status === 'ready' ? searchMode(search.data?.sources) : null;

  return (
    <section className="search" aria-label="Search">
      <form className="search-bar" role="search" onSubmit={handleSubmit}>
        <SearchIcon className="search-bar__icon" />
        <input
          ref={inputRef}
          type="search"
          aria-label="Search documents"
          placeholder="Search documents, ask a question, or enter keywords..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select
          aria-label="Category"
          className="search-bar__select"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
        >
          <option value="">All Categories</option>
          {categories.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
        <button type="submit" className="btn btn--primary search-bar__submit" disabled={search.status === 'loading'}>
          {search.status === 'loading' ? 'Searching…' : 'Search'}
        </button>
      </form>

      {/* The backend picks the mode from what it can run; these report which one did. */}
      <ul className="mode-chips" aria-label="Search modes">
        {SEARCH_MODES.map(({ id, title, text, Icon }) => (
          <li key={id} className={`mode-chip${activeMode === id ? ' is-active' : ''}`}>
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

      <SearchResults search={search} onClear={() => setSearch({ status: 'idle' })} />
    </section>
  );
}

function SearchResults({ search, onClear }) {
  if (search.status === 'idle') {
    return null;
  }

  return (
    <div className="glass-panel section search-results" aria-live="polite">
      <div className="section__header">
        <h2 className="section__title">
          {search.status === 'loading' && <>Searching for “{search.query}”…</>}
          {search.status === 'error' && <>Search failed</>}
          {search.status === 'ready' && (
            <>
              {search.data.totalHits} {search.data.totalHits === 1 ? 'result' : 'results'} for “{search.query}”
            </>
          )}
        </h2>
        <button type="button" className="icon-button" aria-label="Clear search results" onClick={onClear}>
          <XIcon size={18} />
        </button>
      </div>

      {search.status === 'error' && (
        <p className="form-error" role="alert">
          {search.error.message}
        </p>
      )}

      {search.status === 'ready' && search.data.hits.length === 0 && (
        <p className="muted">No documents you can access match this search.</p>
      )}

      {search.status === 'ready' && search.data.hits.length > 0 && (
        <ol className="hit-list">
          {search.data.hits.map((hit) => (
            <li key={hit.documentId} className="hit">
              <div className="hit__body">
                <Link className="hit__title doc-link" to={`/documents/${hit.documentId}`}>
                  {hit.title}
                </Link>
                {hit.description && <p className="hit__description">{hit.description}</p>}
                <p className="hit__meta">
                  {hit.category && <span className="tag">{hit.category}</span>}
                  {(hit.matchedBy ?? []).map((signal) => (
                    <span key={signal} className={`match match--${signal.toLowerCase()}`}>
                      {MATCH_LABELS[signal] ?? signal}
                    </span>
                  ))}
                </p>
              </div>
              <div className="hit__score" title="Relevance">
                <span>{Math.round((hit.score ?? 0) * 100)}%</span>
                <span className="score-bar">
                  <span style={{ width: `${Math.round((hit.score ?? 0) * 100)}%` }} />
                </span>
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}

function QuickAction({ tone, Icon, title, text, onClick }) {
  const available = Boolean(onClick);
  return (
    <button
      type="button"
      className={`quick-action quick-action--${tone}`}
      onClick={onClick}
      disabled={!available}
    >
      <span className={`icon-tile icon-tile--solid-${tone}`}>
        <Icon size={22} />
      </span>
      <span>
        <strong>{title}</strong>
        <span className="quick-action__text">{available ? text : 'Coming soon'}</span>
      </span>
    </button>
  );
}

function RecentDocuments({ state }) {
  return (
    <section className="glass-panel section" aria-labelledby="recent-documents-title">
      <div className="section__header">
        <h2 id="recent-documents-title" className="section__title">
          Recent Documents
        </h2>
        <Link className="view-all" to="/repository">
          View All →
        </Link>
      </div>

      {state.status === 'loading' && <p className="muted">Loading documents…</p>}
      {state.status === 'error' && (
        <p className="form-error" role="alert">
          {state.error.message}
        </p>
      )}
      {state.status === 'ready' && state.data.length === 0 && (
        <p className="muted">You don’t have access to any documents yet.</p>
      )}
      {state.status === 'ready' && state.data.length > 0 && (
        <div className="table-scroll">
          <table className="doc-table">
            <thead>
              <tr>
                <th scope="col">Title</th>
                <th scope="col">Category</th>
                <th scope="col">Status</th>
                <th scope="col">Updated</th>
              </tr>
            </thead>
            <tbody>
              {recentDocuments(state.data).map((doc) => (
                <tr key={doc.id}>
                  <td>
                    <div className="doc-title">
                      <FileBadge type={doc.documentType} />
                      <span>
                        <Link className="doc-title__name doc-link" to={`/documents/${doc.id}`}>
                          {doc.title}
                        </Link>
                        {doc.description && <span className="doc-title__description">{doc.description}</span>}
                      </span>
                    </div>
                  </td>
                  <td>
                    <CategoryTag category={doc.category} />
                  </td>
                  <td>
                    <StatusPill status={doc.status} />
                  </td>
                  <td className="muted">{relativeTime(doc.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function SystemOverview({ documents, collection }) {
  const ready = documents.status === 'ready';
  const summary = ready ? summarize(documents.data ?? []) : null;
  const show = (value) => (ready ? value : '—');
  const chunks = collection.status === 'ready' ? collection.data?.pointsCount ?? '—' : '—';

  const tiles = [
    { label: 'Documents', value: show(summary?.total), Icon: FileTextIcon, tone: 'blue' },
    { label: 'Categories', value: show(summary?.categories), Icon: TagIcon, tone: 'amber' },
    { label: 'Indexed', value: show(summary?.indexed), Icon: CheckCircleIcon, tone: 'green' },
    { label: 'Vector chunks', value: chunks, Icon: DatabaseIcon, tone: 'teal' },
  ];

  return (
    <section className="glass-panel section" aria-labelledby="overview-title">
      <h2 id="overview-title" className="section__title">
        System Overview
      </h2>
      <dl className="stat-grid">
        {tiles.map(({ label, value, Icon, tone }) => (
          <div key={label} className="stat">
            <span className={`stat__icon stat__icon--${tone}`}>
              <Icon size={20} />
            </span>
            <dt className="stat__label">{label}</dt>
            <dd className="stat__value">{value}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function RecentActivity({ state }) {
  return (
    <section className="glass-panel section" aria-labelledby="activity-title">
      <h2 id="activity-title" className="section__title">
        Recent Activity
      </h2>
      {state.status === 'loading' && <p className="muted">Loading…</p>}
      {state.status === 'error' && (
        <p className="muted">
          <AlertIcon size={16} /> Activity is unavailable.
        </p>
      )}
      {state.status === 'ready' && state.data.length === 0 && <p className="muted">No activity yet.</p>}
      {state.status === 'ready' && state.data.length > 0 && (
        <ul className="activity-list">
          {recentActivity(state.data).map((event) => (
            <li key={`${event.id}-${event.kind}`} className="activity">
              <span className={`activity__icon activity__icon--${event.kind}`}>
                {event.kind === 'updated' ? <PencilIcon size={16} /> : <PlusIcon size={16} />}
              </span>
              <span className="activity__text">
                <span>{event.kind === 'updated' ? 'Document updated' : 'Document added'}</span>
                <span className="activity__title">{event.title}</span>
              </span>
              <span className="activity__time">{relativeTime(event.at)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
