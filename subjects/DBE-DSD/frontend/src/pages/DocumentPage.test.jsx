import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { AuthProvider } from '../auth/AuthContext.jsx';
import { jsonResponse, mockApi, tokenFor } from '../test-utils.js';

// Shaped like the live GET /api/documents/3 response.
const DOCUMENT = {
  id: 3,
  title: 'System Architecture v2',
  description: 'New microservices design',
  category: 'Technical',
  owner: 'bob_eng',
  status: 'INDEXED',
  documentType: 'MD',
  createdAt: '2026-09-11T07:49:03.62946Z',
  updatedAt: '2026-09-11T07:49:03.62946Z',
  content: {
    rawText: 'The new architecture uses microservices. Services communicate via Kafka.',
    language: 'en',
    wordCount: 13,
    characterCount: 94,
  },
  source: { filename: 'arch_v2.md', mimeType: 'text/markdown', storageType: 'Git', storageReference: 'git://repo/arch_v2.md' },
  metadata: { authors: ['Bob Engineer'], department: 'Technical' },
  chunks: [
    { chunkId: 'chunk-002', text: 'Services communicate via Kafka.', position: 1, pageNumber: null, tokenCount: 6 },
    { chunkId: 'chunk-001', text: 'The new architecture uses microservices.', position: 0, pageNumber: 2, tokenCount: 5 },
  ],
  references: [{ title: 'Kafka docs', reference: 'https://kafka.apache.org', type: 'URL' }],
  processing: { status: 'COMPLETED', processedAt: '2026-09-11T07:49:06.322+00:00', extractorVersion: 'v1.2', chunkerVersion: 'v1.0' },
  version: { number: 2, changeSummary: 'Added DB details', createdAt: '2026-09-11T07:49:06.322+00:00' },
};

function renderDocument(path, routes) {
  mockApi(routes);
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('document viewer', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('shows metadata, content, ordered chunks and details', async () => {
    renderDocument('/documents/3', { 'GET /api/documents/3': jsonResponse(200, DOCUMENT) });

    expect(await screen.findByRole('heading', { level: 1, name: 'System Architecture v2' })).toBeTruthy();
    expect(screen.getByText('New microservices design')).toBeTruthy();
    expect(screen.getByText('Indexed')).toBeTruthy();
    expect(screen.getByText('Owner: bob_eng')).toBeTruthy();
    expect(screen.getByText(/13 words · 94 characters · en/)).toBeTruthy();
    expect(screen.getByText(DOCUMENT.content.rawText)).toBeTruthy();

    const chunks = screen.getByRole('heading', { name: /Chunks/ }).parentElement;
    const items = within(chunks).getAllByRole('listitem');
    expect(items.map((item) => item.querySelector('.chunk__text').textContent)).toEqual([
      'The new architecture uses microservices.',
      'Services communicate via Kafka.',
    ]);
    expect(within(items[0]).getByText('Page 2')).toBeTruthy();
    expect(within(items[1]).getByText('6 tokens')).toBeTruthy();

    expect(screen.getByText('arch_v2.md')).toBeTruthy();
    expect(screen.getByText('COMPLETED')).toBeTruthy();
    expect(screen.getByText('Added DB details')).toBeTruthy();
    expect(screen.getByText('Bob Engineer')).toBeTruthy();
    expect(screen.getByText('Kafka docs')).toBeTruthy();
    expect(screen.getByRole('link', { name: '← Repository' }).getAttribute('href')).toBe('/repository');
    expect(screen.getByRole('link', { name: 'Repository' }).className).toContain('active');
  });

  it('explains a forbidden document', async () => {
    renderDocument('/documents/4', {
      'GET /api/documents/4': jsonResponse(403, { error: 'Forbidden', message: 'User does not have access to this document' }),
    });

    expect((await screen.findByRole('alert')).textContent).toMatch(/don’t have access to this document/);
  });

  it('explains a document that does not exist', async () => {
    renderDocument('/documents/999', { 'GET /api/documents/999': jsonResponse(404) });

    expect((await screen.findByRole('alert')).textContent).toMatch(/does not exist/);
  });

  it('distinguishes missing content from a missing document', async () => {
    renderDocument('/documents/11', {
      'GET /api/documents/11': jsonResponse(404, {
        error: 'DOCUMENT_CONTENT_NOT_FOUND',
        message: 'Knowledge content was not found for this document.',
      }),
    });

    expect((await screen.findByRole('alert')).textContent).toMatch(/content has not been stored yet/);
  });

  it('does not request a non-numeric id', async () => {
    const fetchMock = mockApi({});
    render(
      <MemoryRouter initialEntries={['/documents/abc']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect((await screen.findByRole('alert')).textContent).toMatch(/does not exist/);
    expect(fetchMock.mock.calls.some(([path]) => path.startsWith('/api/documents/'))).toBe(false);
  });

  it('copes with a document that has no content details', async () => {
    renderDocument('/documents/3', {
      'GET /api/documents/3': jsonResponse(200, { ...DOCUMENT, content: null, chunks: null, metadata: null, references: null, source: null, processing: null, version: null }),
    });

    expect(await screen.findByText('No text content was extracted.')).toBeTruthy();
    expect(screen.getByText('This document has not been split into chunks.')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Metadata' })).toBeNull();
  });
});
