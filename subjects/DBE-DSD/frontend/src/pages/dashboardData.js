// Pure helpers that turn backend responses into what the dashboard shows. Every
// figure is derived from real API data; nothing here is a placeholder.

function timeOf(value) {
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? 0 : time;
}

/** A time-of-day greeting in the viewer's local time. */
export function greeting(date = new Date()) {
  const hour = date.getHours();
  if (hour < 12) return 'Good morning';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

/** Headline counts over the documents the current user may read. */
export function summarize(documents) {
  return {
    total: documents.length,
    categories: categoryNames(documents).length,
    indexed: documents.filter((doc) => doc.status === 'INDEXED').length,
  };
}

export function categoryNames(documents) {
  return [...new Set(documents.map((doc) => doc.category).filter(Boolean))].sort();
}

/** Most recently updated first; ties broken by newest id so the order is stable. */
export function recentDocuments(documents, limit = 5) {
  return [...documents]
    .sort((a, b) => timeOf(b.updatedAt) - timeOf(a.updatedAt) || b.id - a.id)
    .slice(0, limit);
}

/**
 * Activity reconstructed from document timestamps. The backend records no audit
 * trail, so this reports only what the timestamps prove: when a document was
 * added, or last changed if it has been edited since.
 */
export function recentActivity(documents, limit = 5) {
  return documents
    .map((doc) => {
      // Timestamps a second apart come from the same insert, not a later edit.
      const edited = timeOf(doc.updatedAt) - timeOf(doc.createdAt) > 1000;
      return {
        id: doc.id,
        kind: edited ? 'updated' : 'added',
        title: doc.title,
        at: edited ? doc.updatedAt : doc.createdAt,
      };
    })
    .sort((a, b) => timeOf(b.at) - timeOf(a.at) || b.id - a.id)
    .slice(0, limit);
}

const UNITS = [
  ['year', 31536000],
  ['month', 2592000],
  ['week', 604800],
  ['day', 86400],
  ['hour', 3600],
  ['minute', 60],
];

const relativeFormat = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

export function relativeTime(value, now = Date.now()) {
  const time = new Date(value).getTime();
  if (Number.isNaN(time)) {
    return '';
  }
  const seconds = Math.round((time - now) / 1000);
  for (const [unit, size] of UNITS) {
    if (Math.abs(seconds) >= size) {
      return relativeFormat.format(Math.trunc(seconds / size), unit);
    }
  }
  return 'just now';
}

/** "alice_mgr" -> "AM", "dave" -> "DA". */
export function initials(username = '') {
  const parts = username.split(/[^a-zA-Z0-9]+/).filter(Boolean);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }
  return (parts[0] ?? '?').slice(0, 2).toUpperCase();
}

export const STATUS_LABELS = {
  INDEXED: 'Indexed',
  PROCESSING: 'Processing',
  UPLOADED: 'Uploaded',
  ARCHIVED: 'Archived',
  FAILED: 'Failed',
};

export const MATCH_LABELS = {
  KEYWORD: 'Keyword',
  VECTOR: 'Semantic',
  FUZZY: 'Fuzzy',
};
