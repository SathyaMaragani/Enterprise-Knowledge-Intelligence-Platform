import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { Navigate, useLocation } from 'react-router';
import { request, setUnauthorizedHandler, ApiError } from '../api/client.js';
import { clearToken, readToken, saveToken, sessionFromToken } from './session.js';

// setTimeout overflows above 2^31-1 ms (about 24.8 days) and fires at once.
const MAX_TIMER_MS = 2 ** 31 - 1;

const AuthContext = createContext(null);

function restoreSession() {
  const session = sessionFromToken(readToken());
  if (!session) {
    clearToken();
  }
  return session;
}

export function AuthProvider({ children }) {
  const [session, setSession] = useState(restoreSession);
  const [profile, setProfile] = useState(null);
  const token = session?.token ?? null;

  // Who the user is and what they may do, loaded once per token. Until it
  // arrives, or if it fails, the UI shows only what needs no role.
  useEffect(() => {
    setProfile(null);
    if (!token) {
      return undefined;
    }
    let active = true;
    request('/api/auth/me').then(
      (data) => active && setProfile(data),
      () => active && setProfile(null),
    );
    return () => {
      active = false;
    };
  }, [token]);

  const logout = useCallback(() => {
    clearToken();
    setSession(null);
  }, []);

  const login = useCallback(async (username, password) => {
    const response = await request('/api/auth/login', {
      method: 'POST',
      body: { username, password },
      auth: false,
    });
    const next = sessionFromToken(response?.token);
    if (!next) {
      throw new ApiError(0, 'The server returned an unusable sign-in token.');
    }
    saveToken(next.token);
    setSession(next);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  // End the session when the token expires, not on the next request. Timers are
  // capped, so a long-lived token re-checks and re-arms instead of overflowing.
  useEffect(() => {
    if (!session) {
      return undefined;
    }
    const delay = Math.min(session.expiresAt - Date.now(), MAX_TIMER_MS);
    const timer = setTimeout(() => setSession(restoreSession()), delay);
    return () => clearTimeout(timer);
  }, [session]);

  const value = useMemo(
    () => ({ session, profile, login, logout }),
    [session, profile, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}

export function RequireAuth({ children }) {
  const { session } = useAuth();
  const location = useLocation();
  if (!session) {
    // Remember where the user was going, so sign-in can return them there.
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return children;
}
