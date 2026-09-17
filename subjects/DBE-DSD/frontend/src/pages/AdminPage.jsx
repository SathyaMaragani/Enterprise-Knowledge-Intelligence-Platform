import { useState } from 'react';
import { request } from '../api/client.js';
import { useApi } from '../api/useApi.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { can } from '../auth/roles.js';
import { formatDateTime, humanize } from '../components/DocumentBits.jsx';

export default function AdminPage() {
  const { profile } = useAuth();

  if (profile === undefined) {
    return (
      <div className="page">
        <p className="muted">Checking your permissions…</p>
      </div>
    );
  }

  if (!can(profile, 'USER_MANAGE')) {
    return (
      <div className="page">
        <header className="page-header">
          <h1 className="page-title">Users</h1>
        </header>
        <p className="form-error" role="alert">
          {profile === null
            ? 'Your permissions could not be checked. Reload the page to try again.'
            : 'Only administrators can manage users.'}
        </p>
      </div>
    );
  }

  return <UserAdministration currentUsername={profile.username} />;
}

function UserAdministration({ currentUsername }) {
  const users = useApi('/api/admin/users');
  const roles = useApi('/api/admin/roles');
  const [notice, setNotice] = useState(null);
  const roleNames = (roles.data ?? []).map((role) => role.name);

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">Users</h1>
        <p className="page-subtitle">
          Create accounts, set roles, disable access and reset passwords. Disabling an account ends its sessions
          immediately.
        </p>
      </header>

      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}

      <section className="glass-panel section" aria-labelledby="users-title">
        <h2 id="users-title" className="section__title">
          Accounts {users.data && <span className="muted">({users.data.length})</span>}
        </h2>
        {users.status === 'error' && (
          <p className="form-error" role="alert">
            {users.error.message}
          </p>
        )}
        {!users.data && users.status === 'loading' && <p className="muted">Loading users…</p>}
        {users.data && (
          <div className="table-scroll">
            <table className="doc-table admin-table">
              <thead>
                <tr>
                  <th scope="col">User</th>
                  <th scope="col">Role</th>
                  <th scope="col">Status</th>
                  <th scope="col">Created</th>
                  <th scope="col">Password</th>
                </tr>
              </thead>
              <tbody>
                {users.data.map((user) => (
                  <UserRow
                    key={user.id}
                    user={user}
                    roleNames={roleNames}
                    self={user.username === currentUsername}
                    onChanged={(message) => {
                      setNotice(message);
                      users.reload();
                    }}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <CreateUserForm
        roleNames={roleNames}
        onCreated={(user) => {
          setNotice(`Created ${user.username} as ${user.roles.map(humanize).join(', ')}.`);
          users.reload();
        }}
      />
    </div>
  );
}

function UserRow({ user, roleNames, self, onChanged }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [resetting, setResetting] = useState(false);
  const [password, setPassword] = useState('');
  const role = user.roles[0] ?? '';

  async function update(changes, message) {
    setBusy(true);
    setError(null);
    try {
      await request(`/api/admin/users/${user.id}`, { method: 'PATCH', body: changes });
      onChanged(message);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function resetPassword(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await request(`/api/admin/users/${user.id}/password`, { method: 'PUT', body: { password } });
      setResetting(false);
      setPassword('');
      onChanged(`Password reset for ${user.username}.`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const selfNote = self ? 'You cannot change your own role or disable your own account' : undefined;

  return (
    <tr>
      <td>
        <span className="doc-title__name">{user.fullName}</span>
        <span className="doc-title__description">
          {user.username} · {user.email}
          {self && ' · you'}
        </span>
        {error && (
          <span className="form-error form-error--inline" role="alert">
            {error}
          </span>
        )}
      </td>
      <td>
        <select
          aria-label={`Role for ${user.username}`}
          className="select"
          value={role}
          disabled={busy || self}
          title={selfNote}
          onChange={(e) => update({ role: e.target.value }, `${user.username} is now ${humanize(e.target.value)}.`)}
        >
          {!roleNames.includes(role) && <option value={role}>{humanize(role)}</option>}
          {roleNames.map((name) => (
            <option key={name} value={name}>
              {humanize(name)}
            </option>
          ))}
        </select>
      </td>
      <td>
        <span className={`status ${user.active ? 'status--indexed' : 'status--failed'}`}>
          {user.active ? 'Active' : 'Disabled'}
        </span>{' '}
        <button
          type="button"
          className="link-button"
          disabled={busy || self}
          title={selfNote}
          onClick={() =>
            update(
              { active: !user.active },
              user.active ? `Disabled ${user.username}; their sessions have ended.` : `Enabled ${user.username}.`,
            )
          }
        >
          {user.active ? 'Disable' : 'Enable'}
        </button>
      </td>
      <td className="muted">{formatDateTime(user.createdAt)}</td>
      <td>
        {!resetting ? (
          <button type="button" className="link-button" disabled={busy} onClick={() => setResetting(true)}>
            Reset…
          </button>
        ) : (
          <form className="inline-form" onSubmit={resetPassword}>
            <input
              className="text-input"
              type="password"
              aria-label={`New password for ${user.username}`}
              placeholder="New password"
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button type="submit" className="btn btn--primary btn--small" disabled={busy}>
              Save
            </button>
            <button
              type="button"
              className="btn btn--outline btn--small"
              onClick={() => {
                setResetting(false);
                setPassword('');
              }}
            >
              Cancel
            </button>
          </form>
        )}
      </td>
    </tr>
  );
}

const EMPTY_USER = { username: '', fullName: '', email: '', role: '', password: '' };

function CreateUserForm({ roleNames, onCreated }) {
  const [form, setForm] = useState(EMPTY_USER);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const field = (name) => ({
    value: form[name],
    onChange: (e) => setForm((current) => ({ ...current, [name]: e.target.value })),
  });

  async function handleSubmit(event) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const created = await request('/api/admin/users', { method: 'POST', body: form });
      setForm(EMPTY_USER);
      onCreated(created);
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="glass-panel section" aria-labelledby="create-user-title">
      <h2 id="create-user-title" className="section__title">
        Add a user
      </h2>
      <form className="create-user" onSubmit={handleSubmit}>
        <div className="form-row">
          <div>
            <label htmlFor="new-username">Username</label>
            <input id="new-username" className="text-input" autoComplete="off" required {...field('username')} />
          </div>
          <div>
            <label htmlFor="new-full-name">Full name</label>
            <input id="new-full-name" className="text-input" required {...field('fullName')} />
          </div>
          <div>
            <label htmlFor="new-email">Email</label>
            <input id="new-email" className="text-input" type="email" required {...field('email')} />
          </div>
          <div>
            <label htmlFor="new-role">Role</label>
            <select id="new-role" className="select select--block" required {...field('role')}>
              <option value="">Choose a role</option>
              {roleNames.map((name) => (
                <option key={name} value={name}>
                  {humanize(name)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="new-password">Initial password</label>
            <input
              id="new-password"
              className="text-input"
              type="password"
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              required
              {...field('password')}
            />
          </div>
        </div>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <button type="submit" className="btn btn--primary create-user__submit" disabled={saving}>
          {saving ? 'Creating…' : 'Create user'}
        </button>
      </form>
    </section>
  );
}
