import { afterEach, describe, expect, it, vi } from 'vitest';

async function loadConfig(backendUrl) {
  vi.resetModules();
  vi.stubEnv('BACKEND_URL', backendUrl);
  return (await import('./vercel.mjs')).config;
}

describe('vercel.mjs', () => {
  afterEach(() => vi.unstubAllEnvs());

  it('proxies the API to the backend before falling back to the app shell', async () => {
    const config = await loadConfig('https://eip-backend-123.asia-south1.run.app/');

    expect(config.rewrites).toEqual([
      { source: '/api/:path*', destination: 'https://eip-backend-123.asia-south1.run.app/api/:path*' },
      { source: '/(.*)', destination: '/index.html' },
    ]);
    const apiHeaders = config.headers.find((rule) => rule.source === '/api/(.*)').headers;
    expect(apiHeaders).toContainEqual({ key: 'x-vercel-enable-rewrite-caching', value: '0' });
  });

  it('fails the build rather than deploying a frontend with no backend', async () => {
    await expect(loadConfig('')).rejects.toThrow(/Set BACKEND_URL/);
    await expect(loadConfig('http://eip-backend.run.app')).rejects.toThrow(/bare https origin/);
    await expect(loadConfig('https://eip-backend.run.app/api')).rejects.toThrow(/bare https origin/);
  });
});
