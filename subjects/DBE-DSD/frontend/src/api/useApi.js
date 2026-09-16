import { useEffect, useState } from 'react';
import { request } from './client.js';

/** Loads a GET endpoint once per path: { status: 'loading' | 'ready' | 'error', data, error }. */
export function useApi(path) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null });

  useEffect(() => {
    let active = true;
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
