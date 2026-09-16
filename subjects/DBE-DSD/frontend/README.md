# EIP Frontend (DBE-DSD)

React single-page app for the Enterprise Knowledge Intelligence Platform.

**Frontend 1 (current):** app shell, routing, sign-in against the Spring Boot
backend, JWT session handling, protected routes and sign-out. The dashboard,
document repository, search and admin views come in later milestones.

Stack: React 19, React Router 7, Vite 8, Vitest 4 with Testing Library and
jsdom. Plain JavaScript, no UI library, no state library.

## Requirements

- Node.js 20.19 or newer.
- The backend running (see `../backend/docs/TESTING.md` for the database stack).

The test and routing packages are held at the newest majors that still support
Node 20: Vitest 4, jsdom 29, React Router 7. Their next majors need Node 22.

## Run

```bash
cd subjects/DBE-DSD/frontend
npm install
npm run dev
```

Open http://localhost:5173. The dev server forwards `/api` to the backend at
`http://localhost:8080`. To use another backend address, set `API_TARGET`:

```bash
API_TARGET=http://localhost:8081 npm run dev
```

On PowerShell:

```powershell
$env:API_TARGET="http://localhost:8081"; npm run dev
```

Any seeded account works, for example `admin_user` on the test stack.

## Test and build

```bash
npm test
npm run build
```

`npm test` needs no backend: API calls are stubbed with responses shaped like
the live backend's.

## How authentication works

| Concern | Behaviour |
|---|---|
| Sign-in | `POST /api/auth/login` with `{username, password}`. The backend returns `{token, type, username}`. |
| Session | The JWT is stored in `sessionStorage` under `eip.token`, so it survives a reload but not closing the tab. The username and expiry come from the token's `sub` and `exp` claims. |
| API calls | `src/api/client.js` adds `Authorization: Bearer <token>` to every request except sign-in. |
| Expiry | The session ends when `exp` passes, even with no request in flight. An expired token is never sent. |
| Rejected token | Any 401 on a request that carried a token signs the user out, whether the token expired, `JWT_SECRET` changed or the user was removed. A 401 from sign-in is a wrong password, not a sign-out. |
| Errors | 4xx responses show the backend's `message`. 5xx responses show a generic message, because this backend's 500 bodies can contain raw exception text. |
| CORS | None needed. In development the browser only talks to Vite, which proxies `/api`. |

The client decodes the token but does not verify its signature. It doesn't need
to: the backend verifies the signature on every request, and the client only
reads the token to display the username and time the sign-out.

## Known limits

- **Sign-out is client-side only.** The backend has no token revocation, so a
  copied token stays valid until it expires (24 hours by default).
- **No roles in the session.** The JWT carries only `sub`, `iat` and `exp`, and
  the login response has no roles. Role-aware UI (Frontend 2) needs the backend
  to expose them, for example as a claim or a `GET /api/auth/me` endpoint.
- **Production serving is not set up.** `npm run build` produces `dist/`, but
  serving it from the same origin as `/api` is part of the Dockerization
  milestone. Until then, the Vite proxy only covers development.
- **No return to the requested page after sign-in.** Only one signed-in page
  exists so far. Add this with the first real routed pages.
