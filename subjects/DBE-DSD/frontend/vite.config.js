import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  // API_TARGET has no VITE_ prefix on purpose: it is read here, by the dev
  // server, and is never bundled into client code.
  const env = loadEnv(mode, process.cwd(), '');

  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true,
      // The browser only ever talks to Vite, which forwards /api to Spring Boot.
      // Same origin, so the backend needs no CORS configuration.
      proxy: {
        '/api': env.API_TARGET || 'http://localhost:8080',
      },
    },
    test: {
      environment: 'jsdom',
      // Testing Library unmounts between tests only when afterEach is global.
      globals: true,
    },
  };
});
