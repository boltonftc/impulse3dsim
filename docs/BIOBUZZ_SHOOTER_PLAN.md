# BioBuzz Shooter — Program Plan

Living plan for adding the DECODE-style flywheel shooter to the v2 browser sim (`impulse_3dsim_v2`)
and wiring it into the BioBuzz season (launch POLLEN/NECTAR into the raised hive cell to tip it).
Built **bit-by-bit**; each phase is independently testable. We tune ballistics against real-world
performance later in the season.

Reference material (v1): `impulse_3dsim/ROBOT_SHOOTER_DESIGN.md`, `impulse_3dsim/docs/SHOOTER_MECHANICS.md`,
`impulse_3dsim/modules/ftc_starter_course/master/MecanumDrive.java.master` (shooter `@fill` regions),
`impulse_3dsim/modules/ftc_starter_course/module.json` (shooter lesson track). v1 ran Godot+Java over IPC;
v2 is THREE.js + RAPIER + CheerpJ. The physics math and lesson/Java content port; the transport + ball spawn change.

---

## 1. Goal
Robot collects POLLEN + NECTAR, stores them, and **launches them up into the raised (upward-facing)
CELL of its hive**. Enough elements in the raised cell → the seesaw tips (the animation we built) → 20 pts.
Shooter must reach the end-goal capability (two ball types, adjustable hood, turret + AprilTag auto-aim)
but **start minimal**, unlocking capability lesson-by-lesson. Students may stop anywhere and still have a
working, scoring robot.

## 2. Locked decisions (from discussion)
- **Shot pose:** robot **lined up with its hive, back pressed against the wall** on the raised-cell side,
  shooting forward into the raised cell. This is the calibration anchor.
- **Ball masses:** POLLEN ≈ wiffle-ball mass (~0.045 kg); NECTAR = that scaled up (~2×, ~0.09 kg). Tune later.
- **Indexer:** basic (fire-one-ball) for now — not the full star-wheel CRServo yet.
- **Storage:** hold **up to 4** game pieces internally, any **mix of POLLEN and NECTAR** (hard cap 4).
- **Turret + hood:** single common robot rendering; turret locked forward and hood angle fixed to begin.
- **Build order:** minimal first (see Phase 1), then layer.
- **Physics:** mix of real (gravity + aero drag on the free ball) + faked (flywheel→exit-speed launch curve).

## 3. Shot geometry / calibration anchor
All from `game_field/HIVE_DIMENSIONS.md` + the field. One hive centered at X = ±12.75″; raised cell opening
**53.5″ (bottom) → 65.6″ (peak)**, tilted **30°** back, raised-cell bbox center ≈ (X, 54.0″, Z +10.2″).
- Robot backs to the raised-cell-side wall (Z ≈ +72″), 18″ deep → robot center Z ≈ +63″, muzzle ~9″ forward
  and ~12″ up → muzzle ≈ (X=±12.75″, Y≈12″, Z≈+54″).
- **Calibration shot ≈ 44″ horizontal run, ~42–54″ vertical rise into a 30°-tilted opening.** A close, lofted lob.
- Tune (hood angle + full-RPM exit speed) so a **full-RPM POLLEN** shot from this pose drops in; NECTAR from the
  same pose falls short (heavier + more drag) → motivates the adjustable-hood lesson later.
- TODO: confirm exact wall/robot standoff and whether the raised cell is always the +Z (fore) one.

## 4. Physics model
**Real (RAPIER):** dynamic sphere per ball, real mass + radius, gravity −9.81. Add a per-step **quadratic aero
drag** on in-flight balls: `F = -½·ρ·Cd·A·|v|·v` (ρ≈1.2, Cd≈0.47 sphere, A=πr²). Few balls aloft → cheap.
**Faked (launch/contact):** don't simulate wheel grip. `exitSpeed = flywheelSurfaceSpeed × efficiency(ballType)`.
**Flywheel (port v1):** 1st-order motor model, τ ≈ 0.8 s (heavy inertia), goBILDA motor; on fire, RPM sags
15–20% (ball-type dependent), recovers in ~0.3–1 s.
**Ball specs:** POLLEN r 0.0356 m / ~0.045 kg; NECTAR r 0.0457 m / ~0.09 kg. NECTAR pushed short/low by BOTH
lower launch efficiency + bigger RPM sag AND larger drag area — matches "nectar never high enough" with fixed hood.
**Teachable coupling (make PID matter):** shot success depends on **flywheel RPM at instant of fire**. Slightly
low RPM → short → rim-out. Rapid-fire with plain `setPower` → shots 2–3 sag & miss; feedforward+P holds RPM →
all drop in. This is the money demo for the encoder + PID lessons.

## 5. Robot model & articulation
Single common robot skin (goBILDA RI3D DECODE style, per `V1_SOURCE_NOTES.md`). Build the **articulation joints
from day 1, locked**:
- `turret` = yaw pivot group (locked at 0 → launch azimuth = robot heading).
- `hood` = pitch pivot child of turret (fixed angle → sets launch elevation).
- `flywheel` + `muzzle` (spawn point) children of hood.
Later lessons just wire a servo/motor to `hood` / `turret` — no re-rig. Visible internals: intake roller bank,
(basic) indexer, ball channel, flywheel spin, hood plates.

## 6. Lesson plan (course reorg)
Keep the whole spine; **swap the mechanism slot from dumper → shooter thread**; dumper stays in repo (legacy /
"dumper" track). Recommended BioBuzz order:

| # | Lesson | Status |
|---|--------|--------|
| 00–10 | welcome … field-centric | unchanged |
| 11 | Intake (subsystem) — collect pollen/nectar | keep |
| 12 | **Indexer** — feed one ball (basic timed state machine; inherits dumper's teaching) | NEW |
| 13 | **Flywheel Shooter** — spin up, feel inertia | NEW |
| 14 | **Encoder: Flywheel RPM** — read RPM, see spin-up + sag | NEW |
| 15 | **Flywheel Trim** — right-stick real-time tune | NEW |
| 16 | **Flywheel Control (PID)** — hold target RPM, fast recovery | NEW |
| 17 | Localization (Pinpoint) | keep (was 13) |
| 18 | Autonomous (Pedro) — capstone: auto-collect + auto-shoot | keep (was 14) |
| 19+ | *(advanced/optional)* Adjustable Hood → Turret → AprilTag Auto-Aim | later |

Alternative considered: keep dumper + append shooter after autonomous — rejected (autonomous should stay the
capstone; shooter is the season's scoring mechanism so it belongs before auto). Dumper's "timed state machine"
lesson is covered by the indexer fire-rate + flywheel spin-up gating, so removing it leaves no gap.
Course plumbing: `course/module.json` order + `course/build_course.py` (syncs `course/lessons/*` → `web/lessons/`
+ `web/course.json`). Track select (shooter vs dumper) already scaffolded (`__active_track`, package `track`).

## 7. Phased build (bit-by-bit)
- [x] **P0 — Shooter robot rig (static, locked).** DONE (v2.5.24; **real CAD turret v2.5.25**): loads
      `game_field/turret_assembly2.stl` (mm), splits its 3 connected components (housing/flywheel/hood) in JS,
      mounts at the BACK, elevated 10″, as pivots: `turret` (yaw) + `flywheelSpin` (X axle) + `muzzle`; hood tilt
      wired later. STL frame Y-up, launch −Z, yaw at (0,−114,−93)mm. Dumper bed hidden. Awaiting visual review.
      1. Add a shooter sub-assembly to the robot mesh at the front-top: `turret` group (yaw pivot) → `hood` group
         (pitch pivot, child of turret) → `flywheel` cylinder + `muzzle` marker (children of hood).
      2. Lock `turret.rotation.y = 0` (forward) and `hood` at the fixed calibration pitch. Simple primitives
         (turret base, flywheel cylinder, two hood plates); reuse the existing robot-skin build path.
      3. No physics, no animation.
      - **Verify:** robot renders a visible flywheel/hood/turret at the front-top, pointing forward; no console
        errors. Bump `APP_VERSION`, hard-refresh.
- [ ] **P1 — Minimal launch (the "prove it" slice).** IN PROGRESS (v2.5.37): flywheel RPM model (τ0.8, spins the
      striped wheel), debug keys O=flywheel on/off, P=fire pollen. Launch direction = **robot-local +Z** (confirmed
      via a 4-colored-ball 90°-offset debug test — the transform chain kept confusing me; RED/+Z was correct),
      tilted up by ELEV=58°, spawned just outside the muzzle; RAPIER sphere + quadratic drag. MAX_EXIT 4.5 m/s.
      Still to tune: exact elevation + exit speed vs the wall pose, and move the real muzzle to the hood exit.
      1. Flywheel model in the game module: RPM tracks a setpoint with τ≈0.8 s; `exitSpeed = (2π·RPM/60)·radius·eff`.
      2. Debug fire key (e.g. `k`): if the flywheel is spun up, fire ONE POLLEN.
      3. Spawn a RAPIER dynamic sphere (pollen mass ~0.045 kg, r 0.0356 m) at the `muzzle` world position; set
         `linvel = worldLaunchDir · exitSpeed`, where `worldLaunchDir` = hood pitch ∘ turret yaw ∘ robot heading.
      4. Apply per-step quadratic aero drag to in-flight shooter balls; THREE sphere synced to the body; despawn
         on rest / out-of-bounds.
      5. Calibrate the fixed hood pitch + full-RPM `exitSpeed·eff` so a pollen shot from the **wall pose** drops
         into the raised cell.
      - **Verify:** from the wall pose, fire → pollen arcs and lands in/near the raised cell. Bump `APP_VERSION`.
- [ ] **P2 — Intake + storage + basic indexer.** IN PROGRESS (v2.5.38): field collectibles (8 pollen + 3 nectar)
      scattered near the red robot; intake (`I` toggle) captures the nearest at the front mouth into storage (cap 4,
      any mix), stacked as a colour-coded visual over the robot; `P` = indexer feeds one stored piece into the
      flywheel (needs spin-up), nectar leaves slower + sags RPM more. Real motor/servo wiring comes with the lessons.
      1. Intake capture: when intake is on and a pollen/nectar overlaps the front zone, capture it → `stored++`
         (**cap 4, any mix** of pollen/nectar). Reuse the v2 intake pattern.
      2. Storage: a count (+ optional stacked ball visuals in the channel).
      3. Basic indexer: a fire command (button) that, if `stored > 0` AND flywheel RPM > ~50% of target, launches
         one stored ball (P1 launch path), `stored--`, and applies the RPM sag.
      - **Verify:** drive into pollen → count rises; spin up flywheel; index → balls fire one at a time from
        storage. Bump `APP_VERSION`.
- [ ] **P3 — Scoring loop.** Ball entering the raised cell increments its fill; threshold → trigger the seesaw tip
      (hook the tip animation to a real event, not just ambient). Wire matchScore (+20 on tip).
- [ ] **P4 — Two ball types.** NECTAR (bigger/heavier) with its own mass/drag/efficiency; confirm pollen-makes /
      nectar-short from the wall pose with fixed hood.
- [ ] **P5 — Inertia/recovery surfaced.** RPM sag on shot + recovery visible; expose flywheel encoder/RPM to Java.
- [ ] **P6 — Lessons authored.** indexer → flywheel → encoder → trim → PID (Java `@fill` regions, ported from v1;
      lesson HTML + widgets). Reorder `module.json`; retarget content from DECODE holes to the hive cell.
- [ ] **P7 — Advanced (later).** Adjustable hood (servo) → turret (motor) → AprilTag auto-aim (faked tag bearing).

Each phase: bump `APP_VERSION`, hard-refresh, user verifies before moving on (same loop as the hive work).

## 8. Open questions / tune-later
- Exact wall standoff + confirm raised cell = +Z (fore) cell for the calibration pose.
- Real pollen/nectar masses (start wiffle ~45 g / ~90 g; refine with real balls).
- Drag: quadratic vs cheap linear damping first? (start quadratic; fall back if perf/tuning needs).
- Indexer control surface: simple servo/one-shot now; CRServo star-wheel later?
- Scoring detail: count elements in cell vs weight/tip threshold; FLOWER + GARDEN scoring out of scope for now.
- How the tip trigger interacts with the current ambient tipping animation (real fill event should drive it).

## 9. Status log
- (start) Plan created. v1 reviewed. Decisions locked (§2). Next: confirm §8 shot-pose details, then P0/P1.
- Confirmed: **contiguous** shooter thread (§6 order — full thread before localization/autonomous) and the
  **fill → tip → score** trigger (P3 — real cell-fill drives the seesaw tip + 20 pts, replacing the ambient timer).
  First three steps (P0–P2) expanded for review; ready to start P0 on go-ahead.
- P0 turret: user supplied `turret_with_base.stl` (base + housing + flywheel + hood, 4 components). STL frame has
  **−Z = up, +Y = forward launch, X = flywheel axle**; reoriented +90° about X on load. Base bolts to the drivebase
  top (FIXED); turret housing+hood+flywheel yaw on top; flywheel spins. v2.5.26. Awaiting orientation review.
