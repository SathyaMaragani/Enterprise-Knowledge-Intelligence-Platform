import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { request } from '../api/client.js';
import { useApi } from '../api/useApi.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { can } from '../auth/roles.js';
import { UploadIcon } from '../components/icons.jsx';

// Mirrors the backend's limits so most mistakes are caught before uploading.
export const MAX_UPLOAD_BYTES = 1024 * 1024;
const ACCEPTED = ['.txt', '.md', '.markdown'];

export function uploadProblem(file) {
  if (!file) {
    return 'Choose a file to upload.';
  }
  const name = file.name.toLowerCase();
  if (!ACCEPTED.some((extension) => name.endsWith(extension))) {
    return 'Only .txt and .md files can be uploaded.';
  }
  if (file.size === 0) {
    return 'The file is empty.';
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return 'Files can be at most 1 MB.';
  }
  return null;
}

function formatSize(bytes) {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

export default function UploadPage() {
  const { profile } = useAuth();
  const navigate = useNavigate();
  const categories = useApi('/api/categories');

  const [file, setFile] = useState(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [department, setDepartment] = useState('');
  const [error, setError] = useState(null);
  const [uploading, setUploading] = useState(false);

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

  const suggestedTitle = file ? file.name.replace(/\.[^.]+$/, '') : '';

  async function handleSubmit(event) {
    event.preventDefault();
    const problem = uploadProblem(file) ?? (category ? null : 'Choose a category.');
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    setUploading(true);

    const form = new FormData();
    form.append('file', file);
    form.append('category', category);
    if (title.trim()) form.append('title', title.trim());
    if (description.trim()) form.append('description', description.trim());
    if (department.trim()) form.append('department', department.trim());

    try {
      const result = await request('/api/documents', { method: 'POST', form });
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

  return (
    <div className="page page--narrow">
      <Link className="back-link" to="/repository">
        ← Repository
      </Link>
      <header className="page-header">
        <h1 className="page-title">Upload a document</h1>
        <p className="page-subtitle">
          Plain text or Markdown, up to 1 MB. The text is stored, split into chunks and, when semantic search is
          available, embedded so it can be found by meaning.
        </p>
      </header>

      <form className="glass-panel section upload-form" onSubmit={handleSubmit} noValidate>
        <label htmlFor="upload-file">File</label>
        <label className="dropzone" htmlFor="upload-file">
          <UploadIcon size={28} />
          <span>{file ? `${file.name} · ${formatSize(file.size)}` : 'Choose a .txt or .md file'}</span>
        </label>
        <input
          id="upload-file"
          className="visually-hidden"
          type="file"
          accept={ACCEPTED.join(',')}
          onChange={(e) => {
            const chosen = e.target.files?.[0] ?? null;
            setFile(chosen);
            setError(chosen ? uploadProblem(chosen) : null);
          }}
        />

        <label htmlFor="upload-title">Title</label>
        <input
          id="upload-title"
          className="text-input"
          maxLength={255}
          placeholder={suggestedTitle || 'Defaults to the file name'}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
        />

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

        <button type="submit" className="btn btn--primary upload-form__submit" disabled={uploading}>
          {uploading ? 'Uploading…' : 'Upload document'}
        </button>
        {uploading && <p className="hint">Embedding a large file can take a little while.</p>}
      </form>
    </div>
  );
}
