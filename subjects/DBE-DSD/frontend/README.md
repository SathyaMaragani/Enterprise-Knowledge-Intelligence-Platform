# EIP Frontend (DBE-DSD)

React single-page app for the Enterprise Knowledge Intelligence Platform.

**Current state:** sign-in, dashboard, search, document repository, document
viewer, upload and user administration, built to the product UI design on top of
Frontend 1's routing, JWT session handling, protected routes and sign-out.
Categories and analytics are not built yet; the navigation shows them as "Soon".

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

For production, `Dockerfile` builds the app and serves it with nginx, which also
proxies `/api` to the backend (`BACKEND_URL`, default `http://backend:8080`).
`../docker/README.md` runs it together with the backend and databases.

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
| Search bar | `POST /api/search` with the query and optional category. Results show inline with relevance and match signals (Keyword, Semantic, Fuzzy), plus an "Open in search" link carrying the query and category to the search page. |
| Keyword / Semantic / Hybrid | Report which mode the backend ran, from the response's `sources`. The backend chooses the mode, so these are not a selector. |
| Category filter | Categories of the documents the user can read. |
| Quick Actions | Upload Document opens the upload page for roles with `DOCUMENT_CREATE` and says "Not permitted for your role" otherwise. Advanced Search opens the search page. Analytics and Categories are marked "Coming soon". |
| Recent Documents | `GET /api/documents`, newest update first. |
| System Overview | Documents, categories and indexed counts over the user's readable documents; vector chunks from `GET /api/search/vector/collection-info`, which is collection-wide. |
| Recent Activity | Derived from document `createdAt` / `updatedAt`. The backend keeps no audit trail, so this shows additions and edits only. |

Every figure comes from the API. When a request fails, the section says so or
shows `—`; nothing falls back to placeholder numbers.

The hero glow behind the glass documents is threeui's `nebula` scene.

Recent document titles and search hits open the document viewer; "View All"
opens the repository.

### Search (`/search`)

`POST /api/search`, 10 results per page. The query, category, status and page
live in the URL (`/search?q=leave&status=INDEXED&page=1`), so searches can be
shared, reloaded and revisited with back.

| Element | Behaviour |
|---|---|
| Search bar | Runs on submit. Nothing is sent until there is a query. |
| Category / Status | Narrow the results and return to the first page. |
| Mode chips | Light up Keyword, Semantic or Hybrid from the response's `sources`. |
| Results | Title (opens the viewer), description, category, status and match signals; owner, keyword score, raw semantic score and best chunk; overall relevance bar. |
| Previous / Next | Shown when there is more than one page. |

The previous results stay on screen while the next page or filter loads.
Department is not offered: it only filters vector search, and no endpoint lists
departments.

### Repository (`/repository`)

A paged table of every document the user may read, from
`GET /api/documents/page` (10 per page), newest change first.

| Control | Behaviour |
|---|---|
| Title/description filter | Applied on submit, not on every keystroke. |
| Category | From `GET /api/categories`. |
| Status | Indexed, Processing, Uploaded, Archived, Failed. |
| Previous / Next | Disabled at the first and last page. |
| Clear filters | Shown only while a filter is set. |

Filters and page live in the URL (`/repository?page=1&status=INDEXED`), so
reload, back and shared links keep the view. Changing a filter returns to the
first page.

### Document viewer (`/documents/:id`)

`GET /api/documents/{id}`: the PostgreSQL metadata and the MongoDB content
together. Shows the header (type, title, description, category, status, owner,
last update), the extracted text with word and character counts, the chunks in
order, and panels for source, processing, version, metadata and references.

A 403 explains that the user lacks access; a 404 says whether the document or
only its stored content is missing. A non-numeric id is rejected without a
request.

Roles with `DOCUMENT_DELETE` get a Delete button, which asks for confirmation
before calling `DELETE /api/documents/{id}`. On success the repository opens
with a notice; on failure the document stays open with the error.

### Upload (`/upload`)

`POST /api/documents` as multipart form data. Reached from the dashboard's
Upload Document action or the repository's Upload document button, both shown
only to roles with `DOCUMENT_CREATE`.

| Field | Behaviour |
|---|---|
| File | `.txt`, `.md` or `.markdown`, non-empty, at most 1 MB. Checked on selection and again on submit. |
| Title | Optional; the placeholder shows the file name it defaults to. |
| Description | Optional. |
| Category | Required, from `GET /api/categories`. |
| Department | Optional; defaults to the category. |

After a successful upload the new document opens with a notice saying how many
chunks were stored and what search can find: title, description and meaning
when vectors were stored; only title and description when they were not.
Backend rejections are shown on the form, which stays filled in.

A role without `DOCUMENT_CREATE` sees an explanation instead of the form, and a
profile that cannot be loaded is reported rather than left loading.

### Document access

The document viewer shows an **Access** panel to the document's owner and to
anyone with `USER_MANAGE`. It lists current grants with Revoke buttons and gives
READ access by username. Unknown users, the owner and duplicates are reported
from the backend's message.

### Administration (`/admin`)

For roles with `USER_MANAGE`; everyone else sees an explanation, and the sidebar
link is hidden.

| Area | Behaviour |
|---|---|
| Accounts | Every user with full name, username, email, role, status and creation date. |
| Role | A select per user; a change is saved immediately. |
| Status | Disable or Enable. Disabling ends the account's sessions at once. |
| Password | Reset… opens an inline field for a new password (8–72 characters). |
| Add a user | Username, full name, email, role and initial password. |

The signed-in administrator's own role and status controls are disabled, matching
the backend's lockout protection. After every change the list reloads and a
notice confirms it; failures are shown next to the action.

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
  copied token stays valid until it expires (24 hours by default), unless an
  administrator disables the account, which rejects its tokens at once.
- **Roles decide what is shown, not what is allowed.** After sign-in the app
  loads `GET /api/auth/me` for the full name, roles and permissions. The account
  menu shows the name and most senior role, and Administration appears only for
  users with `USER_MANAGE`. If the profile cannot load, the menu falls back to the username
  and role-gated items stay hidden. The backend enforces every rule regardless.
- **Dark theme only.** The design has no light variant, so there is no theme toggle.
- **Production serving is not set up.** `npm run build` produces `dist/`, but
  serving it from the same origin as `/api` is part of the Dockerization
  milestone. Until then, the Vite proxy only covers development.
- **Sign-in returns to the requested page.** Opening `/documents/3` while
  signed out goes to sign-in, then back to `/documents/3`.
