import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router';
import { useAuth } from '../auth/AuthContext.jsx';
import { can, roleLabel } from '../auth/roles.js';
import { initials } from '../pages/dashboardData.js';
import BrandMark from './BrandMark.jsx';
import Mountains from './Mountains.jsx';
import {
  BarChartIcon,
  ChevronDownIcon,
  FileTextIcon,
  HomeIcon,
  LogOutIcon,
  SearchIcon,
  ShieldIcon,
  TagIcon,
} from './icons.jsx';

// Pages that are not built yet are shown, marked as coming, so the navigation
// matches the product plan without linking to pages that do not exist.
const NAV_ITEMS = [
  { label: 'Dashboard', to: '/', Icon: HomeIcon },
  { label: 'Search', to: '/search', Icon: SearchIcon },
  // A document belongs to the repository, so its viewer keeps Repository lit.
  { label: 'Repository', to: '/repository', also: '/documents/', Icon: FileTextIcon },
  { label: 'Categories', Icon: TagIcon },
  { label: 'Analytics', Icon: BarChartIcon },
  { label: 'Administration', to: '/admin', Icon: ShieldIcon, requiresPermission: 'USER_MANAGE' },
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
          {navItems.map(({ label, to, also, Icon }) =>
            to ? (
              <NavLink
                key={label}
                to={to}
                end
                className={({ isActive }) =>
                  `nav-item${isActive || (also && pathname.startsWith(also)) ? ' active' : ''}`
                }
              >
                <Icon />
                <span>{label}</span>
              </NavLink>
            ) : (
              <span key={label} className="nav-item nav-item--soon" aria-disabled="true">
                <Icon />
                <span>{label}</span>
                <span className="soon-badge">Soon</span>
              </span>
            ),
          )}
        </nav>

        <figure className="scenic-card sidebar__card">
          <Mountains className="scenic-card__art" />
          <blockquote>Information finds its purpose when people do.</blockquote>
          <figcaption>EIP</figcaption>
        </figure>
      </aside>

      <div className="shell__main">
        <header className="topbar">
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

function AccountMenu({ name, role, onSignOut }) {
  const [open, setOpen] = useState(false);
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
  }, [open]);

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
        <ChevronDownIcon className="account__chevron" />
      </button>

      {open && (
        <div className="account__menu glass-panel" role="menu">
          <button type="button" role="menuitem" className="menu-item" onClick={onSignOut}>
            <LogOutIcon />
            Sign out
          </button>
        </div>
      )}
    </div>
  );
}
