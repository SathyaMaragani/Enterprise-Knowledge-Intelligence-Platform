import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { AuthProvider } from '../auth/AuthContext.jsx';
import { jsonResponse, tokenFor } from '../test-utils.js';

const ADMIN = { username: 'admin_user', fullName: 'Admin Istrator', roles: ['ADMIN'], permissions: ['USER_MANAGE'] };
const USERS = [
  { id: 1, username: 'admin_user', fullName: 'Admin Istrator', email: 'admin@example.com', active: true, roles: ['ADMIN'], createdAt: '2026-09-11T07:49:03Z' },
  { id: 3, username: 'bob_eng', fullName: 'Bob Engineer', email: 'bob@example.com', active: true, roles: ['EMPLOYEE'], createdAt: '2026-09-11T07:49:03Z' },
];
const ROLES = [
  { name: 'ADMIN', description: '', permissions: [] },
  { name: 'EMPLOYEE', description: '', permissions: [] },
  { name: 'MANAGER', description: '', permissions: [] },
];

/** A small in-memory backend so a change is visible in the reloaded list. */
function renderAdmin({ profile = ADMIN, failWith } = {}) {
  let users = structuredClone(USERS);
  const calls = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, init = {}) => {
      const method = init.method ?? 'GET';
      const body = init.body ? JSON.parse(init.body) : undefined;
      calls.push({ method, url, body });
      if (url === '/api/auth/me') return jsonResponse(200, profile);
      if (url === '/api/admin/roles') return jsonResponse(200, ROLES);
      if (url === '/api/admin/users' && method === 'GET') return jsonResponse(200, users);
      if (failWith && method !== 'GET') return failWith;
      if (url === '/api/admin/users' && method === 'POST') {
        const created = { id: 9, username: body.username, fullName: body.fullName, email: body.email, active: true, roles: [body.role], createdAt: '2026-09-17T10:00:00Z' };
        users = [...users, created];
        return jsonResponse(201, created);
      }
      const patch = url.match(/^\/api\/admin\/users\/(\d+)$/);
      if (patch && method === 'PATCH') {
        users = users.map((user) =>
          user.id === Number(patch[1])
            ? { ...user, ...(body.role ? { roles: [body.role] } : {}), ...(body.active !== undefined ? { active: body.active } : {}) }
            : user,
        );
        return jsonResponse(200, users.find((user) => user.id === Number(patch[1])));
      }
      if (/^\/api\/admin\/users\/\d+\/password$/.test(url) && method === 'PUT') return jsonResponse(204);
      return jsonResponse(404, { message: `No mock for ${method} ${url}` });
    }),
  );
  render(
    <MemoryRouter initialEntries={['/admin']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
  return calls;
}

const row = async (username) => (await screen.findByText(new RegExp(`^${username} ·`))).closest('tr');

describe('administration page', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('admin_user'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('is closed to users without USER_MANAGE', async () => {
    renderAdmin({ profile: { username: 'bob_eng', roles: ['EMPLOYEE'], permissions: ['DOCUMENT_READ'] } });

    expect((await screen.findByRole('alert')).textContent).toBe('Only administrators can manage users.');
    expect(screen.queryByRole('table')).toBeNull();
    expect(screen.queryByRole('link', { name: 'Administration' })).toBeNull();
  });

  it('lists accounts and protects the signed-in admin from locking themselves out', async () => {
    renderAdmin();

    const self = await row('admin_user');
    expect(within(self).getByText(/you/)).toBeTruthy();
    expect(within(self).getByRole('combobox', { name: 'Role for admin_user' }).disabled).toBe(true);
    expect(within(self).getByRole('button', { name: 'Disable' }).disabled).toBe(true);

    const bob = await row('bob_eng');
    expect(within(bob).getByRole('combobox', { name: 'Role for bob_eng' }).value).toBe('EMPLOYEE');
    expect(within(bob).getByText('Active')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Administration' }).className).toContain('active');
  });

  it('changes a role and disables an account, then shows the reloaded state', async () => {
    const calls = renderAdmin();
    const bob = await row('bob_eng');

    fireEvent.change(within(bob).getByRole('combobox', { name: 'Role for bob_eng' }), { target: { value: 'MANAGER' } });
    expect((await screen.findByRole('status')).textContent).toBe('bob_eng is now Manager.');
    await vi.waitFor(() =>
      expect(screen.getByRole('combobox', { name: 'Role for bob_eng' }).value).toBe('MANAGER'),
    );

    fireEvent.click(within(await row('bob_eng')).getByRole('button', { name: 'Disable' }));
    await vi.waitFor(() => expect(screen.getByRole('status').textContent).toMatch(/Disabled bob_eng; their sessions have ended/));
    expect(within(await row('bob_eng')).getByText('Disabled')).toBeTruthy();

    expect(calls.filter((call) => call.method === 'PATCH').map((call) => [call.url, call.body])).toEqual([
      ['/api/admin/users/3', { role: 'MANAGER' }],
      ['/api/admin/users/3', { active: false }],
    ]);
  });

  it('resets a password', async () => {
    const calls = renderAdmin();
    const bob = await row('bob_eng');

    fireEvent.click(within(bob).getByRole('button', { name: 'Reset…' }));
    fireEvent.change(within(bob).getByLabelText('New password for bob_eng'), { target: { value: 'fresh-pass-99' } });
    fireEvent.click(within(bob).getByRole('button', { name: 'Save' }));

    expect((await screen.findByRole('status')).textContent).toBe('Password reset for bob_eng.');
    expect(calls.find((call) => call.method === 'PUT')).toEqual({
      method: 'PUT',
      url: '/api/admin/users/3/password',
      body: { password: 'fresh-pass-99' },
    });
  });

  it('creates a user and adds them to the list', async () => {
    const calls = renderAdmin();
    const createForm = (await screen.findByRole('heading', { name: 'Add a user' })).closest('section');
    await within(createForm).findByRole('option', { name: 'Manager' });

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'carol_ops' } });
    fireEvent.change(screen.getByLabelText('Full name'), { target: { value: 'Carol Ops' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'carol@example.com' } });
    fireEvent.change(screen.getByLabelText('Role'), { target: { value: 'MANAGER' } });
    fireEvent.change(screen.getByLabelText('Initial password'), { target: { value: 'carol-pass-1' } });
    fireEvent.submit(screen.getByRole('button', { name: 'Create user' }).closest('form'));

    expect((await screen.findByRole('status')).textContent).toBe('Created carol_ops as Manager.');
    expect(await row('carol_ops')).toBeTruthy();
    expect(screen.getByLabelText('Username').value).toBe('');
    expect(calls.find((call) => call.method === 'POST').body).toEqual({
      username: 'carol_ops',
      fullName: 'Carol Ops',
      email: 'carol@example.com',
      role: 'MANAGER',
      password: 'carol-pass-1',
    });
  });

  it('shows backend rejections where the action happened', async () => {
    renderAdmin({ failWith: jsonResponse(409, { message: 'Username carol_ops is already taken' }) });
    const createForm = (await screen.findByRole('heading', { name: 'Add a user' })).closest('section');
    await within(createForm).findByRole('option', { name: 'Manager' });

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'carol_ops' } });
    fireEvent.submit(screen.getByRole('button', { name: 'Create user' }).closest('form'));

    expect((await screen.findByRole('alert')).textContent).toBe('Username carol_ops is already taken');
    expect(screen.getByLabelText('Username').value).toBe('carol_ops');
  });
});
