import { NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';

export default function Layout() {
  const { session, logout } = useAuth();

  return (
    <div className="app">
      <header className="app-header">
        <span className="brand">Enterprise Knowledge Intelligence</span>
        <nav aria-label="Main">
          <NavLink to="/" end>
            Home
          </NavLink>
        </nav>
        <div className="account">
          <span className="muted">{session.username}</span>
          <button type="button" onClick={logout}>
            Sign out
          </button>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
