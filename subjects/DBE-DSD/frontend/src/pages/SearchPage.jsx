import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { request } from '../api/client.js';
import { useApi } from '../api/useApi.js';
import {
  KeywordFallbackNotice,
  modeParam,
  parseSearchMode,
  SearchHitList,
  SearchModePicker,
} from '../components/SearchBits.jsx';
import { SearchIcon, SlidersIcon } from '../components/icons.jsx';
import { STATUS_LABELS } from './dashboardData.js';

export const SEARCH_PAGE_SIZE = 10;

export default function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  // The query, mode, filters and page live in the URL, so a search can be
  // shared, reloaded or reached with the back button.
  const q = (searchParams.get('q') ?? '').trim();
  const mode = parseSearchMode(searchParams.get('mode'));
  const category = searchParams.get('category') ?? '';
  const status = searchParams.get('status') ?? '';
  const page = Math.max(0, Number.parseInt(searchParams.get('page') ?? '0', 10) || 0);

  const [draft, setDraft] = useState(q);
  useEffect(() => setDraft(q), [q]);
  const activeFilters = [category, status].filter(Boolean).length;
  const [filtersOpen, setFiltersOpen] = useState(activeFilters > 0);

  const categories = useApi('/api/categories');
  const [result, setResult] = useState({ status: 'idle', data: null, error: null });

  useEffect(() => {
    if (!q) {
      setResult({ status: 'idle', data: null, error: null });
      return undefined;
    }
    let active = true;
    // Keep the previous results on screen while the next page or filter loads.
    setResult((previous) => ({ status: 'loading', data: previous.data, error: null }));
    request('/api/search', {
      method: 'POST',
      body: {
        query: q,
        mode: modeParam(mode),
        category: category || undefined,
        status: status || undefined,
        page,
        size: SEARCH_PAGE_SIZE,
      },
    }).then(
      (data) => active && setResult({ status: 'ready', data, error: null }),
      (error) => active && setResult({ status: 'error', data: null, error }),
    );
    return () => {
      active = false;
    };
  }, [q, mode, category, status, page]);

  function update(changes) {
    const next = { q, category, status, mode, page: 0, ...changes };
    if (next.mode === 'hybrid') {
      next.mode = '';
    }
    const params = {};
    for (const [key, value] of Object.entries(next)) {
      if (value !== '' && !(key === 'page' && value === 0)) {
        params[key] = String(value);
      }
    }
    setSearchParams(params);
  }

  const data = result.data;
  const totalPages = data ? Math.ceil(data.totalHits / SEARCH_PAGE_SIZE) : 0;

  return (
    <div className="page">
      <header className="page-header">
        <p className="eyebrow eyebrow--spaced">Search</p>
        <h1 className="page-title">Search the knowledge base</h1>
        <p className="muted">Keyword, fuzzy and semantic search across every document you can read.</p>
      </header>

      <section className="search" aria-label="Search">
        <form
          className="search-bar"
          role="search"
          onSubmit={(event) => {
            event.preventDefault();
            update({ q: draft.trim() });
          }}
        >
          <SearchIcon className="search-bar__icon" />
          <input
            type="search"
            aria-label="Search documents"
            placeholder="Search documents, ask a question, or enter keywords..."
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
          />
          <button type="submit" className="btn btn--primary search-bar__submit">
            Search
          </button>
        </form>

        <div className="search-toolbar">
          <SearchModePicker value={mode} onChange={(next) => update({ mode: next })} />
          <button
            type="button"
            className="btn btn--outline btn--small"
            aria-expanded={filtersOpen}
            aria-controls="search-filters"
            onClick={() => setFiltersOpen((open) => !open)}
          >
            <SlidersIcon size={16} />
            Filters{activeFilters > 0 && ` (${activeFilters})`}
          </button>
        </div>

        <div id="search-filters" className="filter-bar filter-bar--compact" hidden={!filtersOpen}>
          <select
            aria-label="Category"
            className="select"
            value={category}
            onChange={(e) => update({ category: e.target.value })}
          >
            <option value="">All categories</option>
            {(categories.data ?? []).map((item) => (
              <option key={item.id} value={item.name}>
                {item.name}
              </option>
            ))}
          </select>
          <select
            aria-label="Status"
            className="select"
            value={status}
            onChange={(e) => update({ status: e.target.value })}
          >
            <option value="">All statuses</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </div>
      </section>

      {!q && (
        <section className="glass-panel section">
          <p className="empty-state">
            Enter a query to search. Typos are tolerated, word order does not matter, and when semantic search is
            available, documents with the same meaning match too.
          </p>
        </section>
      )}

      {q && (
        <section className="glass-panel section" aria-live="polite" aria-busy={result.status === 'loading'}>
          <h2 className="section__title">
            {result.status === 'error' && 'Search failed'}
            {result.status === 'loading' && !data && <>Searching for “{q}”…</>}
            {data && (
              <>
                {data.totalHits} {data.totalHits === 1 ? 'result' : 'results'} for “{q}”
              </>
            )}
          </h2>

          {result.status === 'error' && (
            <p className="form-error" role="alert">
              {result.error.message}
            </p>
          )}

          {data && <KeywordFallbackNotice mode={mode} sources={data.sources} />}

          {data && data.hits.length === 0 && (
            <p className="empty-state">
              {page > 0 ? 'No results on this page.' : 'No documents you can access match this search.'}
            </p>
          )}

          {data && data.hits.length > 0 && <SearchHitList hits={data.hits} detailed />}

          {data && totalPages > 1 && (
            <nav className="pagination" aria-label="Pagination">
              <span className="muted">
                Page {page + 1} of {totalPages}
              </span>
              <span className="pagination__buttons">
                <button
                  type="button"
                  className="btn btn--outline btn--small"
                  disabled={page === 0}
                  onClick={() => update({ page: page - 1 })}
                >
                  Previous
                </button>
                <button
                  type="button"
                  className="btn btn--outline btn--small"
                  disabled={page + 1 >= totalPages}
                  onClick={() => update({ page: page + 1 })}
                >
                  Next
                </button>
              </span>
            </nav>
          )}
        </section>
      )}
    </div>
  );
}
