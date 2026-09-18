import { useEffect, useState } from 'react';

// The backend runs on Render's free plan, which stops it after 15 minutes without
// traffic; at its 0.1 CPU a cold start takes a few minutes. Holding the app until
// /api/health answers keeps the first sign-in or profile request from timing out.
const RETRY_MS = 5000;
// A healthy backend answers well within this, so a normal load shows no wake-up screen.
const QUIET_MS = 1500;

async function isHealthy() {
  try {
    const response = await fetch('/api/health', { headers: { Accept: 'application/json' } });
    return response.ok && JSON.parse(await response.text()).status === 'UP';
  } catch {
    // Unreachable, or a proxy's error or wake-up page instead of our JSON.
    return false;
  }
}

export default function ServerGate({ children }) {
  const [ready, setReady] = useState(false);
  const [waiting, setWaiting] = useState(false);

  useEffect(() => {
    let active = true;
    let retry;
    const quiet = setTimeout(() => setWaiting(true), QUIET_MS);
    const check = async () => {
      const healthy = await isHealthy();
      if (!active) {
        return;
      }
      if (healthy) {
        clearTimeout(quiet);
        setReady(true);
      } else {
        setWaiting(true);
        retry = setTimeout(check, RETRY_MS);
      }
    };
    check();
    return () => {
      active = false;
      clearTimeout(quiet);
      clearTimeout(retry);
    };
  }, []);

  if (ready) {
    return children;
  }
  if (!waiting) {
    return null;
  }
  return (
    <main className="server-gate">
      <section className="login-card glass-panel" role="status">
        <h1>Starting the server</h1>
        <p className="muted">
          The backend is on a free plan that sleeps after 15 minutes without visitors. Waking it can take a few
          minutes. This page continues by itself when it is ready.
        </p>
      </section>
    </main>
  );
}
