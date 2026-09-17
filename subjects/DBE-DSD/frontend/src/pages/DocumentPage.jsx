import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { request } from '../api/client.js';
import { useApi } from '../api/useApi.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { can } from '../auth/roles.js';
import AccessPanel from '../components/AccessPanel.jsx';
import {
  CategoryTag,
  FileBadge,
  fileTypeLabel,
  formatDateTime,
  humanize,
  StatusPill,
} from '../components/DocumentBits.jsx';
import { AlertIcon } from '../components/icons.jsx';
import { relativeTime } from './dashboardData.js';

/** A missing or forbidden document gets a message a user can act on. */
function errorMessage(error) {
  if (error.status === 403) {
    return 'You don’t have access to this document. Ask its owner or an administrator for access.';
  }
  if (error.status === 404) {
    return error.body?.error === 'DOCUMENT_CONTENT_NOT_FOUND'
      ? 'This document exists, but its content has not been stored yet.'
      : 'This document does not exist.';
  }
  return error.message;
}

function formatValue(value) {
  if (Array.isArray(value)) {
    return value.join(', ');
  }
  if (value !== null && typeof value === 'object') {
    return JSON.stringify(value);
  }
  return String(value);
}

export default function DocumentPage() {
  const { id } = useParams();
  const valid = /^\d+$/.test(id ?? '');
  const document = useApi(valid ? `/api/documents/${id}` : null);
  const { profile } = useAuth();
  const notice = useLocation().state?.notice;

  const backLink = (
    <Link className="back-link" to="/repository">
      ← Repository
    </Link>
  );

  if (!valid) {
    return (
      <div className="page">
        {backLink}
        <p className="form-error" role="alert">
          This document does not exist.
        </p>
      </div>
    );
  }

  if (document.status === 'loading' || document.status === 'idle') {
    return (
      <div className="page">
        {backLink}
        <p className="muted">Loading document…</p>
      </div>
    );
  }

  if (document.status === 'error') {
    return (
      <div className="page">
        {backLink}
        <p className="form-error" role="alert">
          <AlertIcon size={16} /> {errorMessage(document.error)}
        </p>
      </div>
    );
  }

  const doc = document.data;
  const content = doc.content ?? {};
  const chunks = [...(doc.chunks ?? [])].sort((a, b) => (a.position ?? 0) - (b.position ?? 0));
  const metadata = Object.entries(doc.metadata ?? {});

  return (
    <div className="page">
      {backLink}

      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}

      <header className="doc-header">
        <FileBadge type={doc.documentType} />
        <div className="doc-header__text">
          <h1 className="page-title">{doc.title}</h1>
          {doc.description && <p className="page-subtitle">{doc.description}</p>}
          <p className="doc-header__meta">
            <CategoryTag category={doc.category} />
            <StatusPill status={doc.status} />
            <span className="muted">Owner: {doc.owner ?? '—'}</span>
            <span className="muted" title={formatDateTime(doc.updatedAt)}>
              Updated {relativeTime(doc.updatedAt)}
            </span>
          </p>
        </div>
        {can(profile, 'DOCUMENT_DELETE') && <DeleteDocument id={doc.id} title={doc.title} />}
      </header>

      <div className="doc-layout">
        <div className="doc-layout__main">
          <section className="glass-panel section" aria-labelledby="content-title">
            <div className="section__header">
              <h2 id="content-title" className="section__title">
                Content
              </h2>
              <span className="muted doc-stats">
                {content.wordCount ?? '—'} words · {content.characterCount ?? '—'} characters
                {content.language ? ` · ${content.language}` : ''}
              </span>
            </div>
            {content.rawText ? (
              <div className="doc-content">{content.rawText}</div>
            ) : (
              <p className="muted">No text content was extracted.</p>
            )}
          </section>

          <section className="glass-panel section" aria-labelledby="chunks-title">
            <h2 id="chunks-title" className="section__title">
              Chunks <span className="section__count">{chunks.length}</span>
            </h2>
            {chunks.length === 0 ? (
              <p className="muted">This document has not been split into chunks.</p>
            ) : (
              <ol className="chunk-list">
                {chunks.map((chunk) => (
                  <li key={chunk.chunkId ?? chunk.position} className="chunk">
                    <p className="chunk__meta">
                      <span>#{(chunk.position ?? 0) + 1}</span>
                      {chunk.chunkId && <code>{chunk.chunkId}</code>}
                      {chunk.pageNumber != null && <span>Page {chunk.pageNumber}</span>}
                      {chunk.tokenCount != null && <span>{chunk.tokenCount} tokens</span>}
                    </p>
                    <p className="chunk__text">{chunk.text}</p>
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>

        <aside className="doc-layout__aside">
          {(can(profile, 'USER_MANAGE') || (profile && profile.username === doc.owner)) && (
            <AccessPanel documentId={doc.id} owner={doc.owner} />
          )}
          <DetailPanel
            title="Details"
            groups={[
              [
                ['File', doc.source?.filename],
                ['Type', doc.source?.mimeType && <span title={doc.source.mimeType}>{fileTypeLabel(doc.source.mimeType)}</span>],
                ['Storage', doc.source?.storageType],
                ['Location', doc.source?.storageReference && <code className="detail-code">{doc.source.storageReference}</code>],
              ],
              [
                ['Processing', humanize(doc.processing?.status)],
                ['Processed', doc.processing?.processedAt && formatDateTime(doc.processing.processedAt)],
                ['Extractor', doc.processing?.extractorVersion],
                ['Chunker', doc.processing?.chunkerVersion],
              ],
              [
                ['Version', doc.version?.number],
                ['Change', doc.version?.changeSummary],
                ['Created', formatDateTime(doc.createdAt)],
              ],
            ]}
          />
          {metadata.length > 0 && (
            <DetailPanel
              title="Metadata"
              groups={[metadata.map(([key, value]) => [humanize(key), formatValue(value)])]}
            />
          )}
          {(doc.references ?? []).length > 0 && (
            <section className="glass-panel section" aria-labelledby="references-title">
              <h2 id="references-title" className="section__title">
                References
              </h2>
              <ul className="reference-list">
                {doc.references.map((reference, index) => (
                  <li key={`${reference.reference}-${index}`}>
                    <strong>{reference.title}</strong>
                    <span className="muted">
                      {reference.type} · {reference.reference}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}
        </aside>
      </div>
    </div>
  );
}

/** Delete with a confirmation step; the backend removes vectors, content and metadata. */
function DeleteDocument({ id, title }) {
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState(null);

  async function confirmDelete() {
    setDeleting(true);
    setError(null);
    try {
      await request(`/api/documents/${id}`, { method: 'DELETE' });
      navigate('/repository', { state: { notice: `Deleted “${title}”.` } });
    } catch (err) {
      setError(err.message);
      setDeleting(false);
    }
  }

  return (
    <div className="doc-header__actions">
      {!confirming ? (
        <button type="button" className="btn btn--danger btn--small" onClick={() => setConfirming(true)}>
          Delete
        </button>
      ) : (
        <div className="confirm" role="group" aria-label="Confirm delete">
          <span>Delete permanently?</span>
          <button
            type="button"
            className="btn btn--outline btn--small"
            disabled={deleting}
            onClick={() => setConfirming(false)}
          >
            Cancel
          </button>
          <button type="button" className="btn btn--danger btn--small" disabled={deleting} onClick={confirmDelete}>
            {deleting ? 'Deleting…' : 'Delete permanently'}
          </button>
        </div>
      )}
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

/** Label and value rows, in groups separated by a rule. */
function DetailPanel({ title, groups }) {
  const id = `detail-${title.toLowerCase()}`;
  return (
    <section className="glass-panel section" aria-labelledby={id}>
      <h2 id={id} className="section__title">
        {title}
      </h2>
      {groups.map((rows, index) => (
        <dl key={index} className="detail-list">
          {rows.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value === null || value === undefined || value === '' ? '—' : value}</dd>
            </div>
          ))}
        </dl>
      ))}
    </section>
  );
}
