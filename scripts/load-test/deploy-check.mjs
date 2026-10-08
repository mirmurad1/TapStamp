// Deploy zamanı fasiləsiz trafik (yalnız dev): nginx üzərindən (HTTPS) davamlı sorğular göndərir və hər cavabı sayır.
// Sıfır fasiləli deploy-u yoxlamaq üçün: bu skript işləyərkən başqa terminalda ./scripts/deploy.sh --dev --build.
//
//   node scripts/load-test/deploy-check.mjs [--duration 240]
//
// Sorğular: backend GET (şirkət məlumatı), backend POST (CSRF ilə), frontend (ana səhifə). nginx-in IP limiti
// (20 sorğu/san) aşılmasın deyə API sorğuları saniyədə ~12-dir. Uğur: 2xx/3xx; 429 ayrıca sayılır (limit, xəta deyil).
import https from 'node:https';

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, value, index, all) => (value.startsWith('--') ? [...pairs, [value.slice(2), all[index + 1]]] : pairs), []),
);
const DURATION_S = Number(args.duration ?? 240);
const HOST = 'mockcoffee.localhost';
const agent = new https.Agent({ keepAlive: true, maxSockets: 32, servername: HOST, rejectUnauthorized: false });

function request(method, path, headers = {}) {
  return new Promise((resolve) => {
    const started = performance.now();
    const req = https.request(
      { host: '127.0.0.1', port: Number(process.env.HTTPS_PORT ?? 443), method, path, agent, servername: HOST, headers: { Host: HOST, ...headers } },
      (res) => {
        res.resume();
        res.on('end', () => resolve({ status: res.statusCode, ms: performance.now() - started, cookies: res.headers['set-cookie'] }));
      },
    );
    req.on('error', (error) => resolve({ status: `ERR ${error.code ?? error.message}`, ms: performance.now() - started }));
    req.setTimeout(15_000, () => req.destroy(new Error('timeout')));
    req.end();
  });
}

const counts = {};
const failures = [];
function record(name, res) {
  const key = `${name} → ${res.status}`;
  counts[key] = (counts[key] ?? 0) + 1;
  const ok = typeof res.status === 'number' && (res.status < 400 || res.status === 429);
  if (!ok) failures.push({ at: new Date().toISOString().slice(11, 23), name, status: res.status });
}

async function loop(name, intervalMs, fn) {
  const deadline = Date.now() + DURATION_S * 1000;
  while (Date.now() < deadline) {
    const next = Date.now() + intervalMs;
    record(name, await fn());
    await new Promise((resolve) => setTimeout(resolve, Math.max(0, next - Date.now())));
  }
}

const csrfRes = await request('GET', '/api/v1/csrf');
const csrf = (csrfRes.cookies ?? []).map((c) => c.split(';')[0]).find((c) => c.startsWith('__Host-XSRF-TOKEN='));
const token = csrf?.split('=')[1] ?? '';

const progress = setInterval(() => {
  const total = Object.values(counts).reduce((a, b) => a + b, 0);
  process.stdout.write(`\r  sorğular: ${total}, xətalar: ${failures.length}   `);
}, 1000);

await Promise.all([
  // Backend GET: ~8/san (4 paralel dövr)
  ...Array.from({ length: 4 }, () => loop('GET /api/v1/public/company', 500, () => request('GET', '/api/v1/public/company'))),
  // Backend POST (CSRF): ~4/san
  ...Array.from({ length: 2 }, () =>
    loop('POST /api/v1/auth/logout', 500, () => request('POST', '/api/v1/auth/logout', { Cookie: csrf ?? '', 'X-XSRF-TOKEN': token, 'Content-Length': 0 })),
  ),
  // Frontend: ~10/san
  ...Array.from({ length: 4 }, () => loop('GET / (frontend)', 400, () => request('GET', '/'))),
]);
clearInterval(progress);
console.log('\n\nCavablar:');
for (const [key, value] of Object.entries(counts).sort()) console.log(`  ${key}: ${value}`);
console.log(`\nXətalar (5xx, bağlantı xətası): ${failures.length}`);
for (const failure of failures.slice(0, 30)) console.log(`  ${failure.at} ${failure.name} → ${failure.status}`);
agent.destroy();
process.exit(failures.length === 0 ? 0 : 1);
