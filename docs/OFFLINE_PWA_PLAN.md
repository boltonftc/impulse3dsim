# Offline / installable-PWA plan (raised 2026-08-31)

**Status: design, ready to execute in phases.** Big-ticket item #4. The goal is a Pybricks-style
experience: install the app once, then it launches and works **with no internet** — write code,
compile in-browser (CheerpJ), run the sim, do lessons, and deploy to a robot over USB. The only
things that ever need the network are (a) the very first install/warm-up and (b) pulling *new*
releases or *new* environment packages.

Reference UX: [Pybricks Code](https://code.pybricks.com/) — an installable PWA that runs fully
offline after first load, with the firmware/runtime cached locally.

---

## 1. How the app loads today (grounded facts)

The app is *already* a single-page, mostly-static site. Current network dependencies at boot:

| Asset group | Where | Served from | Offline-ready today? |
|---|---|---|---|
| **CheerpJ runtime** (loader.js + WASM + on-demand JDK class archives) | `web/index.html` head (`document.write` loader) | **CDN** `https://cjrtnc.leaningtech.com/4.3/` in prod; `/cj/` reverse-proxy in dev ([range_server.py](../tools/range_server.py) `CJ_ORIGIN`) | ❌ **the #1 blocker** — CDN, cross-origin, Range-fetched |
| **App/compiler jars** | `app.jar`, `ecj.jar`, `shim.jar`, `jdk-base.jar` | same-origin, CheerpJ classpath `/app/...`, **Range** requests | ⚠️ same-origin (good) but Range needs SW handling |
| **Vendored ES modules** | `vendor/three.module.js`, `rapier3d.mjs`, `codemirror.bundle.js`, `RGBELoader.js`, `ziplite.js` ([index.html](../web/index.html#L805)) | same-origin static | ✅ trivially cacheable |
| **Lessons** | `lessons/module.json` + `lessons/<folder>/lesson.html` (+ media) | same-origin static | ✅ |
| **Starter/student code** | `student/package.json` + `student/*.java`, seeded into `localStorage['impulse.pkgs.v2']` | same-origin static + localStorage | ✅ (localStorage already offline; files just need precache) |
| **Audio SFX** | `audio/*.mp3` (7 clips) | same-origin static | ✅ |
| **Graphics assets** | `assets/env/` HDRI, `assets/splash.jpg`, `assets/sim_icon_v2.png` | same-origin static | ✅ |
| **Robot-deploy libs** (for USB deploy) | `DEPLOY_LIBS` → `pedro-core.jar` / `PedroPathing-core-2.0.6.jar`; `pedro_templates/*.java` system templates | same-origin static | ✅ but conceptually a *different layer* (see §3) |

**Takeaway:** everything except the CheerpJ CDN runtime is already same-origin static. Solve CheerpJ
+ add a service worker + a manifest and we have an offline PWA.

---

## 2. The blockers, precisely

1. **CheerpJ runtime is CDN-hosted and cross-origin.** A service worker *can* cache cross-origin
   responses, but they're **opaque** and CheerpJ fetches runtime pieces with **Range** requests —
   opaque + Range in a SW cache is unreliable. **Fix: self-host the CheerpJ 4.3 runtime same-origin**
   under `web/cj/` so the SW caches it like any other asset. (This also removes the CDN dependency
   even when online.)
2. **Range requests through a service worker.** `cache.match()` ignores the `Range` header and
   returns the **full 200** response; CheerpJ (and our jar classpath) need **206 Partial Content**.
   The SW must implement a **Range responder**: on a request carrying `Range`, pull the *full* cached
   body and synthesize a `206` with the correct `Content-Range`/slice. This is the single trickiest
   piece of code in the whole effort.
3. **First-visit warm-up.** Offline only works after the caches are populated. Need a one-time
   "installing… (downloading runtime)" progress step on first visit, then it's permanent.
4. **License / redistribution — CLEARED (2026-08-31).** Leaning Technologies granted this project
   free, unrestricted use of CheerpJ under the **Community License**
   (<https://cheerpj.com/licensing>) — email from Waqas (CheerpJ team): free non-commercial
   educational content with no ads and no paid content is exactly the intended use, so self-hosting
   the runtime is fine. **One standing obligation:** *showcase the CheerpJ logo on the pages where
   the app runs* (the actual logo, not just a text credit). Tracked as a compliance task in §4.

---

## 3. Cache model — two independent layers

Adopt the two-layer split (per design discussion). They version and update **independently**.

### Layer A — Application shell
The Impulse app itself, versioned and updated as **one coherent release**:
```
Impulse app shell  (cache: impulse-shell-v<APP_VERSION>)
├─ index.html + inline JS/CSS
├─ vendor/*  (three, rapier, codemirror, RGBELoader, ziplite)
├─ app.jar, ecj.jar, shim.jar, jdk-base.jar      (compiler + sim + SDK shims)
├─ cj/**  (self-hosted CheerpJ 4.3 runtime)        ← added in Phase 1
├─ lessons/**, audio/*, assets/*
├─ starter/**, student/**  (seed packages)
└─ manifest.webmanifest + icons
```
- Cache key includes an **`APP_VERSION`** constant. A new release ships a new SW → new versioned
  cache → `activate` deletes old shell caches → clients prompted to reload. Atomic; no half-updated
  shells.

### Layer B — Environment packages
The **robot-runtime targets**, each independently named + versioned, several installable at once:
```
Environment packages  (cache: impulse-env-<id>-v<ver>, one per package)
├─ FTC 2026 Standard        (SDK-version metadata, shim expectations)
├─ Pedro 2.0.6              (PedroPathing-core-2.0.6.jar + pedro_templates/*.java + Pinpoint .java)
├─ Pedro 2.1.x              (future — the multi-version-Pedro roadmap item)
└─ (later) FTC Dashboard aar, Limelight notes, …
```
- Backed by a small **registry in IndexedDB**: `{ id, name, version, files[], installedAt, active }`.
- Maps directly onto today's `DEPLOY_LIBS` + `pedro_templates` + `systemTemplates` concepts — this
  layer is where "which Pedro/SDK do I deploy" lives, decoupled from app releases.
- UI: list installed env packages, install/remove, mark one **active** (drives USB deploy + the
  sim's classpath choice). Installing a package = fetch its file set once → its own versioned cache
  + registry row.

**Why the split matters:** the IDE/sim changes on *our* cadence; the robot targets change on the
*FTC season / Pedro release* cadence. Splitting the caches means a new season's Pedro jar doesn't
invalidate the shell, and an app bugfix doesn't force re-downloading robot libs.

---

## 4. PWA pieces to build

1. **`web/manifest.webmanifest`** — `name`, `short_name` "Impulse", `start_url` (subpath-safe),
   `display: standalone`, theme/background colors, **icons** (192/512 maskable — derive from
   `assets/sim_icon_v2.png`). Linked from `index.html` head.
2. **`web/sw.js`** — the service worker:
   - `install`: precache the **Layer-A shell manifest** (a generated list — see Phase 0).
   - `activate`: delete shell caches whose version ≠ current `APP_VERSION`; `clients.claim()`.
   - `fetch`:
     - **Range responder** for jars + CheerpJ runtime (206 synthesis from full cached body).
     - **cache-first** for shell assets, **stale-while-revalidate** optional for lessons.
     - Layer-B env-package requests served from their own caches by id/version.
   - `message`: `SKIP_WAITING` (apply update), env-package install/remove commands.
3. **Registration + update UX in `index.html`** — register `sw.js`; on `updatefound`/`waiting`,
   show a non-blocking "Update ready → Reload" pill; first-visit "Installing offline runtime…"
   progress (precache is the long pole because of the CheerpJ runtime size).
4. **Self-hosted CheerpJ** — mirror `cjrtnc.leaningtech.com/4.3/**` into `web/cj/`, flip the head
   loader to **always** use `/cj/loader.js` (relative → works on Pages under a subpath too).
5. **CheerpJ logo attribution (Community License compliance)** — add the CheerpJ logo persistently
   visible on the running app page (not only the transient splash "Special Thanks" credits). Needs
   the logo asset (attached to Leaning Technologies' email) dropped into `web/assets/`.

---

## 5. Phased execution (each phase independently verifiable)

> Constraint reminder: `web/**` JS/HTML/CSS + static assets → **browser reload only, no Java
> rebuild**. Jars only change if we rebuild them (we won't for this feature).

### Phase 0 — Shell inventory + version stamp  *(foundation)*
- Add an **`APP_VERSION`** constant (index.html + injected into `sw.js`).
- Write a tiny build/emit step (`tools/gen_sw_manifest.py` or inline) that walks `web/` and emits
  **`web/sw-manifest.json`** = the Layer-A precache URL list + content hashes (so the SW knows the
  exact shell set and cache-busts per release).
- **Verify:** manifest lists every asset in §3 Layer A; hashes change when a file changes.

### Phase 1 — Self-host CheerpJ runtime  *(kills the #1 blocker; do first, it's the risk)*
- Capture the **exact** runtime file set the loader pulls (drive the app through the existing
  `/cj/` dev proxy with DevTools Network recording → the list of `cjrtnc…/4.3/*` URLs incl. the
  lazily-fetched JDK class archives). Mirror them into `web/cj/`.
- Change the head loader ([index.html](../web/index.html#L11)) to unconditionally load
  `/cj/loader.js` (relative). Keep the dev proxy as a fallback for *fetching new* runtime files.
- **Verify (online):** app boots + compiles a program with the runtime served from `web/cj/`
  (Network shows **no** `cjrtnc.leaningtech.com` hits).
- **License:** ✅ cleared — Community License granted (§2.4); self-hosting permitted. Just carry the
  CheerpJ-logo attribution task (§4.5) into the shipped UI.

### Phase 2 — Service worker: offline shell + Range responder  *(the core)*
- Implement `sw.js` (`install`/`activate`/`fetch` with the **206 Range responder**); register it.
- **Verify:** load once online → DevTools **Offline** → full reload → app boots, sim runs, and a
  **compile succeeds offline** (proves CheerpJ runtime + JDK classes + jars all serve from cache
  with correct 206s). This is the make-or-break test for the whole feature.

### Phase 3 — Installable PWA  *(the Pybricks moment)*
- Add `manifest.webmanifest` + icons; wire the install prompt + update pill.
- **Verify:** Chrome shows Install; installed app launches **standalone**, works offline, and shows
  an "Update ready" pill when `APP_VERSION` bumps.

### Phase 4 — Environment-package layer  *(Layer B)*
- IndexedDB registry + per-package versioned caches; a package manifest format
  (`env/<id>/package.json` = `{ id, name, version, files[] }`).
- Repackage today's Pedro/SDK assets as the first env package **"Pedro 2.0.6"** (+ "FTC 2026
  Standard" metadata). Route `DEPLOY_LIBS` + `pedro_templates` through the active env package.
- UI: install / remove / select-active env package.
- **Verify:** app installs "Pedro 2.0.6" offline; USB deploy + sim classpath both read from it;
  removing it frees its cache without touching the shell.

### Phase 5 — Update & status UX  *(polish)*
- New-release detection, per-env-package update, an **offline/online indicator**, first-visit
  precache progress bar, and a "clear caches / reinstall" escape hatch in Settings.

### Phase 6 — License compliance + publishing sign-off
- ✅ CheerpJ Community License granted (§2.4). Remaining: ensure the **CheerpJ logo** is shown on
  the running app page (§4.5) before public ship, and fold this into the GitHub Pages publishing
  decision (item #3 in [BIG_TICKET_ITEMS.md](BIG_TICKET_ITEMS.md)).

---

## 6. Open questions to resolve

1. **CheerpJ self-host licensing** — ✅ RESOLVED 2026-08-31: Community License granted, self-host
   allowed; only obligation is showing the CheerpJ logo on pages where the app runs (§4.5).
2. **Runtime size** — the CheerpJ runtime + JDK archives are tens of MB; acceptable to precache for
   an installed PWA, but confirm the first-visit download is tolerable on classroom Wi-Fi.
3. **Range responder correctness** — validate against CheerpJ's exact request pattern (multi-range?
   suffix ranges?) — most JVM classpath reads are single open-ended ranges, but verify.
4. **Env-package distribution** — do env packages ship inside the repo (simplest) or fetch from a
   separate URL so a new Pedro can drop without an app release? (Leaning toward in-repo now,
   URL-fetchable later.)
5. **Subpath hosting** — everything must stay relative so it works both at localhost and under a
   Pages project subpath (`/impulse3dsim/`); the classpath code already derives `appDir` this way.

## 7. Recommended start
**Phase 1 (self-host CheerpJ)** — it's the highest-risk unknown and unblocks everything else, and
it's independently useful (removes the CDN dependency even online). The licensing gate is now
**cleared** (§2.4), so this can proceed; just carry the CheerpJ-logo attribution task (§4.5). Then
Phase 2's offline-compile test tells us the whole model works before we build UI.
