import { useState } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import BrandMark from '../components/BrandMark.jsx';
import KnowledgeGraph from '../components/KnowledgeGraph.jsx';
import Mountains from '../components/Mountains.jsx';
import ThreeBackdrop from '../components/ThreeBackdrop.jsx';
import {
  EyeIcon,
  EyeOffIcon,
  FileTextIcon,
  LockIcon,
  SearchIcon,
  ShieldIcon,
  UserIcon,
} from '../components/icons.jsx';

const FEATURES = [
  {
    title: 'Hybrid search',
    text: 'Keyword, fuzzy and semantic matching in a single query.',
    Icon: SearchIcon,
    tone: 'blue',
  },
  {
    title: 'One repository',
    text: 'Documents, their content and processing history together.',
    Icon: FileTextIcon,
    tone: 'teal',
  },
  {
    title: 'Role-based access',
    text: 'Everyone sees exactly the documents they are allowed to.',
    Icon: ShieldIcon,
    tone: 'violet',
  },
];

export default function LoginPage() {
  const { session, login } = useAuth();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showResetHelp, setShowResetHelp] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (session) {
    const from = location.state?.from;
    return <Navigate to={from ? `${from.pathname}${from.search ?? ''}` : '/'} replace />;
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username.trim(), password);
      // On success the session re-renders this page into a redirect.
    } catch (err) {
      setError(err.message);
      setSubmitting(false);
    }
  }

  return (
    <div className="login-page">
      <section className="login-hero" aria-label="About EIP">
        <div className="login-hero__scene" aria-hidden="true">
          <ThreeBackdrop scene="cloud-field" className="login-hero__three" />
          <div className="login-hero__glow" />
          <Mountains className="login-hero__mountains" />
          <div className="login-hero__shade" />
        </div>

        <div className="login-hero__content">
          <BrandMark />
          <div className="login-hero__intro">
            <h2 className="hero-title">
              Turn information <span className="gradient-text">into intelligence</span>
            </h2>
            <p className="hero-copy">
              Search, explore and govern your organisation’s documents by keyword, by meaning, or both.
            </p>

            <ul className="feature-list">
              {FEATURES.map(({ title, text, Icon, tone }) => (
                <li key={title} className="feature">
                  <span className={`icon-tile icon-tile--${tone}`}>
                    <Icon size={20} />
                  </span>
                  <span>
                    <strong>{title}</strong>
                    <span className="feature__text">{text}</span>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        <KnowledgeGraph className="login-hero__graph" />
      </section>

      <main className="login-panel">
        <form className="login-card glass-panel" onSubmit={handleSubmit}>
          {/* The hero carries the brand on wide screens; below 1100px it is hidden, so the card does. */}
          <BrandMark className="login-card__brand" subtitle="" />

          <h1>Sign in</h1>
          <p className="muted login-card__intro">Use your EIP account to continue.</p>

          <label htmlFor="username">Username</label>
          <div className="field">
            <UserIcon className="field__icon" />
            <input
              id="username"
              name="username"
              autoComplete="username"
              placeholder="Enter your username"
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </div>

          <label htmlFor="password">Password</label>
          <div className="field">
            <LockIcon className="field__icon" />
            <input
              id="password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="Enter your password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button
              type="button"
              className="field__action"
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              aria-pressed={showPassword}
              onClick={() => setShowPassword((shown) => !shown)}
            >
              {showPassword ? <EyeIcon /> : <EyeOffIcon />}
            </button>
          </div>

          <div className="login-card__links">
            <button
              type="button"
              className="link-button"
              aria-expanded={showResetHelp}
              aria-controls="reset-help"
              onClick={() => setShowResetHelp((shown) => !shown)}
            >
              Forgot password?
            </button>
          </div>
          {showResetHelp && (
            <p id="reset-help" className="notice">
              Password resets are handled by your EIP administrator.
            </p>
          )}

          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}

          <button type="submit" className="btn btn--primary btn--block" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </button>

          <p className="login-card__footer">New to EIP? Ask your administrator for an account.</p>
        </form>

        <footer className="login-panel__footer">© {new Date().getFullYear()} EIP. All rights reserved.</footer>
      </main>
    </div>
  );
}
