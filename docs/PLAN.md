# impulse_3dsim v2 — Browser-Based FTC Learning Platform

**Status:** Planning / de-risking · **Owner:** solo mentor-developer · **Target:** free, public, fully in-browser FTC programming platform served from GitHub Pages.

---

## 1. Vision

A single static web app that lets an 8th-grade FTC student:

1. Open a **web IDE** (Monaco) in any browser — including school-managed **Chromebooks** (the real driver: no JDK / Android Studio / Godot install is possible there).
2. **Compile and run Java** OpMode code entirely client-side, against a shimmed FTC SDK (no Android).
3. Watch their code drive a **robot in a physics simulator** in the same page.
4. **Pull from and push to their own GitHub repo** (and other authorized repos) from the browser.
5. Work through **lessons** that end in runnable example code — the pedagogical model that makes impulse_3dsim work.

Everything is hosted as **static assets on GitHub Pages**. No application backend.

---

## 2. Hard constraints (non-negotiable)

- **Fully static / zero backend.** Must run from a GitHub Pages URL. No server-side compile, no app server.
- **Client-side Java compile + run** of *most of the FTC SDK surface* (via our existing shim layer, not the real Android SDK).
- **Shimmed hardware**, reusing the impulse_3dsim shim approach (plain Java, no Android runtime).
- **Git pull/push to arbitrary authorized repos** from the browser IDE.
- **License-clean** for a free, public, educational tool.

### Two things that can secretly drag a backend back in (and how we avoid them)

- Using **isomorphic-git's raw git transport** → needs a CORS proxy. **Avoid** by using the GitHub REST "Git Data" API (CORS-clean).
- Using **OAuth web/device flow** → needs a client secret or a token-polling relay. **Avoid** by using fine-grained Personal Access Tokens (or a GitHub App device flow only if CORS cooperates).

---

## 3. Key insight that makes this feasible

We are **not** running the real (Android) FTC SDK in the browser — that is impossible on any browser JVM. We run our **shim layer**: plain-Java stub classes with the same package/method signatures. Once everything is plain Java, a browser JVM (CheerpJ) can both **compile** it (`javac`/ECJ) and **run** it. The shim layer is the reusable crown jewel from v1.

**Pipeline:** student `TeamCode` (plain Java) + shim jar (plain Java) → `javac` in browser → run bytecode on browser JVM in a Web Worker → OpMode drives the physics sim via shim hardware objects.

---

## 4. Target architecture (zero-backend)

```
GitHub Pages (static assets only — no backend)
├── Monaco editor (web IDE)
├── CheerpJ 4.3 runtime (loader.js via <script>)   ← compiles AND runs Java
├── shim jar (plain-Java FTC SDK stubs)
├── physics sim (Godot web export OR JS engine + renderer)
├── lessons / hints / grading content
└── app JS (glue)

              GitHub Pages (static assets)
                     │
        ┌────────────┼────────────┐
        │            │            │
     Editor      CheerpJ 4.3   GitHub API
    (Monaco)    (<script>,      (REST Git Data
        │        no backend)     API + token)
        │            │            │
        │            │       pull/push student repo
        ▼            ▼
  student .java  ┌─────────── Web Worker ────────────┐
        └───────▶│  javac ──produces──▶ bytecode     │
                 │   ▲                     │         │
                 │   │                     ▼         │
                 │ FTC shim jar ──────▶   JVM        │
                 │ (compile + run cp)      │         │
                 └─────────────────────────┼─────────┘
                                           │ postMessage
                                           ▼ (hardware state)
                                      simulation
                                   (main-thread render)
```

Notes: the **shim jar is shared** by both the compile step (`javac` classpath) and the run step (JVM classpath); `javac`'s output bytecode **feeds the JVM** (sequential, one CheerpJ instance invoked with two different main classes); the blocking `runOpMode()` loop lives in a **Web Worker** so the UI never freezes.

Rationale for the Worker: `LinearOpMode` blocks (`while (opModeIsActive())`). Running it on the main thread freezes the UI, so the JVM + student loop live in a Web Worker and message hardware state to the sim/renderer.

---

## 5. Component plan

| Component | Choice | Notes |
|---|---|---|
| Editor | **Monaco** | It *is* VS Code's editor; drop-in, static. |
| Java compile + run | **CheerpJ 4.3 + ECJ** | Confirmed: integrates via a `<script>` tag, runtime fully client-side, **no backend**. In-browser compile **verified** (DR-2 ✅) using hosted **ECJ** + a hosted SE API bootclasspath (`-proc:none`, target 1.8); stock `javac` is absent from the runtime. Licensing pending (see §7). |
| OSS compile fallback | **DoppioJVM (MIT)** | Only if CheerpJ license fails; slower/older — perf spike required. |
| Hardware shims | **Reuse impulse v1 shims** | Plain Java; keep chasing SDK API changes. |
| Physics sim | **Godot web export (reuse)** *or* JS engine (Rapier / planck.js) + three.js | Reuse = less rewrite but heavy; JS = lighter but a rewrite. Decide in a spike. |
| Git pull/push | **GitHub REST "Git Data" API** + fine-grained PAT | CORS-clean, no proxy, no server. isomorphic-git only if real git semantics needed. |
| Lessons | Rework v1 fill-section model around the **4-part subsystem shape** | Content, not runtime risk. |

### How "push" works with no git client (GitHub REST Git Data API)
1. `GET /repos/{o}/{r}/git/ref/heads/{branch}` → head commit SHA
2. `GET /repos/{o}/{r}/git/commits/{sha}` → tree SHA
3. `POST /repos/{o}/{r}/git/blobs` (base64 content) → blob SHA (per changed file)
4. `POST /repos/{o}/{r}/git/trees` (`base_tree` + changed paths) → new tree SHA
5. `POST /repos/{o}/{r}/git/commits` (message, tree, parents=[old]) → new commit SHA
6. `PATCH /repos/{o}/{r}/git/refs/heads/{branch}` → move branch. **= a push.**

**Pull:** `GET /git/trees/{sha}?recursive=1` + `GET` blobs (or the contents API / zipball). This is the same mechanism github.dev, vscode.dev, and StackBlitz use.

---

## 6. De-risk items (spikes)

Ordered by how much they can kill the project. Each is a **small, isolated** proof with explicit go/no-go criteria and a fallback.

### DR-1 — CheerpJ licensing  🔴 BLOCKING · status: EMAIL SENT
- **Question:** Does a free, public, individually-developed FTC education app fall under the CheerpJ Community License ("individuals / public-facing educational applications")?
- **Action:** Email sent to CheerpJ/Leaning Technologies (see §7). Confirm which **version** (4.x?) the Community License covers, and that self-hosting the runtime on GitHub Pages is permitted.
- **Go/no-go:** Written confirmation the Community License applies → GO. Otherwise → fall to DR-2 (DoppioJVM) or reconsider zero-backend.
- **Fallback:** DoppioJVM (OSS) or relax the "no backend" rule for compile only.

### DR-2 — In-browser compile (run a compiler itself under CheerpJ)  ✅ GO (verified 2026-08-08)
- **Result:** In-browser compile + run **works** via **ECJ** under CheerpJ 4.3. Spike (`spikes/dr2-compile/index-ecj.html`) compiled a `.java` to Java 8 bytecode and executed it, printing the token — `ECJ exit 0`, `run exit 0`.
- **Stock `javac` is NOT available:** CheerpJ 4.3's Java 17 runtime does **not** ship `jdk.compiler` — `cheerpjRunMain("com.sun.tools.javac.Main", …)` → `ClassNotFoundException`. So we host a compiler instead.
- **Working recipe (both DR-2 questions answered YES):**
  1. Host **`ecj.jar`** (Eclipse Compiler for Java, e.g. 3.41.0 — a self-contained pure-Java compiler) as a static asset.
  2. Host a **Java SE API jar** for the compile *bootclasspath* (spike used `jdk-base.jar` = `java.base` extracted from a JDK 17 via `jimage extract`, ~12 MB). ECJ can't read CheerpJ's runtime as system libs (`/lt/17`), so it needs an explicit API to compile against. Runtime `java.*` still comes from CheerpJ when the class executes.
  3. Compile: `cheerpjRunMain("org.eclipse.jdt.internal.compiler.batch.Main", "/app/ecj.jar", "-source","1.8","-target","1.8","-bootclasspath","/app/jdk-base.jar","-proc:none","-nowarn","-d","/files/", …sources)`. **`-proc:none` is required** (else ECJ tries to open the runtime's absent `jrt-fs.jar` for annotation processing and throws).
  4. Run: `cheerpjRunMain("Main", "/files/")`.
- **Infra requirement:** CheerpJ fetches classpath jars via **HTTP Range** requests. Python's stdlib `http.server` does **not** support Range (dev used `spikes/dr2-compile/range_server.py`). **GitHub Pages supports Range**, so production is fine.
- **For the real platform:** replace the trivial source with student OpMode + the **FTC shim jar** on the classpath (shim jar = compile-time API and runtime impl). Bootclasspath stays the hosted SE API.
- **Open perf item:** first-run compile is slow because ECJ resolves `java.base` types lazily, one class per Range fetch. Mitigate with caching (IndexedDB / service worker / a slimmer bootclasspath jar) — folds into DR-6.
- **Go/no-go:** ✅ GO. Fallbacks (DoppioJVM, backend compile) no longer needed for feasibility.

### DR-3 — Run bytecode in a Web Worker without freezing the UI  🟠 HIGH
- **Hypothesis:** A blocking `runOpMode()` loop runs in a Worker and streams hardware state to the main thread at a usable rate.
- **Spike:** Run a trivial OpMode whose `setPower()` moves a box on a canvas on the main thread; keep UI responsive.
- **Partial evidence (2026-08-08, `spikes/dr2-compile/bridge2.html` + `mecanum_sim.html`):** a blocking Java loop and the JS render/physics loop **coexist on the main thread** with a responsive UI (Java ~470–1490 native-calls/s while rAF holds ~28–56 fps). This shows the bridge + coexistence work; a **Web Worker is still the recommended production shape** so a heavier student loop can never jank the UI.
- **Measure:** effective loop rate, message latency, jank.
- **Go/no-go:** Stable ~20–50 Hz control loop with responsive UI.
- **Fallback:** Throttle student loop; decouple sim clock from control loop.

### DR-4 — Git pull/push from a static page  🟢 GO (CORS verified 2026-08-08)
- **Verified:** A static page reaches GitHub's REST API (`api.github.com`) for **both read and write with zero backend/proxy**. Spike `spikes/dr2-compile/gitsync.html`: pull (Contents), clone-shape (Git Data trees, `recursive=1`), and a no-token PUT all returned **readable** responses in the browser → `readCORS=true treeCORS=true writeCORS=true`, `DR4_STATIC_OK`.
  - **The static gate is CORS reachability, not HTTP 200.** A readable 403/401 proves no proxy is needed; a real CORS block would `throw` before any body is readable. (Our unauth GETs hit the 60/hr rate limit → readable 403 "rate limit exceeded"; the no-token PUT → readable 401 "Requires authentication".)
  - **Critical architecture fact:** use the **GitHub REST / Git Data API** (blobs→tree→commit→ref, or the Contents API), **NOT isomorphic-git over smart-HTTP** — the latter needs a CORS proxy (a server) and would break the static goal.
  - **Auth = fine-grained PAT** typed into the browser (never leaves it). It both authenticates writes and lifts the rate limit to 5000/hr. OAuth/device-flow is out (token exchange endpoint isn't CORS-enabled → would need a server). Page includes a user-driven push tester for an authenticated 200 round-trip.
- **Risk that remains (UX, not feasibility):** **auth for minors** — students handling PATs. Evaluate GitHub Classroom / a classroom-org GitHub App, and rate-limit budgeting at class scale.

### DR-5 — Physics sim in the browser  � GO — JS engine path (Rapier + three.js), 2026-08-08
- **Question:** Reuse the existing Godot sim via **web export**, or rewrite on a JS engine (Rapier/planck.js) + three.js?
- **Result — Rapier3D path proven.** `spikes/dr2-compile/mecanum_sim.html`: `@dimforge/rapier3d-compat@0.14.0` (wasm inlined, 2 MB, vendored same-origin) + `three@0.160.0` run a 12'×12' walled field with 10 dynamic balls and an 18" dynamic robot at ~28–38 fps, `world.step()` ~0.15–0.38 ms. Clean JS interop with the JVM: compiled Java (`mecanum.jar`) drives the robot live via the CheerpJ native bridge at ~1400–1490 Hz.
- **Drivetrain fidelity carried from v1** (force-vector, not roller physics — matches real-robot behavior): Java owns axes → FTC wheel mix → 4× `GoBildaMotorModel` lag → `MecanumKinematics.forward` → publishes chassis velocity; JS applies a velocity-tracking **force/torque controller** to a planar-locked dynamic body (momentum + strafe drift). See `docs/STRUCTURE.md` §1.
- **Godot web-export path not pursued** — the JS engine gives an acceptable bundle and cleaner interop, so no reason to ship the heavier Godot runtime.
- **Go/no-go:** ✅ GO (Rapier + three.js). **Fallback** (2D planck.js) unneeded.

### DR-6 — Cold-load on school Chromebooks / wifi  🟠 HIGH
- **Hypothesis:** CheerpJ + Monaco + shims + sim load acceptably on a low-end managed Chromebook over school wifi.
- **Spike:** Measure first-load and cached-load on representative hardware.
- **Measure:** total transfer, time-to-interactive.
- **Go/no-go:** Usable first-load; fast warm-load via caching/service worker.
- **Fallback:** Aggressive caching, lazy-load the sim, split bundles.

### DR-7 — Shim coverage / SDK API parity  🟡 MEDIUM (ongoing)
- **Core proof done ✅ (2026-08-08):** A realistic student `LinearOpMode` — `@TeleOp`, `hardwareMap.get(DcMotor.class,"arm")`, `setDirection`, `setZeroPowerBehavior`, `waitForStart()`, `opModeIsActive()` loop, `telemetry.addData(fmt,args)`/`update()` — **compiled in-browser (ECJ, target Java 8) against the FTC-package shims and ran against slim browser-side impls** (`SimMotor`/`SimTelemetry`), driving the motor 0.00→0.60 and holding 0.75. `ECJ exit 0`, run exit 0, `DR7_PASS`. Spike: `spikes/dr2-compile/dr7.html` + `dr7src/`.
  - **Shape confirmed:** student code is byte-for-byte the same as on the real robot; only the *impl behind the interface* differs (SimMotor vs. real `DcMotorImpl`). This is the whole thesis of the platform, and it holds.
  - **`/str/` is flat (no subdirs).** Stage sources as flat basenames; ECJ reads the package from the `package` statement and emits into proper package dirs under `/files/`. (`cheerpjAddStringFile` errors on nested paths.)
  - **Slim shim > existing jar for v2:** the impulse-v1 shim jar couples to a `sim.*` package (`SensorFeedback`, `GamepadState`) and the heavy `DcMotorEx`/`AngleUnit` chain. The v2 slice re-used the clean *interface* files verbatim but gave `Gamepad`/`HardwareMap`/impls a `sim.*`-free version. Keep this decoupling.
  - **Perf note:** CheerpJ logs a non-fatal `JIT failure … Parser.consumeRule` (ECJ's giant switch falls back to the interpreter) — a real contributor to first-run compile latency, alongside lazy `java.base` Range fetches. Folds into DR-6 (cache + warm-up).
- **Remaining (incremental):** grow the shim surface to the full dumper track — Servo, IMU, Pinpoint (`GoBildaPinpointDriver` or its interface), `ElapsedTime`, dashboard. Wire the impls to the real physics sim (DR-5) instead of just recording values.
- **Go/no-go:** All lesson OpModes compile against shims + run against sim impls. (First representative OpMode: ✅.)
- **Fallback:** Extend shims incrementally per lesson.

### DR-8 — Lesson system rework  🟡 MEDIUM (content)
- **Question:** Re-express the dumper track around the 4-part subsystem shape (see §8), Limelight removed, Pinpoint-only, hardcoded start pose.
- **Not a runtime risk** — but the largest *content* effort. Track separately.

---

## 7. Licensing status

- **Email sent** to CheerpJ team describing: individual developer, free public-facing FTC educational web app, not commercial, affiliated with (but not developing on behalf of) an FTC-team-associated nonprofit; asking whether this falls under the Community License "individuals / public-facing educational applications" provision.
- **Assumption:** likely eligible; **awaiting written confirmation** before building on CheerpJ.
- **Version:** target **CheerpJ 4.3**. Its docs confirm the *technical* side (script-tag integration, fully client-side, no backend); confirm the *legal* side — that the Community License terms apply to 4.3 and that self-hosting/serving the runtime for a public educational app is allowed.
- **Note:** "runs Java client-side" (confirmed) is separate from "runs the Java *compiler* client-side" (DR-2). The script-tag docs settle the former; DR-2 settles the latter.
- **Contingency:** if not eligible → DoppioJVM (MIT) spike, or a minimal compile service (breaks zero-backend).

---

## 8. Teaching model this must serve (carried from v1 decisions)

- Keep **subsystems** (enum state machines, constructor, public/private, `update()`), a **Robot** container, a **Dashboard** object; **remove Limelight/fusion** — Pinpoint-only with a **hardcoded start pose**.
- **"Every subsystem has 4 parts":** (1) stuff it owns (private), (2) constructor, (3) public command methods that set state, (4) `update()` that makes hardware match state. Teach once, reuse everywhere.
- **Depth tiers:** *use-only* (Drivebase, Localization, Dashboard, Robot — given complete), *author-simple* (Intake), *author-FSM* (Dumper).
- **One concept per lesson, introduced just-in-time, each ending in a runnable robot.** No single "subsystem deep dive" lecture.
- Best motivators to preserve: **the same `intake.forward()` works in TeleOp and Auto**, and the **non-blocking FSM** (robot dumps while still driving).

---

## 9. Evolutionary roadmap (not big-bang)

- **Phase 0 — De-risk:** DR-1 (license), DR-2 (compile), DR-4 (git), DR-3 (worker). Gate the project on these.
- **Phase 1 — MVP:** Monaco + CheerpJ compile+run + one shim subsystem + trivial sim + git pull/push. Prove the loop end-to-end.
- **Phase 2 — Sim + shims:** full shim parity (DR-7) + real physics sim (DR-5, likely reuse Godot web export first).
- **Phase 3 — Lessons:** rework the dumper track (DR-8) with hints/grading.
- **Phase 4 — Classroom:** Chromebook hardening (DR-6), auth UX for minors (DR-4 residual), rollout.

Bias: reuse the existing Godot sim early; migrate compile fully client-side as soon as DR-2 proves out.

---

## 10. Open questions

- Which CheerpJ major version does the Community License cover, and is self-hosting on Pages permitted?
- Auth for minors: fine-grained PAT vs. classroom-org GitHub App vs. GitHub Classroom?
- Reuse Godot (web export) or rewrite the sim in JS? (DR-5 decides.)
- Is DoppioJVM a real fallback, or is CheerpJ effectively load-bearing?
- Where do lessons/grading rules live — in the platform repo, or per-student repo?

---

## 11. Prior art / not reinventing the wheel

- **ftcsim.org** — browser FTC sim (pattern exists).
- **virtual_robot** (already in this workspace) — desktop JavaFX FTC sim; same shim+sim concept.
- **github.dev / vscode.dev / StackBlitz** — static SPAs doing browser git to arbitrary repos.
- **CheerpJ / DoppioJVM** — Java-in-browser proof.

The novel, non-commodity value is the **integration** (shims + physics + lessons + git-backed student projects, browser-native) plus the assets we already own (**shim layer + lesson content**).
