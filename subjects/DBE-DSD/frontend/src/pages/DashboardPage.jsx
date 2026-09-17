import { useRef, useState } from 'react';
import { Link } from 'react-router';
import { request } from '../api/client.js';
import { useApi } from '../api/useApi.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { can } from '../auth/roles.js';
import { CategoryTag, FileBadge, StatusPill } from '../components/DocumentBits.jsx';
import {
  KeywordFallbackNotice,
  modeLabel,
  modeParam,
  parseSearchMode,
  SearchHitList,
  SearchModePicker,
} from '../components/SearchBits.jsx';
import {
  AlertIcon,
  CheckCircleIcon,
  DatabaseIcon,
  FileTextIcon,
  PencilIcon,
  PlusIcon,
  SearchIcon,
  TagIcon,
  UploadIcon,
  XIcon,
} from '../components/icons.jsx';
import {
  categoryNames,
  greeting,
  recentActivity,
  recentDocuments,
  relativeTime,
  summarize,
} from './dashboardData.js';

export default function DashboardPage() {
  const documents = useApi('/api/documents');
  const collection = useApi('/api/search/vector/collection-info');
  const searchActivity = useApi('/api/search/history?limit=5');
  const { session, profile } = useAuth();
  const firstName = (profile?.fullName || session.username).split(' ')[0];

  const docs = documents.status === 'ready' ? documents.data ?? [] : [];

  return (
    <div className="page dashboard">
      <header className="page-header page-header--with-action">
        <div>
          <h1 className="page-title">
            {greeting()}, {firstName}
          </h1>
          <p className="page-subtitle">Search and explore the knowledge you have access to.</p>
        </div>
        {can(profile, 'DOCUMENT_CREATE') && (
          <Link className="btn btn--primary" to="/upload">
            <UploadIcon size={16} />
            Upload document
          </Link>
        )}
      </header>

      <SearchPanel categories={categoryNames(docs)} onSearched={searchActivity.reload} />

      <Overview documents={documents} collection={collection} />

      <div className="dashboard__grid">
        <RecentDocuments state={documents} />
        <div className="dashboard__side">
          <SearchActivity state={searchActivity} />
          <RecentActivity state={documents} />
        </div>
      </div>
    </div>
  );
}

function SearchPanel({ categories, onSearched }) {
  const inputRef = useRef(null);
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('');
  const [mode, setMode] = useState('hybrid');
  const [search, setSearch] = useState({ status: 'idle' });

  async function runSearch(trimmed, searchMode) {
    setSearch({ status: 'loading', query: trimmed, mode: searchMode });
    try {
      const data = await request('/api/search', {
        method: 'POST',
        body: { query: trimmed, mode: modeParam(searchMode), category: category || undefined, page: 0, size: 10 },
      });
      setSearch({ status: 'ready', query: trimmed, mode: searchMode, data });
      onSearched();
    } catch (error) {
      setSearch({ status: 'error', query: trimmed, mode: searchMode, error });
    }
  }

  function handleSubmit(event) {
    event.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) {
      inputRef.current?.focus();
      return;
    }
    runSearch(trimmed, mode);
  }

  function changeMode(next) {
    setMode(next);
    // Results on screen follow the chosen mode rather than going stale.
    if (search.status !== 'idle') {
      runSearch(search.query, next);
    }
  }

  return (
    <>
      <section className="glass-panel section search-card" aria-label="Search">
        <form className="search-bar" role="search" onSubmit={handleSubmit}>
          <SearchIcon className="search-bar__icon" size={18} />
          <input
            ref={inputRef}
            type="search"
            aria-label="Search documents"
            placeholder="Search documents, ask a question, or enter keywords…"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
          <select
            aria-label="Category"
            className="search-bar__select"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          >
            <option value="">All categories</option>
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
        <SearchModePicker value={mode} onChange={changeMode} />
      </section>

      <SearchResults search={search} category={category} onClear={() => setSearch({ status: 'idle' })} />
    </>
  );
}

function SearchResults({ search, category, onClear }) {
  if (search.status === 'idle') {
    return null;
  }
  const params = { q: search.query };
  if (category) params.category = category;
  if (search.mode !== 'hybrid') params.mode = search.mode;

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
        <span className="search-results__actions">
          <Link className="view-all" to={`/search?${new URLSearchParams(params)}`}>
            Open in search →
          </Link>
          <button type="button" className="icon-button" aria-label="Clear search results" onClick={onClear}>
            <XIcon size={18} />
          </button>
        </span>
      </div>

      {search.status === 'error' && (
        <p className="form-error" role="alert">
          {search.error.message}
        </p>
      )}

      {search.status === 'ready' && <KeywordFallbackNotice mode={search.mode} sources={search.data.sources} />}

      {search.status === 'ready' && search.data.hits.length === 0 && (
        <p className="empty-state">No documents you can access match this search.</p>
      )}

      {search.status === 'ready' && search.data.hits.length > 0 && <SearchHitList hits={search.data.hits} />}
    </div>
  );
}

function Overview({ documents, collection }) {
  const ready = documents.status === 'ready';
  const summary = ready ? summarize(documents.data ?? []) : null;
  const show = (value) => (ready ? value : '—');
  const chunks = collection.status === 'ready' ? collection.data?.pointsCount ?? '—' : '—';

  const tiles = [
    { label: 'Documents', value: show(summary?.total), hint: 'You can access', Icon: FileTextIcon },
    { label: 'Indexed', value: show(summary?.indexed), hint: 'Ready for semantic search', Icon: CheckCircleIcon },
    { label: 'Categories', value: show(summary?.categories), hint: 'Across your documents', Icon: TagIcon },
    { label: 'Vector chunks', value: chunks, hint: 'In the search index', Icon: DatabaseIcon },
  ];

  return (
    <section aria-label="Overview">
      <dl className="kpi-grid">
        {tiles.map(({ label, value, hint, Icon }) => (
          <div key={label} className="glass-panel kpi">
            <dt className="kpi__label">
              <Icon size={15} />
              {label}
            </dt>
            <dd className="kpi__value">{value}</dd>
            <dd className="kpi__hint">{hint}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

function RecentDocuments({ state }) {
  return (
    <section className="glass-panel section" aria-labelledby="recent-documents-title">
      <div className="section__header">
        <h2 id="recent-documents-title" className="section__title">
          Recent documents
        </h2>
        <Link className="view-all" to="/repository">
          View all →
        </Link>
      </div>

      {state.status === 'loading' && <p className="muted">Loading documents…</p>}
      {state.status === 'error' && (
        <p className="form-error" role="alert">
          {state.error.message}
        </p>
      )}
      {state.status === 'ready' && state.data.length === 0 && (
        <p className="empty-state">You don’t have access to any documents yet.</p>
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
                  <td className="muted nowrap">{relativeTime(doc.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function searchLink({ query, mode }) {
  const params = { q: query };
  const parsed = parseSearchMode(mode?.toLowerCase());
  if (parsed !== 'hybrid') params.mode = parsed;
  return `/search?${new URLSearchParams(params)}`;
}

/** The signed-in user's own recent searches; nobody else's are ever shown. */
function SearchActivity({ state }) {
  const entries = state.data;
  return (
    <section className="glass-panel section" aria-labelledby="search-activity-title">
      <h2 id="search-activity-title" className="section__title">
        Your recent searches
      </h2>
      {state.status === 'loading' && !entries && <p className="muted">Loading…</p>}
      {state.status === 'error' && (
        <p className="muted activity-unavailable">
          <AlertIcon size={16} /> Search activity is unavailable.
        </p>
      )}
      {entries && entries.length === 0 && <p className="muted">Your searches will show up here.</p>}
      {entries && entries.length > 0 && (
        <ul className="activity-list" aria-label="Your recent searches">
          {entries.map((entry) => (
            <li key={`${entry.mode}:${entry.query}`} className="activity">
              <span className="activity__icon activity__icon--search">
                <SearchIcon size={14} />
              </span>
              <span className="activity__text">
                <Link className="doc-link activity__query" to={searchLink(entry)}>
                  {entry.query}
                </Link>
                <span className="activity__title">
                  {modeLabel(entry.mode)} · {entry.resultCount} {entry.resultCount === 1 ? 'result' : 'results'}
                </span>
              </span>
              <span className="activity__time">{relativeTime(entry.searchedAt)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function RecentActivity({ state }) {
  return (
    <section className="glass-panel section" aria-labelledby="activity-title">
      <h2 id="activity-title" className="section__title">
        Document activity
      </h2>
      {state.status === 'loading' && <p className="muted">Loading…</p>}
      {state.status === 'error' && (
        <p className="muted activity-unavailable">
          <AlertIcon size={16} /> Activity is unavailable.
        </p>
      )}
      {state.status === 'ready' && state.data.length === 0 && <p className="muted">No activity yet.</p>}
      {state.status === 'ready' && state.data.length > 0 && (
        <ul className="activity-list">
          {recentActivity(state.data).map((event) => (
            <li key={`${event.id}-${event.kind}`} className="activity">
              <span className={`activity__icon activity__icon--${event.kind}`}>
                {event.kind === 'updated' ? <PencilIcon size={14} /> : <PlusIcon size={14} />}
              </span>
              <span className="activity__text">
                <span className="activity__query">{event.title}</span>
                <span className="activity__title">{event.kind === 'updated' ? 'Updated' : 'Added'}</span>
              </span>
              <span className="activity__time">{relativeTime(event.at)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
