# Big-ticket items (raised 2026-08-14)

Three larger initiatives to work through, one at a time, with the user. Not milestones in the
dependency-ordered M-series (see v2_roadmap in agent memory) — these are bigger, more open-ended
efforts that each deserve their own design conversation before implementation.

## 1. Graphics polish — DONE (2026-08-16), one deferred sub-item
Shadows, lighting, game field enhancements/decorations. Self-contained three.js work; no
architecture decisions block it. Good candidate for fast, visible wins.

Shipped: shadow maps (PCFSoftShadowMap) + ACES tone mapping, a full gym shell (floor/walls/
ceiling/bleachers/doors/GO RED/GO BLUE banners) lit by a real CC0 HDRI environment map, and a
full match-start light-show ceremony (fade to black, field reset, camera cut, red then blue
banner+field spotlight sweeps done in parallel, camera zoom + lights-up to the Driver Station
view, foghorn + pulsing "LET'S GO!", then match start) — iterated through several rounds of
user feedback (darkness fix, phase-order fix, parallel red/blue, spotlight brightness/width,
pacing, LET'S GO framing).
Deferred (not built, low priority): the low/medium/high graphics-quality settings tier from the
original phased plan (toggle shadowMap/antialias/devicePixelRatio/bloom for lower-end
Chromebooks). Revisit if perf becomes an issue.

Reference: the v1 (Godot) simulator had significant atmosphere:
- Field placed inside a high school gymnasium: bleachers at the far end, a standard double door
  between them.
- Score rendered as an actual high-school-style scoreboard mounted on the gym wall, with dot-matrix
  digit numbers.
- Match-start sequence: ambient lighting dims, red/blue spotlights sweep the field with a heartbeat
  sound, alternating "GO RED"/"GO BLUE" text building excitement, a foghorn, a glowing/pulsing
  "LET'S GO" text with a lightning-energy look, then lights come back up and the match begins.
- Shadows, anti-aliasing, alpha glow on lights.
- Configurable graphics level (low/medium/high) in settings so students can trade looks for
  performance (relevant here too, maybe more so — CheerpJ's JVM already competes for the main
  thread with rendering).

v2 (three.js) is capable of all of this, achieved differently than Godot:
- Shadow maps: `renderer.shadowMap.enabled` + `castShadow`/`receiveShadow` per mesh + directional/
  spot light `castShadow` — standard, low-effort.
- Fog: built-in `THREE.Fog`/`FogExp2`.
- Glow/bloom: needs vendoring three.js's `EffectComposer` + `UnrealBloomPass` (same "vendor locally,
  no CDN" approach already used for three.module.js/rapier3d.mjs) — very doable.
- Animated spotlights sweeping + color changes: trivial, just tween `SpotLight.angle`/`target`/
  `color` over time.
- Glowing pulsing text ("LET'S GO", "GO RED"/"GO BLUE"): cheaper and more Chromebook-friendly as a
  canvas-texture plane (same technique already used for the wiffle-ball and wheel-arrow textures)
  than three.js `TextGeometry` (extruded 3D text, needs a loaded font JSON) — recommend the canvas
  approach, animate opacity/scale, let bloom pass make it "glow".
- Dot-matrix scoreboard: the CURRENT scoreboard is an HTML/CSS overlay (`#matchbar`), not a 3D
  mesh, and it already reads well (user likes it, wants to keep it as the FUNCTIONAL score/clock).
  A 3D wall-mounted scoreboard PROP is possible as a purely decorative/atmosphere flourish later
  (static or simple, not live-updating) — low priority, avoid overengineering.
- Gym shell (bleachers, back wall, doors): straightforward low-poly BoxGeometry stacks, no need for
  an external model/importer. Should be simplified/toggled off at lower graphics-quality tiers.
- Audio cues (heartbeat, foghorn, crowd "GO RED"/"GO BLUE" chant): the sim already has a small SFX
  system (`web/audio/*.mp3` + `SFX`/`playSfx()` in index.html) for match_start/autonomous_complete/
  tele_start/endgame_start/match_end — same pattern extends cleanly. Currently NO heartbeat/foghorn/
  crowd-chant clips exist in `web/audio/` — need to source or have the user supply clips.
- Graphics quality tiers (low/med/high): NOT built into three.js, needs a manual settings-driven
  toggle (shadowMap on/off, antialias, devicePixelRatio cap, bloom pass on/off, bleacher/crowd
  poly count or hide entirely at low). Fits naturally as a new "Graphics" section in the SETTINGS
  bottom sheet already built. Recommended to build this scaffold EARLY (Phase 1) rather than bolt
  it on after effects exist, precisely because CheerpJ's JVM runs cooperatively on the same main
  thread as rendering (documented perf finding: M4 PERF note in v2_roadmap) — heavy effects will
  compound with compile-time slowdowns more than they would in a native engine.

Proposed phased plan (subject to discussion):
1. Shadow maps + the low/medium/high Settings scaffold (foundation, verify perf impact first).
2. Static gym shell: simple bleachers + back wall + double doors, toggled/simplified per tier;
   warmer gym-style ambient + a few point/spot lights (vs current flat lighting).
3. Match-start light-show sequence (spotlights sweep, heartbeat, GO RED/GO BLUE, foghorn, glowing
   pulsing "LET'S GO", lights back up) — triggered from `startMatch()`/`beginPlay()`. Needs new SFX
   assets (ask user for clips or source them) and new choreography code. Most "fun", also most work.
4. (Optional, low priority) decorative 3D wall-mounted scoreboard prop — cosmetic only; the HTML
   `#matchbar` overlay stays the functional score/clock per user's preference.

## 2. Lessons / starter-code system v2 + interactive widgets
Port the v1 concept (NOT the exact v1 structure — user explicitly wants something different this
time):
- A starter code package with comment-flag anchors that lessons cross-link to (existing anchor
  mechanism already exists for the current single-package model — see M6a in v2_roadmap — but the
  PROGRESSIVE, multi-lesson revert semantics below are new).
- Each lesson progressively builds on the previous one.
- Each lesson has a "Revert to beginning" that syncs the student's code as if every prior lesson
  had already been completed (not just the very first starter state).
- v1 had a Python authoring tool to help produce/maintain this progression — worth revisiting for
  ideas, not necessarily reusing directly.
- New want: interactive widgets, e.g. PID tuning explorer, Mecanum drive force exploration.

This needs a real design conversation before implementation: how lesson anchors and per-lesson
progressive snapshots interact with the current package-store model (`impulse.pkgs.v2` in
localStorage, `store.packages[name].files`), what the widget architecture looks like (probably
small standalone panels/canvases, possibly reusing the Pit's bench-viewport pattern). Big scope,
likely multi-session.

## 3. GitHub Pages publishing prep
Mostly a checklist, but with one real open question:
- **Licensing — RESOLVED (2026-08-31)**: Leaning Technologies granted this project free, unrestricted
  use of CheerpJ under the **Community License** (<https://cheerpj.com/licensing>) — email from Waqas
  confirms free non-commercial educational use (no ads, no paid content) is exactly the intended use,
  and covers a public-facing site. **One standing obligation:** showcase the **CheerpJ logo** on the
  pages where the app runs (the logo, not just the current text credit in the splash). Add the logo
  asset to `web/assets/` and render it persistently before public ship.
- Repo visibility: user wants the possibility of a PUBLIC Pages site backed by a PRIVATE repo,
  until the project is further along — need to confirm this is supported by their GitHub plan
  (public Pages from a private repo requires GitHub Pro/Team/Enterprise, or a paid personal plan —
  free personal accounts can only publish Pages from PUBLIC repos). Worth checking their plan tier.
- Build-time-only artifacts (`tools/node/`, `tools/editor-build/node_modules/`) should be
  gitignored / excluded from what ships, if not already.
- COOP/COEP note (from the M4 perf entry in v2_roadmap): if a Web Worker + `SharedArrayBuffer`
  move is ever pursued for perf, GH Pages can't natively set the required headers — would need the
  `coi-serviceworker` workaround (still fully static, no server changes needed).

## 4. Offline use / installable PWA — IN PROGRESS (started 2026-08-31)
Pybricks-style: install once, then the whole tool (IDE + CheerpJ compile + sim + lessons + USB
deploy) works with **no internet**. Two-layer cache model (application shell vs. independently
versioned environment packages like "FTC 2026 Standard" / "Pedro 2.0.6") + service worker + web app
manifest. The #1 blocker is the CDN-hosted CheerpJ runtime (self-host it same-origin) and the SW
Range responder (206 synthesis) needed by the JVM classpath. Full executable plan:
**[OFFLINE_PWA_PLAN.md](OFFLINE_PWA_PLAN.md)**. Licensing — CLEARED (CheerpJ Community License
granted, self-host OK; only obligation = show the CheerpJ logo where the app runs, same as #3).

## 5. USB↔Robot Wi-Fi via a local system-tray helper — NOT STARTED
A small resident local app (system tray) that bridges the browser to the Control Hub over **Wi-Fi**
(`192.168.43.1:8080`), punching through the CORS wall that blocks a pure-browser `fetch()` to
OnBotJava. Complements (does not replace) the existing zero-install WebUSB deploy. Design fork:
localhost CORS-proxy vs. a helper that also speaks ADB-over-USB (both transports in one binary);
packaging for a Windows classroom (Go/Rust single-binary + tray vs. Tauri/Electron). Shares a client
layer with the USB deploy work (see agent memory `usb_onbotjava_spike`). Needs its own design pass —
parked until offline (#4) lands.

---
Recommended starting point (agreed 2026-08-14): **Graphics polish (#1)** — fastest visible win, no
blocking design decisions, safe to run in parallel with the (slower, discussion-heavy) lessons-v2
design conversation.
