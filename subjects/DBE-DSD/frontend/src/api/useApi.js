import { useCallback, useEffect, useState } from 'react';
import { request } from './client.js';

/**
 * Loads a GET endpoint whenever `path` changes, or when `reload()` is called:
 * { status: 'loading' | 'ready' | 'error', data, error, reload }.
 *
 * While a new path loads, the previous `data` is kept, so a paged table does not
 * blank between pages. A null path loads nothing.
 */
export function useApi(path) {
  const [state, setState] = useState({ status: path ? 'loading' : 'idle', data: null, error: null });
  const [version, setVersion] = useState(0);

  useEffect(() => {
    if (!path) {
      return undefined;
    }
    let active = true;
    setState((previous) => ({ status: 'loading', data: previous.data, error: null }));
    request(path).then(
      (data) => active && setState({ status: 'ready', data, error: null }),
      (error) => active && setState({ status: 'error', data: null, error }),
    );
    return () => {
      active = false;
    };
  }, [path, version]);

  const reload = useCallback(() => setVersion((current) => current + 1), []);
  return { ...state, reload };
}

/** POSTs to `path` on demand and keeps the latest result or error: [state, run, fail]. */
export function useRun(path) {
  const [state, setState] = useState({ result: null, error: null, busy: false });
  async function run(body) {
    setState((previous) => ({ ...previous, error: null, busy: true }));
    try {
      setState({ result: await request(path, { method: 'POST', body }), error: null, busy: false });
    } catch (err) {
      setState({ result: null, error: err.message, busy: false });
    }
  }
  const fail = (error) => setState({ result: null, error, busy: false });
  return [state, run, fail];
}
