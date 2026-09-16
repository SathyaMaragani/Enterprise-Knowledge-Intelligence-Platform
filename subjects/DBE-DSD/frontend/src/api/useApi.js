import { useEffect, useState } from 'react';
import { request } from './client.js';

/**
 * Loads a GET endpoint whenever `path` changes:
 * { status: 'loading' | 'ready' | 'error', data, error }.
 *
 * While a new path loads, the previous `data` is kept, so a paged table does not
 * blank between pages. A null path loads nothing.
 */
export function useApi(path) {
  const [state, setState] = useState({ status: path ? 'loading' : 'idle', data: null, error: null });

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
  }, [path]);

  return state;
}
