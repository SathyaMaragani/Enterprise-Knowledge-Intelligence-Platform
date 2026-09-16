import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, makeToken, mockFetch, tokenFor } from '../test-utils.js';
import { ApiError, request, setUnauthorizedHandler } from './client.js';

describe('request', () => {
  let onUnauthorized;

  beforeEach(() => {
    sessionStorage.clear();
    onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
  });

  afterEach(() => {
    setUnauthorizedHandler(null);
    vi.unstubAllGlobals();
  });

  it('sends the stored token as a bearer header and returns parsed JSON', async () => {
    const token = tokenFor('alice_mgr');
    sessionStorage.setItem('eip.token', token);
    const fetchMock = mockFetch(jsonResponse(200, { hits: [] }));

    await expect(request('/api/search', { method: 'POST', body: { query: 'x' } })).resolves.toEqual({
      hits: [],
    });

    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe('/api/search');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe(`Bearer ${token}`);
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body)).toEqual({ query: 'x' });
  });

  it('sends no token when auth is false, even if one is stored', async () => {
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
    const fetchMock = mockFetch(jsonResponse(200, {}));

    await request('/api/auth/login', { method: 'POST', body: {}, auth: false });

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('omits the JSON content type when there is no body', async () => {
    const fetchMock = mockFetch(jsonResponse(200, []));
    await request('/api/documents');
    expect(fetchMock.mock.calls[0][1].headers['Content-Type']).toBeUndefined();
    expect(fetchMock.mock.calls[0][1].body).toBeUndefined();
  });

  it('sends form data as multipart without forcing a content type', async () => {
    const fetchMock = mockFetch(jsonResponse(201, { id: 11 }));
    const form = new FormData();
    form.append('category', 'HR');

    await expect(request('/api/documents', { method: 'POST', form })).resolves.toEqual({ id: 11 });

    const init = fetchMock.mock.calls[0][1];
    expect(init.body).toBe(form);
    expect(init.headers['Content-Type']).toBeUndefined();
  });

  it('returns null for an empty success body', async () => {
    mockFetch(jsonResponse(204));
    await expect(request('/api/anything')).resolves.toBeNull();
  });

  it("surfaces the backend's message for a client error", async () => {
    mockFetch(jsonResponse(400, { error: 'Bad Request', message: 'size must be between 1 and 100' }));

    const error = await request('/api/search').catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.message).toBe('size must be between 1 and 100');
  });

  it('hides raw exception text from server errors', async () => {
    mockFetch(jsonResponse(500, { error: 'Internal Server Error', message: 'NullPointerException at Foo' }));

    const error = await request('/api/search').catch((e) => e);

    expect(error.status).toBe(500);
    expect(error.message).not.toContain('NullPointerException');
  });

  it('reports an unreachable server distinctly', async () => {
    mockFetch(new TypeError('Failed to fetch'));

    const error = await request('/api/health').catch((e) => e);

    expect(error.status).toBe(0);
    expect(error.message).toMatch(/cannot reach the server/i);
  });

  it('ends the session when the backend rejects a token it was sent', async () => {
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
    mockFetch(jsonResponse(401, { error: 'Unauthorized' }));

    const error = await request('/api/documents').catch((e) => e);

    expect(error.status).toBe(401);
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('does not treat a failed login as an ended session', async () => {
    mockFetch(jsonResponse(401, { error: 'Unauthorized', message: 'Invalid username or password' }));

    const error = await request('/api/auth/login', { method: 'POST', body: {}, auth: false }).catch(
      (e) => e,
    );

    expect(error.message).toBe('Invalid username or password');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('does not send a locally expired token at all', async () => {
    sessionStorage.setItem('eip.token', makeToken({ sub: 'alice_mgr', exp: 1 }));
    const fetchMock = mockFetch(jsonResponse(200, {}));

    const error = await request('/api/documents').catch((e) => e);

    expect(error.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });
});
