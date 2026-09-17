import { useEffect, useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router';
import { useApi } from '../api/useApi.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { can } from '../auth/roles.js';
import { CategoryTag, FileBadge, StatusPill } from '../components/DocumentBits.jsx';
import { SearchIcon, UploadIcon } from '../components/icons.jsx';
import { relativeTime, STATUS_LABELS } from './dashboardData.js';

export const REPOSITORY_PAGE_SIZE = 10;

/** GET /api/documents/page for the given filters, with parameters in a fixed order. */
export function repositoryPath({ page, category, status, q }) {
  const params = new URLSearchParams({ page: String(page), size: String(REPOSITORY_PAGE_SIZE) });
  if (category) params.set('category', category);
  if (status) params.set('status', status);
  if (q) params.set('q', q);
  return `/api/documents/page?${params}`;
}

export default function RepositoryPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { profile } = useAuth();
  const notice = useLocation().state?.notice;
  // Filters live in the URL, so reload, back and shared links keep the view.
  const filters = {
    page: Math.max(0, Number.parseInt(searchParams.get('page') ?? '0', 10) || 0),
    category: searchParams.get('category') ?? '',
    status: searchParams.get('status') ?? '',
    q: searchParams.get('q') ?? '',
  };
  const [titleDraft, setTitleDraft] = useState(filters.q);
  // Keep the box in step when the URL changes underneath it (back, clear, links).
  useEffect(() => setTitleDraft(filters.q), [filters.q]);

  const documents = useApi(repositoryPath(filters));
  const categories = useApi('/api/categories');

  function update(changes) {
    const next = { ...filters, page: 0, ...changes };
    const params = {};
    for (const [key, value] of Object.entries(next)) {
      if (value !== '' && !(key === 'page' && value === 0)) {
        params[key] = String(value);
      }
    }
    setSearchParams(params);
  }

  const hasFilters = Boolean(filters.category || filters.status || filters.q);
  const page = documents.data;

  return (
    <div className="page">
      <header className="page-header page-header--with-action">
        <div>
          <h1 className="page-title">Documents</h1>
          <p className="page-subtitle">Every document you can read, newest change first.</p>
        </div>
        {can(profile, 'DOCUMENT_CREATE') && (
          <Link className="btn btn--primary" to="/upload">
            <UploadIcon size={16} />
            Upload document
          </Link>
        )}
      </header>

      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}

      <section className="glass-panel section">
        <div className="filter-bar">
          <form
            className="filter-bar__search"
            role="search"
            onSubmit={(event) => {
              event.preventDefault();
              update({ q: titleDraft.trim() });
            }}
          >
            <SearchIcon className="filter-bar__icon" />
            <input
              type="search"
              aria-label="Filter by title or description"
              placeholder="Filter by title or description"
              value={titleDraft}
              onChange={(e) => setTitleDraft(e.target.value)}
            />
            <button type="submit" className="btn btn--primary btn--small">
              Apply
            </button>
          </form>

          <select
            aria-label="Category"
            className="select"
            value={filters.category}
            onChange={(e) => update({ category: e.target.value })}
          >
            <option value="">All categories</option>
            {(categories.data ?? []).map((category) => (
              <option key={category.id} value={category.name}>
                {category.name}
              </option>
            ))}
          </select>

          <select
            aria-label="Status"
            className="select"
            value={filters.status}
            onChange={(e) => update({ status: e.target.value })}
          >
            <option value="">All statuses</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>

          {hasFilters && (
            <button
              type="button"
              className="btn btn--outline btn--small"
              onClick={() => {
                setTitleDraft('');
                setSearchParams({});
              }}
            >
              Clear filters
            </button>
          )}
        </div>

        {documents.status === 'error' && (
          <p className="form-error" role="alert">
            {documents.error.message}
          </p>
        )}

        {!page && documents.status === 'loading' && <p className="muted">Loading documents…</p>}

        {page && page.items.length === 0 && (
          <p className="empty-state">
            {hasFilters ? 'No documents match these filters.' : 'You don’t have access to any documents yet.'}
          </p>
        )}

        {page && page.items.length > 0 && (
          <>
            <div className="table-scroll" aria-busy={documents.status === 'loading'}>
              <table className="doc-table">
                <thead>
                  <tr>
                    <th scope="col">Title</th>
                    <th scope="col">Category</th>
                    <th scope="col">Status</th>
                    <th scope="col">Owner</th>
                    <th scope="col">Updated</th>
                  </tr>
                </thead>
                <tbody>
                  {page.items.map((doc) => (
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
                      <td className="muted">{doc.owner ?? '—'}</td>
                      <td className="muted">{relativeTime(doc.updatedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <nav className="pagination" aria-label="Pagination">
              <span className="muted">
                {page.totalItems} {page.totalItems === 1 ? 'document' : 'documents'} · Page {page.page + 1} of{' '}
                {Math.max(page.totalPages, 1)}
              </span>
              <span className="pagination__buttons">
                <button
                  type="button"
                  className="btn btn--outline btn--small"
                  disabled={page.page === 0}
                  onClick={() => update({ page: page.page - 1 })}
                >
                  Previous
                </button>
                <button
                  type="button"
                  className="btn btn--outline btn--small"
                  disabled={page.page + 1 >= page.totalPages}
                  onClick={() => update({ page: page.page + 1 })}
                >
                  Next
                </button>
              </span>
            </nav>
          </>
        )}
      </section>
    </div>
  );
}
