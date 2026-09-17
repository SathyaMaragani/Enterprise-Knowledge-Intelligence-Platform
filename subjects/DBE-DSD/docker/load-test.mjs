// Closed-loop load test against a running stack: CONCURRENCY virtual users each
// sign in once, then repeat a read-heavy mix (search, repository page, document,
// profile) for DURATION seconds. Node 20+, no dependencies.
//
//   BASE_URL=http://localhost:8088 USERNAME=... PASSWORD=... \
//   CONCURRENCY=20 DURATION=60 node load-test.mjs
//
// Every virtual user shares one account, so it measures request handling, not
// login. It only reads; seed documents first for meaningful search numbers.

const BASE_URL = (process.env.BASE_URL || 'http://localhost:8088').replace(/\/$/, '');
const { USERNAME, PASSWORD } = process.env;
const CONCURRENCY = Number(process.env.CONCURRENCY || 20);
const DURATION = Number(process.env.DURATION || 60);
if (!USERNAME || !PASSWORD) {
  console.error('Set USERNAME and PASSWORD');
  process.exit(2);
}

const QUERIES = ['remote work policy', 'quarterly budget', 'security incident response', 'vpn credentials',
  'travel expenses', 'onboarding checklist', 'data retention', 'architecture review'];

const login = await fetch(`${BASE_URL}/api/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
});
if (!login.ok) {
  console.error(`Login failed: ${login.status}`);
  process.exit(1);
}
const auth = { Authorization: `Bearer ${(await login.json()).token}` };

const firstPage = await (await fetch(`${BASE_URL}/api/documents/page?size=50`, { headers: auth })).json();
const documentIds = firstPage.items.map((d) => d.id);

const scenarios = {
  search: (i) => fetch(`${BASE_URL}/api/search`, {
    method: 'POST',
    headers: { ...auth, 'Content-Type': 'application/json' },
    body: JSON.stringify({ query: QUERIES[i % QUERIES.length] }),
  }),
  page: (i) => fetch(`${BASE_URL}/api/documents/page?page=${i % 3}&size=20`, { headers: auth }),
  document: (i) => documentIds.length
    ? fetch(`${BASE_URL}/api/documents/${documentIds[i % documentIds.length]}`, { headers: auth })
    : fetch(`${BASE_URL}/api/auth/me`, { headers: auth }),
  me: () => fetch(`${BASE_URL}/api/auth/me`, { headers: auth }),
};
// Weighted mix: search is the expensive path, so it gets the largest share.
const MIX = ['search', 'search', 'search', 'search', 'page', 'page', 'page', 'document', 'document', 'me'];

const results = Object.fromEntries(Object.keys(scenarios).map((name) => [name, { latencies: [], errors: 0 }]));
const deadline = Date.now() + DURATION * 1000;

async function virtualUser(id) {
  for (let i = id; Date.now() < deadline; i++) {
    const name = MIX[i % MIX.length];
    const started = performance.now();
    try {
      const res = await scenarios[name](i);
      await res.arrayBuffer();
      if (res.ok) results[name].latencies.push(performance.now() - started);
      else results[name].errors++;
    } catch {
      results[name].errors++;
    }
  }
}

const percentile = (sorted, p) => sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * p))] : NaN;
const ms = (value) => (Number.isNaN(value) ? '-' : value.toFixed(0)).padStart(7);

console.log(`${CONCURRENCY} virtual users for ${DURATION}s against ${BASE_URL} (${documentIds.length} readable documents)\n`);
const started = Date.now();
await Promise.all(Array.from({ length: CONCURRENCY }, (_, i) => virtualUser(i)));
const elapsed = (Date.now() - started) / 1000;

console.log('endpoint     requests  errors    rps   p50ms  p95ms  p99ms  maxms');
let total = 0;
let errors = 0;
for (const [name, { latencies, errors: failed }] of Object.entries(results)) {
  const sorted = latencies.sort((a, b) => a - b);
  total += sorted.length + failed;
  errors += failed;
  console.log(`${name.padEnd(12)} ${String(sorted.length + failed).padStart(8)} ${String(failed).padStart(7)}`
    + ` ${(sorted.length / elapsed).toFixed(1).padStart(6)} ${ms(percentile(sorted, 0.5))}${ms(percentile(sorted, 0.95))}`
    + `${ms(percentile(sorted, 0.99))}${ms(sorted.at(-1) ?? NaN)}`);
}
console.log(`\ntotal ${total} requests, ${errors} errors, ${(total / elapsed).toFixed(1)} req/s overall`);
process.exit(errors ? 1 : 0);
