# Deploy-to-Robot over USB — plan (raised 2026-08-23)

**Status: exploratory / parked.** Not a committed milestone. We expect to work on lessons first
and come back to this. This document captures the full design conversation so we can resume cold.

## The goal (the "Pybricks moment")

A student writes code in our browser IDE + sim, clicks one button, and — with the Control Hub on a
USB cable — the code compiles and runs on the real robot. No installed companion app, no cloud, no
OnBot Java *editor*. Reference UX: [Pybricks Code](https://code.pybricks.com/) (browser flashes a
LEGO hub over Web Bluetooth). We want the FTC equivalent over WebUSB.

## Why this is even plausible (what we've already done right)

- Student code is written against **real, unmodified library APIs** (`com.acmerobotics.dashboard.*`,
  `com.qualcomm.hardware.gobilda.GoBildaPinpointDriver`, `com.pedropathing.*`). It is portable by
  design — the sim only swaps in API-compatible shims/impls underneath. The only sim-specific hook
  is a reflection-based `registerForVisualization()` that **no-ops on hardware**.
- So "make it run on a real robot" is not a rewrite — it's a packaging + transport problem.

## The two hard facts that shape everything

1. **The robot is never reachable from the internet.** A Control Hub runs its own isolated network
   (Wi-Fi Direct `192.168.43.x`) or, on the field, the FMS network. No public IP, no inbound route.
   → **A cloud/GitHub-hosted runner physically cannot push to the robot.** Cloud can *compile* (free,
   e.g. GitHub Actions), but the artifact still has to reach the hub from a machine on the local
   network. A self-hosted GitHub runner works but is still a resident local agent — no escape from
   "something local must run."
2. **The hub runs Android/ART, so code must become DEX**, not just compiled JVM `.class`. Our
   browser ECJ produces `.class`; dexing in-browser is the unsolved heavy part. The clean way around
   this is to let the **hub** do the final compile+dex+hot-load (that's exactly what OnBot Java's
   on-hub build engine does). So we use OnBot Java as a *headless build service*, while our browser
   stays the editor.

## Transport decision: USB + WebADB

- OnBot Java is natively a **Wi-Fi** web server on the hub (`http://192.168.43.1:8080`); USB also works.
- For a **scripted one-click push from our own web app**, Wi-Fi `fetch()` is blocked by **CORS**
  (OnBot doesn't send `Access-Control-Allow-Origin`). USB avoids this entirely.
- **[ya-webadb](https://github.com/yume-chan/ya-webadb)** implements the adb protocol in the browser
  over **WebUSB** (Chrome/Edge only). Raw adb sockets are not HTTP origins → **no CORS wall**.
- So: **USB + WebADB** is the friction-free transport for the button. (Wi-Fi remains fine for a human
  browsing the stock OnBot UI, just not for our scripted push.)

## Dependency reality on OnBot Java (the "will competition code work" question)

OnBot Java is really two things: a weak web **editor** (skip it — we have our own) and an on-hub
**compile + hot-load engine** (use it). Bypass the editor and the only gap vs. Android Studio is
resource-heavy `.aar` handling / manifest merging / annotation processors. For our stack:

| Library | OnBot friction | Notes |
|---|---|---|
| **Pedro Pathing** | Low | Pure-Java `.jar`; already proven to compile+run in a plain browser JVM (CheerpJ) in our sim. Real deploy should use an **official Pedro release jar**, not our sim-tailored `pedro-core.jar`. |
| **goBILDA Pinpoint** | None | Distributed as a **single `.java` source file** — just include it. Our shim is API-identical. |
| **Limelight 3A** | None | **Built into the FTC SDK** (`com.qualcomm.hardware.limelightvision.Limelight3A`). Just an import on a real hub. (Sim would need an optional shim, like Pinpoint — not a blocker.) |
| **FTC Dashboard** | **Medium — the one asterisk** | Ships as an `.aar` with bundled web-UI assets — the historically finicky OnBot upload. If it loads, you get **full live dashboard telemetry on the real robot** (graphs + Pinpoint-driven field render). Our no-op stub jar is only a *sim convenience / fallback*, NOT a limitation forced on the real robot. |

**Same code, not a branch.** The OpMode source is identical whether Dashboard is real or stubbed —
behavior is decided by *which jar is present at compile time*, never by editing student code.

## The plan (staged by information value, no time estimates)

**Two independent risks; the button only matters once both are green:**
- **Build risk** — does `competition_code` (Pedro + Dashboard + Pinpoint) compile and run on the hub?
- **Transport risk** — can the browser talk to the hub over WebUSB/adb at all?

### Stage 0 — De-risk with tools that already exist (do this FIRST)

- **0a. Build spike (answers build risk) — no code from us.** Using the stock OnBot Java web UI:
  join the hub's Wi-Fi, upload Pedro `.jar` + Dashboard `.aar` + Pinpoint `.java` as external
  libraries, paste `competition_code`, build, and drive. This is *the* make-or-break test and
  needs only a hub + laptop. If the Dashboard `.aar` is going to fight, this is where we learn it.
- **0b. Transport spike (answers transport risk) — throwaway page.** Minimal ya-webadb page:
  WebUSB connect → `adb shell echo hello` from the hub. **Watch the wrinkle:** adb key
  authorization on a **headless** Control Hub (no on-screen "allow USB debugging" prompt). Usually
  auto-accepts or is pre-authorized, but verify here in ~30 lines rather than inside real UI.

If both pass, confidence is earned by evidence and the rest is plumbing.

### Stage 1 — Deploy mechanism (the real work)

On button click:
1. WebUSB connect + adb handshake (reuse 0b).
2. `adb push` the current package's `.java` files into OnBot's source dir (`/sdcard/FIRST/java/src/...`).
3. Trigger the on-hub build by writing **raw HTTP over an adb socket** to the hub's `:8080` (OnBot's
   own web UI drives save / build/start / build/wait endpoints). Raw socket ⇒ no CORS.
4. Stream the build log into a console panel; report success + registered OpMode name, or surface
   compile errors.

External libs (Pedro/Dashboard/Pinpoint) are pushed **once during setup**, not every deploy.

### Stage 2 — Button + UX in `web/index.html`

- "Deploy to Robot (USB)" button near existing controls.
- Connection state machine (connect → push → build → done) + reconnect path.
- Build-error display routed into the existing console.
- "Now select the OpMode on the Driver Station" nudge.
- Caveat to document for users: **WebUSB is Chrome/Edge only.**

### Effort summary

| Stage | Effort | Buys |
|---|---|---|
| 0a build spike | Small | Proves competition code runs on OnBot — the make-or-break |
| 0b transport spike | Small | Proves browser↔hub over USB; flushes the adb-auth wrinkle |
| 1 deploy mechanism | Medium | The push/build/log plumbing |
| 2 button + UX | Small–Medium | The polished one-click experience |

## Open unknowns to resolve (honest list)

1. Does OnBot cleanly load Pedro's full multi-class jar at real size? (Likely yes for pure Java; untested by us on a hub.)
2. Is an **official** Pedro release jar hub-compatible as an OnBot external lib? (Our `pedro-core.jar` was proven only in a browser JVM.)
3. Does the FTC Dashboard `.aar` load in OnBot's external-libs manager and actually serve its live UI? (The single biggest asterisk.)
4. adb authorization behavior on a **headless** Control Hub over WebADB.
5. WebUSB availability constraint (Chrome/Edge only) — acceptable for a class that standardizes on Chrome.

## Recommended resume point

Start with **Stage 0a** — cheapest, highest-information, runnable today with just a hub, a laptop,
and the stock OnBot web UI. It either green-lights this whole direction or names the exact wall to
rethink, before any button code exists. **Do not build Stages 1–2 until 0a passes.**

## Related artifacts in this repo

- `spikes/pedro-cheerpj/pedro_spike.html` (and `web/pedro_spike.html`) — existing spike: does the
  real `pedro-core.jar` compile & run in-browser (CheerpJ)? Evidence Pedro is pure-enough Java.
- `java/shim/com/acmerobotics/dashboard/` — no-op `FtcDashboard` / `TelemetryPacket` / `Canvas`
  (the sim-side Dashboard stub; also the fallback OnBot stub jar).
- `java/sim/GoBildaPinpointDriverImpl.java` — feeds the Pinpoint shim from physics.
- `course/starter/` and `java/examples/.../teamcode/` — the starter stub and the `competition_code`
  endpoint this whole pipeline would deploy.
