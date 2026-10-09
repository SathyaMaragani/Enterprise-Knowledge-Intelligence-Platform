import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App.jsx';
import { AuthProvider } from '../auth/AuthContext.jsx';
import { jsonResponse, tokenFor } from '../test-utils.js';
import { parseThreadDemo } from './ShellForgePage.jsx';

const EMPLOYEE = { username: 'bob_eng', fullName: 'Bob Engineer', roles: ['EMPLOYEE'], permissions: ['DOCUMENT_READ'] };

const SESSIONS = [
  { id: 'pipes', week: 7, title: 'Pipes and IPC', summary: 'pipe() and dup2().', commands: ['ls /usr/bin | wc -l'], monitorSeconds: 0 },
  { id: 'monitor', week: 10, title: 'Background monitor thread', summary: 'A second thread.', commands: ['sleep 2.5'], monitorSeconds: 1 },
  { id: 'threads', week: 10, title: 'Race condition and mutex', summary: 'Shared counter.', commands: ['demo-threads 4 100000'], monitorSeconds: 0 },
];

const THREAD_OUTPUT = `myshell> demo-threads 2 1000
[Threads] 2 threads x 1000 increments, expected total 2000
  Without a mutex: counter = 1500 (500 updates lost to the race), 0.3 ms
  With a mutex:    counter = 2000 (0 lost), 1.2 ms
[Threads] all 2 workers joined with pthread_join()
myshell> exit
Exiting ShellForge...
`;

const run = (session, output, commands = []) =>
  jsonResponse(200, { session, commands, output, exitCode: 0, durationMillis: 42, timedOut: false });

/** Answers the overview with `overview` and each run with `answer(body)`; records every body sent. */
function renderShellForge({ overview = { available: true, sessions: SESSIONS }, answer } = {}) {
  const sent = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, init = {}) => {
      if (url === '/api/auth/me') return jsonResponse(200, EMPLOYEE);
      if (url === '/api/shellforge') return jsonResponse(200, overview);
      if (url === '/api/shellforge/run' && init.method === 'POST') {
        const body = JSON.parse(init.body);
        sent.push(body);
        return answer(body);
      }
      return jsonResponse(404, { message: `No mock for ${url}` });
    }),
  );
  render(
    <MemoryRouter initialEntries={['/shellforge']}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
  return sent;
}

describe('parseThreadDemo', () => {
  it('reads the expected, unprotected and protected totals', () => {
    expect(parseThreadDemo(THREAD_OUTPUT)).toEqual({
      expected: 2000,
      racy: 1500,
      lost: 500,
      racyMs: 0.3,
      safe: 2000,
      safeLost: 0,
      safeMs: 1.2,
    });
  });

  it('returns null for output without the demo', () => {
    expect(parseThreadDemo('ShellForge: usage: demo-threads [threads 1-16]')).toBeNull();
  });
});

describe('ShellForgePage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem('eip.token', tokenFor('bob_eng'));
  });

  afterEach(() => vi.unstubAllGlobals());

  it('is linked from the navigation and lists the sessions apart from the race demo', async () => {
    renderShellForge();

    expect(screen.getByRole('link', { name: 'ShellForge' }).className).toContain('active');
    expect(await screen.findByRole('button', { name: 'Run Pipes and IPC' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Run Background monitor thread' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Run Race condition and mutex' })).toBeNull();
    expect(screen.getByText('ls /usr/bin | wc -l')).toBeTruthy();
  });

  it('runs a session and shows the transcript', async () => {
    const sent = renderShellForge({
      answer: (body) => run(body.session, 'myshell> ls /usr/bin | wc -l\n731\nmyshell> exit\n'),
    });

    fireEvent.click(await screen.findByRole('button', { name: 'Run Pipes and IPC' }));

    const transcript = await screen.findByLabelText('ShellForge transcript');
    expect(transcript.textContent).toContain('731');
    expect(sent).toEqual([{ session: 'pipes' }]);
    expect(screen.getByText(/Exit code 0 · 42 ms/)).toBeTruthy();
  });

  it('runs the race demo with the chosen numbers and shows the totals', async () => {
    const sent = renderShellForge({ answer: (body) => run(body.session, THREAD_OUTPUT) });
    const lab = (await screen.findByRole('heading', { name: 'Week 10: race condition and mutex' })).closest('section');

    fireEvent.change(within(lab).getByLabelText('Threads'), { target: { value: '2' } });
    fireEvent.change(within(lab).getByLabelText('Increments per thread'), { target: { value: '1000' } });
    fireEvent.click(within(lab).getByRole('button', { name: 'Run the threads' }));

    expect(await within(lab).findByText('1,500 (500 lost) · 0.3 ms')).toBeTruthy();
    expect(within(lab).getByText('2,000 (0 lost) · 1.2 ms')).toBeTruthy();
    expect(sent).toEqual([{ session: 'threads', threads: 2, increments: 1000 }]);
  });

  it('shows the server’s reason when a run is refused', async () => {
    renderShellForge({ answer: () => jsonResponse(400, { message: 'threads must be between 1 and 8' }) });
    const lab = (await screen.findByRole('heading', { name: 'Week 10: race condition and mutex' })).closest('section');

    fireEvent.click(within(lab).getByRole('button', { name: 'Run the threads' }));

    expect((await within(lab).findByRole('alert')).textContent).toBe('threads must be between 1 and 8');
  });

  it('says so when the shell is not installed', async () => {
    renderShellForge({ overview: { available: false, sessions: SESSIONS } });

    expect((await screen.findByRole('status')).textContent).toContain('ShellForge is not installed on this server');
    expect(screen.queryByRole('button', { name: 'Run the threads' })).toBeNull();
  });
});
