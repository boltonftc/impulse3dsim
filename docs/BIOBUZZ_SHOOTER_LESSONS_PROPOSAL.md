# BioBuzz Shooter — Lessons Proposal

Proposal for the Java student lessons that teach the shooter/intake/hive mechanics we just built into
the v2 sim (`impulse_3dsim_v2/web/index.html`, currently **v2.5.63**). Companion to
`docs/BIOBUZZ_SHOOTER_PLAN.md` (the sim build plan). This doc is about the **student-facing Java course**:
what each lesson wires, which sim hook it drives, and the integration work that has to land first.

Status: **DRAFT for review.** Nothing here is built yet.

---

## 1. Where the sim is today (what students will control)

This session added, as **sim-side mechanics** (no student Java behind them yet):

- **Flywheel** — 1st-order RPM model (τ 0.8 s), `MAX_EXIT` 5.625 m/s. Toggled by gamepad **Y** (`debugFlywheel`).
- **Shoot / indexer** — `fireShot()` pops the oldest stored piece, keyed to gamepad **X**.
- **Intake capture** — captures the nearest field ball at the front mouth into FIFO storage (cap 4, any mix).
  Driven by the **Java** intake motor (`sim.mech.intake < -0.15`) *or* the `I` debug key.
- **Storage** — FIFO queue of `{ type, alliance }`; nectar keeps its alliance colour through storage → shoot.
- **Eject / dump** — reverse intake (`M.intake > 0.15`) spits pieces out the front; dump bed (`M.dump > 0.6`)
  cascades them off the rear. Both now expel **physical** pieces.
- **Hive tipping** — rule-based: raised-cell weighted load ≥ 8 (nectar 1.8 / pollen 1) held 1.0 s → tips over
  2.5–3.0 s. Blue hive starts tipped the other way.
- **Nectar economy** — 3 colour-matched nectar preloaded per hive; +1 to a hive's alliance loading zone per tip
  (cap 5 adds / 8 on field); remainder released at the 1:00 mark.
- **Scoring** — AUTO mobility 3; loading-zone park 5 (end auto) + 5 (end teleop); hive tip 20; 2 / piece in hive at end.
- **Match sequence** — ceremony (red → blue → both + heartbeats → LET'S GO → 3 s hold → Cavalry Charge),
  8 s AUTO→TELEOP transition (robots frozen, `tele_start` at +2 s), foghorn first-time-only.

## 2. Java bridge — the hooks lessons will use

`OpModeHost.java` streams the student's actuator state to the sim every tick via:

```
mech(slot, intake, feeder, shooter, hood, dump)
```

which lands in the sim as `sim.mech.{intake, feeder, shooter, hood, dump}` (index.html ~L3238). Student code
gets these by driving named hardware:

| Hardware name (config) | Type      | mech field | Meaning in the sim |
|------------------------|-----------|------------|--------------------|
| `intake`               | DcMotor   | `intake`   | `< -0.15` = intake (capture); `> 0.15` = reverse/eject. Motor is **reversed** in-sim. |
| `feeder`               | DcMotor/CRServo | `feeder` | **intended** = fire one stored piece (indexer). *Not consumed yet.* |
| `shooter`              | DcMotorEx | `shooter`  | flywheel spin (−1..1, from `getVelocity()/SHOOTER_MAXV`). *Not consumed yet.* |
| `hood`                 | Servo     | `hood`     | hood elevation 0..1. *Not consumed yet.* |
| `dump`                 | Servo     | `dump`     | `> 0.6` = dump bed tips (rear cascade). Already consumed. |

## 3. ⚠️ Integration gap to close BEFORE the shooter lessons

The flywheel and fire are **gamepad-only** right now. The lessons need the sim to read the Java hooks instead:

1. **Flywheel ← `sim.mech.shooter`.** Replace `const tgt = (debugFlywheel ? 1 : 0) * SH.MAX_RPM;` so the target
   RPM follows `M.shooter` (keep `debugFlywheel`/Y as an OR for un-coded testing). This is what makes the
   *encoder/PID* lessons meaningful — RPM only holds if the student's control loop holds it.
2. **Fire ← `sim.mech.feeder`.** An edge on `feeder` (rising past a threshold) calls `fireShot()` once
   (keep X as an OR). Debounce so one press = one ball.
3. **Hood ← `sim.mech.hood`.** Map the servo 0..1 to the launch elevation (`SH.ELEV`) so the hood lesson
   changes range. Today `ELEV` is fixed at 58°.
4. Keep the **sim-only `Y`/`X`** controls as a fallback for lessons 00–10 (before the shooter is coded), and
   keep `Y` masked off the Java button bitmask (already done, temporary).

Recommend doing #1–#3 as one small sim PR (call it the "shooter Java bridge") and bumping `APP_VERSION`, then
authoring the lessons against it.

## 4. Proposed lesson sequence

Builds on the existing spine (`course/module.json`: 00–14) and the shooter thread in `BIOBUZZ_SHOOTER_PLAN.md §6`.
Slots the mechanism thread between drive (10) and localization/auto.

| # | Lesson (id) | Tier | New Java concept | Sim hook it proves | Files |
|---|-------------|------|------------------|--------------------|-------|
| 11 | `11_intake` (exists) | adv | enum state machine, toggle buttons | `intake` fwd/rev → capture / eject | `Intake.java` |
| 12 | `12_indexer` **NEW** | adv | timed one-shot (ElapsedTime, no `sleep`) | `feeder` edge → fire one stored piece | `Indexer.java` |
| 13 | `13_flywheel` **NEW** | adv | `DcMotorEx.setPower`, feel spin-up inertia | `shooter` → flywheel RPM; fire while spun up | `Flywheel.java` |
| 14 | `14_encoder_rpm` **NEW** | adv | `getVelocity()`, encoder ticks → RPM, telemetry | watch spin-up + per-shot RPM sag | `Flywheel.java` |
| 15 | `15_flywheel_trim` **NEW** | adv | right-stick real-time constant tuning | live exit-speed change → make/miss | `Flywheel.java` |
| 16 | `16_flywheel_pid` **NEW** | adv | feedforward + P to hold target RPM | rapid-fire holds RPM → all drop in | `Flywheel.java` |
| 17 | `17_hood` **NEW (optional)** | adv | `Servo.setPosition`, elevation → range | `hood` changes arc; NECTAR reaches | `Hood.java` |
| 18 | localization (was 13) | adv | Pinpoint x/y/heading | unchanged | `Localization.java` |
| 19 | autonomous (was 14) | adv | Pedro: auto-collect + auto-shoot (capstone) | scoring: mobility/park/tips | `SimpleAuto.java` |

Notes:
- The **dumper track** (existing 12) stays in-repo as a legacy/alternate track; the shooter thread is the
  BioBuzz default (`default_track` already scaffolded — `module.json "tracks"`).
- Each lesson stays independently testable and "stop-anywhere-still-scores," per the plan's principle.
- The **money demo** is 14→16: encoder shows RPM sag on rapid fire; PID fixes it → every ball tips the hive.

## 5. Per-lesson skeleton (authoring)

Each lesson = `course/lessons/NN_slug/lesson.html` + `@begin/@fill` regions in a `course/master/*.java.master`,
built by `python course/build_course.py` → `web/course.json` + `web/lessons/` + `module.json`. (See §7.)

Example — **13_flywheel**:
- **KEY_IDEA:** a flywheel is heavy; `setPower(1)` doesn't hit full speed instantly (τ ≈ 0.8 s). Fire too early → short.
- **TODO:** get the `shooter` motor from `hardwareMap`; on right-trigger set power 1, else 0; fire (`feeder`) on X.
- **CODE:** `DcMotorEx shooter = hardwareMap.get(DcMotorEx.class, "shooter"); shooter.setPower(rt > 0.2 ? 1 : 0);`
- **Verify (sim):** hold RT, wait for spin-up, tap fire → pollen tips the raised cell; fire cold → falls short.

## 6. Open decisions (need your call before authoring)

1. **Fire button in Java** — `feeder` motor edge, or a dedicated gamepad button read in the OpMode? (Sim can do either.)
2. **Flywheel input** — single `setPower` (13) then `DcMotorEx` velocity/PID (14–16). Confirm goBILDA motor + ticks/rev
   for the encoder lesson math (the sim's `SHOOTER_MAXV = 30 rad/s` maps to full spin).
3. **Hood lesson** — include now (17) or defer to "advanced/optional" like turret/AprilTag?
4. **Renumbering** — inserting 12–17 pushes localization/auto to 18/19. OK to renumber, or keep old ids and just
   reorder in `module.json`?
5. **Two-player** — lessons assume the red (main) robot. Blue mobility/park scoring is still stubbed; fine to leave
   until 2-up.

## 7. Authoring mechanics quick-ref (verified conventions)

- Build: `python course/build_course.py [--check]` (reads `course/master/*.java.master` + `course/lessons/NN/lesson.html`).
- Grammar: `// @begin(id)…// @end(id)` visible once lesson reached; `// @fill(id)…// @end(id)` = answer shown only
  after completion; a file that processes to empty is omitted (that's how a file "doesn't exist yet").
- `lesson.html` blocks: `blk-key` (gold), `blk-code` (green, has Copy), `blk-todo` (blue), `blk-challenge` (orange).
  Actions: `data-action="open_file" data-arg="File.java|Anchor substring"`, then `revert` + `complete`. Anchor must
  exactly match a comment substring in that lesson's START snapshot (`--check` validates).
- `module.json` entry: `id, title, tier, code, html, active, folder, description` (+ optional `track`).
- Serve/verify: `python impulse_3dsim_v2\tools\range_server.py` → http://localhost:8972 → PIT → lessons → DEPLOY ▸.
  First compile ~1 min. Bump `window.APP_VERSION` on any web change (SW cache).
