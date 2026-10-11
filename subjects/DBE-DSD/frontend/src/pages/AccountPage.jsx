import { useState } from 'react';
import { request } from '../api/client.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { roleLabel } from '../auth/roles.js';

/** The signed-in user's details, and changing their own password. */
export default function AccountPage() {
  const { profile, session } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [done, setDone] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setDone(false);
    if (!current) {
      setError('Enter your current password.');
      return;
    }
    // The same rule the server applies: BCrypt reads at most 72 bytes.
    if (next.length < 8 || next.length > 72) {
      setError('The new password must be 8 to 72 characters.');
      return;
    }
    if (next !== confirm) {
      setError('The two new passwords do not match.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await request('/api/auth/password', {
        method: 'PUT',
        body: { currentPassword: current, newPassword: next },
      });
      setCurrent('');
      setNext('');
      setConfirm('');
      setDone(true);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const field = (id, label, value, setValue, autoComplete) => (
    <>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        className="text-input"
        type="password"
        autoComplete={autoComplete}
        maxLength={72}
        value={value}
        onChange={(e) => setValue(e.target.value)}
      />
    </>
  );

  return (
    <div className="page page--narrow">
      <header className="page-header">
        <h1 className="page-title">Your account</h1>
        <p className="page-subtitle">Your details, and your password.</p>
      </header>

      <section className="glass-panel section" aria-labelledby="account-details">
        <h2 id="account-details" className="section__title">
          Details
        </h2>
        <dl className="detail-list">
          <div>
            <dt>Name</dt>
            <dd>{profile?.fullName ?? '—'}</dd>
          </div>
          <div>
            <dt>Username</dt>
            <dd>{profile?.username ?? session?.username}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{profile?.email ?? '—'}</dd>
          </div>
          <div>
            <dt>Role</dt>
            <dd>{roleLabel(profile) ?? '—'}</dd>
          </div>
        </dl>
        <p className="muted account-note">An administrator can change your name, email or role.</p>
      </section>

      <form className="glass-panel section edit-form" aria-labelledby="password-title" onSubmit={handleSubmit} noValidate>
        <h2 id="password-title" className="section__title">
          Change password
        </h2>
        {field('current-password', 'Current password', current, setCurrent, 'current-password')}
        {field('new-password', 'New password', next, setNext, 'new-password')}
        <p className="hint">8 to 72 characters.</p>
        {field('confirm-password', 'New password again', confirm, setConfirm, 'new-password')}
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        {done && (
          <p className="notice" role="status">
            Password changed. Use the new one the next time you sign in.
          </p>
        )}
        <div className="edit-form__actions">
          <button type="submit" className="btn btn--primary" disabled={busy}>
            {busy ? 'Changing…' : 'Change password'}
          </button>
        </div>
      </form>
    </div>
  );
}
