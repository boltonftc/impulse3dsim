/* Impulse3DSim service worker — offline shell + runtime cache with HTTP Range support.
 *
 * Strategy:
 *  - Normal GETs: network-first (online behavior unchanged), fall back to cache offline;
 *    successful 200s are cached.
 *  - Range GETs (CheerpJ runtime `/cj/*` + classpath `*.jar`): pass the real Range request
 *    straight to the network when online (unchanged), and in the BACKGROUND fetch the full file
 *    once so that offline we can synthesize 206 Partial Content from the cached whole file.
 *
 * Two cache layers (per OFFLINE_PWA_PLAN.md):
 *  - SHELL   (versioned by APP_VERSION): the app itself.
 *  - RUNTIME (its own version): CheerpJ runtime + jars — big, changes on a different cadence.
 */
const APP_VERSION = new URL(self.location).searchParams.get('v') || 'dev';
const SHELL = 'impulse-shell-' + APP_VERSION;
const RUNTIME = 'impulse-runtime-v1';

// Small, always-present shell files worth precaching so the app can boot offline even if the
// first visit was interrupted before everything got runtime-cached. Big/optional assets (audio,
// lessons, jars, CheerpJ runtime) are cached opportunistically on first use instead.
const PRECACHE = [
  './', './index.html', './manifest.webmanifest',
  './vendor/three.module.js', './vendor/rapier3d.mjs', './vendor/codemirror.bundle.js',
  './vendor/RGBELoader.js', './vendor/ziplite.js',
  './assets/icon-192.png', './assets/icon-512.png',
  './assets/cheerpj_logo.png', './assets/sim_icon_v2.png', './assets/splash.jpg'
];

self.addEventListener('install', (e) => {
  e.waitUntil((async () => {
    const cache = await caches.open(SHELL);
    // resilient precache: one bad URL must not abort the whole install
    await Promise.all(PRECACHE.map(async (u) => {
      try { const r = await fetch(u, { cache: 'no-store' }); if (r && r.ok) await cache.put(u, r); } catch (_) {}
    }));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', (e) => {
  e.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.map((k) => (k.startsWith('impulse-shell-') && k !== SHELL) ? caches.delete(k) : Promise.resolve()));
    await self.clients.claim();
  })());
});

self.addEventListener('message', (e) => {
  const data = e.data;
  if (data === 'SKIP_WAITING') { self.skipWaiting(); return; }
  if (data === 'CLEAR_CACHES' || (data && data.type === 'CLEAR_CACHES')) {
    e.waitUntil(caches.keys()
      .then((ks) => Promise.all(ks.map((k) => caches.delete(k))))
      .then(() => { if (e.ports && e.ports[0]) e.ports[0].postMessage({ ok: true }); }));
    return;
  }
  if (data && data.type === 'CACHE_STATUS') {
    e.waitUntil(cacheStatus().then((s) => { if (e.ports && e.ports[0]) e.ports[0].postMessage(s); }));
  }
});

// Summarize what's cached so the UI can show an "offline-ready" indicator. runtimeReady means the
// CheerpJ loader + core wasm + JDK JImage + at least one classpath jar are present — i.e. an offline
// compile should succeed.
async function cacheStatus() {
  let shell = 0, runtime = 0, loader = false, wasm = false, jimage = false, anyJar = false;
  try {
    shell = (await (await caches.open(SHELL)).keys()).length;
    const rk = await (await caches.open(RUNTIME)).keys();
    runtime = rk.length;
    for (const req of rk) {
      const p = new URL(req.url).pathname;
      if (p.endsWith('cj/loader.js')) loader = true;
      else if (p.endsWith('cj3.wasm')) wasm = true;
      else if (p.endsWith('17/lib/modules')) jimage = true;
      if (p.endsWith('.jar')) anyJar = true;
    }
  } catch (_) {}
  return { version: APP_VERSION, shell, runtime, runtimeReady: loader && wasm && jimage && anyJar };
}

function isRuntime(url) { return url.pathname.includes('/cj/') || url.pathname.endsWith('.jar'); }
function cacheFor(url) { return isRuntime(url) ? RUNTIME : SHELL; }

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return; // cross-origin (e.g. CheerpJ CDN in prod) passes through
  if (req.headers.get('range')) { event.respondWith(rangeStrategy(url, req.headers.get('range'))); return; }
  event.respondWith(networkFirst(req, url));
});

async function networkFirst(req, url) {
  const key = url.pathname + url.search;
  try {
    const net = await fetch(req);
    if (net && net.status === 200) { const c = await caches.open(cacheFor(url)); c.put(key, net.clone()); }
    return net;
  } catch (err) {
    const cached = (await caches.match(key)) || (await caches.match(req));
    if (cached) return cached;
    throw err;
  }
}

const pendingFull = new Set();
function fetchFullAndCache(url, key) {
  if (pendingFull.has(key)) return;
  pendingFull.add(key);
  fetch(url.href, { cache: 'no-store' })
    .then((r) => (r && r.status === 200) ? caches.open(cacheFor(url)).then((c) => c.put(key, r)) : null)
    .catch(() => {})
    .finally(() => pendingFull.delete(key));
}

async function rangeStrategy(url, range) {
  const key = url.pathname + url.search;
  const cache = await caches.open(cacheFor(url));
  const full = await cache.match(key);
  if (full) return buildPartial(full, range);           // offline-capable slice from cached whole file
  let netRange;
  try { netRange = await fetch(url.href, { headers: { Range: range } }); } // online: unchanged behavior
  catch (e) { return new Response(null, { status: 504, statusText: 'Offline' }); }
  fetchFullAndCache(url, key);                            // populate cache for future offline slices
  return netRange;
}

async function buildPartial(fullResp, rangeHeader) {
  const buf = await fullResp.clone().arrayBuffer();
  const total = buf.byteLength;
  const m = /bytes=(\d*)-(\d*)/.exec(rangeHeader || '');
  let start = m && m[1] !== '' ? parseInt(m[1], 10) : 0;
  let end = m && m[2] !== '' ? parseInt(m[2], 10) : total - 1;
  if (isNaN(start)) start = 0;
  if (isNaN(end) || end >= total) end = total - 1;
  if (start > end || start >= total) {
    return new Response(null, { status: 416, headers: { 'Content-Range': `bytes */${total}` } });
  }
  const headers = new Headers();
  const ct = fullResp.headers.get('Content-Type'); if (ct) headers.set('Content-Type', ct);
  headers.set('Content-Range', `bytes ${start}-${end}/${total}`);
  headers.set('Content-Length', String(end - start + 1));
  headers.set('Accept-Ranges', 'bytes');
  return new Response(buf.slice(start, end + 1), { status: 206, statusText: 'Partial Content', headers });
}
