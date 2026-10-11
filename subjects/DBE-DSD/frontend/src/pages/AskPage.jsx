import { useState } from 'react';
import { Link } from 'react-router';
import { request } from '../api/client.js';
import { useApi } from '../api/useApi.js';
import { useAuth } from '../auth/AuthContext.jsx';

// Mirrors the backend's limit.
export const MAX_QUESTION_CHARS = 500;
const PROVIDER_NAMES = { groq: 'Groq', gemini: 'Gemini' };

// Answers stay while the app is open, so opening a source and coming back does
// not lose them. They quote documents, so they are kept in memory only, never in
// browser storage, and belong to the user who asked.
const kept = { username: null, turns: [] };

export function forgetAnswers() {
  kept.username = null;
  kept.turns = [];
}

/** Splits an answer into text and citations: "Yes [1, 3]." gives the numbers 1 and 3 as their own parts. */
export function answerParts(text) {
  const parts = [];
  let cursor = 0;
  for (const match of text.matchAll(/\[(\d+(?:\s*,\s*\d+)*)\]/g)) {
    if (match.index > cursor) parts.push({ text: text.slice(cursor, match.index) });
    parts.push({ cites: match[1].split(',').map((number) => Number(number.trim())) });
    cursor = match.index + match[0].length;
  }
  if (cursor < text.length) parts.push({ text: text.slice(cursor) });
  return parts;
}

function AnswerText({ turn }) {
  const numbers = new Set(turn.sources.map((source) => source.number));
  return (
    <p className="answer__text">
      {answerParts(turn.answer).map((part, index) =>
        part.text !== undefined ? (
          <span key={index}>{part.text}</span>
        ) : (
          <span key={index} className="answer__cites">
            {part.cites.map((number) =>
              // A number the model made up has no source to point at.
              numbers.has(number) ? (
                <a key={number} href={`#source-${turn.id}-${number}`} aria-label={`Source ${number}`}>
                  {number}
                </a>
              ) : (
                <span key={number}>{number}</span>
              ),
            )}
          </span>
        ),
      )}
    </p>
  );
}

function Turn({ turn }) {
  if (turn.answer === null) {
    return (
      <article className="glass-panel section answer" aria-label={turn.question}>
        <h2 className="answer__question">{turn.question}</h2>
        <p className="muted">
          No document you can read matches this question, so no answer was written. Try other words, or{' '}
          <Link className="doc-link" to={`/search?q=${encodeURIComponent(turn.question)}`}>
            search for it
          </Link>
          .
        </p>
      </article>
    );
  }

  const cited = turn.sources.filter((source) => source.cited).length;
  return (
    <article className="glass-panel section answer" aria-label={turn.question}>
      <h2 className="answer__question">{turn.question}</h2>
      <AnswerText turn={turn} />
      <p className="answer__by">
        Written by {PROVIDER_NAMES[turn.provider] ?? turn.provider} ({turn.model}) from{' '}
        {turn.sources.length === 1 ? '1 passage' : `${turn.sources.length} passages`}
        {cited > 0 ? `, ${cited} cited` : ''}. Check the sources before relying on it.
      </p>

      <h3 className="answer__sources-title">Sources</h3>
      <ol className="answer__sources">
        {turn.sources.map((source) => (
          <li
            key={source.number}
            id={`source-${turn.id}-${source.number}`}
            className={source.cited ? 'is-cited' : undefined}
          >
            <span className="answer__number">{source.number}</span>
            <div>
              <Link className="doc-link" to={`/documents/${source.documentId}`}>
                {source.title}
              </Link>
              <span className="muted">
                {source.category ? ` · ${source.category}` : ''}
                {source.cited ? '' : ' · read, not cited'}
              </span>
              <details open={source.cited && cited === 1}>
                <summary>Passage</summary>
                <p>{source.passage}</p>
              </details>
            </div>
          </li>
        ))}
      </ol>
    </article>
  );
}

export default function AskPage() {
  const { session } = useAuth();
  const overview = useApi('/api/ask');
  if (kept.username !== session.username) {
    kept.username = session.username;
    kept.turns = [];
  }
  const [turns, setTurns] = useState(kept.turns);
  const [question, setQuestion] = useState('');
  const [provider, setProvider] = useState('auto');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  async function handleSubmit(event) {
    event.preventDefault();
    const asked = question.trim();
    if (!asked) {
      setError('Type a question.');
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const result = await request('/api/ask', {
        method: 'POST',
        body: provider === 'auto' ? { question: asked } : { question: asked, provider },
      });
      kept.turns = [{ id: Date.now(), question: asked, ...result }, ...kept.turns];
      setTurns(kept.turns);
      setQuestion('');
    } catch (err) {
      // The client hides 5xx text, but a 503 here is worded for the asker: which model did not answer.
      setError(err.status === 503 && err.body?.message ? err.body.message : err.message);
    }
    setBusy(false);
  }

  const providers = overview.data?.providers ?? [];

  return (
    <div className="page page--narrow">
      <header className="page-header">
        <h1 className="page-title">Ask</h1>
        <p className="page-subtitle">
          Ask a question in plain words. The search finds the passages closest to it in the documents you can read,
          and a language model writes the answer from those passages only, citing each one it used.
        </p>
      </header>

      {overview.status === 'error' && (
        <p className="form-error" role="alert">
          {overview.error.message}
        </p>
      )}
      {overview.status === 'loading' && !overview.data && <p className="muted">Loading…</p>}
      {overview.data && !overview.data.available && (
        <p className="glass-panel section muted" role="status">
          No language model is set up on this server. An administrator sets GROQ_API_KEY or GEMINI_API_KEY in the
          backend&rsquo;s environment to turn this page on. Search works without it.
        </p>
      )}

      {overview.data?.available && (
        <form className="glass-panel section ask-form" onSubmit={handleSubmit} noValidate>
          <label htmlFor="ask-question">Your question</label>
          <textarea
            id="ask-question"
            className="text-input"
            rows={2}
            maxLength={MAX_QUESTION_CHARS}
            placeholder="For example: how many days of annual leave do new employees get?"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => {
              // Enter asks; Shift+Enter starts a new line.
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                e.currentTarget.form.requestSubmit();
              }
            }}
          />
          <div className="ask-form__row">
            <div>
              <label htmlFor="ask-provider">Model</label>
              <select
                id="ask-provider"
                className="select"
                value={provider}
                onChange={(e) => setProvider(e.target.value)}
              >
                <option value="auto">Automatic</option>
                {providers.map(({ id, model }) => (
                  <option key={id} value={id}>
                    {PROVIDER_NAMES[id] ?? id} · {model}
                  </option>
                ))}
              </select>
            </div>
            <button type="submit" className="btn btn--primary" disabled={busy}>
              {busy ? 'Reading the documents…' : 'Ask'}
            </button>
          </div>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <p className="hint">
            Each question is answered on its own: a follow-up does not remember the one before it. The question and
            the passages found for it are sent to the model&rsquo;s provider (Groq or Google) to write the answer.
          </p>
        </form>
      )}

      <div className="answers" aria-live="polite">
        {turns.map((turn) => (
          <Turn key={turn.id} turn={turn} />
        ))}
      </div>
    </div>
  );
}
