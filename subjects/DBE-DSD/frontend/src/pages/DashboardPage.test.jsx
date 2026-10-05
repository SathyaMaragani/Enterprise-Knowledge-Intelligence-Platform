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
    'GET /api/search/history?limit=5': jsonResponse(200, []),
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
    expect(screen.getByRole('link', { name: 'View all documents' }).getAttribute('href')).toBe('/repository');
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
    expect(screen.getByText('No documents yet')).toBeTruthy();
    // Without upload permission the empty state does not invite an upload.
    expect(screen.queryByRole('link', { name: 'Upload document' })).toBeNull();
  });

  it('invites an uploader to add the first document', async () => {
    renderDashboard({
      'GET /api/documents': jsonResponse(200, []),
      'GET /api/auth/me': jsonResponse(200, {
        username: 'alice_mgr',
        fullName: 'Alice Manager',
        roles: ['MANAGER'],
        permissions: ['DOCUMENT_CREATE', 'DOCUMENT_READ'],
      }),
    });

    expect(await screen.findByText(/Upload your first document/)).toBeTruthy();
    // One in the hero, one in the empty state.
    expect(screen.getAllByRole('link', { name: 'Upload document' })).toHaveLength(2);
    expect(screen.getByText('No activity yet')).toBeTruthy();
  });

  it('lists the user’s own recent searches, each reopening the search', async () => {
    renderDashboard({
      'GET /api/search/history?limit=5': jsonResponse(200, [
        { query: 'vendor contract', mode: 'FUZZY', resultCount: 2, searchedAt: new Date(Date.now() - 5 * 60000).toISOString() },
        { query: 'leave policy', mode: 'HYBRID', resultCount: 1, searchedAt: new Date(Date.now() - 3600000).toISOString() },
        { query: 'NDA', mode: 'TEXTHACK', resultCount: 0, searchedAt: '2026-09-01T09:00:00Z' },
      ]),
    });

    const list = await screen.findByRole('list', { name: 'Your recent searches' });
    const items = within(list).getAllByRole('listitem');
    expect(within(items[0]).getByRole('link', { name: 'vendor contract' }).getAttribute('href')).toBe(
      '/search?q=vendor+contract&mode=fuzzy',
    );
    expect(within(items[0]).getByText('Fuzzy · 2 results')).toBeTruthy();
    expect(within(items[0]).getByText('5 minutes ago')).toBeTruthy();
    expect(within(items[1]).getByRole('link', { name: 'leave policy' }).getAttribute('href')).toBe('/search?q=leave+policy');
    expect(within(items[1]).getByText('Hybrid · 1 result')).toBeTruthy();
    // Older history from before search modes opens as a hybrid search.
    expect(within(items[2]).getByRole('link', { name: 'NDA' }).getAttribute('href')).toBe('/search?q=NDA');
    expect(within(items[2]).getByText('TextHack · 0 results')).toBeTruthy();
  });

  it('invites a first search when there is no activity', async () => {
    renderDashboard();
    expect(await screen.findByText('No recent searches')).toBeTruthy();
    expect(screen.getByText(/Your search history will appear here/)).toBeTruthy();
  });

  it('says when search activity cannot load without disturbing the rest', async () => {
    renderDashboard({ 'GET /api/search/history?limit=5': jsonResponse(503, { message: 'down' }) });
    expect(await screen.findByText('Search activity is unavailable.')).toBeTruthy();
    expect(await screen.findByRole('table')).toBeTruthy();
  });

  it('offers only built pages and actions', async () => {
    renderDashboard();
    await screen.findByRole('table');

    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).getAllByRole('link').map((link) => link.textContent)).toEqual([
      'Dashboard',
      'Search',
      'Repository',
      'TextHack',
      'ML insights',
    ]);
    // Unbuilt pages are not advertised in the navigation.
    expect(within(nav).queryByText('Soon')).toBeNull();
    expect(within(nav).queryByText('Analytics')).toBeNull();
    // The dashboard searches through the top bar.
    expect(screen.getByRole('search', { name: 'Global search' })).toBeTruthy();
    // Without a profile that allows uploads, no upload action is offered at all.
    expect(screen.queryByRole('link', { name: 'Upload document' })).toBeNull();
  });

  it('greets the user by first name once the profile loads', async () => {
    renderDashboard({
      'GET /api/auth/me': jsonResponse(200, {
        username: 'alice_mgr',
        fullName: 'Alice Manager',
        roles: ['MANAGER'],
        permissions: ['DOCUMENT_CREATE', 'DOCUMENT_READ'],
      }),
    });

    expect(await screen.findByRole('heading', { name: /^Good (morning|afternoon|evening), Alice$/ })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Upload document' }).getAttribute('href')).toBe('/upload');
  });

  it('links each overview card and feature to its page', async () => {
    renderDashboard();
    await screen.findByRole('table');

    expect(screen.getByRole('link', { name: 'Open indexed' }).getAttribute('href')).toBe('/repository?status=INDEXED');
    expect(screen.getByRole('link', { name: 'Open vector chunks' }).getAttribute('href')).toBe('/search?mode=semantic');
    const features = screen.getByRole('region', { name: 'Explore features' });
    expect(within(features).getAllByRole('link').map((link) => link.getAttribute('href'))).toEqual([
      '/search',
      '/repository',
      '/texthack',
    ]);
  });

  it('opens the upload form with a file dropped on the upload button', async () => {
    renderDashboard({
      'GET /api/auth/me': jsonResponse(200, {
        username: 'alice_mgr',
        roles: ['MANAGER'],
        permissions: ['DOCUMENT_CREATE', 'DOCUMENT_READ'],
      }),
      'GET /api/categories': jsonResponse(200, []),
    });
    const upload = await screen.findByRole('link', { name: 'Upload document' });

    const file = new File(['hello'], 'notes.md', { type: 'text/markdown' });
    fireEvent.drop(upload.parentElement, { dataTransfer: { files: [file] } });

    expect(await screen.findByRole('heading', { name: 'Upload a document' })).toBeTruthy();
    expect(screen.getByText('notes.md · 5 B')).toBeTruthy();
  });
});

describe('top bar search from the dashboard', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
  });

  afterEach(() => vi.unstubAllGlobals());

  const EMPTY_RESULT = jsonResponse(200, { hits: [], page: 0, size: 10, totalHits: 0, sources: ['KEYWORD', 'VECTOR'] });

  function runSearch(query, category) {
    const form = screen.getByRole('search', { name: 'Global search' });
    fireEvent.change(within(form).getByRole('searchbox'), { target: { value: query } });
    if (category) {
      fireEvent.change(within(form).getByRole('combobox', { name: 'Search in category' }), { target: { value: category } });
    }
    fireEvent.submit(form);
  }

  const searchBodies = (fetchMock) =>
    fetchMock.mock.calls.filter(([path]) => path === '/api/search').map(([, init]) => JSON.parse(init.body));

  it('opens the search page with the query and category', async () => {
    const fetchMock = renderDashboard({
      'GET /api/categories': jsonResponse(200, [{ id: 1, name: 'Finance' }]),
      'POST /api/search': EMPTY_RESULT,
    });
    await screen.findByRole('table');
    await screen.findByRole('option', { name: 'Finance' });

    runSearch('  Finacial ', 'Finance');

    await vi.waitFor(() => expect(searchBodies(fetchMock)).toHaveLength(1));
    expect(searchBodies(fetchMock)[0]).toEqual({ query: 'Finacial', category: 'Finance', page: 0, size: 10 });
    expect(screen.getByRole('heading', { level: 1 }).textContent).not.toMatch(/^Good/);
  });

  it('searches in the mode chosen in the hero', async () => {
    const fetchMock = renderDashboard({ 'POST /api/search': EMPTY_RESULT });
    await screen.findByRole('table');

    expect(screen.getByRole('radio', { name: /^Hybrid/ }).checked).toBe(true);
    fireEvent.click(screen.getByRole('radio', { name: /^Fuzzy/ }));
    expect(screen.getByRole('radio', { name: /^Fuzzy/ }).checked).toBe(true);
    runSearch('finacial');

    await vi.waitFor(() => expect(searchBodies(fetchMock)).toHaveLength(1));
    expect(searchBodies(fetchMock)[0]).toEqual({ query: 'finacial', mode: 'FUZZY', page: 0, size: 10 });
  });

  it('does not search for a blank query', async () => {
    const fetchMock = renderDashboard();
    await screen.findByRole('table');

    runSearch('   ');

    expect(searchBodies(fetchMock)).toHaveLength(0);
    expect(screen.getByRole('heading', { name: /^Good/ })).toBeTruthy();
  });
});
