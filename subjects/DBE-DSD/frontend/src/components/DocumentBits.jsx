import { STATUS_LABELS } from '../pages/dashboardData.js';

// Small pieces every document list and the viewer render the same way.

export function fileTone(type = '') {
  switch ((type ?? '').toUpperCase()) {
    case 'PDF':
      return 'red';
    case 'DOC':
    case 'DOCX':
      return 'blue';
    case 'XLS':
    case 'XLSX':
      return 'green';
    case 'PPT':
    case 'PPTX':
      return 'orange';
    default:
      return 'slate';
  }
}

export function FileBadge({ type }) {
  return <span className={`file-badge file-badge--${fileTone(type)}`}>{type || 'FILE'}</span>;
}

export function StatusPill({ status }) {
  return (
    <span className={`status status--${(status ?? '').toLowerCase()}`}>{STATUS_LABELS[status] ?? status ?? '—'}</span>
  );
}

export function CategoryTag({ category }) {
  return category ? <span className="tag">{category}</span> : <span className="muted">—</span>;
}

const absoluteFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' });

export function formatDateTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : absoluteFormat.format(date);
}
