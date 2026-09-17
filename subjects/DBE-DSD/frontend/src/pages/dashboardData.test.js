import { describe, expect, it } from 'vitest';
import { documentFixture } from '../test-utils.js';
import {
  categoryNames,
  greeting,
  initials,
  recentActivity,
  recentDocuments,
  relativeTime,
  summarize,
} from './dashboardData.js';

describe('greeting', () => {
  it('follows the local time of day', () => {
    expect(greeting(new Date(2026, 8, 17, 0, 0))).toBe('Good morning');
    expect(greeting(new Date(2026, 8, 17, 11, 59))).toBe('Good morning');
    expect(greeting(new Date(2026, 8, 17, 12, 0))).toBe('Good afternoon');
    expect(greeting(new Date(2026, 8, 17, 17, 59))).toBe('Good afternoon');
    expect(greeting(new Date(2026, 8, 17, 18, 0))).toBe('Good evening');
  });
});

describe('summarize', () => {
  it('counts documents, distinct categories and indexed documents', () => {
    const docs = [
      documentFixture({ id: 1, category: 'HR', status: 'INDEXED' }),
      documentFixture({ id: 2, category: 'HR', status: 'FAILED' }),
      documentFixture({ id: 3, category: 'Legal', status: 'INDEXED' }),
      documentFixture({ id: 4, category: null, status: 'UPLOADED' }),
    ];
    expect(summarize(docs)).toEqual({ total: 4, categories: 2, indexed: 2 });
  });

  it('handles no documents', () => {
    expect(summarize([])).toEqual({ total: 0, categories: 0, indexed: 0 });
  });
});

describe('categoryNames', () => {
  it('is distinct, sorted and skips documents without a category', () => {
    const docs = [
      documentFixture({ category: 'Legal' }),
      documentFixture({ category: 'Finance' }),
      documentFixture({ category: 'Legal' }),
      documentFixture({ category: null }),
    ];
    expect(categoryNames(docs)).toEqual(['Finance', 'Legal']);
  });
});

describe('recentDocuments', () => {
  it('orders by last update, newest first, with ties broken by newest id', () => {
    const docs = [
      documentFixture({ id: 1, updatedAt: '2026-09-01T00:00:00Z' }),
      documentFixture({ id: 2, updatedAt: '2026-09-05T00:00:00Z' }),
      documentFixture({ id: 3, updatedAt: '2026-09-01T00:00:00Z' }),
    ];
    expect(recentDocuments(docs).map((d) => d.id)).toEqual([2, 3, 1]);
  });

  it('limits the list and does not reorder the input', () => {
    const docs = [1, 2, 3, 4, 5, 6].map((id) => documentFixture({ id, updatedAt: `2026-09-0${id}T00:00:00Z` }));
    expect(recentDocuments(docs, 2).map((d) => d.id)).toEqual([6, 5]);
    expect(docs.map((d) => d.id)).toEqual([1, 2, 3, 4, 5, 6]);
  });
});

describe('recentActivity', () => {
  it('reports an edit only when the update is later than the insert', () => {
    const docs = [
      documentFixture({ id: 1, title: 'Added', createdAt: '2026-09-01T00:00:00Z', updatedAt: '2026-09-01T00:00:00.500Z' }),
      documentFixture({ id: 2, title: 'Edited', createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-09-03T00:00:00Z' }),
    ];
    expect(recentActivity(docs)).toEqual([
      { id: 2, kind: 'updated', title: 'Edited', at: '2026-09-03T00:00:00Z' },
      { id: 1, kind: 'added', title: 'Added', at: '2026-09-01T00:00:00Z' },
    ]);
  });
});

describe('relativeTime', () => {
  const now = Date.UTC(2026, 8, 16, 12, 0, 0);

  it.each([
    ['2026-09-16T11:59:30Z', 'just now'],
    ['2026-09-16T11:55:00Z', '5 minutes ago'],
    ['2026-09-16T09:00:00Z', '3 hours ago'],
    ['2026-09-15T12:00:00Z', 'yesterday'],
    ['2026-09-11T12:00:00Z', '5 days ago'],
    ['2026-09-02T12:00:00Z', '2 weeks ago'],
  ])('formats %s as "%s"', (value, expected) => {
    expect(relativeTime(value, now)).toBe(expected);
  });

  it('returns an empty string for an unparseable date', () => {
    expect(relativeTime('not a date', now)).toBe('');
    expect(relativeTime(undefined, now)).toBe('');
  });
});

describe('initials', () => {
  it.each([
    ['alice_mgr', 'AM'],
    ['admin_user', 'AU'],
    ['dave', 'DA'],
    ['', '?'],
  ])('%s -> %s', (username, expected) => {
    expect(initials(username)).toBe(expected);
  });
});
