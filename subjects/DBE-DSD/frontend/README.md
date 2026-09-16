# EIP Frontend (DBE-DSD)

React single-page app for the Enterprise Knowledge Intelligence Platform.

**Current state:** a sign-in page and a dashboard built to the product UI design,
on top of Frontend 1's routing, JWT session handling, protected routes and
sign-out. Search, repository, categories, analytics and administration pages are
not built yet; the navigation shows them as "Soon".

Stack: React 19, React Router 7, Vite 8, Vitest 4 with Testing Library and
jsdom, and [ThreeUI Community](https://github.com/MengTo/threeui)
(`@designcodeio/threeui`, MIT) with three.js for the animated backgrounds.
Plain JavaScript and plain CSS; no UI kit, icon library or state library.

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

## Pages

### Sign-in

A two-panel page. The left panel carries the product message over a dusk
landscape: threeui's `cloud-field` scene (animated sky and drifting cloud ridges)
behind SVG mountains and a CSS 3D stack of glass slabs. The right panel holds the
sign-in form.

| Element | Behaviour |
|---|---|
| Username / Password | Signs in with `POST /api/auth/login`. The backend looks users up by username only, so the field does not offer email. |
| Show / hide password | Toggles the password field between hidden and plain text. |
| Forgot password? | Explains that resets go through an administrator. The backend has no self-service reset. |
| Sign in with SSO | Shown disabled, marked "not configured". The backend has no SSO. |

### Dashboard

| Section | Data |
|---|---|
| Search bar | `POST /api/search` with the query and optional category. Results show inline with relevance and match signals (Keyword, Semantic, Fuzzy). |
| Keyword / Semantic / Hybrid | Report which mode the backend ran, from the response's `sources`. The backend chooses the mode, so these are not a selector. |
| Category filter | Categories of the documents the user can read. |
| Quick Actions | Advanced Search focuses the search bar. Upload, Analytics and Categories are marked "Coming soon". |
| Recent Documents | `GET /api/documents`, newest update first. |
| System Overview | Documents, categories and indexed counts over the user's readable documents; vector chunks from `GET /api/search/vector/collection-info`, which is collection-wide. |
| Recent Activity | Derived from document `createdAt` / `updatedAt`. The backend keeps no audit trail, so this shows additions and edits only. |

Every figure comes from the API. When a request fails, the section says so or
shows `—`; nothing falls back to placeholder numbers.

The hero glow behind the glass documents is threeui's `nebula` scene.

## threeui scenes

`src/components/ThreeBackdrop.jsx` renders a scene only when it is appropriate:

- **WebGL must be available.** Without it the page keeps its CSS gradient.
- **Reduced motion is respected.** If the operating system asks for reduced
  motion, no scene renders. On Windows this follows *Settings → Accessibility →
  Visual effects → Animation effects*; with it off, the pages show their static
  gradients and SVG art instead of the animated scenes.
- **Loaded lazily.** threeui and three.js (about 1.5 MB before compression) are
  a separate chunk, fetched only when a scene will render. The pages paint first.
- **Failures stay contained.** An error inside a scene removes the scene, not the
  page. Scenes ignore pointer events.

Only two components are imported, by subpath: `PortalFieldCollection`
(`cloud-field`) and `StructureFlowCollection` (`nebula`). Both render
self-contained documents with no network requests.

## How authentication works

| Concern | Behaviour |
|---|---|
| Sign-in | `POST /api/auth/login` with `{username, password}`. The backend returns `{token, type, username}`. |
| Session | The JWT is stored in `sessionStorage` under `eip.token`, so it survives a reload but not closing the tab. The username and expiry come from the token's `sub` and `exp` claims. |
| API calls | `src/api/client.js` adds `Authorization: Bearer <token>` to every request except sign-in. |
| Expiry | The session ends when `exp` passes, even with no request in flight. An expired token is never sent. |
| Rejected token | Any 401 on a request that carried a token signs the user out, whether the token expired, `JWT_SECRET` changed or the user was removed. A 401 from sign-in is a wrong password, not a sign-out. |
| Errors | 4xx responses show the backend's `message`. 5xx responses show a generic message. The backend no longer puts exception text in 500 bodies, but the client does not rely on that. |
| CORS | None needed. In development the browser only talks to Vite, which proxies `/api`. |

The client decodes the token but does not verify its signature. It doesn't need
to: the backend verifies the signature on every request, and the client only
reads the token to display the username and time the sign-out.

## Known limits

- **Sign-out is client-side only.** The backend has no token revocation, so a
  copied token stays valid until it expires (24 hours by default).
- **Roles decide what is shown, not what is allowed.** After sign-in the app
  loads `GET /api/auth/me` for the full name, roles and permissions. The account
  menu shows the name and most senior role, and Administration appears only for
  administrators. If the profile cannot load, the menu falls back to the username
  and role-gated items stay hidden. The backend enforces every rule regardless.
- **Dark theme only.** The design has no light variant, so there is no theme toggle.
- **Production serving is not set up.** `npm run build` produces `dist/`, but
  serving it from the same origin as `/api` is part of the Dockerization
  milestone. Until then, the Vite proxy only covers development.
- **No return to the requested page after sign-in.** Only one signed-in page
  exists so far. Add this with the first real routed pages.
