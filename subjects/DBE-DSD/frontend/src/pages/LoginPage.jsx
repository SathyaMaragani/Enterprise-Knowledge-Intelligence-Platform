import { useState } from 'react';
import { Navigate } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import BrandMark from '../components/BrandMark.jsx';
import GlassStack from '../components/GlassStack.jsx';
import Mountains from '../components/Mountains.jsx';
import ThreeBackdrop from '../components/ThreeBackdrop.jsx';
import {
  ArrowRightIcon,
  BarChartIcon,
  BuildingIcon,
  EyeIcon,
  EyeOffIcon,
  FileTextIcon,
  LockIcon,
  SearchIcon,
  SunIcon,
  UserIcon,
  UsersIcon,
} from '../components/icons.jsx';

const FEATURES = [
  {
    title: 'Smart Search',
    text: 'Keyword, semantic and fuzzy search powered by advanced algorithms',
    Icon: SearchIcon,
    tone: 'blue',
  },
  {
    title: 'Unified Repository',
    text: 'Documents, knowledge and insights in one place',
    Icon: FileTextIcon,
    tone: 'teal',
  },
  {
    title: 'Secure & Role-Based',
    text: 'Right information to the right people',
    Icon: UsersIcon,
    tone: 'violet',
  },
  {
    title: 'Intelligent Insights',
    text: 'Transform knowledge into impact',
    Icon: BarChartIcon,
    tone: 'amber',
  },
];

export default function LoginPage() {
  const { session, login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showResetHelp, setShowResetHelp] = useState(false);
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  if (session) {
    return <Navigate to="/" replace />;
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
          <p className="eyebrow eyebrow--spaced">Knowledge • People • Insights • Impact</p>
          <h2 className="hero-title">
            Turn Information <span className="gradient-text">into Intelligence</span>
          </h2>
          <p className="hero-lead">Search. Discover. Understand. Empower.</p>
          <p className="hero-copy">
            A unified platform that brings together your documents, people and knowledge with the
            power of semantic search and AI-driven insights.
          </p>

          <ul className="feature-list">
            {FEATURES.map(({ title, text, Icon, tone }) => (
              <li key={title} className="feature">
                <span className={`icon-tile icon-tile--${tone}`}>
                  <Icon size={24} />
                </span>
                <span>
                  <strong>{title}</strong>
                  <span className="feature__text">{text}</span>
                </span>
              </li>
            ))}
          </ul>

          <figure className="hero-quote">
            <blockquote>“Knowledge is not just what you know, but what you can do with it.”</blockquote>
            <figcaption>EIP</figcaption>
          </figure>
        </div>

        <GlassStack className="login-hero__stack" />
      </section>

      <main className="login-panel">
        <p className="login-panel__tagline">
          <SunIcon size={28} />
          <span>
            A smarter tomorrow
            <br />
            starts with knowledge.
          </span>
        </p>

        <form className="login-card glass-panel" onSubmit={handleSubmit}>
          <BrandMark className="brand--centered" />

          <h1>Welcome Back</h1>
          <p className="muted login-card__intro">Sign in to access your knowledge workspace</p>

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
            <span>{submitting ? 'Signing in…' : 'Sign In'}</span>
            <ArrowRightIcon className="btn__trailing" />
          </button>

          <div className="divider">
            <span>or</span>
          </div>

          <button
            type="button"
            className="btn btn--outline btn--block"
            disabled
            aria-describedby="sso-note"
          >
            <BuildingIcon />
            <span>Sign in with SSO</span>
          </button>
          <p id="sso-note" className="hint">
            Single sign-on is not configured for this workspace.
          </p>

          <p className="login-card__footer">New to EIP? Contact your administrator</p>
        </form>

        <footer className="login-panel__footer">© {new Date().getFullYear()} EIP. All rights reserved.</footer>
      </main>
    </div>
  );
}
