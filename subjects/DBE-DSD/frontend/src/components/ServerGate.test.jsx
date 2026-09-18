import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, mockFetch } from '../test-utils.js';
import ServerGate from './ServerGate.jsx';

const renderGate = () =>
  render(
    <ServerGate>
      <p>app content</p>
    </ServerGate>,
  );

describe('ServerGate', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('shows the app as soon as the backend is healthy', async () => {
    mockFetch(jsonResponse(200, { status: 'UP' }));
    renderGate();

    expect(await screen.findByText('app content')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Starting the server' })).toBeNull();
  });

  it('waits and retries while a sleeping backend wakes up', async () => {
    vi.useFakeTimers();
    // A proxy timeout, then a network error, then the backend is up.
    const fetchMock = mockFetch(jsonResponse(504), new TypeError('Failed to fetch'), jsonResponse(200, { status: 'UP' }));
    renderGate();

    await act(() => vi.advanceTimersByTimeAsync(0));
    expect(screen.getByRole('heading', { name: 'Starting the server' })).toBeTruthy();
    expect(screen.queryByText('app content')).toBeNull();

    await act(() => vi.advanceTimersByTimeAsync(5_000));
    expect(screen.queryByText('app content')).toBeNull();

    await act(() => vi.advanceTimersByTimeAsync(5_000));
    expect(screen.getByText('app content')).toBeTruthy();
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/health');
  });
});
