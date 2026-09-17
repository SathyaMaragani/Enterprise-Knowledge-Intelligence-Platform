// End-to-end check of a running stack through the public entry point (nginx),
// exactly as a browser reaches it. Node 20+, no dependencies.
//
//   BASE_URL=http://localhost:8088 ADMIN_USERNAME=... ADMIN_PASSWORD=... node smoke-test.mjs
//
// It creates two throwaway users (disabled again at the end; the API has no user
// delete) and one document (deleted at the end).

const BASE_URL = (process.env.BASE_URL || 'http://localhost:8088').replace(/\/$/, '');
const { ADMIN_USERNAME, ADMIN_PASSWORD } = process.env;
if (!ADMIN_USERNAME || !ADMIN_PASSWORD) {
  console.error('Set ADMIN_USERNAME and ADMIN_PASSWORD');
  process.exit(2);
}

let failures = 0;
function check(name, ok, detail = '') {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok || !detail ? '' : `  (${detail})`}`);
  if (!ok) failures++;
}

async function call(method, path, { token, json, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (json) headers['Content-Type'] = 'application/json';
  const res = await fetch(BASE_URL + path, { method, headers, body: json ? JSON.stringify(json) : form });
  const text = await res.text();
  let body = text;
  try { body = JSON.parse(text); } catch { /* not JSON */ }
  return { status: res.status, headers: res.headers, body };
}

async function login(username, password) {
  const res = await call('POST', '/api/auth/login', { json: { username, password } });
  return res.status === 200 ? res.body.token : null;
}

async function waitForApi(seconds = 120) {
  for (let i = 0; i < seconds; i++) {
    try {
      if ((await fetch(`${BASE_URL}/api/health`)).ok) return true;
    } catch { /* not up yet */ }
    await new Promise((r) => setTimeout(r, 1000));
  }
  return false;
}

const suffix = Date.now().toString(36);
const manager = { username: `smoke_mgr_${suffix}`, password: `Mgr-${suffix}-pass!` };
const employee = { username: `smoke_emp_${suffix}`, password: `Emp-${suffix}-pass!` };
const cleanup = [];

try {
  check('API reachable through the web server', await waitForApi());

  // The web app itself, including a client-side route served by the SPA fallback.
  const home = await call('GET', '/');
  check('GET / serves the app shell', home.status === 200 && String(home.body).includes('<div id="root">'));
  const deepLink = await call('GET', '/repository');
  check('GET /repository falls back to the app shell', deepLink.status === 200 && String(deepLink.body).includes('<div id="root">'));

  check('Unauthenticated API call is rejected', (await call('GET', '/api/documents/page')).status === 401);
  check('Wrong password is rejected', (await call('POST', '/api/auth/login', { json: { username: ADMIN_USERNAME, password: 'wrong-password' } })).status === 401);

  const admin = await login(ADMIN_USERNAME, ADMIN_PASSWORD);
  check('Bootstrap administrator can sign in', Boolean(admin));
  if (!admin) throw new Error('cannot continue without an administrator token');

  const me = await call('GET', '/api/auth/me', { token: admin });
  check('Administrator profile has USER_MANAGE', me.status === 200 && me.body.permissions.includes('USER_MANAGE'));

  const categories = await call('GET', '/api/categories', { token: admin });
  check('Reference categories are loaded', categories.status === 200 && categories.body.length === 6);

  for (const [account, role] of [[manager, 'MANAGER'], [employee, 'EMPLOYEE']]) {
    const created = await call('POST', '/api/admin/users', {
      token: admin,
      json: { ...account, email: `${account.username}@example.com`, fullName: `Smoke ${role}`, role },
    });
    check(`Administrator creates a user with role ${role}`, created.status === 201, `${created.status} ${JSON.stringify(created.body)}`);
    if (created.status === 201) {
      cleanup.push(() => call('PATCH', `/api/admin/users/${created.body.id}`, { token: admin, json: { active: false } }));
    }
  }

  const managerToken = await login(manager.username, manager.password);
  const employeeToken = await login(employee.username, employee.password);
  check('New users can sign in', Boolean(managerToken && employeeToken));

  check('Employee cannot reach administration', (await call('GET', '/api/admin/users', { token: employeeToken })).status === 403);

  const form = new FormData();
  const marker = `zephyrquill${suffix}`;
  const text = `Smoke test handbook ${marker}.\n\nRemote workers must encrypt laptops and rotate their VPN credentials every ninety days. `
    + 'Travel expenses are reimbursed within two weeks when receipts are attached. '.repeat(20);
  form.append('file', new Blob([text], { type: 'text/plain' }), 'smoke-handbook.txt');
  form.append('title', `Smoke handbook ${marker}`);
  form.append('description', 'Created by docker/smoke-test.mjs');
  form.append('category', categories.body[0].name);
  check('Employee cannot upload', (await call('POST', '/api/documents', { token: employeeToken, form })).status === 403);

  const upload = await call('POST', '/api/documents', { token: managerToken, form });
  check('Manager uploads a document', upload.status === 201, `${upload.status} ${JSON.stringify(upload.body)}`);
  const docId = upload.body?.id;
  // MANAGER has no DOCUMENT_DELETE (see reference-data.sql), so the administrator removes it.
  if (docId) cleanup.unshift(() => call('DELETE', `/api/documents/${docId}`, { token: admin }));
  check('Upload is chunked and embedded', upload.body?.status === 'INDEXED' && upload.body.vectorsStored > 0,
        JSON.stringify(upload.body));

  const doc = await call('GET', `/api/documents/${docId}`, { token: managerToken });
  check('Owner reads the document back', doc.status === 200);

  check('Employee cannot read an ungranted document', (await call('GET', `/api/documents/${docId}`, { token: employeeToken })).status === 403);
  const hidden = await call('POST', '/api/search', { token: employeeToken, json: { query: marker } });
  check('Search hides it from the employee', hidden.status === 200 && !hidden.body.hits.some((h) => h.documentId === docId));

  const grant = await call('POST', `/api/documents/${docId}/permissions`, { token: managerToken, json: { username: employee.username } });
  check('Owner grants the employee read access', grant.status === 201, `${grant.status} ${JSON.stringify(grant.body)}`);
  check('Employee reads it after the grant', (await call('GET', `/api/documents/${docId}`, { token: employeeToken })).status === 200);

  const keyword = await call('POST', '/api/search', { token: employeeToken, json: { query: marker } });
  check('Keyword search finds it', keyword.body.hits?.[0]?.documentId === docId, JSON.stringify(keyword.body.hits?.slice(0, 2)));

  const semantic = await call('POST', '/api/search', { token: employeeToken, json: { query: 'how often should VPN passwords be changed' } });
  const semanticHit = semantic.body.hits?.find((h) => h.documentId === docId);
  check('Semantic search finds it by meaning', Boolean(semanticHit?.matchedBy.includes('VECTOR')), JSON.stringify(semantic.body.hits?.slice(0, 2)));

  const page = await call('GET', '/api/documents/page?size=5', { token: employeeToken });
  check('Repository page lists it for the employee', page.status === 200 && page.body.items.some((d) => d.id === docId));

  check('Manager cannot delete (no DOCUMENT_DELETE)', (await call('DELETE', `/api/documents/${docId}`, { token: managerToken })).status === 403);

  const tooBig = new FormData();
  tooBig.append('file', new Blob(['a'.repeat(1_100_000)], { type: 'text/plain' }), 'big.txt');
  tooBig.append('category', categories.body[0].name);
  const big = await call('POST', '/api/documents', { token: managerToken, form: tooBig });
  check('Oversized upload is refused with 413', big.status === 413, `${big.status}`);
} catch (error) {
  check('Smoke test ran to completion', false, error.message);
} finally {
  for (const step of cleanup) {
    const res = await step();
    check(`Cleanup (${res.status})`, res.status < 300);
  }
}

console.log(failures ? `\n${failures} check(s) failed` : '\nAll checks passed');
process.exit(failures ? 1 : 0);
