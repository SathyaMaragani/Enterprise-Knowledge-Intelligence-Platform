import { useState } from 'react';
import { request } from '../api/client.js';
import { useApi } from '../api/useApi.js';

/**
 * Who else may read a document. Shown to its owner and to administrators; the
 * backend enforces the same rule on every call.
 */
export default function AccessPanel({ documentId, owner }) {
  const grants = useApi(`/api/documents/${documentId}/permissions`);
  const [username, setUsername] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function run(action) {
    setBusy(true);
    setError(null);
    try {
      await action();
      grants.reload();
      return true;
    } catch (err) {
      setError(err.message);
      return false;
    } finally {
      setBusy(false);
    }
  }

  async function grant(event) {
    event.preventDefault();
    const name = username.trim();
    if (!name) {
      setError('Enter a username.');
      return;
    }
    const ok = await run(() =>
      request(`/api/documents/${documentId}/permissions`, { method: 'POST', body: { username: name } }),
    );
    if (ok) {
      setUsername('');
    }
  }

  return (
    <section className="glass-panel section" aria-labelledby="access-title">
      <h2 id="access-title" className="section__title">
        Access
      </h2>
      <p className="muted access-note">
        {owner ? `${owner} owns this document. ` : ''}Administrators can read every document.
      </p>

      {grants.status === 'error' && (
        <p className="form-error" role="alert">
          {grants.error.message}
        </p>
      )}
      {grants.data && grants.data.length === 0 && <p className="muted">No one else has been given access.</p>}
      {grants.data && grants.data.length > 0 && (
        <ul className="grant-list">
          {grants.data.map((item) => (
            <li key={item.id}>
              <span>
                <strong>{item.fullName}</strong>
                <span className="muted">
                  {' '}
                  {item.username} · {item.permissionType}
                </span>
              </span>
              <button
                type="button"
                className="link-button"
                disabled={busy}
                aria-label={`Revoke ${item.permissionType} access for ${item.username}`}
                onClick={() =>
                  run(() =>
                    request(`/api/documents/${documentId}/permissions/${item.id}`, { method: 'DELETE' }),
                  )
                }
              >
                Revoke
              </button>
            </li>
          ))}
        </ul>
      )}

      <form className="inline-form grant-form" onSubmit={grant}>
        <input
          className="text-input"
          aria-label="Username to give read access"
          placeholder="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        />
        <button type="submit" className="btn btn--primary btn--small" disabled={busy}>
          Give read access
        </button>
      </form>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </section>
  );
}
