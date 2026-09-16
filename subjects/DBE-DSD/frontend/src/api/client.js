import { readToken, sessionFromToken } from '../auth/session.js';

export class ApiError extends Error {
  constructor(status, message, body = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}

let unauthorizedHandler = null;

/** Called when the backend rejects the stored token. The auth provider registers logout here. */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

/**
 * Calls the backend and returns the parsed JSON body (null when empty).
 *
 * Requests carry the stored bearer token unless `auth` is false. Failures throw
 * ApiError with a message fit to show a user.
 */
export async function request(path, { method = 'GET', body, auth = true } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const token = auth ? readToken() : null;
  if (token) {
    if (!sessionFromToken(token)) {
      // Expired locally: the backend would only reject it, so do not send it.
      unauthorizedHandler?.();
      throw new ApiError(401, SESSION_EXPIRED);
    }
    headers.Authorization = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check that the backend is running.');
  }

  const data = await readJson(response);
  if (response.ok) {
    return data;
  }

  // A rejected token ends the session however it happened: expiry, a rotated
  // JWT_SECRET, or a removed user. A 401 without a token is a failed login and
  // is left to the caller.
  if (response.status === 401 && token) {
    unauthorizedHandler?.();
    throw new ApiError(401, SESSION_EXPIRED, data);
  }
  throw new ApiError(response.status, messageFor(response.status, data), data);
}

const SESSION_EXPIRED = 'Your session has expired. Sign in again.';

async function readJson(response) {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function messageFor(status, data) {
  // 5xx bodies from this backend can carry raw exception text; it means nothing
  // to a user and may describe internals, so it is not shown.
  if (status >= 500) {
    return 'The server is unavailable or hit an error. Try again shortly.';
  }
  if (data && typeof data.message === 'string' && data.message) {
    return data.message;
  }
  return `Request failed (${status}).`;
}
