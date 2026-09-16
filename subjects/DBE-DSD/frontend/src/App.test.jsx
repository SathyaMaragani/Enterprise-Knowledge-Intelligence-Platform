import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';
import { request } from './api/client.js';
import { AuthProvider } from './auth/AuthContext.jsx';
import { documentFixture, jsonResponse, makeToken, mockApi, tokenFor } from './test-utils.js';

function renderApp(path = '/') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

function signIn(username, password) {
  fireEvent.change(screen.getByLabelText('Username'), { target: { value: username } });
  fireEvent.change(screen.getByLabelText('Password'), { target: { value: password } });
  fireEvent.click(screen.getByRole('button', { name: 'Sign In' }));
}

function signOut() {
  fireEvent.click(screen.getByRole('button', { name: /^Account menu for / }));
  fireEvent.click(screen.getByRole('menuitem', { name: 'Sign out' }));
}

const signInHeading = () => screen.getByRole('heading', { name: 'Welcome Back' });
const dashboardHeading = () => screen.getByRole('heading', { name: 'Find what matters' });

// A signed-in shell loads dashboard data straight away; these keep it quiet.
const DASHBOARD_ROUTES = {
  'GET /api/documents': jsonResponse(200, [documentFixture()]),
  'GET /api/search/vector/collection-info': jsonResponse(200, { pointsCount: 30 }),
};

describe('authentication flow', () => {
  beforeEach(() => sessionStorage.clear());

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('sends an anonymous visitor to the sign-in page', () => {
    renderApp('/');
    expect(signInHeading()).toBeTruthy();
  });

  it('sends unknown paths through the same guard', () => {
    renderApp('/no-such-page');
    expect(signInHeading()).toBeTruthy();
  });

  it('signs in against /api/auth/login and shows the dashboard', async () => {
    const token = tokenFor('alice_mgr');
    const fetchMock = mockApi({
      ...DASHBOARD_ROUTES,
      'POST /api/auth/login': jsonResponse(200, { token, type: 'Bearer', username: 'alice_mgr' }),
    });
    renderApp('/');

    signIn('  alice_mgr ', 'stub-password');

    expect(await screen.findByRole('heading', { name: 'Find what matters' })).toBeTruthy();
    expect(screen.getByText('alice_mgr')).toBeTruthy();

    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe('/api/auth/login');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ username: 'alice_mgr', password: 'stub-password' });
    expect(init.headers.Authorization).toBeUndefined();
    expect(sessionStorage.getItem('eip.token')).toBe(token);
  });

  it('shows the rejection and stays signed out for bad credentials', async () => {
    mockApi({
      'POST /api/auth/login': jsonResponse(401, { error: 'Unauthorized', message: 'Invalid username or password' }),
    });
    renderApp('/login');

    signIn('alice_mgr', 'wrong');

    expect((await screen.findByRole('alert')).textContent).toBe('Invalid username or password');
    expect(signInHeading()).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Sign In' }).disabled).toBe(false);
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });

  it('refuses a login response without a usable token', async () => {
    mockApi({ 'POST /api/auth/login': jsonResponse(200, { token: 'not-a-jwt', username: 'alice_mgr' }) });
    renderApp('/login');

    signIn('alice_mgr', 'stub-password');

    expect((await screen.findByRole('alert')).textContent).toMatch(/unusable/);
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });

  it('restores a valid session on reload and signs out from the account menu', () => {
    mockApi(DASHBOARD_ROUTES);
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
    renderApp('/');

    expect(dashboardHeading()).toBeTruthy();

    signOut();

    expect(signInHeading()).toBeTruthy();
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });

  it('returns the user to the page they asked for after signing in', async () => {
    mockApi({
      'POST /api/auth/login': jsonResponse(200, { token: tokenFor('bob_eng'), type: 'Bearer', username: 'bob_eng' }),
      'GET /api/documents/3': jsonResponse(403, { error: 'Forbidden', message: 'no' }),
    });
    renderApp('/documents/3');
    expect(signInHeading()).toBeTruthy();

    signIn('bob_eng', 'stub-password');

    expect(await screen.findByRole('link', { name: '← Repository' })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Find what matters' })).toBeNull();
  });

  it('keeps a signed-in user away from the sign-in page', () => {
    mockApi(DASHBOARD_ROUTES);
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
    renderApp('/login');
    expect(dashboardHeading()).toBeTruthy();
  });

  it.each([
    ['expired', makeToken({ sub: 'bob_eng', exp: 1 })],
    ['malformed', 'garbage'],
  ])('discards a stored %s token', (_label, token) => {
    sessionStorage.setItem('eip.token', token);
    renderApp('/');

    expect(signInHeading()).toBeTruthy();
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });

  it('signs out when the backend rejects the token on the dashboard load', async () => {
    mockApi({
      'GET /api/documents': jsonResponse(401, { error: 'Unauthorized' }),
      'GET /api/search/vector/collection-info': jsonResponse(401, { error: 'Unauthorized' }),
    });
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Welcome Back' })).toBeTruthy();
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });

  it('signs out when the backend rejects the token on any request', async () => {
    mockApi(DASHBOARD_ROUTES);
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
    renderApp('/');

    mockApi({ 'GET /api/anything': jsonResponse(401, { error: 'Unauthorized' }) });
    await act(() => request('/api/anything').catch(() => {}));

    expect(signInHeading()).toBeTruthy();
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });

  it('signs out at the moment the token expires, without a request', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(Date.UTC(2026, 0, 1));
    mockApi(DASHBOARD_ROUTES);
    sessionStorage.setItem('eip.token', tokenFor('bob_eng', 60));
    renderApp('/');
    expect(dashboardHeading()).toBeTruthy();

    await act(() => vi.advanceTimersByTimeAsync(59_000));
    expect(dashboardHeading()).toBeTruthy();

    await act(() => vi.advanceTimersByTimeAsync(1_000));
    expect(signInHeading()).toBeTruthy();
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });
});

describe('signed-in profile', () => {
  beforeEach(() => sessionStorage.clear());

  afterEach(() => vi.unstubAllGlobals());

  const profile = (overrides) => ({
    username: 'alice_mgr',
    fullName: 'Alice Manager',
    email: 'alice@example.com',
    roles: ['MANAGER'],
    permissions: ['DOCUMENT_CREATE', 'DOCUMENT_READ', 'DOCUMENT_UPDATE'],
    ...overrides,
  });

  it('shows the full name and role from /api/auth/me', async () => {
    const fetchMock = mockApi({ ...DASHBOARD_ROUTES, 'GET /api/auth/me': jsonResponse(200, profile()) });
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
    renderApp('/');

    const account = await screen.findByRole('button', { name: 'Account menu for Alice Manager' });

    expect(within(account).getByText('Manager')).toBeTruthy();
    expect(within(account).getByText('AM')).toBeTruthy();
    const meCall = fetchMock.mock.calls.find(([path]) => path === '/api/auth/me');
    expect(meCall[1].headers.Authorization).toMatch(/^Bearer /);
  });

  it('shows Administration only to administrators', async () => {
    mockApi({ ...DASHBOARD_ROUTES, 'GET /api/auth/me': jsonResponse(200, profile()) });
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
    const { unmount } = renderApp('/');
    await screen.findByRole('button', { name: 'Account menu for Alice Manager' });
    expect(screen.queryByText('Administration')).toBeNull();
    unmount();

    mockApi({
      ...DASHBOARD_ROUTES,
      'GET /api/auth/me': jsonResponse(200, profile({ username: 'admin_user', fullName: 'Admin Istrator', roles: ['ADMIN'] })),
    });
    sessionStorage.setItem('eip.token', tokenFor('admin_user'));
    renderApp('/');
    await screen.findByRole('button', { name: 'Account menu for Admin Istrator' });
    expect(screen.getByText('Administration')).toBeTruthy();
    expect(screen.getByText('Administrator')).toBeTruthy();
  });

  it('falls back to the username when the profile cannot load', async () => {
    mockApi({ ...DASHBOARD_ROUTES, 'GET /api/auth/me': jsonResponse(500, { message: 'boom' }) });
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
    renderApp('/');

    const account = await screen.findByRole('button', { name: 'Account menu for bob_eng' });

    expect(within(account).getByText('Signed in')).toBeTruthy();
    expect(screen.queryByText('Administration')).toBeNull();
  });

  it('loads a fresh profile for each sign-in', async () => {
    const fetchMock = mockApi({
      ...DASHBOARD_ROUTES,
      'GET /api/auth/me': jsonResponse(200, profile()),
      'POST /api/auth/login': jsonResponse(200, { token: tokenFor('alice_mgr'), type: 'Bearer', username: 'alice_mgr' }),
    });
    renderApp('/login');

    signIn('alice_mgr', 'stub-password');
    await screen.findByRole('button', { name: 'Account menu for Alice Manager' });
    signOut();
    signIn('alice_mgr', 'stub-password');
    await screen.findByRole('button', { name: 'Account menu for Alice Manager' });

    expect(fetchMock.mock.calls.filter(([path]) => path === '/api/auth/me')).toHaveLength(2);
  });
});

describe('sign-in page', () => {
  beforeEach(() => sessionStorage.clear());

  it('toggles password visibility', () => {
    renderApp('/login');
    const password = screen.getByLabelText('Password');

    expect(password.type).toBe('password');
    fireEvent.click(screen.getByRole('button', { name: 'Show password' }));
    expect(password.type).toBe('text');
    fireEvent.click(screen.getByRole('button', { name: 'Hide password' }));
    expect(password.type).toBe('password');
  });

  it('explains password resets instead of linking nowhere', () => {
    renderApp('/login');

    fireEvent.click(screen.getByRole('button', { name: 'Forgot password?' }));

    expect(screen.getByText(/handled by your EIP administrator/)).toBeTruthy();
  });

  it('shows single sign-on as unavailable', () => {
    renderApp('/login');

    const sso = screen.getByRole('button', { name: 'Sign in with SSO' });

    expect(sso.disabled).toBe(true);
    expect(within(sso.parentElement).getByText(/not configured/)).toBeTruthy();
  });
});
