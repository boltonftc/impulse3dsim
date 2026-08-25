# BIOBUZZ OOP Starter Course — lesson outline (detailed sketch, 2026-08-23)

Working design doc for the code-lesson track. Synthesizes three inputs:
- **alan412 / LearnJavaForFTC** — DNA: one Java concept at a time, always inside a runnable robot
  program; wrap hardware in a class early (the "ProgrammingBoard" → our `RobotConfig`/`Subsystem`).
- **Bolton V1 lessons** (boltonftc.github.io/programming) — pacing/tiers + reusable non-code lessons.
- **Our v2 codebase** — the endpoint is `competition_code` (subsystem OOP on one cooperative scheduler).

Audience: novice 8th-grade programmers. Method: **learn by doing**, small concept budget per lesson.

---

## 1. Pedagogical spine — the on-ramp (agreed)

An 8th-grader cannot *start* at "implement the `Subsystem` interface." So we go **procedural first,
feel the pain, then refactor into classes** (alan412's arc):

1. **Procedural basics** (standalone editing of one OpMode) — telemetry, gamepad, tank, mecanum.
2. **The refactor** — the OpMode gets messy; we *organize it into a `Drivebase` class*. OOP is
   born here because the student *wanted* it, not because they were told to.
3. **Subsystems** — Intake, Dumper on the `Robot` scheduler (interface, enums, state machines).
4. **Localization & Autonomous** — Pinpoint pose, Pedro paths.
5. **Tooling** — FTC Dashboard.
6. **Transfer capstone** — student builds their *own* subsystem (Elevator) from the Intake/Dumper
   patterns.

## 2. How the build machinery serves this (already in place)

- `course/module.json` — ordered lesson list (id, title, tier, code, html, `active` file, folder, desc).
- `course/master/*.java.master` — tagged sources. Grammar:
  - `// @begin(lesson_id) … // @end(lesson_id)` — block hidden until that lesson is **reached**.
  - `// @fill(lesson_id[, supersedes=other_id]) … // @end` — answer code included only **after**
    that lesson is completed; `supersedes` lets a later, fuller version **replace** an earlier one
    (this is how the inline drive code gets swapped for `Drivebase` calls at the refactor).
- `course/build_course.py` → `web/course.json` = `{ snapshots: { lesson_id: { active, files } } }`.
  Each snapshot is the WHOLE package as it should look at the **start** of that lesson.
  **A master that processes to empty is omitted** → that is how a file "does not exist yet."
- **Reset Lesson Code** (`#lesson-reset` → `revertToLesson`) loads the lesson's snapshot and
  **replaces the entire file set** — so files that belong to later lessons are removed and files
  introduced by this lesson appear, automatically. **No add/remove work needed.** ✅

**Design consequence:** the code package is a *single anchor file that grows*, with subsystem files
**appearing beside it** as their lessons arrive. Anchor file = `MecanumDrive.java` (the TeleOp), which
starts as a bare skeleton and evolves into the full field-centric teleop; `Drivebase`, `RobotConfig`,
`Subsystem`, `Robot`, `Intake`, `Dumper`, `Localization`, `SimpleAuto`, etc. materialize on schedule.

### Runtime edit model (decided 2026-08-23) — three tiers

Student edits **persist** across lesson navigation; opening a lesson never wipes. Only two things
change the file set, and both are deliberate:

| Tier | Scope | Trigger |
|---|---|---|
| **Persist** (default) | nothing touched | normal navigation between lessons |
| **Scaffold this step** *(refactor lessons only)* | just the file(s) that step introduces/changes | student clicks a lesson button |
| **Reset Lesson Code** | whole package → canonical snapshot | emergency ("I broke it") |

This mirrors how v1 worked (all anchors present from the start; Reset was the only wipe), **adapted**
for our grows-model where files *appear* (`Drivebase.java` at 09) and one file *changes* (the L09
`supersedes` swap of `MecanumDrive`). Persist-only would leave a never-Reset student without those
files, so refactor lessons get a **Scaffold button**: a new `onLessonAction` case (e.g.
`add_file "Drivebase.java|Subsystem.java|Robot.java"`) that pulls the named files from the current
lesson's snapshot into the `course` package — additive for new files, and an explicit, *explained*
replace for the one superseded file (which also brings in that step's anchors). Idempotent; distinct
from the global Reset. **To build when we author Lesson 09** (one action-handler case + button markup
in the lesson HTML); everything through Lesson 08 lives entirely in `MecanumDrive.java`, so no scaffold
is needed before then.

## 3. "Your Code Package" gets stripped down (agreed change)

The current `02_code_package` tours the *whole* 13-file subsystem package (`active=MecanumDrive.java`).
That contradicts "start simple." **Rework it:** the package at this point contains **one file** — a
bare `MecanumDrive.java` OpMode skeleton (package/imports/class/`@TeleOp`/init–`waitForStart()`–loop
with a single telemetry line). The lesson tours *that one file* conceptually and does a first
Build & Run. Everything else is added as we go.

## 4. Lesson-by-lesson sketch

Legend — **New concepts** (budget ≈2–3), **Do** (the hands-on), **Package at start** (growing file
set), **active** (focused file). Non-code intro lessons kept as-is; all other non-code lessons skipped
for now. IDs 03–08 fill the on-ramp; 09/10 keep the existing Drivebase/Field-Centric anchors; 11+ new.

### Tier: Easier

- **00 · Welcome to the Pit** *(no code — keep as-is)*
- **01 · FTC Robot Basics** *(no code — keep as-is)*

- **02 · Your Code Package** *(REWORK: strip to one file)*
  - New: what a **package** is, what an **OpMode** is, the `@TeleOp` annotation, the three phases
    (**init → `waitForStart()` → run loop**), Build & Run.
  - Do: open the one-file package, read the skeleton, build it, run it (robot sits still).
  - Package at start: `MecanumDrive.java` (bare skeleton). active: `MecanumDrive.java`.

- **03 · Hello, Telemetry** *(the lesson that FOLLOWS Your Code Package — see §6)*
  - New: **`telemetry.addData` / `update()`**, the loop as a heartbeat, comments/`// TODO`.
  - Do: print a message + a counter that ticks every loop; watch init-vs-loop live.
  - Package: `MecanumDrive.java` (skeleton). active: `MecanumDrive.java`.

- **04 · Reading the Gamepad**
  - New: **variables & types** (`double` sticks, `boolean` buttons), reading `gamepad1`.
  - Do: show left-stick Y and the A-button state on telemetry.
  - Package: `MecanumDrive.java` (+telemetry). active: `MecanumDrive.java`.

- **05 · Tank Drive**
  - New: **`DcMotor`**, `setPower`, motor direction/reverse, mapping stick→power, a **helper method**.
  - Do: two-motor tank drive, all inline.
  - Package: `MecanumDrive.java` (+gamepad). active: `MecanumDrive.java`.

- **06 · Mecanum Drive**
  - New: the mecanum mixing math (drive/strafe/turn sign pattern) + power normalization; foreshadow "why extract code."
  - Do: four-motor mecanum **flat inline** (no helper method yet) so the loop *feels crowded* &mdash; the pain
    that motivates the Lesson 09 refactor. "Methods with parameters &amp; return values" is taught at 09 where
    the class/constructor actually appears.
  - Package: `MecanumDrive.java` (tank **superseded** by inline mecanum). active: `MecanumDrive.java`.

- **08 · IMU Heading**
  - New: reading a **sensor** &mdash; the Control Hub **IMU**, heading (yaw) in radians, the &plusmn;&pi;
    range, `resetYaw()`. Motivates field-centric (a heading is the missing piece).
  - Do: fetch `imu` from the hardware map + `initialize(...)` in INIT; each loop read
    `getRobotYawPitchRollAngles().getYaw(RADIANS)`, show it on telemetry, reset on right bumper. The read
    heading is already wired into the drive call so it lights up at Lesson 10. **Turntable/pit rotate hooks
    from the reference lesson are intentionally skipped.** IMU is a plain OpMode local (not a subsystem);
    Pinpoint stays purely localization/pathing at Lesson 13.
  - Package: `MecanumDrive.java` (+ IMU setup/read anchors). active: `MecanumDrive.java`.
  - Sim: added a shim `IMU`/`RevHubOrientationOnRobot`/`YawPitchRollAngles` + `IMUImpl` (ground-truth yaw,
    offset-based reset), registered as device `"imu"` in `OpModeHost`.

### Tier: Intermediate

- **09 · From Messy Loop to Clean Class** *(THE REFACTOR — OOP is born)*
  - New: **class**, **constructor**, **`private` vs `public`** (and *why*), `this`, `hardwareMap`,
    the hardware-wrapper idea (**`RobotConfig`**).
  - Do: move the messy inline drive code into a real **`Drivebase`** class the OpMode calls.
  - Package: `MecanumDrive.java` (drive code **superseded** to call `drive`) **+ new** `Drivebase.java`,
    `RobotConfig.java`, `MecanumKinematics.java`. active: `Drivebase.java`.
  - **Scaffold button** (first use of it): a lesson button creates the new files and does the explained,
    one-file `MecanumDrive` restructure — see §2 "Runtime edit model." Build the `add_file`/scaffold
    `onLessonAction` case here.

- **10 · Field-Centric Driving**
  - New: rotating the drive vector by the robot **heading** (the value read from the IMU at Lesson 08).
  - Do: add the rotation in `driveFieldCentric(...)` so "forward" is always away from the driver &mdash; it
    works immediately because the IMU heading is already flowing in.
  - Package: + `Drivebase` gains field-centric. active: `Drivebase.java`.

- **11 · Intake Subsystem**
  - New: the **`Subsystem` interface**, **enums** (`State{FORWARD,OFF,REVERSE}`), `update()`, the
    **`Robot` scheduler** calling every subsystem, **edge detection** for a toggle button.
  - Do: fill `Intake`; wire a button to cycle states.
  - Package: **+ new** `Subsystem.java`, `Robot.java`, `Intake.java`. active: `Intake.java`.

- **12 · Servo Dumper**
  - New: **`Servo`**, a **multi-state machine** (`STOWED/RAISING/DUMPING/LOWERING`), **non-blocking
    timing** with `ElapsedTime`, `isBusy()`, *why `sleep()` in a loop is bad*.
  - Do: fill `Dumper`; one button press runs the whole sequence without freezing the loop.
  - Package: **+ new** `Dumper.java`. active: `Dumper.java`.

### Tier: Advanced

- **13 · Pinpoint Localization + Telemetry**
  - New: **packages & imports**, the `GoBildaPinpointDriver`, pose (x, y, heading), units/frame.
  - Do: fill `Localization`; drive and watch (x, y, θ) update live.
  - Package: **+ new** `Localization.java`, `PinpointLocalizer.java` (Pedro adapter, given/full).
    active: `Localization.java`.

- **14 · Autonomous with Pedro Pathing**
  - New: `@Autonomous` structure, `setStartingPose`, `PathChain`/`BezierLine`, `Follower`, the
    drivetrain/localizer adapters, sequencing with a state machine.
  - Do: fill `SimpleAuto`; run a two-segment path.
  - Package: **+ new** `SimpleAuto.java`, `MecanumDrivetrainSubsystem.java` (Pedro adapter, given/full).
    active: `SimpleAuto.java`.

- **15 · FTC Dashboard**
  - New: `sendTelemetryPacket`, field overlay via `Canvas`, `@Config` tunables, live tuning workflow.
  - Do: draw the robot on the dashboard field; tune a value live.
  - Package: **+ new** `DashboardDraw.java` + dashboard wiring. active: `DashboardDraw.java`.

- **16 · Capstone: Build Your Own Subsystem (Elevator)** *(transfer/assessment)*
  - New: none — **apply everything**. Student writes a new subsystem from scratch by pattern-matching
    `Intake` (motor + simple states) and `Dumper` (staged state machine): own enum, constructor,
    `update()`, button binding, registered on the `Robot` scheduler.
  - Do: build an Elevator (or similar) using the two subsystems as reference templates.

## 5. Concept threading (nothing floods; each idea is reinforced later)

| Concept | Introduced | Reinforced |
|---|---|---|
| init vs. run loop | 02 | every lesson |
| telemetry | 03 | every lesson |
| variables / types | 04 | 05–10 |
| methods (params/returns) | 05–06 | 09+ |
| reading a sensor (IMU) | 08 | 10, 13 |
| class / constructor / private–public | 09 | 10–15 |
| interface | 11 | 12 |
| enum + state machine | 11 (simple) | 12 (complex), 14 (auto) |
| edge detection | 11 | 12, capstone |
| non-blocking timing | 12 | 14 |
| packages / imports | 13 | 14 |
| libraries as tools | 14 (Pedro) | 15 (Dashboard) |

## 6. Open granularity notes

- IDs leave slack (07 unused) so heavy lessons can split — likely candidates: **10 Field-Centric**
  (concept-heavy math) and **14 Autonomous** (paths + adapters). V1 tiers were 8/6/6 = 20, so there is
  room to expand from these ~15.
- The anchor-file-that-grows + `supersedes=` swap at lesson 09 is the one master-tagging pattern we
  should prototype first when we start authoring, to prove the procedural→class transition renders
  cleanly in snapshots.

## 7. Immediate next step

Author **Lesson 03 · Hello, Telemetry** (the lesson after the reworked "Your Code Package"), plus the
stripped `MecanumDrive.java` skeleton master it edits. That single lesson exercises the whole pipeline
(master tags → `course.json` snapshot → Reset button → Build & Run) at the simplest possible scope.
