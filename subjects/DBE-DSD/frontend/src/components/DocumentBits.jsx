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

const FILE_TYPES = {
  'application/pdf': 'PDF document',
  'text/plain': 'Plain text',
  'text/markdown': 'Markdown',
  'text/csv': 'CSV',
  'application/json': 'JSON',
  'application/msword': 'Word document',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'Word document',
  'application/vnd.ms-excel': 'Excel spreadsheet',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'Excel spreadsheet',
  'application/vnd.ms-powerpoint': 'PowerPoint presentation',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'PowerPoint presentation',
};

/** A reader-friendly name for a MIME type; unknown types are shown as they are. */
export function fileTypeLabel(mimeType) {
  return FILE_TYPES[mimeType] ?? mimeType;
}

/** `COMPLETED` -> `Completed`, `authors` -> `Authors`, `page_count` -> `Page count`. */
export function humanize(value) {
  if (value === null || value === undefined) return value;
  const words = String(value)
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

const absoluteFormat = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' });

export function formatDateTime(value) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : absoluteFormat.format(date);
}
