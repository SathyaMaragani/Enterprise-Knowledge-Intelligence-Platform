import { vi } from 'vitest';

/** An unsigned JWT with the given payload. Signatures are the backend's concern. */
export function makeToken(payload) {
  const encode = (value) =>
    btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(value))))
      .replace(/=+$/, '')
      .replace(/\+/g, '-')
      .replace(/\//g, '_');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode(payload)}.signature`;
}

export function tokenFor(username, secondsFromNow = 3600) {
  return makeToken({ sub: username, exp: Math.floor(Date.now() / 1000) + secondsFromNow });
}

/** Just enough of a fetch Response for the API client. */
export function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => (body === undefined ? '' : JSON.stringify(body)),
  };
}

/** Replaces global fetch with a mock answering each call in turn. */
export function mockFetch(...responses) {
  const fetchMock = vi.fn();
  for (const response of responses) {
    if (response instanceof Error) {
      fetchMock.mockRejectedValueOnce(response);
    } else {
      fetchMock.mockResolvedValueOnce(response);
    }
  }
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}
