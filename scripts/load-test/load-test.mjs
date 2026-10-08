// Yük testi (yalnız dev mühiti): N şirkət eyni anda — hər şirkətin öz çipi, müştəriləri və bazası.
//
// Axın (real müştəri kimi): imzalı NTAG 424 linki (GET /t/{uid}) → möhürün alınması (POST /api/v1/taps/{token}/claim)
// → kartın açılması (GET /api/v1/me). Toxunuş linkləri real NTAG 424 SUN kriptoqrafiyası ilə yaradılır (AES-CBC + CMAC),
// açarlar platforma panelinin provisioning paketindən götürülür.
//
// Sorğular nginx-siz birbaşa backend-ə (127.0.0.1:8080) gedir: hər virtual müştəri real telefon kimi öz IP-si ilə
// (X-Forwarded-For) gəlir. nginx-in IP limitləri bir maşından göndərilən testi boğardı.
//
// İşə salma (dev compose işləyir):  node scripts/load-test/load-test.mjs [--tenants 50] [--customers 20] [--duration 60]
// Test şirkətləri (lt01, lt02, …) dev bazasında qalır; təkrar işə salanda yenidən yaradılmır.
import { execFile, execFileSync } from 'node:child_process';
import { promisify } from 'node:util';
import { createCipheriv, createHmac, randomBytes, randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import http from 'node:http';

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, value, index, all) => (value.startsWith('--') ? [...pairs, [value.slice(2), all[index + 1]]] : pairs), []),
);
const TENANTS = Number(args.tenants ?? 50);
const CUSTOMERS = Number(args.customers ?? 20);
const DURATION_S = Number(args.duration ?? 60);
const BACKEND = { host: '127.0.0.1', port: Number(process.env.BACKEND_PORT ?? 8080) };
const PLATFORM_HOST = 'platform.localhost';
const DEV_PASSWORD = process.env.DEV_SEED_ADMIN_PASSWORD ?? 'Kofe-Dev-Parol-2026';
const DEV_TOTP_SECRET = Buffer.from('cafe-loyalty-dev-key', 'ascii');
const COMPOSE = ['compose', '-f', 'docker-compose.yml', '-f', 'docker-compose.dev.yml'];
const execFileAsync = promisify(execFile);
const agent = new http.Agent({ keepAlive: true, maxSockets: 512 });

// ------------------------------------------------------------------ HTTP klienti (cookie qabı ilə)

class Client {
  constructor(host, ip = `10.${rand(255)}.${rand(255)}.${1 + rand(250)}`) {
    this.host = host;
    this.ip = ip;
    this.cookies = new Map();
  }

  request(method, path, body) {
    const payload = body === undefined ? undefined : Buffer.from(JSON.stringify(body));
    const headers = {
      Host: this.host,
      'X-Forwarded-For': this.ip,
      'X-Forwarded-Proto': 'https',
      'X-Forwarded-Host': this.host,
      'X-Forwarded-Port': '443',
      'User-Agent': 'Mozilla/5.0 (Linux; Android 14) load-test',
      Accept: 'application/json',
      'Accept-Language': 'az',
    };
    if (this.cookies.size) headers.Cookie = [...this.cookies].map(([k, v]) => `${k}=${v}`).join('; ');
    const csrf = this.cookies.get('__Host-XSRF-TOKEN');
    if (csrf && method !== 'GET') headers['X-XSRF-TOKEN'] = csrf;
    if (payload) {
      headers['Content-Type'] = 'application/json';
      headers['Content-Length'] = payload.length;
    }
    return new Promise((resolve, reject) => {
      const req = http.request({ ...BACKEND, method, path, headers, agent }, (res) => {
        const chunks = [];
        res.on('data', (chunk) => chunks.push(chunk));
        res.on('end', () => {
          for (const cookie of [res.headers['set-cookie'] ?? []].flat()) {
            const [pair] = cookie.split(';');
            const eq = pair.indexOf('=');
            const value = pair.slice(eq + 1);
            if (value === '' || /Max-Age=0/i.test(cookie)) this.cookies.delete(pair.slice(0, eq));
            else this.cookies.set(pair.slice(0, eq), value);
          }
          const text = Buffer.concat(chunks).toString('utf8');
          let json;
          try {
            json = text ? JSON.parse(text) : undefined;
          } catch {
            json = undefined;
          }
          resolve({ status: res.statusCode, headers: res.headers, json, text });
        });
      });
      req.on('error', reject);
      req.setTimeout(30_000, () => req.destroy(new Error('timeout')));
      if (payload) req.write(payload);
      req.end();
    });
  }

  async csrf() {
    if (!this.cookies.has('__Host-XSRF-TOKEN')) await this.request('GET', '/api/v1/csrf');
  }

  async ok(method, path, body, expected = [200, 201, 202, 204]) {
    const res = await this.request(method, path, body);
    if (!expected.includes(res.status)) throw new Error(`${method} ${this.host}${path} → ${res.status} ${res.text.slice(0, 300)}`);
    return res;
  }
}

function rand(n) {
  return Math.floor(Math.random() * n);
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// ------------------------------------------------------------------ TOTP (dev platforma admini)

let lastTotpStep = 0;
async function nextTotp() {
  // Eyni addımın kodu ikinci dəfə qəbul olunmur; server ±1 addımı qəbul edir
  let step = Math.max(Math.floor(Date.now() / 30_000), lastTotpStep + 1);
  while (step > Math.floor(Date.now() / 30_000) + 1) {
    await sleep(2_000);
    step = Math.max(Math.floor(Date.now() / 30_000), lastTotpStep + 1);
  }
  lastTotpStep = step;
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const hmac = createHmac('sha1', DEV_TOTP_SECRET).update(counter).digest();
  const offset = hmac[19] & 0xf;
  const code = ((hmac[offset] & 0x7f) << 24) | (hmac[offset + 1] << 16) | (hmac[offset + 2] << 8) | hmac[offset + 3];
  return String(code % 1_000_000).padStart(6, '0');
}

// ------------------------------------------------------------------ NTAG 424 DNA SUN (bax: Ntag424Sun.java)

function aes(key, block) {
  const cipher = createCipheriv('aes-128-ecb', key, null);
  cipher.setAutoPadding(false);
  return Buffer.concat([cipher.update(block), cipher.final()]);
}

function shiftLeft(buf) {
  const out = Buffer.alloc(16);
  for (let i = 0; i < 16; i++) out[i] = ((buf[i] << 1) | (i < 15 ? buf[i + 1] >> 7 : 0)) & 0xff;
  if (buf[0] & 0x80) out[15] ^= 0x87;
  return out;
}

function cmac(key, message) {
  const k1 = shiftLeft(aes(key, Buffer.alloc(16)));
  const k2 = shiftLeft(k1);
  const blocks = Math.max(1, Math.ceil(message.length / 16));
  const complete = message.length > 0 && message.length % 16 === 0;
  const last = Buffer.alloc(16);
  message.copy(last, 0, (blocks - 1) * 16);
  if (!complete) last[message.length - (blocks - 1) * 16] = 0x80;
  const subkey = complete ? k1 : k2;
  for (let i = 0; i < 16; i++) last[i] ^= subkey[i];
  let x = Buffer.alloc(16);
  for (let b = 0; b < blocks - 1; b++) {
    const block = message.subarray(b * 16, b * 16 + 16);
    for (let i = 0; i < 16; i++) x[i] ^= block[i];
    x = aes(key, x);
  }
  for (let i = 0; i < 16; i++) x[i] ^= last[i];
  return aes(key, x);
}

function counterBytes(counter) {
  return Buffer.from([counter & 0xff, (counter >> 8) & 0xff, (counter >> 16) & 0xff]);
}

function tapPath(tag, counter) {
  const plain = Buffer.concat([Buffer.from([0xc7]), tag.uid, counterBytes(counter), randomBytes(5)]);
  const cipher = createCipheriv('aes-128-cbc', tag.k1, Buffer.alloc(16));
  cipher.setAutoPadding(false);
  const picc = Buffer.concat([cipher.update(plain), cipher.final()]);
  const sv2 = Buffer.concat([Buffer.from([0x3c, 0xc3, 0x00, 0x01, 0x00, 0x80]), tag.uid, counterBytes(counter)]);
  const full = cmac(cmac(tag.k2, sv2), Buffer.alloc(0));
  const mac = Buffer.from([1, 3, 5, 7, 9, 11, 13, 15].map((i) => full[i]));
  const hex = (b) => b.toString('hex').toUpperCase();
  return `/t/${hex(tag.uid)}?picc=${hex(picc)}&cmac=${hex(mac)}`;
}

// ------------------------------------------------------------------ Docker / psql

function docker(argsList, input) {
  return execFileSync('docker', [...COMPOSE, ...argsList], { input, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] });
}

function psql(database, sql) {
  return docker(['exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-d', database, '-v', 'ON_ERROR_STOP=1', '-Atq'], sql).trim();
}

// ------------------------------------------------------------------ Hazırlıq

const slug = (i) => `lt${String(i).padStart(2, '0')}`;
const hostOf = (i) => `${slug(i)}.localhost`;

async function platformAdmin() {
  const admin = new Client(PLATFORM_HOST, '10.255.0.1');
  await admin.csrf();
  const login = await admin.ok('POST', '/api/v1/platform/auth/login', { email: 'superadmin@platform.localhost', password: DEV_PASSWORD });
  // 2FA könüllüdür: aktivdirsə TOTP, deyilsə step-up şifrə ilə
  admin.totp = login.json?.status !== 'AUTHENTICATED';
  if (admin.totp) await admin.ok('POST', '/api/v1/platform/auth/mfa/verify', { code: await nextTotp() });
  return admin;
}

async function withStepUp(admin, action) {
  const first = await action();
  if (first.json?.code !== 'STEP_UP_REQUIRED') return first;
  await admin.ok('POST', '/api/v1/platform/auth/step-up', admin.totp ? { code: await nextTotp() } : { password: DEV_PASSWORD });
  return action();
}

async function setupTenant(admin, i, existing) {
  const s = slug(i);
  let tenant = existing.find((t) => t.slug === s);
  if (!tenant) {
    const res = await withStepUp(admin, () =>
      admin.request('POST', '/api/v1/platform/tenants', {
        slug: s,
        displayName: { az: `Yük testi ${i}`, en: `Load test ${i}`, ru: `Нагрузка ${i}` },
        primaryDomain: hostOf(i),
        defaultLanguage: 'az',
        timezone: 'Asia/Baku',
      }),
    );
    if (res.status !== 201) throw new Error(`provision ${s} → ${res.status} ${res.text}`);
    tenant = res.json;
  }
  // Filial, hədiyyə və sərbəst möhür qaydası — birbaşa test şirkətinin bazasına (sahib paneli olmadan)
  const branchId = psql(
    `loyalty_t_${s}`,
    `INSERT INTO branches (id, name, status, created_at, updated_at)
       SELECT '${randomUUID()}', '{"az":"Mərkəz"}', 'ACTIVE', now(), now() WHERE NOT EXISTS (SELECT 1 FROM branches);
     INSERT INTO reward_rules (id, rule_version, stamps_required, reward_name, active, created_at)
       SELECT '${randomUUID()}', 1, 10, '{"az":"Hədiyyə qəhvə"}', true, now() WHERE NOT EXISTS (SELECT 1 FROM reward_rules);
     UPDATE stamp_policies SET min_interval_minutes = 0, max_stamps_per_day = 50, max_taps_per_tag_per_minute = 600
       WHERE branch_id IS NULL;
     SELECT id FROM branches ORDER BY created_at LIMIT 1;`,
  )
    .split('\n')
    .pop();

  // Çip: platformadan qeydiyyat + provisioning paketi (açarlar), ilk toxunuş PENDING açarı aktivləşdirir
  const uidHex = `04${randomBytes(6).toString('hex').toUpperCase()}`;
  const registered = await admin.ok('POST', `/api/v1/platform/tenants/${tenant.id}/tags`, { uid: uidHex, branchId, label: 'Yük testi' });
  const pkg = await withStepUp(admin, () =>
    admin.request('POST', `/api/v1/platform/tenants/${tenant.id}/tags/${registered.json.id}/provisioning-package`),
  );
  if (pkg.status !== 200) throw new Error(`package ${s} → ${pkg.status} ${pkg.text}`);
  const tag = { uid: Buffer.from(uidHex, 'hex'), k1: Buffer.from(pkg.json.sdmMetaReadKey, 'hex'), k2: Buffer.from(pkg.json.sdmFileReadKey, 'hex'), counter: 0 };
  return { index: i, host: hostOf(i), tenantId: tenant.id, tag, customers: [] };
}

let phoneSeq = Math.floor(Math.random() * 1_000_000);
async function newCustomer(host) {
  const client = new Client(host);
  await client.csrf();
  const phone = `070${String(1_000_000 + (phoneSeq++ % 9_000_000)).slice(-7)}`;
  const challenge = await client.ok('POST', '/api/v1/auth/otp/request', { phone, language: 'az' });
  const e164 = `+99470${phone.slice(3)}`;
  let code;
  for (let attempt = 0; attempt < 40 && !code; attempt++) {
    const outbox = await client.ok('GET', '/api/v1/dev/outbox?channel=SMS');
    code = outbox.json.find((entry) => entry.to === e164)?.body.match(/\b(\d{6})\b/)?.[1];
    if (!code) await sleep(250);
  }
  if (!code) throw new Error(`OTP not found for ${e164}`);
  const verify = await client.ok('POST', '/api/v1/auth/otp/verify', { challengeId: challenge.json.challengeId, code });
  if (verify.json.status === 'REGISTRATION_REQUIRED') {
    const legal = await client.ok('GET', '/api/v1/public/legal');
    await client.ok('POST', '/api/v1/auth/register', {
      registrationToken: verify.json.registrationToken,
      name: 'Yük Testi',
      acceptedDocuments: (legal.json ?? []).map((d) => ({ type: d.type, version: d.version })),
      language: 'az',
    });
  }
  return client;
}

// ------------------------------------------------------------------ Ölçmə

const metrics = {};
function record(name, ms, outcome) {
  const m = (metrics[name] ??= { latencies: [], outcomes: {} });
  m.latencies.push(ms);
  m.outcomes[outcome] = (m.outcomes[outcome] ?? 0) + 1;
}

async function timed(name, fn, classify) {
  const start = performance.now();
  try {
    const res = await fn();
    record(name, performance.now() - start, classify(res));
    return res;
  } catch (error) {
    record(name, performance.now() - start, `error:${error.message.slice(0, 40)}`);
    return undefined;
  }
}

function percentile(sorted, p) {
  return sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))] : 0;
}

function summary(durationS) {
  const rows = {};
  for (const [name, m] of Object.entries(metrics)) {
    const sorted = [...m.latencies].sort((a, b) => a - b);
    rows[name] = {
      count: sorted.length,
      perSecond: +(sorted.length / durationS).toFixed(1),
      p50: +percentile(sorted, 50).toFixed(0),
      p95: +percentile(sorted, 95).toFixed(0),
      p99: +percentile(sorted, 99).toFixed(0),
      max: +(sorted.at(-1) ?? 0).toFixed(0),
      outcomes: m.outcomes,
    };
  }
  return rows;
}

function sampleResources(samples, stop) {
  return (async () => {
    while (!stop.done) {
      try {
        // Asinxron: ölçmə yükün özünü (Node-un hadisə dövrəsini) bloklamamalıdır
        const { stdout: count } = await execFileAsync('docker', [...COMPOSE, 'exec', '-T', 'postgres', 'psql', '-U', 'postgres', '-Atqc', "SELECT count(*) FROM pg_stat_activity WHERE backend_type = 'client backend'"]);
        const connections = Number(count.trim());
        const stats = (await execFileAsync('docker', ['stats', '--no-stream', '--format', '{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}'])).stdout
          .trim()
          .split('\n')
          .filter((line) => /backend|postgres/.test(line));
        samples.push({ t: Date.now(), connections, stats });
      } catch {
        // nümunə buraxılır
      }
      await sleep(1_000);
    }
  })();
}

// ------------------------------------------------------------------ Ssenari: hər şirkətdə bir çip, müştərilər növbə ilə

async function scenario(name, tenants, thinkMs) {
  for (const key of Object.keys(metrics)) delete metrics[key];
  const samples = [];
  const stop = { done: false };
  const sampler = sampleResources(samples, stop);
  const deadline = Date.now() + DURATION_S * 1000;
  const started = Date.now();
  // Bir çipə toxunuşlar ardıcıldır (real çipin sayğacı kimi); 50 şirkət paralel işləyir
  await Promise.all(
    tenants.map(async (tenant) => {
      let turn = 0;
      await sleep(rand(thinkMs || 200));
      while (Date.now() < deadline) {
        const customer = tenant.customers[turn++ % tenant.customers.length];
        tenant.tag.counter += 1;
        const tap = await timed('1. toxunuş (GET /t/…)', () => customer.request('GET', tapPath(tenant.tag, tenant.tag.counter)), (res) => {
          const location = res.headers.location ?? '';
          return res.status === 303 && location.startsWith('/tap/') && !location.startsWith('/tap/error') ? 'ok' : `${res.status} ${location}`;
        });
        const location = tap?.headers.location ?? '';
        if (location.startsWith('/tap/') && !location.startsWith('/tap/error')) {
          const token = location.slice('/tap/'.length);
          await timed('2. möhür (POST claim)', () => customer.request('POST', `/api/v1/taps/${token}/claim`, {}), (res) =>
            res.status === 200 ? 'ok' : `${res.status} ${res.json?.code ?? ''}`,
          );
          await timed('3. kart (GET /api/v1/me)', () => customer.request('GET', '/api/v1/me'), (res) => (res.status === 200 ? 'ok' : `${res.status}`));
        }
        if (thinkMs) await sleep(thinkMs * (0.5 + Math.random()));
      }
    }),
  );
  const elapsed = (Date.now() - started) / 1000;
  stop.done = true;
  await sampler;
  const result = { name, tenants: tenants.length, durationS: +elapsed.toFixed(0), thinkMs, steps: summary(elapsed), resources: resourceSummary(samples) };
  console.log(JSON.stringify(result, null, 2));
  return result;
}

function resourceSummary(samples) {
  const parse = (line, index) => line.split('|')[index];
  const cpu = (pattern) => samples.flatMap((s) => s.stats.filter((l) => l.includes(pattern)).map((l) => parseFloat(parse(l, 1))));
  const mem = (pattern) => samples.flatMap((s) => s.stats.filter((l) => l.includes(pattern)).map((l) => parse(l, 2).split('/')[0].trim()));
  const max = (list) => (list.length ? Math.max(...list) : 0);
  const avg = (list) => (list.length ? list.reduce((a, b) => a + b, 0) / list.length : 0);
  return {
    dbConnectionsMax: max(samples.map((s) => s.connections)),
    dbConnectionsAvg: +avg(samples.map((s) => s.connections)).toFixed(0),
    backendCpuAvgPercent: +avg(cpu('backend')).toFixed(0),
    backendCpuMaxPercent: +max(cpu('backend')).toFixed(0),
    postgresCpuAvgPercent: +avg(cpu('postgres')).toFixed(0),
    postgresCpuMaxPercent: +max(cpu('postgres')).toFixed(0),
    backendMemLast: mem('backend').at(-1),
    postgresMemLast: mem('postgres').at(-1),
  };
}

// ------------------------------------------------------------------ Əsas

async function main() {
  console.log(`Hazırlıq: ${TENANTS} şirkət, hər birində 1 çip və ${CUSTOMERS} müştəri…`);
  const admin = await platformAdmin();
  const existing = (await admin.ok('GET', '/api/v1/platform/tenants')).json;
  const tenants = [];
  for (let i = 1; i <= TENANTS; i++) {
    tenants.push(await setupTenant(admin, i, existing));
    process.stdout.write(`\r  şirkətlər: ${i}/${TENANTS}`);
  }
  console.log();

  // Müştərilər: OTP qlobal limiti (dəqiqədə 300) aşılmasın deyə hissə-hissə
  let created = 0;
  for (const tenant of tenants) {
    while (tenant.customers.length < CUSTOMERS) {
      const batch = await Promise.all(Array.from({ length: Math.min(10, CUSTOMERS - tenant.customers.length) }, () => newCustomer(tenant.host)));
      tenant.customers.push(...batch);
      created += batch.length;
      process.stdout.write(`\r  müştərilər: ${created}/${TENANTS * CUSTOMERS}`);
      await sleep(2_500);
    }
  }
  console.log();

  // İlk toxunuş çipin PENDING açarını aktivləşdirir
  for (const tenant of tenants) {
    tenant.tag.counter += 1;
    const res = await tenant.customers[0].request('GET', tapPath(tenant.tag, tenant.tag.counter));
    if (!(res.headers.location ?? '').startsWith('/tap/') || res.headers.location.startsWith('/tap/error')) {
      throw new Error(`activation tap ${tenant.host} → ${res.status} ${res.headers.location}`);
    }
  }

  const results = [];
  results.push(await scenario('Real pik: hər kafedə ~3 saniyədə bir müştəri', tenants, 3_000));
  results.push(await scenario('Stress: gözləmə yoxdur (hər kafe maksimum sürətlə)', tenants, 0));
  const out = new URL('./last-result.json', import.meta.url);
  writeFileSync(out, JSON.stringify({ at: new Date().toISOString(), tenants: TENANTS, customersPerTenant: CUSTOMERS, results }, null, 2));
  console.log(`Nəticə: ${out.pathname}`);
  agent.destroy();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
