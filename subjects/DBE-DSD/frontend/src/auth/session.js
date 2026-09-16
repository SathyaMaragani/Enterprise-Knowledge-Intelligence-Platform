// The JWT is kept in sessionStorage: it survives a reload but not closing the
// tab, which bounds how long a token left behind on a shared machine stays
// usable. localStorage would keep it for the token's full lifetime (24 hours by
// default). Neither is safe from XSS; an httpOnly cookie would be, but that
// needs backend support.
const TOKEN_KEY = 'eip.token';

// Browsers can refuse storage access outright (privacy modes, blocked site
// data). Treat that as "no session" rather than crashing the app.
export function readToken() {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function saveToken(token) {
  try {
    sessionStorage.setItem(TOKEN_KEY, token);
  } catch {
    // The session then lasts until reload, which is still usable.
  }
}

export function clearToken() {
  try {
    sessionStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing stored, nothing to clear.
  }
}

/**
 * The JWT payload as an object, or null if the token is not a well-formed JWT.
 *
 * This does NOT verify the signature. The backend verifies it on every request;
 * the client only reads `sub` and `exp` to show who is signed in and to end the
 * session on time.
 */
export function decodeToken(token) {
  if (typeof token !== 'string') {
    return null;
  }
  const parts = token.split('.');
  if (parts.length !== 3) {
    return null;
  }
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
    const payload = JSON.parse(new TextDecoder().decode(bytes));
    return payload !== null && typeof payload === 'object' ? payload : null;
  } catch {
    return null;
  }
}

/** The signed-in session a token describes, or null if it is malformed or expired. */
export function sessionFromToken(token, now = Date.now()) {
  const payload = decodeToken(token);
  if (!payload || typeof payload.sub !== 'string' || typeof payload.exp !== 'number') {
    return null;
  }
  const expiresAt = payload.exp * 1000;
  if (expiresAt <= now) {
    return null;
  }
  return { token, username: payload.sub, expiresAt };
}
