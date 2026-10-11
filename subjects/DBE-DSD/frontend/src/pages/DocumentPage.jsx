import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { download, request } from '../api/client.js';
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
import { AlertIcon, PencilIcon } from '../components/icons.jsx';
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
  const arrivalNotice = useLocation().state?.notice;
  const [editing, setEditing] = useState(false);
  const [savedNotice, setSavedNotice] = useState(null);
  const notice = savedNotice ?? arrivalNotice;

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

  if (!document.data && (document.status === 'loading' || document.status === 'idle')) {
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
        <div className="doc-header__actions">
          {doc.originalAvailable && <OriginalFile id={doc.id} mimeType={doc.source?.mimeType} />}
          {doc.canEdit && !editing && (
            <button
              type="button"
              className="btn btn--outline btn--small"
              onClick={() => {
                setSavedNotice(null);
                setEditing(true);
              }}
            >
              <PencilIcon size={15} /> Edit
            </button>
          )}
          {can(profile, 'DOCUMENT_DELETE') && <DeleteDocument id={doc.id} title={doc.title} />}
        </div>
      </header>

      {editing && (
        <EditDocument
          doc={doc}
          onCancel={() => setEditing(false)}
          onSaved={() => {
            setEditing(false);
            setSavedNotice('Changes saved.');
            document.reload();
          }}
        />
      )}

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
    <>
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
    </>
  );
}

/**
 * The file as it was uploaded. A link cannot carry the sign-in token, so the
 * file is fetched and handed to the browser: saved under its own name, or, for
 * a PDF, shown in a new tab. The tab is opened at the click itself, because a
 * tab opened after the download finishes is treated as a pop-up and blocked.
 */
function OriginalFile({ id, mimeType }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function fetchFile(tab) {
    setBusy(true);
    setError(null);
    try {
      const { blob, filename } = await download(`/api/documents/${id}/file`);
      const url = URL.createObjectURL(blob);
      if (tab) {
        tab.location.href = url;
      } else {
        const link = window.document.createElement('a');
        link.href = url;
        link.download = filename;
        link.click();
      }
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      tab?.close();
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      {mimeType === 'application/pdf' && (
        <button
          type="button"
          className="btn btn--outline btn--small"
          disabled={busy}
          onClick={() => fetchFile(window.open('', '_blank'))}
        >
          Open PDF
        </button>
      )}
      <button type="button" className="btn btn--outline btn--small" disabled={busy} onClick={() => fetchFile(null)}>
        {busy ? 'Fetching…' : 'Download'}
      </button>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </>
  );
}

/** Title, description and category, saved together; the text itself is not editable. */
function EditDocument({ doc, onSaved, onCancel }) {
  const categories = useApi('/api/categories');
  const [title, setTitle] = useState(doc.title ?? '');
  const [description, setDescription] = useState(doc.description ?? '');
  const [category, setCategory] = useState(doc.category ?? '');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    if (!title.trim()) {
      setError('Give the document a title.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await request(`/api/documents/${doc.id}`, {
        method: 'PATCH',
        // An uncategorised document stays so unless a category is chosen.
        body: { title, description, category: category || undefined },
      });
      onSaved();
    } catch (err) {
      setError(err.message);
      setSaving(false);
    }
  }

  return (
    <form className="glass-panel section edit-form" aria-labelledby="edit-title" onSubmit={handleSubmit} noValidate>
      <h2 id="edit-title" className="section__title">
        Edit details
      </h2>
      <label htmlFor="edit-doc-title">Title</label>
      <input
        id="edit-doc-title"
        className="text-input"
        maxLength={255}
        value={title}
        onChange={(e) => setTitle(e.target.value)}
      />
      <label htmlFor="edit-doc-description">Description</label>
      <textarea
        id="edit-doc-description"
        className="text-input"
        rows={3}
        maxLength={2000}
        value={description}
        onChange={(e) => setDescription(e.target.value)}
      />
      <label htmlFor="edit-doc-category">Category</label>
      <select
        id="edit-doc-category"
        className="select select--block"
        value={category}
        onChange={(e) => setCategory(e.target.value)}
      >
        {!doc.category && <option value="">No category</option>}
        {(categories.data ?? [{ id: 'current', name: doc.category }])
          .filter((item) => item.name)
          .map((item) => (
            <option key={item.id} value={item.name}>
              {item.name}
            </option>
          ))}
      </select>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="edit-form__actions">
        <button type="button" className="btn btn--outline" disabled={saving} onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="btn btn--primary" disabled={saving}>
          {saving ? 'Saving…' : 'Save changes'}
        </button>
      </div>
    </form>
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
