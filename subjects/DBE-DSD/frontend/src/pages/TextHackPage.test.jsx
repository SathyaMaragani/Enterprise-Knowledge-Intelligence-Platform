import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { AuthProvider } from '../auth/AuthContext.jsx';
import { jsonResponse, tokenFor } from '../test-utils.js';
import { highlightSegments, parseCitations } from './TextHackPage.jsx';

const EMPLOYEE = { username: 'bob_eng', fullName: 'Bob Engineer', roles: ['EMPLOYEE'], permissions: ['DOCUMENT_READ'] };
const COMPLEXITY = [
  { name: 'KMP', category: 'String', time: 'O(n+m)', space: 'O(m)', note: 'No pathological input' },
  { name: 'Dinic', category: 'Graph', time: 'O(V^2*E)', space: 'O(V)', note: 'O(E*sqrt(E)) on unit capacity' },
];

/** Routes POSTs to `answers[path](body)`; records every body sent. */
function renderWorkbench(answers = {}) {
  const sent = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, init = {}) => {
      if (url === '/api/auth/me') return jsonResponse(200, EMPLOYEE);
      if (url === '/api/texthack/complexity') return jsonResponse(200, COMPLEXITY);
      if (init.method === 'POST' && answers[url]) {
        const body = JSON.parse(init.body);
        sent.push({ url, body });
        return answers[url](body);
      }
      return jsonResponse(404, { message: `No mock for ${url}` });
    }),
  );
  render(
    <MemoryRouter initialEntries={['/texthack']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
  return sent;
}

const TABS = { 'Pattern search': 'Pattern search', 'Similarity and alignment': 'Similarity', 'Citation flow': 'Citation flow' };
/** Opens the tool's tab, then returns its panel section. */
const section = (name) => {
  fireEvent.click(screen.getByRole('tab', { name: TABS[name] }));
  return screen.getByRole('heading', { name }).closest('section');
};

describe('highlightSegments', () => {
  it('marks matches and keeps the text between them', () => {
    expect(highlightSegments('ushers', [{ start: 2, end: 4 }])).toEqual([
      { text: 'us', marked: false },
      { text: 'he', marked: true },
      { text: 'rs', marked: false },
    ]);
  });

  it('merges overlapping and touching matches in any order', () => {
    const matches = [
      { start: 2, end: 6 },
      { start: 1, end: 4 },
      { start: 6, end: 7 },
    ];
    expect(highlightSegments('xushersx', matches)).toEqual([
      { text: 'x', marked: false },
      { text: 'ushers', marked: true },
      { text: 'x', marked: false },
    ]);
  });

  it('returns the text unmarked when nothing matched', () => {
    expect(highlightSegments('plain', [])).toEqual([{ text: 'plain', marked: false }]);
  });
});

describe('parseCitations', () => {
  it('reads spaces and arrows and skips blank lines', () => {
    expect(parseCitations('0 1\n\n  2 -> 3 \n4→5')).toEqual({
      citations: [
        { from: 0, to: 1 },
        { from: 2, to: 3 },
        { from: 4, to: 5 },
      ],
    });
  });

  it('names the first line it cannot read', () => {
    expect(parseCitations('0 1\n01').error).toMatch(/^Line 2:/);
    expect(parseCitations('0 1\na b').error).toMatch(/^Line 2:/);
    expect(parseCitations('-1 2').error).toMatch(/^Line 1:/);
  });
});

describe('TextHack workbench', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('is linked from the navigation and lists algorithm complexity', async () => {
    renderWorkbench();

    expect(screen.getByRole('link', { name: 'TextHack' }).className).toContain('active');
    expect(screen.getByRole('tab', { name: 'Pattern search' }).getAttribute('aria-selected')).toBe('true');
    fireEvent.click(screen.getByRole('tab', { name: 'Complexity' }));
    const table = await screen.findByRole('table');
    expect(within(table).getByText('KMP')).toBeTruthy();
    expect(within(table).getByText('O(V^2*E)')).toBeTruthy();
  });

  it('shows one tool at a time and keeps each tool\u2019s input when switching', async () => {
    renderWorkbench();
    const patternTab = screen.getByRole('tab', { name: 'Pattern search' });

    fireEvent.change(screen.getByLabelText('Text'), { target: { value: 'kept between tabs' } });
    fireEvent.keyDown(patternTab, { key: 'ArrowRight' });
    expect(screen.getByRole('tab', { name: 'Similarity' }).getAttribute('aria-selected')).toBe('true');
    expect(screen.queryByRole('heading', { name: 'Pattern search' })).toBeNull();
    expect(screen.getByRole('heading', { name: 'Similarity and alignment' })).toBeTruthy();

    fireEvent.keyDown(screen.getByRole('tab', { name: 'Similarity' }), { key: 'ArrowLeft' });
    expect(screen.getByLabelText('Text').value).toBe('kept between tabs');
  });

  it('searches for the patterns and highlights the matches', async () => {
    const sent = renderWorkbench({
      '/api/texthack/pattern': () =>
        jsonResponse(200, {
          algorithm: 'Aho-Corasick',
          matches: [
            { pattern: 'she', start: 1, end: 4 },
            { pattern: 'he', start: 2, end: 4 },
          ],
          longestRepeated: 's',
        }),
    });
    const pattern = section('Pattern search');

    fireEvent.change(within(pattern).getByLabelText('Text'), { target: { value: 'ushers' } });
    fireEvent.change(within(pattern).getByLabelText('Patterns, one per line'), { target: { value: 'she\n\nhe\n' } });
    fireEvent.click(within(pattern).getByRole('button', { name: 'Find matches' }));

    expect(await within(pattern).findByText('Aho-Corasick')).toBeTruthy();
    expect(sent[0].body).toEqual({ text: 'ushers', patterns: ['she', 'he'] });
    const highlighted = within(pattern).getByTestId('highlighted-text');
    expect([...highlighted.querySelectorAll('mark')].map((mark) => mark.textContent)).toEqual(['she']);
    expect(within(pattern).getByRole('list', { name: 'Matches' }).children).toHaveLength(2);
  });

  it('asks for a pattern rather than sending none', async () => {
    const sent = renderWorkbench({ '/api/texthack/pattern': () => jsonResponse(200, {}) });
    const pattern = section('Pattern search');

    fireEvent.change(within(pattern).getByLabelText('Patterns, one per line'), { target: { value: '' } });
    fireEvent.click(within(pattern).getByRole('button', { name: 'Find matches' }));

    expect((await within(pattern).findByRole('alert')).textContent).toMatch(/at least one pattern/);
    expect(sent).toHaveLength(0);
  });

  it('shows distances and both alignments', async () => {
    const alignment = (score) => ({
      alignedFirst: 'recieve',
      alignedSecond: 'receive',
      score,
      identity: 5 / 7,
      firstStart: 0,
      firstEnd: 7,
      secondStart: 0,
      secondEnd: 7,
    });
    const sent = renderWorkbench({
      '/api/texthack/similarity': () =>
        jsonResponse(200, {
          levenshteinDistance: 2,
          damerauDistance: 1,
          similarity: 1 - 2 / 7,
          global: alignment(3),
          local: alignment(8),
        }),
    });
    const similarity = section('Similarity and alignment');

    fireEvent.change(within(similarity).getByLabelText('First text'), { target: { value: 'recieve' } });
    fireEvent.change(within(similarity).getByLabelText('Second text'), { target: { value: 'receive' } });
    fireEvent.click(within(similarity).getByRole('button', { name: 'Compare' }));

    expect(await within(similarity).findByText('2 edits')).toBeTruthy();
    expect(sent[0].body).toEqual({ first: 'recieve', second: 'receive' });
    expect(within(similarity).getByText('1 edits')).toBeTruthy();
    expect(within(similarity).getByText('71%')).toBeTruthy();
    expect(within(similarity).getByLabelText('Global alignment').textContent).toBe('recieve\nreceive');
    expect(within(similarity).getByText(/Score 8/)).toBeTruthy();
  });

  it('analyses citations and lists the bottleneck', async () => {
    const sent = renderWorkbench({
      '/api/texthack/citations': () =>
        jsonResponse(200, { influence: 1, sourceSide: [0, 1], bottleneck: [{ from: 1, to: 2 }] }),
    });
    const citations = section('Citation flow');

    fireEvent.change(within(citations).getByLabelText('Documents'), { target: { value: '3' } });
    fireEvent.change(within(citations).getByLabelText('Sink'), { target: { value: '2' } });
    fireEvent.change(within(citations).getByLabelText('Citations, one “from to” per line'), {
      target: { value: '0 1\n0 1\n1 2' },
    });
    fireEvent.click(within(citations).getByRole('button', { name: 'Analyse' }));

    expect(await within(citations).findByText('Influence 1')).toBeTruthy();
    expect(sent[0].body).toEqual({
      documents: 3,
      source: 0,
      sink: 2,
      citations: [
        { from: 0, to: 1 },
        { from: 0, to: 1 },
        { from: 1, to: 2 },
      ],
    });
    expect(within(citations).getByRole('list', { name: 'Bottleneck citations' }).textContent).toBe('1 → 2');
    expect(within(citations).getByText('Source side of the cut: 0, 1')).toBeTruthy();
  });

  it('reports unreadable citations locally and server rejections as sent', async () => {
    const sent = renderWorkbench({
      '/api/texthack/citations': () =>
        jsonResponse(400, { error: 'Bad Request', message: 'Source and sink must be different documents' }),
    });
    const citations = section('Citation flow');
    const list = within(citations).getByLabelText('Citations, one “from to” per line');

    fireEvent.change(list, { target: { value: '0 1\nzero one' } });
    fireEvent.click(within(citations).getByRole('button', { name: 'Analyse' }));
    expect((await within(citations).findByRole('alert')).textContent).toMatch(/^Line 2:/);
    expect(sent).toHaveLength(0);

    fireEvent.change(list, { target: { value: '0 1' } });
    fireEvent.change(within(citations).getByLabelText('Sink'), { target: { value: '0' } });
    fireEvent.click(within(citations).getByRole('button', { name: 'Analyse' }));
    await vi.waitFor(() =>
      expect(within(citations).getByRole('alert').textContent).toBe('Source and sink must be different documents'),
    );
    expect(sent).toHaveLength(1);
  });
});
