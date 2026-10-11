import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { AuthProvider } from '../auth/AuthContext.jsx';
import { jsonResponse, tokenFor } from '../test-utils.js';
import { answerParts, forgetAnswers } from './AskPage.jsx';

const EMPLOYEE = { username: 'bob_eng', fullName: 'Bob Engineer', roles: ['EMPLOYEE'], permissions: ['DOCUMENT_READ'] };

const BOTH = {
  available: true,
  providers: [
    { id: 'groq', model: 'openai/gpt-oss-120b' },
    { id: 'gemini', model: 'gemini-flash-lite-latest' },
  ],
};

const LEAVE = {
  answer: 'New staff get 20 days of annual leave [1]. Unused days carry over [1, 7].',
  provider: 'groq',
  model: 'openai/gpt-oss-120b',
  sources: [
    { number: 1, documentId: 12, title: 'Leave Policy', category: 'HR', passage: 'Staff get 20 days.', cited: true },
    { number: 2, documentId: 30, title: 'Travel Policy', category: null, passage: 'Book trips early.', cited: false },
  ],
};

/** Answers the overview with `overview` and each question with `answer(body)`; records every body sent. */
function renderAsk({ overview = BOTH, answer = () => jsonResponse(200, LEAVE), path = '/ask' } = {}) {
  const sent = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, init = {}) => {
      if (url === '/api/auth/me') return jsonResponse(200, EMPLOYEE);
      if (url === '/api/ask' && init.method === 'POST') {
        const body = JSON.parse(init.body);
        sent.push(body);
        return answer(body);
      }
      if (url === '/api/ask') return overview instanceof Error ? Promise.reject(overview) : jsonResponse(200, overview);
      return jsonResponse(404, { message: `No mock for ${url}` });
    }),
  );
  const view = render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
  return { sent, view };
}

async function ask(question) {
  fireEvent.change(await screen.findByLabelText('Your question'), { target: { value: question } });
  fireEvent.click(screen.getByRole('button', { name: 'Ask' }));
}

describe('answerParts', () => {
  it('separates citations from the text around them', () => {
    expect(answerParts('Yes [1]. Also [2, 3] and [x].')).toEqual([
      { text: 'Yes ' },
      { cites: [1] },
      { text: '. Also ' },
      { cites: [2, 3] },
      { text: ' and [x].' },
    ]);
    expect(answerParts('No citations here.')).toEqual([{ text: 'No citations here.' }]);
  });
});

describe('AskPage', () => {
  beforeEach(() => {
    forgetAnswers();
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('is linked from the navigation and offers each model that is set up', async () => {
    renderAsk();

    expect(screen.getByRole('link', { name: 'Ask' }).className).toContain('active');
    const model = await screen.findByLabelText('Model');
    expect(within(model).getAllByRole('option').map((option) => option.textContent)).toEqual([
      'Automatic',
      'Groq · openai/gpt-oss-120b',
      'Gemini · gemini-flash-lite-latest',
    ]);
  });

  it('shows the answer with its citations linked to the passages it was written from', async () => {
    const { sent } = renderAsk();

    await ask('  How many days of annual leave?  ');

    const answer = await screen.findByRole('article', { name: 'How many days of annual leave?' });
    expect(sent).toEqual([{ question: 'How many days of annual leave?' }]);
    expect(answer.querySelector('.answer__text').textContent).toBe(
      'New staff get 20 days of annual leave 1. Unused days carry over 17.',
    );
    // Each citation jumps to its source; 7 is not a source, so it is not a link.
    const citations = within(answer).getAllByRole('link', { name: 'Source 1' });
    expect(citations).toHaveLength(2);
    const target = answer.querySelector(citations[0].getAttribute('href'));
    expect(within(target).getByRole('link', { name: 'Leave Policy' }).getAttribute('href')).toBe('/documents/12');
    expect(within(answer).queryByRole('link', { name: 'Source 7' })).toBeNull();

    expect(within(answer).getByText(/Written by Groq \(openai\/gpt-oss-120b\) from 2 passages, 1 cited\./)).toBeTruthy();
    const sources = within(answer).getAllByRole('listitem');
    expect(sources.map((item) => item.className)).toEqual(['is-cited', '']);
    expect(sources[0].textContent).toContain('Staff get 20 days.');
    expect(sources[1].textContent).toContain('Travel Policy · read, not cited');
    // The question box is ready for the next one.
    expect(screen.getByLabelText('Your question').value).toBe('');
  });

  it('asks the chosen model, and Enter sends the question', async () => {
    const { sent } = renderAsk();
    const box = await screen.findByLabelText('Your question');
    fireEvent.change(screen.getByLabelText('Model'), { target: { value: 'gemini' } });
    fireEvent.change(box, { target: { value: 'Who approves travel?' } });

    fireEvent.keyDown(box, { key: 'Enter', shiftKey: true });
    expect(sent).toEqual([]);
    fireEvent.keyDown(box, { key: 'Enter' });

    await screen.findByRole('article', { name: 'Who approves travel?' });
    expect(sent).toEqual([{ question: 'Who approves travel?', provider: 'gemini' }]);
  });

  it('says so when no readable document matches, and offers a search instead', async () => {
    renderAsk({ answer: () => jsonResponse(200, { answer: null, provider: null, model: null, sources: [] }) });

    await ask('wifi password & guests?');

    const answer = await screen.findByRole('article', { name: 'wifi password & guests?' });
    expect(answer.textContent).toContain('No document you can read matches this question');
    expect(within(answer).getByRole('link', { name: 'search for it' }).getAttribute('href')).toBe(
      '/search?q=wifi%20password%20%26%20guests%3F',
    );
  });

  it('keeps the question and shows the reason when the model does not answer', async () => {
    let fail = true;
    const { sent } = renderAsk({
      answer: () =>
        fail
          ? jsonResponse(503, { message: 'The language model did not answer. Try again in a moment.' })
          : jsonResponse(200, LEAVE),
    });

    await ask('How many days of annual leave?');

    expect((await screen.findByRole('alert')).textContent).toBe(
      'The language model did not answer. Try again in a moment.',
    );
    expect(screen.getByLabelText('Your question').value).toBe('How many days of annual leave?');
    expect(screen.queryByRole('article')).toBeNull();

    fail = false;
    fireEvent.click(screen.getByRole('button', { name: 'Ask' }));
    await screen.findByRole('article', { name: 'How many days of annual leave?' });
    expect(screen.queryByRole('alert')).toBeNull();
    expect(sent).toHaveLength(2);
  });

  it('does not send an empty question', async () => {
    const { sent } = renderAsk();

    await ask('   ');

    expect(screen.getByRole('alert').textContent).toBe('Type a question.');
    expect(sent).toEqual([]);
  });

  it('lists answers newest first and keeps them while the app is open, for the same user only', async () => {
    const { view } = renderAsk({ answer: (body) => jsonResponse(200, { ...LEAVE, answer: `About: ${body.question}` }) });
    await ask('First question');
    await screen.findByRole('article', { name: 'First question' });
    await ask('Second question');
    await screen.findByRole('article', { name: 'Second question' });

    expect(screen.getAllByRole('article').map((article) => article.getAttribute('aria-label'))).toEqual([
      'Second question',
      'First question',
    ]);

    // Leaving the page and coming back (here: a fresh render) still shows them.
    view.unmount();
    renderAsk();
    expect(await screen.findAllByRole('article')).toHaveLength(2);

    // Someone else signing in does not.
    document.body.innerHTML = '';
    sessionStorage.setItem('eip.token', tokenFor('alice_mgr'));
    renderAsk();
    await screen.findByLabelText('Your question');
    expect(screen.queryByRole('article')).toBeNull();
  });

  it('explains itself when no model is set up, or the server cannot be reached', async () => {
    const { view } = renderAsk({ overview: { available: false, providers: [] } });
    expect((await screen.findByRole('status')).textContent).toContain('No language model is set up on this server');
    expect(screen.queryByLabelText('Your question')).toBeNull();

    view.unmount();
    renderAsk({ overview: new TypeError('Failed to fetch') });
    expect(await screen.findByRole('alert')).toBeTruthy();
    expect(screen.queryByLabelText('Your question')).toBeNull();
  });
});
