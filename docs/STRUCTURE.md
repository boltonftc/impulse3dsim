# v2 Structure — graduating spikes into a publishable layout

**Status:** first module migrated + validated in browser · **Date:** 2026-08-08

Context: the de-risk spikes have started producing **durable, reusable pieces** (FTC
shims, drivetrain math, the CheerpJ↔JS bridge, the Rapier physics world). They currently
all live in one flat folder, `spikes/dr2-compile/`, mixed with throwaway HTML harnesses.
This note records the mecanum milestone and proposes where the durable pieces should live
once we move from "spike" to "the thing we publish to GitHub Pages."

---

## 1. Milestone recorded — mecanum TeleOp on real physics (2026-08-08)

Compiled Java (`mecanum.jar`, `javac --release 17`) runs on CheerpJ's JVM and drives a
**dynamic** Rapier3D robot through a 12'×12' walled field of 10 balls.

- **Control path lives in Java** (matches v1 architecture): axes → standard FTC wheel mix
  (`fl=d+s+t, fr=d-s-t, bl=d-s+t, br=d+s-t`, normalized) → 4× `GoBildaMotorModel`
  (312 RPM, τ=0.15, first-order lag) → `MecanumKinematics.forward` → publishes robot-frame
  `(vx, vy, ω)` across the native bridge.
- **JS force controller** on a planar-locked dynamic body gives real momentum/strafe drift:
  `addForce = m·(v_target−v)/τ_lin`, `addTorque = I·(ω_target−ω)/τ_rot`.
- **v1 reuse:** `MotorModel`, `GoBildaMotorModel`, `MecanumKinematics` ported verbatim;
  only trimmed `MotorType` and converted the `ChassisVelocity` record → plain class
  (CheerpJ `invokedynamic` avoidance).
- **Measured:** native bridge ~1400–1490 Hz, render ~28–38 fps, `world.step()` ~0.15–0.38 ms,
  robot tracking ~0.75–0.79 m/s. Value-returning and `boolean` natives work under COI.
- **This de-risks DR-5** (physics engine) on the Rapier+three.js path — see PLAN.md.

Files: `spikes/dr2-compile/{mecanum_sim.html, mecanum.jar, mecanumsrc/**}`.

---

## 2. Durable vs. throwaway (current `spikes/dr2-compile/`)

**Durable — graduate into modules:**
| Artifact | Becomes |
|---|---|
| `mecanumsrc/sim/*` (MotorModel, GoBildaMotorModel, MecanumKinematics, ChassisVelocity) | `java/sim/` (drivetrain math) |
| `dr7src/*` (FTC shim interfaces + SimMotor/SimTelemetry) | `java/shim/` + `java/sim/` |
| `mecanum_sim.html` physics + force-controller JS | `web/js/sim/` |
| bridge native-registry pattern (`bridge2.html`) | `web/js/bridge/` |
| `vendor/{rapier3d.mjs, three.module.js}` | `web/vendor/` |
| `ecj.jar`, `jdk-base.jar` (compiler + bootclasspath) | build inputs under `tools/` or `vendor/` |
| `range_server.py` (Range + CDN reverse-proxy) | `tools/` (dev server only) |

**Throwaway — freeze as historical proof, stop editing:**
`ping*`, `bridge*`, `bench*`, `gitsync.html`, `field_sim.html`, `index*.html`, `dr7.html`.
Their learnings are captured in PLAN.md and session memory; the harness files can stay in
`spikes/` for reference.

---

## 3. Proposed publishable layout

```
impulse_3dsim_v2/
├─ docs/                 # PLAN.md, STRUCTURE.md (planning/design)
├─ spikes/               # frozen de-risk experiments (historical; stop adding)
├─ web/                  # the GitHub Pages site root (published as-is)
│  ├─ index.html
│  ├─ vendor/            # three, rapier, (self-hosted cheerpj?) — same-origin
│  ├─ js/
│  │  ├─ bridge/         # cheerpjInit + native registry
│  │  ├─ sim/            # Rapier world: field, robot body, force controller, render
│  │  ├─ editor/         # Monaco glue (later)
│  │  └─ git/            # REST Git Data pull/push (later)
│  ├─ assets/            # lessons, images
│  └─ coi-serviceworker.js  # COI shim (Pages can't send COOP/COEP headers)
├─ java/                 # all Java compiled into the JVM
│  ├─ shim/              # FTC SDK stubs the student sees  → shim.jar
│  │  └─ com/qualcomm/…, org/firstinspires/…
│  ├─ sim/               # impls behind the shims (SimMotor…) + drivetrain math
│  └─ examples/          # sample OpModes (MecanumTeleOp as real @TeleOp LinearOpMode)
├─ tools/                # build.ps1 (shim.jar/examples.jar), range_server.py (dev)
└─ build/                # generated jars (gitignored)
```

Principles:
- Three clean tiers: **Java-in-JVM** (`java/`), **host app** (`web/`), **throwaway** (`spikes/`).
- The **shim is a first-class module** built once into `shim.jar`, reused as *both* the
  compile classpath and the runtime classpath (PLAN §4).
- **Drivetrain math stays JVM-side** (`java/sim/`), faithful to v1 and unit-testable; JS
  only does rigid-body integration + the force controller.
- Student code under `java/examples/` is byte-for-byte what runs on a real Control Hub;
  only the impl behind the interface differs (SimMotor vs. `DcMotorImpl`).

---

## 4. First production vertical — DONE (2026-08-08)

DR-7 (shim) fused with DR-5 (Rapier sim) as a real module tree, replacing the spike:

1. `java/shim/` (FTC stubs, verbatim from DR-7) → `shim.jar`; `java/sim/` (impls +
   drivetrain math + host) → `app.jar` (NO student code). `java/examples/` is only a
   desktop sanity-compile; its source is published to `web/student/` and compiled
   **in the browser**. Built by `tools/build.ps1` (Liberica 17).
2. `java/examples/org/firstinspires/ftc/teamcode/MecanumTeleOp.java` is a real
   `@TeleOp class MecanumTeleOp extends LinearOpMode` using `hardwareMap.get(DcMotor.class,…)`,
   `gamepad1`, `telemetry` — byte-for-byte student code.
3. `sim.OpModeHost` runs the student OpMode on its own thread and samples the drivetrain
   (`SimDrivetrain`: 4 `SimMotor` → `GoBildaMotorModel` lag → `MecanumKinematics.forward`)
   on the main thread, publishing chassis velocity to the JS force controller.
4. `web/index.html` hosts it on Rapier3D; `tools/range_server.py` (port 8972) serves `web/`.

**Validated in browser:** two concurrent Java threads work under CheerpJ (student loop +
host sampler), host ~760–850 Hz, render ~30–40 fps, `world.step()` ~0.2–0.6 ms, telemetry
HUD live, robot drives the field. Native keys must include the package: `Java_sim_OpModeHost_*`.

---

## 5. In-browser compile loop — resident runner — DONE (2026-08-08)

The crown-jewel slice: the page compiles the **student** source client-side (no bundled
OpMode) and runs it, closing edit→compile→run→drive. A single long-lived JVM keeps the
ECJ compiler warm so recompiles are fast.

1. `sim.OpModeHost` is a **resident runner** launched once via
   `cheerpjRunMain('sim.OpModeHost', '/app/app.jar:/app/ecj.jar')` (never resolves). Its
   `main` loop polls a JS flag (`pollCompile` native); on request it: stops any current
   run, compiles the staged source **in-process** (`new ecj…batch.Main(...).compile(args)`),
   hot-loads the fresh class, and runs it. `app.jar` has zero student coupling.
2. **Hot reload:** each run builds a new `FilesLoader` (a `ClassLoader` whose `findClass`
   reads `/files/<pkg>/<Class>.class` and `defineClass`es it, delegating everything else to
   the parent). A fresh loader per run picks up recompiled bytecode; the parent (app.jar)
   keeps shim/sim types identity-stable so the `LinearOpMode` cast holds across reruns.
3. `web/index.html` boots CheerpJ once, starts the runner, fetches
   `student/MecanumTeleOp.java` into a `<textarea>`, then `compileAndRun()` just stages the
   source (`cheerpjAddStringFile('/str/MecanumTeleOp.java', src)`) and sets
   `sim.compilePending = true`. Natives: `pollCompile` (one-shot int), `status(code,msg)`
   (HUD), plus the existing `gp*/opActive/publish/telemetry`. In-process ECJ args:
   `-bootclasspath /app/jdk-base.jar -classpath /app/shim.jar -proc:none -nowarn -d /files/`.
4. `web/` also ships `ecj.jar` (~3 MB) + `jdk-base.jar` (~12 MB); `build.ps1` compiles
   `java/sim` against `shim.jar;ecj.jar` (host references ECJ `Main`).

**Validated in browser (port 8972):** `RUNNER_READY`, first compile `COMPILE_OK 58025ms`
(one-time parser-table load), `HOST_START`, host ~700–760 Hz, robot drives (auto ~0.92 m/s),
telemetry mirrors student drive/strafe/turn. **Recompile = `COMPILE_OK 5498ms`** (~10× faster;
same warm `Parser` class, no reload) with clean `HOST_DONE samples=… → HOST_START`. The
`JIT failure … consumeRule` notices are benign CheerpJ interpreter fallbacks.

Open: first-compile warmup (~58 s) — mask with a "warming compiler…" state and/or a
pre-warm compile at boot. Next: Monaco editor + git glue (de-risked) into `web/js/{editor,git}/`;
derive the OpMode class name from the source rather than hardcoding.

## 6. Driver-Station lifecycle — INIT / START / STOP — DONE (2026-08-08)

The resident runner grew a real FTC lifecycle so the page drives a proper
INIT→START→STOP state machine instead of an auto-run-on-compile flag. This is the
skin-agnostic backend a future emulated Driver Hub UI sits on.

1. **Command API.** The runner's `main` loop polls `pollCommand()` (int native): `1`=INIT,
   `2`=START, `3`=STOP. It reports back through `status(code,msg)`: `1`compiling / `2`initialized
   / `3`running / `4`stopped / `5`error. INIT compiles the staged source in-process, constructs
   the OpMode, and calls `internalStart()` — the worker then **blocks at `waitForStart()`**
   (true INIT phase). START calls `internalNotifyStart()`; STOP calls `internalRequestStop()`
   and joins the worker + sampler.
2. **OpMode discovery.** JS `discover(src)` scrapes `package`, `class … extends
   LinearOpMode/OpMode`, `@TeleOp`/`@Autonomous`, and `name=/group=` straight from the source;
   INIT stages `/str/<Class>.java` + writes the FQN to `/str/opclass.txt`. The runner reads
   `opclass.txt` and hot-loads that class via `FilesLoader` — **nothing is hardcoded**.
3. **Crash / end detection.** The monitor loop watches `current.getUncaughtException()`
   (→ `OPMODE_CRASH`, status `5`) and worker liveness after start (natural end → status `4`).
   A student runtime exception surfaces in the HUD; the runner **self-heals** — a following
   INIT with valid source recompiles (~5.5 s warm) and runs clean.
4. **Page controls (`web/index.html`).** Editor strip: **Build & Init / Start / Stop / Reset
   code**; HUD gains `op <name·type>` + `state <pill>`. Boot chains INIT→auto-START (via
   `autoStart` consumed on status `2`) to preserve "loads and drives". Source autosaves to
   `localStorage` (`impulse.opmode.src`); Reset restores the fetched seed. `firstLine()` skips
   ECJ `----------` dividers so compile errors show the real `ERROR in …` line.

**Validated in browser (port 8972):** boot → `COMPILE_OK` → `HOST_INIT
op=org.firstinspires.ftc.teamcode.MecanumTeleOp` → auto `HOST_START` → running ~570–790 Hz;
W/D drive ~1.53 m/s; STOP → `HOST_DONE`, robot halts, state `stopped`; INIT-only parks the
worker at `waitForStart()` (telemetry "Initialized"); a syntax error → state `error` +
`ERROR in …` message (warm recompile ~6–8 s); a post-START `throw` → `OPMODE_CRASH …` + state
`error`; reset→INIT→START recovers and drives.
