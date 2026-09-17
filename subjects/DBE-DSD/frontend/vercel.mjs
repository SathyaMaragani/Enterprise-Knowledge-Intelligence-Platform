// Vercel project configuration, evaluated at build time.
//
// The browser only ever talks to the Vercel domain: /api/* is proxied to the
// Cloud Run backend named by BACKEND_URL (set in the Vercel project's environment
// variables), so the backend needs no CORS configuration and preview deployments
// work unchanged. Every other path that is not a built file serves the app shell,
// so client-side routes such as /documents/12 survive a reload.

export function createConfig(backendUrl) {
  if (!backendUrl) {
    throw new Error('Set BACKEND_URL in the Vercel project, e.g. https://eip-backend-123456789.asia-south1.run.app');
  }
  const origin = new URL(backendUrl);
  if (origin.protocol !== 'https:' || origin.pathname !== '/' || origin.search) {
    throw new Error(`BACKEND_URL must be a bare https origin, got ${backendUrl}`);
  }

  return {
    rewrites: [
      { source: '/api/:path*', destination: `${origin.origin}/api/:path*` },
      { source: '/(.*)', destination: '/index.html' },
    ],
    headers: [
      {
        // API responses are per user; never let the CDN cache them.
        source: '/api/(.*)',
        headers: [{ key: 'x-vercel-enable-rewrite-caching', value: '0' }],
      },
      {
        // Vite fingerprints everything under /assets, so it can be cached forever.
        source: '/assets/(.*)',
        headers: [{ key: 'Cache-Control', value: 'public, max-age=31536000, immutable' }],
      },
    ],
  };
}

export const config = createConfig(process.env.BACKEND_URL);
