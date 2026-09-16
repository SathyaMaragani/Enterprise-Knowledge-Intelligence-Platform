import { act, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App.jsx';
import { request } from './api/client.js';
import { AuthProvider } from './auth/AuthContext.jsx';
import { jsonResponse, makeToken, mockFetch, tokenFor } from './test-utils.js';

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
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

const signInHeading = () => screen.getByRole('heading', { name: 'Sign in' });

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

  it('signs in against /api/auth/login and shows the signed-in shell', async () => {
    const token = tokenFor('alice_mgr');
    const fetchMock = mockFetch(jsonResponse(200, { token, type: 'Bearer', username: 'alice_mgr' }));
    renderApp('/');

    signIn('  alice_mgr ', 'stub-password');

    expect(await screen.findByRole('heading', { name: 'Welcome, alice_mgr' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeTruthy();

    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe('/api/auth/login');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ username: 'alice_mgr', password: 'stub-password' });
    expect(init.headers.Authorization).toBeUndefined();
    expect(sessionStorage.getItem('eip.token')).toBe(token);
  });

  it('shows the rejection and stays signed out for bad credentials', async () => {
    mockFetch(jsonResponse(401, { error: 'Unauthorized', message: 'Invalid username or password' }));
    renderApp('/login');

    signIn('alice_mgr', 'wrong');

    expect((await screen.findByRole('alert')).textContent).toBe('Invalid username or password');
    expect(signInHeading()).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Sign in' }).disabled).toBe(false);
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });

  it('refuses a login response without a usable token', async () => {
    mockFetch(jsonResponse(200, { token: 'not-a-jwt', username: 'alice_mgr' }));
    renderApp('/login');

    signIn('alice_mgr', 'stub-password');

    expect((await screen.findByRole('alert')).textContent).toMatch(/unusable/);
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });

  it('restores a valid session on reload and signs out', () => {
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
    renderApp('/');

    expect(screen.getByRole('heading', { name: 'Welcome, bob_eng' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(signInHeading()).toBeTruthy();
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });

  it('keeps a signed-in user away from the sign-in page', () => {
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
    renderApp('/login');
    expect(screen.getByRole('heading', { name: 'Welcome, bob_eng' })).toBeTruthy();
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

  it('signs out when the backend rejects the token on any request', async () => {
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
    mockFetch(jsonResponse(401, { error: 'Unauthorized' }));
    renderApp('/');

    await act(() => request('/api/documents').catch(() => {}));

    expect(signInHeading()).toBeTruthy();
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });

  it('signs out at the moment the token expires, without a request', () => {
    vi.useFakeTimers();
    vi.setSystemTime(Date.UTC(2026, 0, 1));
    sessionStorage.setItem('eip.token', tokenFor('bob_eng', 60));
    renderApp('/');
    expect(screen.getByRole('heading', { name: 'Welcome, bob_eng' })).toBeTruthy();

    act(() => vi.advanceTimersByTime(59_000));
    expect(screen.getByRole('heading', { name: 'Welcome, bob_eng' })).toBeTruthy();

    act(() => vi.advanceTimersByTime(1_000));
    expect(signInHeading()).toBeTruthy();
    expect(sessionStorage.getItem('eip.token')).toBeNull();
  });
});
