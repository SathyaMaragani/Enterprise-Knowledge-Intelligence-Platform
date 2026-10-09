import { useState } from 'react';
import { useApi, useRun } from '../api/useApi.js';

const count = (value) => value.toLocaleString('en-US');

/** Reads the totals from `demo-threads` output, or null if they are not there. */
export function parseThreadDemo(output) {
  const expected = output.match(/expected total (\d+)/);
  const racy = output.match(/Without a mutex: counter = (\d+) \((\d+) updates lost to the race\), ([\d.]+) ms/);
  const safe = output.match(/With a mutex: +counter = (\d+) \((\d+) lost\), ([\d.]+) ms/);
  if (!expected || !racy || !safe) {
    return null;
  }
  return {
    expected: Number(expected[1]),
    racy: Number(racy[1]),
    lost: Number(racy[2]),
    racyMs: Number(racy[3]),
    safe: Number(safe[1]),
    safeLost: Number(safe[2]),
    safeMs: Number(safe[3]),
  };
}

function ErrorLine({ error }) {
  return error ? (
    <p className="form-error" role="alert">
      {error}
    </p>
  ) : null;
}

function Transcript({ run }) {
  return (
    <div className="shellforge-run">
      <pre className="terminal" aria-label="ShellForge transcript">
        {run.output}
      </pre>
      <p className="muted">
        {run.timedOut ? 'Stopped at the time limit' : `Exit code ${run.exitCode}`} · {run.durationMillis} ms on the
        server
      </p>
    </div>
  );
}

function ThreadLab() {
  const [threads, setThreads] = useState('4');
  const [increments, setIncrements] = useState('200000');
  const [{ result, error, busy }, run] = useRun('/api/shellforge/run');
  const totals = result && parseThreadDemo(result.output);

  function handleSubmit(event) {
    event.preventDefault();
    run({ session: 'threads', threads: Number(threads), increments: Number(increments) });
  }

  return (
    <section className="glass-panel section" aria-labelledby="threads-title">
      <h2 id="threads-title" className="section__title">
        Week 10: race condition and mutex
      </h2>
      <p className="muted texthack-intro">
        Each thread adds 1 to a shared counter. <code>counter++</code> is a load, an add and a store, so two threads
        can load the same value and one increment is lost. Under a mutex only one thread is in that critical section at
        a time. Every worker is waited for with <code>pthread_join()</code>. The lost count changes from run to run.
      </p>
      <form className="texthack-form" onSubmit={handleSubmit}>
        <div className="form-row texthack-numbers">
          <div>
            <label htmlFor="threads-count">Threads</label>
            <input
              id="threads-count"
              className="text-input"
              type="number"
              min={1}
              max={8}
              value={threads}
              onChange={(e) => setThreads(e.target.value)}
            />
          </div>
          <div>
            <label htmlFor="threads-increments">Increments per thread</label>
            <input
              id="threads-increments"
              className="text-input"
              type="number"
              min={1}
              max={200000}
              value={increments}
              onChange={(e) => setIncrements(e.target.value)}
            />
          </div>
        </div>
        <ErrorLine error={error} />
        <button type="submit" className="btn btn--primary texthack-form__submit" disabled={busy}>
          {busy ? 'Running…' : 'Run the threads'}
        </button>
      </form>

      {result && (
        <div className="texthack-result" aria-live="polite">
          {totals && (
            <dl className="detail-list texthack-facts">
              <div>
                <dt>Expected</dt>
                <dd>{count(totals.expected)}</dd>
              </div>
              <div>
                <dt>Without a mutex</dt>
                <dd>
                  {count(totals.racy)} ({count(totals.lost)} lost) · {totals.racyMs} ms
                </dd>
              </div>
              <div>
                <dt>With a mutex</dt>
                <dd>
                  {count(totals.safe)} ({count(totals.safeLost)} lost) · {totals.safeMs} ms
                </dd>
              </div>
            </dl>
          )}
          <Transcript run={result} />
        </div>
      )}
    </section>
  );
}

function Sessions({ sessions }) {
  const [{ result, error, busy }, run] = useRun('/api/shellforge/run');
  const [chosen, setChosen] = useState(null);

  function start(id) {
    setChosen(id);
    run({ session: id });
  }

  return (
    <section className="glass-panel section" aria-labelledby="sessions-title">
      <h2 id="sessions-title" className="section__title">
        Sessions
      </h2>
      <p className="muted texthack-intro">
        Each session types a fixed list of commands into the shell, in a fresh empty folder, with none of the
        server&rsquo;s own settings. Typing your own commands is not offered: it would let anyone run programs on the
        server.
      </p>
      <ul className="shellforge-sessions">
        {sessions.map((session) => (
          <li key={session.id} className="shellforge-session">
            <p className="shellforge-session__week">Week {session.week}</p>
            <h3>{session.title}</h3>
            <p className="muted">{session.summary}</p>
            <pre className="shellforge-session__commands">{session.commands.join('\n')}</pre>
            <button
              type="button"
              className="btn btn--primary"
              disabled={busy}
              onClick={() => start(session.id)}
              aria-label={`Run ${session.title}`}
            >
              {busy && chosen === session.id ? 'Running…' : 'Run'}
            </button>
          </li>
        ))}
      </ul>
      <ErrorLine error={error} />
      {result && (
        <div className="texthack-result" aria-live="polite">
          <h3>{sessions.find((session) => session.id === result.session)?.title}</h3>
          <Transcript run={result} />
        </div>
      )}
    </section>
  );
}

export default function ShellForgePage() {
  const { status, data, error } = useApi('/api/shellforge');

  return (
    <div className="page">
      <header className="page-header">
        <h1 className="page-title">ShellForge</h1>
        <p className="page-subtitle">
          The OSSP Unix shell, written in C and running on this server. Each run starts the real program and shows
          what it printed.
        </p>
      </header>

      {status === 'error' && <ErrorLine error={error.message} />}
      {status === 'loading' && !data && <p className="muted">Loading…</p>}
      {data && !data.available && (
        <p className="glass-panel section muted" role="status">
          ShellForge is not installed on this server. It is built into the backend&rsquo;s Docker image.
        </p>
      )}
      {data?.available && (
        <>
          <ThreadLab />
          <Sessions sessions={data.sessions.filter((session) => session.id !== 'threads')} />
        </>
      )}
    </div>
  );
}
