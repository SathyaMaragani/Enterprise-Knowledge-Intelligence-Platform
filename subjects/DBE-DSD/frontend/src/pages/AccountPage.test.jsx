import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { AuthProvider } from '../auth/AuthContext.jsx';
import { jsonResponse, mockApi, tokenFor } from '../test-utils.js';

const BOB = {
  username: 'bob_eng',
  fullName: 'Bob Engineer',
  email: 'bob@example.com',
  roles: ['EMPLOYEE'],
  permissions: ['DOCUMENT_READ'],
};

const minutesAgo = (minutes) => new Date(Date.now() - minutes * 60_000).toISOString();

function renderAccount(routes = {}) {
  const fetchMock = mockApi({ 'GET /api/auth/me': jsonResponse(200, BOB), ...routes });
  render(
    <MemoryRouter initialEntries={['/account']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
  return fetchMock;
}

beforeEach(() => {
  sessionStorage.clear();
  localStorage.clear();
  sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
});

afterEach(() => vi.unstubAllGlobals());

describe('account page', () => {
  const fill = (form, current, next, again) => {
    fireEvent.change(within(form).getByLabelText('Current password'), { target: { value: current } });
    fireEvent.change(within(form).getByLabelText('New password'), { target: { value: next } });
    fireEvent.change(within(form).getByLabelText('New password again'), { target: { value: again } });
    fireEvent.click(within(form).getByRole('button', { name: 'Change password' }));
  };

  it('is reached from the account menu and shows the user’s details', async () => {
    renderAccount();

    fireEvent.click(await screen.findByRole('button', { name: 'Account menu for Bob Engineer' }));
    expect(screen.getByRole('menuitem', { name: 'Account and password' }).getAttribute('href')).toBe('/account');

    const details = screen.getByRole('heading', { name: 'Details' }).closest('section');
    expect(within(details).getByText('Bob Engineer')).toBeTruthy();
    expect(within(details).getByText('bob_eng')).toBeTruthy();
    expect(within(details).getByText('bob@example.com')).toBeTruthy();
  });

  it('changes the password and clears the form', async () => {
    const fetchMock = renderAccount({ 'PUT /api/auth/password': jsonResponse(204) });
    const form = await screen.findByRole('form', { name: 'Change password' });

    fill(form, 'old-pass-123', 'brand-new-pass-1', 'brand-new-pass-1');

    expect((await within(form).findByRole('status')).textContent).toMatch(/Password changed/);
    const [, init] = fetchMock.mock.calls.find(([path]) => path === '/api/auth/password');
    expect(JSON.parse(init.body)).toEqual({ currentPassword: 'old-pass-123', newPassword: 'brand-new-pass-1' });
    expect(within(form).getByLabelText('Current password').value).toBe('');
    expect(within(form).getByLabelText('New password').value).toBe('');
  });

  it('checks the new password before asking the server', async () => {
    const fetchMock = renderAccount();
    const form = await screen.findByRole('form', { name: 'Change password' });
    const sent = () => fetchMock.mock.calls.some(([path]) => path === '/api/auth/password');

    fill(form, '', 'brand-new-pass-1', 'brand-new-pass-1');
    expect(within(form).getByRole('alert').textContent).toBe('Enter your current password.');
    fill(form, 'old-pass-123', 'short', 'short');
    expect(within(form).getByRole('alert').textContent).toBe('The new password must be 8 to 72 characters.');
    fill(form, 'old-pass-123', 'brand-new-pass-1', 'brand-new-pass-2');
    expect(within(form).getByRole('alert').textContent).toBe('The two new passwords do not match.');
    expect(sent()).toBe(false);
  });

  it('shows the server’s reason and keeps the user signed in when the current password is wrong', async () => {
    renderAccount({
      'PUT /api/auth/password': jsonResponse(400, { message: 'The current password is not correct' }),
    });
    const form = await screen.findByRole('form', { name: 'Change password' });

    fill(form, 'wrong-pass-123', 'brand-new-pass-1', 'brand-new-pass-1');

    expect((await within(form).findByRole('alert')).textContent).toBe('The current password is not correct');
    expect(sessionStorage.getItem('eip.token')).not.toBeNull();
    expect(within(form).queryByRole('status')).toBeNull();
  });
});

describe('notifications', () => {
  const NOTIFICATIONS = [
    { type: 'UPDATED', documentId: 7, title: 'Q2 Budget Draft', owner: 'alice_mgr', at: minutesAgo(5) },
    { type: 'SHARED', documentId: 7, title: 'Q2 Budget Draft', owner: 'alice_mgr', at: minutesAgo(120) },
  ];

  it('counts what is new, lists it with links, and marks it seen when the panel closes', async () => {
    renderAccount({ 'GET /api/notifications': jsonResponse(200, NOTIFICATIONS) });

    const bell = await screen.findByRole('button', { name: 'Notifications, 2 new' });
    fireEvent.click(bell);

    const items = screen.getAllByRole('listitem').filter((item) => item.closest('.notification-list'));
    expect(items.map((item) => item.querySelector('a').textContent)).toEqual([
      'Q2 Budget Draft was updated',
      'Q2 Budget Draft was shared with you',
    ]);
    expect(items[0].querySelector('a').getAttribute('href')).toBe('/documents/7');
    expect(items[0].textContent).toContain('Owner: alice_mgr');
    expect(items.every((item) => item.classList.contains('is-new'))).toBe(true);

    fireEvent.click(bell);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Notifications' })).toBeTruthy());
    expect(Number(localStorage.getItem('eip.notifications.seen.bob_eng'))).toBeGreaterThan(Date.now() - 5000);
  });

  it('counts only notifications newer than the last visit', async () => {
    localStorage.setItem('eip.notifications.seen.bob_eng', String(Date.now() - 60 * 60_000));
    renderAccount({ 'GET /api/notifications': jsonResponse(200, NOTIFICATIONS) });

    expect(await screen.findByRole('button', { name: 'Notifications, 1 new' })).toBeTruthy();
  });

  it('says so when there is nothing', async () => {
    renderAccount({ 'GET /api/notifications': jsonResponse(200, []) });
    fireEvent.click(await screen.findByRole('button', { name: 'Notifications' }));
    expect(screen.getByText('You’re all caught up.')).toBeTruthy();
  });
});
