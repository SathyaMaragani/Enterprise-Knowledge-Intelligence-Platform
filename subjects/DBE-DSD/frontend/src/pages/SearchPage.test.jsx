import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { AuthProvider } from '../auth/AuthContext.jsx';
import { jsonResponse, tokenFor } from '../test-utils.js';

const CATEGORIES = [
  { id: 1, name: 'Finance', description: 'Money' },
  { id: 2, name: 'HR', description: 'People' },
];

const HIT = {
  documentId: 2,
  title: 'Q1 Financial Report',
  description: 'Q1 results',
  category: 'Finance',
  owner: 'alice_mgr',
  status: 'INDEXED',
  score: 0.63,
  keywordScore: 0.63,
  vectorScore: null,
  chunkId: null,
  matchedBy: ['KEYWORD', 'FUZZY'],
};

let currentLocation;
function LocationProbe() {
  currentLocation = useLocation();
  return null;
}

/** Answers POST /api/search from a function of the parsed request body. */
function renderSearch(path, answer = () => jsonResponse(200, { hits: [], page: 0, size: 10, totalHits: 0, sources: ['KEYWORD'] })) {
  const bodies = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, init = {}) => {
      if (url === '/api/categories') return jsonResponse(200, CATEGORIES);
      if (url === '/api/search' && init.method === 'POST') {
        const body = JSON.parse(init.body);
        bodies.push(body);
        return answer(body);
      }
      return jsonResponse(404, { message: `No mock for ${url}` });
    }),
  );
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
        <LocationProbe />
      </AuthProvider>
    </MemoryRouter>,
  );
  return bodies;
}

describe('search page', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('explains itself and sends nothing until there is a query', async () => {
    const bodies = renderSearch('/search');

    expect(await screen.findByText(/Enter a query to search/)).toBeTruthy();
    expect(bodies).toHaveLength(0);
    expect(screen.getByRole('link', { name: 'Search' }).className).toContain('active');
  });

  it('runs the search from the URL and shows detailed hits', async () => {
    const bodies = renderSearch('/search?q=finacial', () =>
      jsonResponse(200, { hits: [HIT], page: 0, size: 10, totalHits: 1, sources: ['KEYWORD'] }),
    );

    expect(await screen.findByRole('heading', { name: '1 result for “finacial”' })).toBeTruthy();
    expect(bodies[0]).toEqual({ query: 'finacial', page: 0, size: 10 });

    const hit = screen.getByRole('link', { name: 'Q1 Financial Report' });
    expect(hit.getAttribute('href')).toBe('/documents/2');
    const item = hit.closest('li');
    expect(within(item).getByText('Fuzzy')).toBeTruthy();
    expect(within(item).getByText('Indexed')).toBeTruthy();
    expect(within(item).getByText('Owner: alice_mgr')).toBeTruthy();
    expect(within(item).getByText('63%')).toBeTruthy();
    // Internal scoring detail (raw cosine, chunk ids) is not shown to users.
    expect(within(item).queryByText(/Best chunk|Semantic: /)).toBeNull();

    expect(screen.getByRole('radio', { name: /^Hybrid/ }).checked).toBe(true);
    expect(screen.getByText(/Semantic search is unavailable right now/)).toBeTruthy();
  });

  it('runs the mode from the URL and switches modes through the URL', async () => {
    const bodies = renderSearch('/search?q=finacial&mode=fuzzy');

    await screen.findByRole('heading', { name: '0 results for “finacial”' });
    expect(bodies[0]).toEqual({ query: 'finacial', mode: 'FUZZY', page: 0, size: 10 });
    expect(screen.getByRole('radio', { name: /^Fuzzy/ }).checked).toBe(true);

    fireEvent.click(screen.getByRole('radio', { name: /^Semantic/ }));
    await vi.waitFor(() => expect(bodies.at(-1)).toEqual({ query: 'finacial', mode: 'SEMANTIC', page: 0, size: 10 }));
    expect(currentLocation.search).toBe('?q=finacial&mode=semantic');

    // Hybrid is the default, so it leaves the URL and the request.
    fireEvent.click(screen.getByRole('radio', { name: /^Hybrid/ }));
    await vi.waitFor(() => expect(bodies.at(-1)).toEqual({ query: 'finacial', page: 0, size: 10 }));
    expect(currentLocation.search).toBe('?q=finacial');
  });

  it('treats an unknown mode in the URL as hybrid', async () => {
    const bodies = renderSearch('/search?q=policy&mode=everything');

    await screen.findByRole('heading', { name: '0 results for “policy”' });
    expect(bodies[0]).toEqual({ query: 'policy', page: 0, size: 10 });
    expect(screen.getByRole('radio', { name: /^Hybrid/ }).checked).toBe(true);
  });

  it('submits a new query and applies filters through the URL', async () => {
    const bodies = renderSearch('/search');

    fireEvent.change(screen.getByRole('searchbox', { name: 'Search documents' }), { target: { value: ' budget ' } });
    fireEvent.submit(screen.getByRole('search'));
    await screen.findByRole('heading', { name: '0 results for “budget”' });

    // Filters start folded away; the toggle opens them and counts what is applied.
    const toggle = screen.getByRole('button', { name: 'Filters' });
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(screen.queryByRole('combobox', { name: 'Category' })).toBeNull();
    fireEvent.click(toggle);
    await screen.findByRole('option', { name: 'Finance' });

    fireEvent.change(screen.getByRole('combobox', { name: 'Category' }), { target: { value: 'Finance' } });
    fireEvent.change(screen.getByRole('combobox', { name: 'Status' }), { target: { value: 'INDEXED' } });

    await vi.waitFor(() =>
      expect(bodies.at(-1)).toEqual({ query: 'budget', category: 'Finance', status: 'INDEXED', page: 0, size: 10 }),
    );
    expect(currentLocation.search).toBe('?q=budget&category=Finance&status=INDEXED');
    expect(screen.getByRole('button', { name: 'Filters (2)' }).getAttribute('aria-expanded')).toBe('true');
  });

  it('pages through results and resets to the first page when filters change', async () => {
    const bodies = renderSearch('/search?q=policy', (body) =>
      jsonResponse(200, {
        hits: [{ ...HIT, documentId: 10 + body.page, title: `Result on page ${body.page + 1}` }],
        page: body.page,
        size: 10,
        totalHits: 25,
        sources: ['KEYWORD', 'VECTOR'],
      }),
    );
    await screen.findByRole('link', { name: 'Result on page 1' });
    expect(screen.getByText('Page 1 of 3')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(await screen.findByRole('link', { name: 'Result on page 2' })).toBeTruthy();
    expect(currentLocation.search).toBe('?q=policy&page=1');

    fireEvent.click(screen.getByRole('button', { name: 'Filters' }));
    fireEvent.change(screen.getByRole('combobox', { name: 'Status' }), { target: { value: 'FAILED' } });
    expect(await screen.findByRole('link', { name: 'Result on page 1' })).toBeTruthy();
    expect(bodies.at(-1)).toEqual({ query: 'policy', status: 'FAILED', page: 0, size: 10 });

    expect(screen.queryByText(/Semantic search is unavailable/)).toBeNull();
  });

  it('reports a failed search', async () => {
    renderSearch('/search?q=x', () => jsonResponse(503, { message: 'Qdrant is unreachable' }));

    expect((await screen.findByRole('alert')).textContent).toMatch(/server is unavailable/);
    expect(screen.getByRole('heading', { name: 'Search failed' })).toBeTruthy();
  });

  it('says when nothing matches', async () => {
    renderSearch('/search?q=zzz');

    expect(await screen.findByText('No documents you can access match this search.')).toBeTruthy();
    expect(screen.queryByRole('navigation', { name: 'Pagination' })).toBeNull();
  });
});
