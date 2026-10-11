import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { request } from '../api/client.js';
import { useApi } from '../api/useApi.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { can } from '../auth/roles.js';
import { UploadIcon } from '../components/icons.jsx';

// Mirrors the backend's limits so most mistakes are caught before uploading.
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
const ACCEPTED = ['.pdf', '.docx', '.txt', '.md', '.markdown'];
// ponytail: files in a batch go up one at a time, because the free server embeds
// each inside its request. A batch is capped so it finishes in minutes; raise
// the cap, or upload in parallel, when uploads move to a background job.
export const MAX_BULK_FILES = 20;

export function uploadProblem(file) {
  if (!file) {
    return 'Choose a file to upload.';
  }
  const name = file.name.toLowerCase();
  if (!ACCEPTED.some((extension) => name.endsWith(extension))) {
    return 'Only .pdf, .docx, .txt and .md files can be uploaded.';
  }
  if (file.size === 0) {
    return 'The file is empty.';
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return 'Files can be at most 10 MB.';
  }
  return null;
}

function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** One chosen file and where it stands: ready, invalid, uploading, done or failed. */
function toItem(file, index) {
  const problem = uploadProblem(file);
  return { key: `${index}:${file.name}:${file.size}`, file, status: problem ? 'invalid' : 'ready', problem };
}

function fields(file, { title, description, category, department }) {
  const form = new FormData();
  form.append('file', file);
  form.append('category', category);
  if (title?.trim()) form.append('title', title.trim());
  if (description.trim()) form.append('description', description.trim());
  if (department.trim()) form.append('department', department.trim());
  return form;
}

export default function UploadPage() {
  const { profile } = useAuth();
  const navigate = useNavigate();
  const categories = useApi('/api/categories');

  // Files dropped on the dashboard's upload button arrive already chosen.
  const location = useLocation();
  const arrived = location.state?.files ?? (location.state?.file ? [location.state.file] : []);
  const [items, setItems] = useState(() => arrived.slice(0, MAX_BULK_FILES).map(toItem));
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [department, setDepartment] = useState('');
  const [error, setError] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [dragging, setDragging] = useState(false);

  if (profile === undefined) {
    return (
      <div className="page">
        <p className="muted">Checking your permissions…</p>
      </div>
    );
  }

  if (profile === null) {
    return (
      <div className="page">
        <p className="form-error" role="alert">
          Your permissions could not be checked. Reload the page to try again.
        </p>
      </div>
    );
  }

  if (!can(profile, 'DOCUMENT_CREATE')) {
    return (
      <div className="page">
        <header className="page-header">
          <h1 className="page-title">Upload a document</h1>
        </header>
        <p className="form-error" role="alert">
          Your role cannot upload documents. Ask an administrator if you need to add one.
        </p>
      </div>
    );
  }

  // Several files make a batch: no title field, a status per file, and the page
  // stays here afterwards. It stays a batch even if files are then removed.
  const bulk = items.length > 1 || items.some((item) => item.status === 'done' || item.status === 'failed');
  const file = items[0]?.file ?? null;
  const suggestedTitle = file ? file.name.replace(/\.[^.]+$/, '') : '';
  const pending = items.filter((item) => item.status === 'ready' || item.status === 'failed');
  const done = items.filter((item) => item.status === 'done');
  const finished = bulk && !uploading && pending.length === 0 && done.length > 0;

  function choose(fileList) {
    const chosen = [...(fileList ?? [])];
    const kept = chosen.slice(0, MAX_BULK_FILES);
    setItems(kept.map(toItem));
    if (chosen.length > MAX_BULK_FILES) {
      setError(`Only the first ${MAX_BULK_FILES} files were kept. Upload the rest as another batch.`);
    } else {
      setError(kept.length === 1 ? uploadProblem(kept[0]) : null);
    }
  }

  const update = (key, change) =>
    setItems((current) => current.map((item) => (item.key === key ? { ...item, ...change } : item)));

  async function uploadOne() {
    const problem = uploadProblem(file) ?? (category ? null : 'Choose a category.');
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    setUploading(true);
    try {
      const result = await request('/api/documents', {
        method: 'POST',
        form: fields(file, { title, description, category, department }),
      });
      // Keyword search covers title and description; only embeddings reach the body text.
      const searchable = result.vectorsStored
        ? 'Search finds it by title, description and the meaning of its content.'
        : 'Search finds it by title and description; semantic search was not available, so its content is not searchable yet.';
      navigate(`/documents/${result.id}`, {
        state: { notice: `Uploaded “${result.title}” as ${result.chunkCount} chunks. ${searchable}` },
      });
    } catch (err) {
      setError(err.message);
      setUploading(false);
    }
  }

  /** One request per file, in order: a failure is recorded against its file and the rest go on. */
  async function uploadBatch() {
    if (!category) {
      setError('Choose a category.');
      return;
    }
    setError(null);
    setUploading(true);
    for (const item of pending) {
      update(item.key, { status: 'uploading', problem: null });
      try {
        const result = await request('/api/documents', {
          method: 'POST',
          form: fields(item.file, { description, category, department }),
        });
        update(item.key, { status: 'done', result });
      } catch (err) {
        update(item.key, { status: 'failed', problem: err.message });
        if (err.status === 401) {
          break; // signed out: the remaining files would only fail the same way
        }
      }
    }
    setUploading(false);
  }

  function handleSubmit(event) {
    event.preventDefault();
    if (bulk) {
      uploadBatch();
    } else {
      uploadOne();
    }
  }

  const uploadingIndex = items.filter((item) => item.status === 'done' || item.status === 'failed').length + 1;
  const batchSize = items.filter((item) => item.status !== 'invalid').length;
  let submitLabel = 'Upload document';
  if (bulk && pending.length === 0) {
    submitLabel = 'Nothing to upload';
  } else if (bulk) {
    submitLabel = pending.every((item) => item.status === 'failed')
      ? `Retry ${pending.length} failed ${pending.length === 1 ? 'upload' : 'uploads'}`
      : `Upload ${pending.length} ${pending.length === 1 ? 'document' : 'documents'}`;
  }
  if (uploading) {
    submitLabel = bulk ? `Uploading ${Math.min(uploadingIndex, batchSize)} of ${batchSize}…` : 'Uploading…';
  }

  let dropzoneText = 'Choose or drop PDF, DOCX, TXT or MD files';
  if (items.length === 1) {
    dropzoneText = `${file.name} · ${formatSize(file.size)}`;
  } else if (items.length > 1) {
    dropzoneText = `${items.length} files · ${formatSize(items.reduce((sum, item) => sum + item.file.size, 0))}`;
  }

  return (
    <div className="page page--narrow">
      <Link className="back-link" to="/repository">
        ← Repository
      </Link>
      <header className="page-header">
        <h1 className="page-title">Upload a document</h1>
        <p className="page-subtitle">
          PDF, Word, plain text or Markdown, up to 10 MB each. Choose several files to upload them together, up to{' '}
          {MAX_BULK_FILES} at a time. Extracted text is stored, split into chunks and, when semantic search is
          available, embedded so it can be found by meaning. Scanned PDFs need OCR and are not supported.
        </p>
      </header>

      <form className="glass-panel section upload-form" onSubmit={handleSubmit} noValidate>
        <label htmlFor="upload-file">File</label>
        <label
          className={`dropzone${dragging ? ' is-dragging' : ''}`}
          htmlFor="upload-file"
          onDragOver={(event) => {
            event.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            if (!uploading && event.dataTransfer.files?.length) {
              choose(event.dataTransfer.files);
            }
          }}
        >
          <UploadIcon size={28} />
          <span>{dropzoneText}</span>
        </label>
        <input
          id="upload-file"
          className="visually-hidden"
          type="file"
          multiple
          accept={ACCEPTED.join(',')}
          disabled={uploading}
          onChange={(e) => choose(e.target.files)}
        />

        {bulk ? (
          <>
            <ul className="upload-list" aria-label="Files in this batch">
              {items.map((item) => (
                <li key={item.key} className={`upload-list__item is-${item.status}`}>
                  <span className="upload-list__name">{item.file.name}</span>
                  <span className="upload-list__size">{formatSize(item.file.size)}</span>
                  <span className="upload-list__status">
                    {item.status === 'ready' && 'Ready'}
                    {item.status === 'uploading' && 'Uploading…'}
                    {item.status === 'invalid' && item.problem}
                    {item.status === 'failed' && `Failed: ${item.problem}`}
                    {item.status === 'done' && (
                      <Link className="doc-link" to={`/documents/${item.result.id}`}>
                        Uploaded · {item.result.chunkCount} {item.result.chunkCount === 1 ? 'chunk' : 'chunks'}
                      </Link>
                    )}
                  </span>
                  {!uploading && item.status !== 'done' && (
                    <button
                      type="button"
                      className="link-button"
                      aria-label={`Remove ${item.file.name}`}
                      onClick={() => setItems((current) => current.filter((other) => other.key !== item.key))}
                    >
                      Remove
                    </button>
                  )}
                </li>
              ))}
            </ul>
            <p className="hint">
              Each document is titled from its file name; titles can be edited afterwards. The fields below apply to
              every file.
            </p>
          </>
        ) : (
          <>
            <label htmlFor="upload-title">Title</label>
            <input
              id="upload-title"
              className="text-input"
              maxLength={255}
              placeholder={suggestedTitle || 'Defaults to the file name'}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </>
        )}

        <label htmlFor="upload-description">Description</label>
        <textarea
          id="upload-description"
          className="text-input"
          rows={3}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />

        <div className="form-row">
          <div>
            <label htmlFor="upload-category">Category</label>
            <select
              id="upload-category"
              className="select select--block"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            >
              <option value="">Choose a category</option>
              {(categories.data ?? []).map((item) => (
                <option key={item.id} value={item.name}>
                  {item.name}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="upload-department">Department</label>
            <input
              id="upload-department"
              className="text-input"
              placeholder="Defaults to the category"
              value={department}
              onChange={(e) => setDepartment(e.target.value)}
            />
          </div>
        </div>

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        {finished ? (
          <>
            <p className="notice" role="status">
              Uploaded {done.length} of {items.length} {items.length === 1 ? 'document' : 'documents'}.
            </p>
            <div className="upload-form__after">
              <button type="button" className="btn btn--outline" onClick={() => choose([])}>
                Upload more
              </button>
              <Link className="btn btn--primary" to="/repository">
                Go to the repository
              </Link>
            </div>
          </>
        ) : (
          <button
            type="submit"
            className="btn btn--primary upload-form__submit"
            disabled={uploading || (bulk && pending.length === 0)}
          >
            {submitLabel}
          </button>
        )}
        {uploading && (
          <p className="hint">
            {bulk
              ? 'Files go up one at a time. Keep this page open until they finish.'
              : 'Embedding a large file can take a little while.'}
          </p>
        )}
      </form>
    </div>
  );
}
