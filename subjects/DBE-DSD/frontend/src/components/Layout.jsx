import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate, useSearchParams } from 'react-router';
import { useApi } from '../api/useApi.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { can, roleLabel } from '../auth/roles.js';
import { initials } from '../pages/dashboardData.js';
import BrandMark from './BrandMark.jsx';
import { modeParam, parseSearchMode } from './SearchBits.jsx';
import {
  BarChartIcon,
  BellIcon,
  ChevronDownIcon,
  FileTextIcon,
  HomeIcon,
  LightbulbIcon,
  LogOutIcon,
  SearchIcon,
  ShieldCheckIcon,
  SlidersIcon,
  ZapIcon,
} from './icons.jsx';

const NAV_ITEMS = [
  { label: 'Dashboard', to: '/', Icon: HomeIcon },
  { label: 'Search', to: '/search', Icon: SearchIcon },
  // A document belongs to the repository, so its viewer keeps Repository lit.
  { label: 'Repository', to: '/repository', also: '/documents/', Icon: FileTextIcon },
  { label: 'TextHack', to: '/texthack', Icon: ZapIcon },
  { label: 'ML insights', to: '/insights', Icon: BarChartIcon },
  { label: 'Administration', to: '/admin', Icon: ShieldCheckIcon, requiresPermission: 'USER_MANAGE' },
];

export default function Layout() {
  const { session, profile, logout } = useAuth();
  const { pathname } = useLocation();
  const navItems = NAV_ITEMS.filter(({ requiresPermission }) => !requiresPermission || can(profile, requiresPermission));

  return (
    <div className="shell">
      <aside className="sidebar">
        <BrandMark subtitle="Knowledge. Connected." className="sidebar__brand" />

        <nav className="sidebar__nav" aria-label="Main">
          {navItems.map(({ label, to, also, Icon }) => (
            <NavLink
              key={label}
              to={to}
              end
              className={({ isActive }) =>
                `nav-item${isActive || (also && pathname.startsWith(also)) ? ' active' : ''}`
              }
            >
              <Icon size={19} />
              <span>{label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar__note">
          <LightbulbIcon className="sidebar__note-icon" size={22} />
          <p>
            <strong>Turn knowledge into impact.</strong>
            <span>Search smarter. Work faster.</span>
          </p>
        </div>
      </aside>

      <div className="shell__main">
        <header className="topbar">
          {/* The search page leads with its own, fuller search form. */}
          {pathname !== '/search' && <GlobalSearch />}
          <Notifications />
          <AccountMenu
            name={profile?.fullName || session.username}
            role={roleLabel(profile) ?? 'Signed in'}
            onSignOut={logout}
          />
        </header>
        <Outlet />
      </div>
    </div>
  );
}

/**
 * Search from anywhere: submitting opens the search page with the query and
 * category. On the dashboard, the mode chosen in its hero (`?mode=`) goes too.
 */
function GlobalSearch() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const categories = useApi('/api/categories');
  const [query, setQuery] = useState('');
  const [category, setCategory] = useState('');

  return (
    <>
      <form
        className="topbar-search"
        role="search"
        aria-label="Global search"
        onSubmit={(event) => {
          event.preventDefault();
          const q = query.trim();
          if (!q) {
            return;
          }
          const params = { q };
          if (category) params.category = category;
          const mode = modeParam(parseSearchMode(searchParams.get('mode')));
          if (mode) params.mode = mode.toLowerCase();
          navigate(`/search?${new URLSearchParams(params)}`);
          setQuery('');
        }}
      >
        <SearchIcon className="topbar-search__icon" size={19} />
        <input
          type="search"
          aria-label="Search enterprise knowledge"
          placeholder="Search documents, ask a question, or enter keywords…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select
          aria-label="Search in category"
          className="topbar-search__select"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
        >
          <option value="">All categories</option>
          {(categories.data ?? []).map((item) => (
            <option key={item.id} value={item.name}>
              {item.name}
            </option>
          ))}
        </select>
      </form>
      <Link className="topbar-search__filters" to="/search" aria-label="Advanced search filters">
        <SlidersIcon size={19} />
      </Link>
    </>
  );
}

/** Closes a popover on an outside click or Escape. */
function useDismiss(open, setOpen) {
  const containerRef = useRef(null);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const closeOnOutside = (event) => {
      if (!containerRef.current?.contains(event.target)) {
        setOpen(false);
      }
    };
    const closeOnEscape = (event) => {
      if (event.key === 'Escape') {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', closeOnOutside);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('mousedown', closeOnOutside);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [open, setOpen]);

  return containerRef;
}

/**
 * The backend sends no notifications yet, so the bell says so honestly rather
 * than showing an unread marker with nothing behind it.
 */
function Notifications() {
  const [open, setOpen] = useState(false);
  const containerRef = useDismiss(open, setOpen);

  return (
    <div className="notifications" ref={containerRef}>
      <button
        type="button"
        className="icon-button topbar__bell"
        aria-label="Notifications"
        aria-expanded={open}
        onClick={() => setOpen((isOpen) => !isOpen)}
      >
        <BellIcon size={21} />
      </button>
      {open && (
        <div className="popover glass-panel" role="status">
          <p className="popover__title">Notifications</p>
          <p className="muted">You’re all caught up.</p>
        </div>
      )}
    </div>
  );
}

function AccountMenu({ name, role, onSignOut }) {
  const [open, setOpen] = useState(false);
  const containerRef = useDismiss(open, setOpen);

  return (
    <div className="account" ref={containerRef}>
      <button
        type="button"
        className="account__button"
        aria-haspopup="menu"
        aria-label={`Account menu for ${name}`}
        aria-expanded={open}
        onClick={() => setOpen((isOpen) => !isOpen)}
      >
        <span className="avatar" aria-hidden="true">
          {initials(name)}
        </span>
        <span className="account__text">
          <span className="account__name">{name}</span>
          <span className="account__role">{role}</span>
        </span>
        <ChevronDownIcon className="account__chevron" size={16} />
      </button>

      {open && (
        <div className="account__menu popover glass-panel" role="menu">
          <button type="button" role="menuitem" className="menu-item" onClick={onSignOut}>
            <LogOutIcon />
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
