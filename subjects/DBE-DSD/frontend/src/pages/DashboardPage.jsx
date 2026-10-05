import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { useApi } from '../api/useApi.js';
import { useAuth } from '../auth/AuthContext.jsx';
import { can } from '../auth/roles.js';
import { EmptyDocumentsArt, HeroArt, ScribbleArrow, Wave } from '../components/DashboardArt.jsx';
import { CategoryTag, FileBadge, StatusPill } from '../components/DocumentBits.jsx';
import { modeLabel, parseSearchMode, SearchModePicker } from '../components/SearchBits.jsx';
import {
  ActivityIcon,
  AlertIcon,
  ArrowRightIcon,
  BarChartIcon,
  ChevronRightIcon,
  ClockIcon,
  CubeIcon,
  DatabaseIcon,
  FileTextIcon,
  FolderIcon,
  PencilIcon,
  PlusIcon,
  SearchIcon,
  ShieldCheckIcon,
  SparkleIcon,
  UploadIcon,
  ZapIcon,
} from '../components/icons.jsx';
import { greeting, recentActivity, recentDocuments, relativeTime, summarize } from './dashboardData.js';

export default function DashboardPage() {
  const documents = useApi('/api/documents');
  const collection = useApi('/api/search/vector/collection-info');
  const searchActivity = useApi('/api/search/history?limit=5');
  const { session, profile } = useAuth();
  const firstName = (profile?.fullName || session.username).split(' ')[0];
  const canUpload = can(profile, 'DOCUMENT_CREATE');

  return (
    <div className="page dashboard">
      <Hero name={firstName} canUpload={canUpload} />

      <Overview documents={documents} collection={collection} />

      <div className="dashboard__grid">
        <RecentDocuments state={documents} canUpload={canUpload} />
        <div className="dashboard__side">
          <SearchActivity state={searchActivity} />
          <RecentActivity state={documents} />
        </div>
      </div>

      <ExploreFeatures canManageUsers={can(profile, 'USER_MANAGE')} />
    </div>
  );
}

/**
 * The greeting, the search mode, and the way in for new documents. The mode is
 * kept in the URL (`/?mode=fuzzy`) because the search box it applies to lives in
 * the top bar, outside this page.
 */
function Hero({ name, canUpload }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const mode = parseSearchMode(searchParams.get('mode'));

  function changeMode(next) {
    setSearchParams(next === 'hybrid' ? {} : { mode: next }, { replace: true });
  }

  return (
    <section className="hero" aria-label="Welcome">
      <div className="hero__text">
        <h1 className="hero__title">
          {greeting()},{' '}
          <span className="hero__name">
            {name}
            <span className="hero__wave" aria-hidden="true">
              {' '}
              👋
            </span>
          </span>
        </h1>
        <p className="hero__copy">
          Search, manage and explore your organizational knowledge with the power of semantic search.
        </p>
        <SearchModePicker value={mode} onChange={changeMode} showHint={false} />
      </div>

      <HeroArt className="hero__art" />

      {canUpload && <UploadDropTarget />}
    </section>
  );
}

/** The upload button; a file dropped on it opens the upload form with that file already chosen. */
function UploadDropTarget() {
  const navigate = useNavigate();
  const [dragging, setDragging] = useState(false);

  return (
    <div
      className={`hero__upload${dragging ? ' is-dragging' : ''}`}
      onDragOver={(event) => {
        event.preventDefault();
        setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault();
        setDragging(false);
        const file = event.dataTransfer.files?.[0];
        if (file) {
          navigate('/upload', { state: { file } });
        }
      }}
    >
      <Link className="btn btn--primary btn--large" to="/upload">
        <UploadIcon size={20} />
        Upload document
      </Link>
      <p className="hero__upload-hint">Drag and drop or click to upload</p>
      <p className="hero__scribble" aria-hidden="true">
        <ScribbleArrow />
        <span>
          Turn documents
          <br />
          into searchable knowledge
        </span>
      </p>
    </div>
  );
}

function Overview({ documents, collection }) {
  const ready = documents.status === 'ready';
  const summary = ready ? summarize(documents.data ?? []) : null;
  const show = (value) => (ready ? value : '—');
  const chunks = collection.status === 'ready' ? collection.data?.pointsCount ?? '—' : '—';

  const tiles = [
    { label: 'Documents', value: show(summary?.total), hint: 'Manage and access your documents', Icon: FileTextIcon, tone: 'green', to: '/repository' },
    { label: 'Indexed', value: show(summary?.indexed), hint: 'Ready for semantic search', Icon: DatabaseIcon, tone: 'blue', to: '/repository?status=INDEXED' },
    { label: 'Categories', value: show(summary?.categories), hint: 'Across your documents', Icon: FolderIcon, tone: 'amber', to: '/repository' },
    { label: 'Vector chunks', value: chunks, hint: 'In the search index', Icon: CubeIcon, tone: 'coral', to: '/search?mode=semantic' },
  ];

  return (
    <section className="kpi-grid" aria-label="Overview">
      {tiles.map(({ label, value, hint, Icon, tone, to }) => (
        <div key={label} className={`kpi tone--${tone}`}>
          <span className="kpi__icon">
            <Icon size={22} />
          </span>
          <dl className="kpi__body">
            <dt className="kpi__label">{label}</dt>
            <dd className="kpi__value">{value}</dd>
            <dd className="kpi__hint">{hint}</dd>
          </dl>
          <Link className="round-link" to={to} aria-label={`Open ${label.toLowerCase()}`}>
            <ChevronRightIcon size={16} />
          </Link>
          <Wave />
        </div>
      ))}
    </section>
  );
}

function PanelHeader({ id, Icon, title, link, linkLabel }) {
  return (
    <div className="panel__header">
      <h2 id={id} className="panel__title">
        <Icon className="panel__icon" size={20} />
        {title}
      </h2>
      {link && (
        <Link className="view-all" to={link} aria-label={linkLabel}>
          View all <ArrowRightIcon size={15} />
        </Link>
      )}
    </div>
  );
}

function EmptyPanel({ Icon, title, children }) {
  return (
    <div className="empty-panel">
      <Icon className="empty-panel__icon" size={40} />
      <p className="empty-panel__title">{title}</p>
      <p className="empty-panel__text">{children}</p>
    </div>
  );
}

function RecentDocuments({ state, canUpload }) {
  return (
    <section className="glass-panel panel" aria-labelledby="recent-documents-title">
      <PanelHeader id="recent-documents-title" Icon={FileTextIcon} title="Recent documents" link="/repository" linkLabel="View all documents" />

      {state.status === 'loading' && <p className="muted">Loading documents…</p>}
      {state.status === 'error' && (
        <p className="form-error" role="alert">
          {state.error.message}
        </p>
      )}
      {state.status === 'ready' && state.data.length === 0 && (
        <div className="empty-documents">
          <EmptyDocumentsArt />
          <p className="empty-documents__title">No documents yet</p>
          {canUpload ? (
            <>
              <p className="empty-documents__text">
                Upload your first document to start building your knowledge base.
                <br />
                Once you upload documents, they will appear here.
              </p>
              <Link className="btn btn--primary btn--large" to="/upload">
                <UploadIcon size={18} />
                Upload document
              </Link>
            </>
          ) : (
            <p className="empty-documents__text">You don’t have access to any documents yet.</p>
          )}
        </div>
      )}
      {state.status === 'ready' && state.data.length > 0 && (
        <div className="table-scroll">
          <table className="doc-table">
            <thead>
              <tr>
                <th scope="col">Title</th>
                <th scope="col">Category</th>
                <th scope="col">Status</th>
                <th scope="col">Updated</th>
              </tr>
            </thead>
            <tbody>
              {recentDocuments(state.data).map((doc) => (
                <tr key={doc.id}>
                  <td>
                    <div className="doc-title">
                      <FileBadge type={doc.documentType} />
                      <span>
                        <Link className="doc-title__name doc-link" to={`/documents/${doc.id}`}>
                          {doc.title}
                        </Link>
                        {doc.description && <span className="doc-title__description">{doc.description}</span>}
                      </span>
                    </div>
                  </td>
                  <td>
                    <CategoryTag category={doc.category} />
                  </td>
                  <td>
                    <StatusPill status={doc.status} />
                  </td>
                  <td className="muted nowrap">{relativeTime(doc.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function searchLink({ query, mode }) {
  const params = { q: query };
  const parsed = parseSearchMode(mode?.toLowerCase());
  if (parsed !== 'hybrid') params.mode = parsed;
  return `/search?${new URLSearchParams(params)}`;
}

/** The signed-in user's own recent searches; nobody else's are ever shown. */
function SearchActivity({ state }) {
  const entries = state.data;
  return (
    <section className="glass-panel panel panel--side" aria-labelledby="search-activity-title">
      <PanelHeader id="search-activity-title" Icon={ClockIcon} title="Your recent searches" link="/search" linkLabel="View all searches" />
      {state.status === 'loading' && !entries && <p className="muted">Loading…</p>}
      {state.status === 'error' && (
        <p className="muted activity-unavailable">
          <AlertIcon size={16} /> Search activity is unavailable.
        </p>
      )}
      {entries && entries.length === 0 && (
        <EmptyPanel Icon={SearchIcon} title="No recent searches">
          Your search history will appear here once you start searching.
        </EmptyPanel>
      )}
      {entries && entries.length > 0 && (
        <ul className="activity-list" aria-label="Your recent searches">
          {entries.map((entry) => (
            <li key={`${entry.mode}:${entry.query}`} className="activity">
              <span className="activity__icon activity__icon--search">
                <SearchIcon size={14} />
              </span>
              <span className="activity__text">
                <Link className="doc-link activity__query" to={searchLink(entry)}>
                  {entry.query}
                </Link>
                <span className="activity__title">
                  {modeLabel(entry.mode)} · {entry.resultCount} {entry.resultCount === 1 ? 'result' : 'results'}
                </span>
              </span>
              <span className="activity__time">{relativeTime(entry.searchedAt)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function RecentActivity({ state }) {
  return (
    <section className="glass-panel panel panel--side" aria-labelledby="activity-title">
      <PanelHeader id="activity-title" Icon={ActivityIcon} title="Document activity" link="/repository" linkLabel="View all document activity" />
      {state.status === 'loading' && <p className="muted">Loading…</p>}
      {state.status === 'error' && (
        <p className="muted activity-unavailable">
          <AlertIcon size={16} /> Activity is unavailable.
        </p>
      )}
      {state.status === 'ready' && state.data.length === 0 && (
        <EmptyPanel Icon={BarChartIcon} title="No activity yet">
          Upload documents to see their activity here.
        </EmptyPanel>
      )}
      {state.status === 'ready' && state.data.length > 0 && (
        <ul className="activity-list">
          {recentActivity(state.data).map((event) => (
            <li key={`${event.id}-${event.kind}`} className="activity">
              <span className={`activity__icon activity__icon--${event.kind}`}>
                {event.kind === 'updated' ? <PencilIcon size={14} /> : <PlusIcon size={14} />}
              </span>
              <span className="activity__text">
                <span className="activity__query">{event.title}</span>
                <span className="activity__title">{event.kind === 'updated' ? 'Updated' : 'Added'}</span>
              </span>
              <span className="activity__time">{relativeTime(event.at)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

const FEATURES = [
  { title: 'Smart Search', text: 'Find documents using keyword, semantic or hybrid search.', to: '/search', Icon: SearchIcon, tone: 'green' },
  { title: 'Document Repository', text: 'Upload, organize and manage your knowledge.', to: '/repository', Icon: FileTextIcon, tone: 'blue' },
  { title: 'TextHack', text: 'Advanced text search with powerful algorithms.', to: '/texthack', Icon: ZapIcon, tone: 'amber' },
  { title: 'Administration', text: 'Manage users, roles and permissions.', to: '/admin', Icon: ShieldCheckIcon, tone: 'coral', requiresUserManage: true },
];

function ExploreFeatures({ canManageUsers }) {
  const features = FEATURES.filter(({ requiresUserManage }) => !requiresUserManage || canManageUsers);
  return (
    <section className="glass-panel panel explore" aria-labelledby="explore-title">
      <h2 id="explore-title" className="panel__title">
        <SparkleIcon className="panel__icon panel__icon--sparkle" size={20} />
        Explore features
      </h2>
      <ul className="feature-grid">
        {features.map(({ title, text, to, Icon, tone }) => (
          <li key={title}>
            <Link className={`feature-card tone--${tone}`} to={to}>
              <span className="feature-card__icon">
                <Icon size={22} />
              </span>
              <span className="feature-card__text">
                <span className="feature-card__title">{title}</span>
                <span className="feature-card__body">{text}</span>
              </span>
              <span className="round-link round-link--small" aria-hidden="true">
                <ArrowRightIcon size={14} />
              </span>
              <Wave />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
