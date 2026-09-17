import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { AuthProvider } from '../auth/AuthContext.jsx';
import { documentFixture, jsonResponse, mockApi, tokenFor } from '../test-utils.js';

const DOCUMENTS = [
  documentFixture({ id: 1, title: 'Employee Handbook 2026', category: 'HR', status: 'INDEXED', updatedAt: '2026-09-01T09:00:00Z' }),
  documentFixture({ id: 2, title: 'Q1 Financial Report', category: 'Finance', status: 'INDEXED', documentType: 'XLSX', updatedAt: '2026-09-10T09:00:00Z' }),
  documentFixture({ id: 5, title: 'Vendor Contract A', category: 'Legal', status: 'UPLOADED', createdAt: '2026-09-12T09:00:00Z', updatedAt: '2026-09-12T09:00:00Z' }),
  documentFixture({ id: 9, title: 'Leave Policy Update', category: 'HR', status: 'FAILED', updatedAt: '2026-08-01T09:00:00Z' }),
];

function renderDashboard(routes) {
  const fetchMock = mockApi({
    'GET /api/documents': jsonResponse(200, DOCUMENTS),
    'GET /api/search/vector/collection-info': jsonResponse(200, { pointsCount: 30 }),
    ...routes,
  });
  render(
    <MemoryRouter initialEntries={['/']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
  return fetchMock;
}

const statValue = (label) => screen.getByText(label, { selector: 'dt' }).nextElementSibling.textContent;

describe('dashboard', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('summarises only the documents the API returned', async () => {
    renderDashboard();

    await screen.findByText('Q1 Financial Report', { selector: '.doc-title__name' });

    expect(statValue('Documents')).toBe('4');
    expect(statValue('Categories')).toBe('3');
    expect(statValue('Indexed')).toBe('2');
    expect(statValue('Vector chunks')).toBe('30');
  });

  it('lists recent documents newest first with their status', async () => {
    renderDashboard();

    const table = await screen.findByRole('table');
    const rows = within(table).getAllByRole('row').slice(1);

    expect(rows.map((row) => row.querySelector('.doc-title__name').textContent)).toEqual([
      'Vendor Contract A',
      'Q1 Financial Report',
      'Employee Handbook 2026',
      'Leave Policy Update',
    ]);
    expect(within(rows[0]).getByText('Uploaded')).toBeTruthy();
    expect(within(rows[3]).getByText('Failed')).toBeTruthy();
    expect(within(rows[0]).getByRole('link', { name: 'Vendor Contract A' }).getAttribute('href')).toBe('/documents/5');
    expect(screen.getByRole('link', { name: 'View All →' }).getAttribute('href')).toBe('/repository');
  });

  it('still shows documents when vector statistics are unavailable', async () => {
    renderDashboard({
      'GET /api/search/vector/collection-info': jsonResponse(503, { message: 'Qdrant is unreachable' }),
    });

    await screen.findByRole('table');

    expect(statValue('Documents')).toBe('4');
    expect(statValue('Vector chunks')).toBe('—');
  });

  it('reports a failed document load instead of showing empty data', async () => {
    renderDashboard({ 'GET /api/documents': jsonResponse(500, { message: 'NullPointerException' }) });

    const alert = await screen.findByRole('alert');

    expect(alert.textContent).toMatch(/server is unavailable/);
    expect(statValue('Documents')).toBe('—');
  });

  it('tells a user with no access that there is nothing to show', async () => {
    renderDashboard({ 'GET /api/documents': jsonResponse(200, []) });

    expect(await screen.findByText(/don’t have access to any documents/)).toBeTruthy();
  });

  it('marks unbuilt navigation and actions as coming soon rather than linking nowhere', async () => {
    renderDashboard();
    await screen.findByRole('table');

    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'Dashboard',
      'Search',
      'Repository',
      'TextHack',
    ]);
    // Two for everyone; Administration is added only for administrators.
    expect(within(nav).getAllByText('Soon')).toHaveLength(2);
    // The hero search is the dashboard's search; the top bar does not repeat it.
    expect(screen.queryByRole('search', { name: 'Global search' })).toBeNull();

    expect(screen.getByRole('button', { name: /Upload Document/ }).disabled).toBe(true);
    expect(screen.getByRole('button', { name: /Advanced Search/ }).disabled).toBe(false);
  });

  it('opens the full search page from the Advanced Search action', async () => {
    renderDashboard({ 'GET /api/categories': jsonResponse(200, []) });
    await screen.findByRole('table');

    fireEvent.click(screen.getByRole('button', { name: /Advanced Search/ }));

    expect(await screen.findByRole('heading', { name: 'Search the knowledge base' })).toBeTruthy();
  });
});

describe('dashboard search', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
  });

  afterEach(() => vi.unstubAllGlobals());

  function runSearch(query, category) {
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search documents' }), { target: { value: query } });
    if (category) {
      fireEvent.change(screen.getByRole('combobox', { name: 'Category' }), { target: { value: category } });
    }
    fireEvent.submit(screen.getByRole('search'));
  }

  it('posts the query and category, then shows hits with their match signals', async () => {
    const fetchMock = renderDashboard({
      'POST /api/search': jsonResponse(200, {
        hits: [
          {
            documentId: 2,
            title: 'Q1 Financial Report',
            description: 'Q1 results',
            category: 'Finance',
            score: 0.63,
            matchedBy: ['KEYWORD', 'FUZZY'],
          },
        ],
        page: 0,
        size: 10,
        totalHits: 1,
        sources: ['KEYWORD'],
      }),
    });
    await screen.findByRole('table');

    runSearch('  Finacial ', 'Finance');

    const results = await screen.findByText('1 result for “Finacial”');
    const panel = results.closest('.search-results');
    expect(within(panel).getByRole('link', { name: 'Q1 Financial Report' }).getAttribute('href')).toBe('/documents/2');
    expect(within(panel).getByText('Keyword')).toBeTruthy();
    expect(within(panel).getByText('Fuzzy')).toBeTruthy();
    expect(within(panel).getByText('63%')).toBeTruthy();

    expect(within(panel).getByRole('link', { name: 'Open in search →' }).getAttribute('href')).toBe(
      '/search?q=Finacial&category=Finance',
    );

    const searchCall = fetchMock.mock.calls.find(([path]) => path === '/api/search');
    expect(JSON.parse(searchCall[1].body)).toEqual({ query: 'Finacial', category: 'Finance', page: 0, size: 10 });
    expect(searchCall[1].headers.Authorization).toMatch(/^Bearer /);

    // Hybrid was requested but only keywords answered, so the results say so.
    expect(screen.getByRole('radio', { name: /^Hybrid/ }).checked).toBe(true);
    expect(within(panel).getByText(/Semantic search is unavailable right now/)).toBeTruthy();
  });

  it('omits the category when searching all categories', async () => {
    const fetchMock = renderDashboard({
      'POST /api/search': jsonResponse(200, { hits: [], page: 0, size: 10, totalHits: 0, sources: ['KEYWORD', 'VECTOR'] }),
    });
    await screen.findByRole('table');

    runSearch('leave policy');

    expect(await screen.findByText(/No documents you can access match/)).toBeTruthy();
    const searchCall = fetchMock.mock.calls.find(([path]) => path === '/api/search');
    expect(JSON.parse(searchCall[1].body)).toEqual({ query: 'leave policy', page: 0, size: 10 });

    expect(screen.queryByText(/Semantic search is unavailable/)).toBeNull();
  });

  it('searches in the chosen mode and re-runs shown results when the mode changes', async () => {
    const fetchMock = renderDashboard({
      'POST /api/search': jsonResponse(200, { hits: [], page: 0, size: 10, totalHits: 0, sources: ['KEYWORD'] }),
    });
    await screen.findByRole('table');
    const bodies = () =>
      fetchMock.mock.calls.filter(([path]) => path === '/api/search').map(([, init]) => JSON.parse(init.body));

    fireEvent.click(screen.getByRole('radio', { name: /^Fuzzy/ }));
    runSearch('finacial');
    await vi.waitFor(() => expect(bodies()).toHaveLength(1));
    expect(bodies()[0]).toEqual({ query: 'finacial', mode: 'FUZZY', page: 0, size: 10 });
    // Keyword sources are what a fuzzy search is expected to use: no fallback notice.
    await screen.findByText(/No documents you can access match/);
    expect(screen.queryByText(/Semantic search is unavailable/)).toBeNull();
    expect(screen.getByRole('link', { name: 'Open in search →' }).getAttribute('href')).toBe(
      '/search?q=finacial&mode=fuzzy',
    );

    fireEvent.click(screen.getByRole('radio', { name: /^Keyword/ }));
    await vi.waitFor(() => expect(bodies()).toHaveLength(2));
    expect(bodies()[1]).toEqual({ query: 'finacial', mode: 'KEYWORD', page: 0, size: 10 });
  });

  it('does not search for a blank query', async () => {
    const fetchMock = renderDashboard();
    await screen.findByRole('table');

    runSearch('   ');

    expect(fetchMock.mock.calls.some(([path]) => path === '/api/search')).toBe(false);
  });

  it('shows a search failure and clears the results panel', async () => {
    renderDashboard({ 'POST /api/search': jsonResponse(400, { message: 'size must be between 1 and 100' }) });
    await screen.findByRole('table');

    runSearch('budget');

    expect((await screen.findByText('size must be between 1 and 100')).getAttribute('role')).toBe('alert');

    fireEvent.click(screen.getByRole('button', { name: 'Clear search results' }));

    expect(screen.queryByText('Search failed')).toBeNull();
  });
});
