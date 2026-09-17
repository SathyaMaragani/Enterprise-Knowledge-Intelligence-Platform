import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { AuthProvider } from '../auth/AuthContext.jsx';
import { documentFixture, jsonResponse, mockApi, tokenFor } from '../test-utils.js';
import { repositoryPath } from './RepositoryPage.jsx';

const CATEGORIES = [
  { id: 1, name: 'Finance', description: 'Money' },
  { id: 2, name: 'Legal', description: 'Contracts' },
];

function pageOf(items, { page = 0, totalItems = items.length, totalPages = 1 } = {}) {
  return jsonResponse(200, { items, page, size: 10, totalItems, totalPages });
}

let currentLocation;
function LocationProbe() {
  currentLocation = useLocation();
  return null;
}

function renderRepository(routes, path = '/repository') {
  const fetchMock = mockApi({ 'GET /api/categories': jsonResponse(200, CATEGORIES), ...routes });
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
        <LocationProbe />
      </AuthProvider>
    </MemoryRouter>,
  );
  return fetchMock;
}

const requestedPaths = (fetchMock) =>
  fetchMock.mock.calls.map(([path]) => path).filter((path) => path.startsWith('/api/documents/page'));

describe('repositoryPath', () => {
  it('builds parameters in a fixed order and omits empty filters', () => {
    expect(repositoryPath({ page: 0, category: '', status: '', q: '' })).toBe('/api/documents/page?page=0&size=10');
    expect(repositoryPath({ page: 2, category: 'Legal & Compliance', status: 'INDEXED', q: 'nda' })).toBe(
      '/api/documents/page?page=2&size=10&category=Legal+%26+Compliance&status=INDEXED&q=nda',
    );
  });
});

describe('repository page', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('lists a page of documents linking to the viewer', async () => {
    renderRepository({
      'GET /api/documents/page?page=0&size=10': pageOf([
        documentFixture({ id: 7, title: 'Q2 Budget Draft', category: 'Finance', owner: 'alice_mgr', documentType: 'XLSX' }),
        documentFixture({ id: 5, title: 'Vendor Contract A', category: 'Legal', status: 'UPLOADED' }),
      ]),
    });

    const link = await screen.findByRole('link', { name: 'Q2 Budget Draft' });

    expect(link.getAttribute('href')).toBe('/documents/7');
    const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
    expect(within(rows[0]).getByText('alice_mgr')).toBeTruthy();
    expect(within(rows[1]).getByText('Uploaded')).toBeTruthy();
    expect(screen.getByText(/2 documents · Page 1 of 1/)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Repository' }).className).toContain('active');
  });

  it('offers global search in the top bar, which opens the search page', async () => {
    renderRepository({ 'GET /api/documents/page?page=0&size=10': pageOf([documentFixture()]) });
    await screen.findByRole('table');

    const global = screen.getByRole('search', { name: 'Global search' });
    fireEvent.change(within(global).getByRole('searchbox', { name: 'Search enterprise knowledge' }), {
      target: { value: '  budget plan ' },
    });
    fireEvent.submit(global);

    await vi.waitFor(() => expect(currentLocation.pathname).toBe('/search'));
    expect(currentLocation.search).toBe('?q=budget%20plan');
    // The search page leads with its own search bar, so the top bar one steps aside.
    expect(screen.queryByRole('search', { name: 'Global search' })).toBeNull();
  });

  it('ignores an empty global search', async () => {
    renderRepository({ 'GET /api/documents/page?page=0&size=10': pageOf([documentFixture()]) });
    await screen.findByRole('table');

    fireEvent.submit(screen.getByRole('search', { name: 'Global search' }));
    expect(currentLocation.pathname).toBe('/repository');
  });

  it('applies category, status and text filters through the URL', async () => {
    const empty = pageOf([], { totalPages: 0 });
    const fetchMock = renderRepository({
      'GET /api/documents/page?page=0&size=10': pageOf([documentFixture()]),
      'GET /api/documents/page?page=0&size=10&category=Legal': empty,
      'GET /api/documents/page?page=0&size=10&category=Legal&status=INDEXED': empty,
      'GET /api/documents/page?page=0&size=10&category=Legal&status=INDEXED&q=nda': empty,
    });
    await screen.findByRole('table');
    await screen.findByRole('option', { name: 'Legal' });

    fireEvent.change(screen.getByRole('combobox', { name: 'Category' }), { target: { value: 'Legal' } });
    await screen.findByText('No documents match these filters.');
    fireEvent.change(screen.getByRole('combobox', { name: 'Status' }), { target: { value: 'INDEXED' } });
    fireEvent.change(screen.getByRole('searchbox', { name: 'Filter by title or description' }), {
      target: { value: '  nda ' },
    });
    fireEvent.submit(screen.getByRole('searchbox', { name: 'Filter by title or description' }).closest('form'));

    await vi.waitFor(() => expect(requestedPaths(fetchMock)).toContain('/api/documents/page?page=0&size=10&category=Legal&status=INDEXED&q=nda'));
    expect(currentLocation.search).toBe('?category=Legal&status=INDEXED&q=nda');
  });

  it('pages forward and back, keeping filters', async () => {
    const fetchMock = renderRepository(
      {
        'GET /api/documents/page?page=0&size=10&status=INDEXED': pageOf([documentFixture({ id: 1, title: 'First' })], { totalItems: 11, totalPages: 2 }),
        'GET /api/documents/page?page=1&size=10&status=INDEXED': pageOf([documentFixture({ id: 2, title: 'Second' })], { page: 1, totalItems: 11, totalPages: 2 }),
      },
      '/repository?status=INDEXED',
    );
    await screen.findByRole('link', { name: 'First' });
    expect(screen.getByRole('button', { name: 'Previous' }).disabled).toBe(true);

    fireEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByRole('link', { name: 'Second' })).toBeTruthy();
    expect(screen.getByText(/11 documents · Page 2 of 2/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Next' }).disabled).toBe(true);
    expect(currentLocation.search).toBe('?page=1&status=INDEXED');

    fireEvent.click(screen.getByRole('button', { name: 'Previous' }));
    expect(await screen.findByRole('link', { name: 'First' })).toBeTruthy();
    expect(requestedPaths(fetchMock)).toEqual([
      '/api/documents/page?page=0&size=10&status=INDEXED',
      '/api/documents/page?page=1&size=10&status=INDEXED',
      '/api/documents/page?page=0&size=10&status=INDEXED',
    ]);
  });

  it('clears every filter at once', async () => {
    renderRepository(
      {
        'GET /api/documents/page?page=0&size=10&category=Legal&q=x': pageOf([], { totalPages: 0 }),
        'GET /api/documents/page?page=0&size=10': pageOf([documentFixture({ title: 'Everything' })]),
      },
      '/repository?category=Legal&q=x',
    );
    await screen.findByText('No documents match these filters.');
    expect(screen.getByRole('searchbox', { name: 'Filter by title or description' }).value).toBe('x');

    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }));

    expect(await screen.findByRole('link', { name: 'Everything' })).toBeTruthy();
    expect(screen.getByRole('searchbox', { name: 'Filter by title or description' }).value).toBe('');
    expect(currentLocation.search).toBe('');
  });

  it('says so when the user can read nothing', async () => {
    renderRepository({ 'GET /api/documents/page?page=0&size=10': pageOf([], { totalPages: 0 }) });

    expect(await screen.findByText('You don’t have access to any documents yet.')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Clear filters' })).toBeNull();
  });

  it('shows a load failure', async () => {
    renderRepository({ 'GET /api/documents/page?page=0&size=10': jsonResponse(400, { message: 'size must be between 1 and 100' }) });

    expect((await screen.findByRole('alert')).textContent).toBe('size must be between 1 and 100');
  });
});
