# EIP Frontend (DBE-DSD)

React single-page app for the Enterprise Knowledge Intelligence Platform.

**Current state:** the app has these pages:
- sign-in
- dashboard
- search
- document repository
- document viewer
- upload
- user administration
- the TextHack algorithm workbench

It uses a light product style, white cards on a faintly green page with a deep
green accent, and is built on Frontend 1's routing,
JWT session handling, protected routes and sign-out. Only built pages appear in
the navigation; category management and analytics do not exist yet and are not
advertised.

## Design conventions

`src/styles.css` holds one small design system. Follow it rather than styling
case by case:

| Piece | Rule |
|---|---|
| Tokens | Colours, radii, control height (`--control-height`, 2.5rem) and input background are variables on `:root`. The accent is `--accent` (deep green); links use `--accent-text`. |
| Tones | `.tone--green`, `--blue`, `--amber`, `--coral` set a card's accent, soft tile, wash, wave and border. Dashboard cards and the insights tiles use them. |
| Buttons | `.btn` with `--primary`, `--outline` or `--danger`; `--small` for dense rows. No glow, no hover movement. |
| Inputs | `.text-input` and `.select` share the control height and radius; selects draw one consistent chevron. |
| Badges | `.tag` (category) and `.status` (document state) share one shape. Match signals are `.signal` dots, not badges. |
| Page header | `.page-header` with `.page-title` and one `.page-subtitle` line; actions sit to the right with `--with-action`. No eyebrow labels. |
| Cards | `.glass-panel.section` with a `.section__title`; dashboard panels use `.panel` with a `.panel__header` (icon, title, View all). |
| Hidden content | Use the `hidden` attribute; a global `[hidden]` rule keeps component display rules from overriding it. |
| Illustrations | Inline SVG in `components/DashboardArt.jsx`; no images, no WebGL. |
| Type | Plus Jakarta Sans from Google Fonts (Caveat for the dashboard's handwritten note), falling back to the system UI font. |

Stack: React 19, React Router 7, Vite 8, Vitest 4 with Testing Library and
and jsdom. Plain JavaScript and plain CSS; no UI kit, icon library, chart
library or state library.

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

For production there are two paths, both keeping the browser on a single origin:

- **Vercel** (the chosen hosting). `vercel.mjs` proxies `/api/*` to the Render
  backend named by the `BACKEND_URL` project variable and serves `index.html` for
  client-side routes. The build fails if the variable is missing. The free backend
  sleeps when idle, so `ServerGate` holds the app on a "Starting the server" screen
  until `/api/health` answers. See `../deploy/README.md`.
- **Docker.** `Dockerfile` builds the app and serves it with nginx, which also
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

Every signed-in page shares the sidebar and a top bar. The top bar holds:

- a global search box with a category picker, which opens `/search` with the
  query and category;
- a filters button that opens the search page;
- a notifications bell, which says there is nothing new (the backend sends no
  notifications, so it shows no unread marker);
- the account menu (name and role).

The search box is left out only on the search page, which leads with its own,
fuller form.

### Sign-in

A two-panel page.

The left panel has the brand, one headline, one line of copy and three features
on a soft green gradient, with the same document-and-search illustration as the
dashboard beside the copy. Below 1250px wide there is no column beside the copy,
so the illustration fades behind it; the whole left panel is hidden below 1100px.

The right panel holds a compact "Sign in" card. The card repeats the brand only
when the left panel is hidden.

| Element | Behaviour |
|---|---|
| Username / Password | Signs in with `POST /api/auth/login`. The backend looks users up by username only, so the field does not offer email. |
| Show / hide password | Toggles the password field between hidden and plain text. |
| Forgot password? | Explains that resets go through an administrator. The backend has no self-service reset. |

Single sign-on is not offered, because the backend has none.

### Dashboard

| Section | Data |
|---|---|
| Hero | A time-of-day greeting with the user's first name, and the search mode chips (Hybrid, Semantic, Keyword, Fuzzy). The chosen mode is kept in the URL (`/?mode=fuzzy`) and applied by the top bar search. An **Upload document** button appears for roles with `DOCUMENT_CREATE`; a file dropped on it opens the upload form with that file chosen. Other roles see no upload action. |
| Overview cards | Documents, indexed and categories over the user's readable documents. Vector chunks come from `GET /api/search/vector/collection-info`, which is collection-wide. Each card's arrow opens the matching repository or search view. |
| Recent documents | `GET /api/documents`, newest update first. When there are none, an illustrated empty state invites an upload (or, without upload rights, says there is nothing to show). |
| Your recent searches | `GET /api/search/history?limit=5`: the user's own latest searches with mode, result count and time. Each one reopens the search in its mode. |
| Document activity | Derived from document `createdAt` / `updatedAt`. The backend keeps no audit trail, so this shows additions and edits only. |
| Explore features | Links to Search, Repository, TextHack and, for `USER_MANAGE`, Administration. |

Every figure comes from the API. When a request fails, the section says so or
shows `—`; nothing falls back to placeholder numbers.

Recent document titles open the document viewer; "View all" opens the
repository or the search page.

### Search (`/search`)

`POST /api/search`, 10 results per page. The query, mode, category, status and
page live in the URL (`/search?q=leave&mode=fuzzy&status=INDEXED&page=1`), so
searches can be shared, reloaded and revisited with back. Hybrid is the default
and is left out of the URL; an unknown mode is treated as Hybrid.

| Element | Behaviour |
|---|---|
| Search bar | Runs on submit. Nothing is sent until there is a query. |
| Mode control | Hybrid, Semantic, Keyword or Fuzzy (see `docs/API.md`), with the chosen mode explained. Choosing one re-runs the search from the first page. |
| Filters | Shows or hides Category and Status, with a count of those applied. Opens by default when the URL already has filters. |
| Category / Status | Narrow the results and return to the first page. |
| Keyword-only notice | Shown when Hybrid ran without semantic search. |
| Results | Title (opens the viewer), description, category, status and owner. How the hit matched (Keyword, Semantic, Fuzzy) shows as labelled dots, beside an overall relevance percentage and bar. Raw per-signal scores and chunk ids are internal and not shown. |
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
together. It shows:
- the header: type, title, description, category, status, owner, last update
- the extracted text, with word and character counts
- the chunks, in order
- one grouped **Details** panel: source, processing and version
- **Metadata** and **References** panels when present

File types read as names ("Excel spreadsheet") with the MIME type on hover.
Statuses and metadata keys are humanized.

A 403 explains that the user lacks access; a 404 says whether the document or
only its stored content is missing. A non-numeric id is rejected without a
request.

Roles with `DOCUMENT_DELETE` get a Delete button, which asks for confirmation
before calling `DELETE /api/documents/{id}`. On success the repository opens
with a notice; on failure the document stays open with the error.

### Upload (`/upload`)

`POST /api/documents` as multipart form data. Reached from the **Upload
document** button on the dashboard or the repository, shown only to roles with
`DOCUMENT_CREATE`.

| Field | Behaviour |
|---|---|
| File | `.pdf`, `.docx`, `.txt`, `.md` or `.markdown`, non-empty, at most 10 MB. Checked on selection and again on submit. PDFs must contain a text layer; scanned/image-only PDFs are not OCRed. |
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
| Role | A select per user showing Admin, Manager or Employee; a change is saved immediately. |
| Status | Disable or Enable. Disabling ends the account's sessions at once. |
| Password | Reset… opens an inline field for a new password (8–72 characters). |
| Add a user | Username, full name, email, role and initial password. |

The signed-in administrator's own role and status controls are disabled, matching
the backend's lockout protection. After every change the list reloads and a
notice confirms it; failures are shown next to the action.

### TextHack (`/texthack`)

A workbench for the DSA-3 engine, open to every signed-in user. Four tabs show
one tool at a time. Tabs support arrow keys, and each tool keeps its input and
results while hidden. Each tool sends its input to `/api/texthack/*` and shows
what the engine computed:

| Tab | Shows |
|---|---|
| Pattern search | Match count and algorithm (KMP or Aho-Corasick), the text with matches highlighted (overlapping matches merge), each match's offsets, and the longest repeated substring |
| Similarity | Levenshtein and Damerau distances, similarity, and the global and local alignments with score and identity |
| Citation flow | Influence (maximum flow), the bottleneck citations (minimum cut) and the source side of the cut; citations are typed one `from to` per line and checked before sending |
| Complexity | Time and space for every algorithm in the engine |

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
- **Light theme only.** The design has no dark variant, so there is no theme toggle.
- **Production serving is not set up.** `npm run build` produces `dist/`, but
  serving it from the same origin as `/api` is part of the Dockerization
  milestone. Until then, the Vite proxy only covers development.
- **Sign-in returns to the requested page.** Opening `/documents/3` while
  signed out goes to sign-in, then back to `/documents/3`.
