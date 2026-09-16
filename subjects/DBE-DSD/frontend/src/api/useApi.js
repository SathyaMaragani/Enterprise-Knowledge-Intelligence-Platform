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
