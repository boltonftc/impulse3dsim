# Impulse 3D Sim — UI Notes (v1 reference, for v2 rebuild)

Working notes captured from the v1 screenshots in this folder. Goal: turn each screen
into a concrete UI + backend contract for the browser (v2) rebuild. User owns the visual
UI; these notes drive the skin-agnostic plumbing (command/status API, registry, timer,
telemetry, gamepads, lesson cross-links).

## General concept (from image_captures.txt)
- FTC-centric teaching tool: fun gameplay + "upgrade your robot by writing code."
- Simulated **2:30 match**: 30 s autonomous, pause, 2:00 tele-op.
- **1 or 2 players** simultaneously, each with their own compiled code package.
- **2 robot types:** "dumper" and "shooter".
- **FMS (Field Management System):** tracks game stages, score, high scores.
- **Camera views:** driver station, FPV, top-down, chase.

---

## 1. main_simulator_screen.jpg — main 3D game view (1-player, red "1UP")
Window title `Impulse 3D Sim (DEBUG)`.

**Back-wall boards**
- `SCORING` legend: 1 PT (yellow ball), 3 PTS (purple ball), ENDGAME 25 PTS (pink cube).
- `1UP HIGH SCORES` + `2UP HIGH SCORES`: rank / 3-letter initials / score, 8 slots each
  (arcade initials e.g. KTM, HAL, KMM). Separate high-score tables per player-count mode.
- `GAME STATUS` board = phase ladder: AUTONOMOUS -> TRANSITION -> TELEOP -> END GAME.

**Field**
- Grey FTC tile floor, black perimeter wall.
- Translucent red goal/backboard back-left; blue goal back-right.
- Colored floor zones (blue + red squares) = parking/scoring regions.
- AprilTag markers on posts and floor (localization props).
- Red & blue signal beacons on front corner posts.

**Center mechanism**
- Large horizontal ring = ball respawner ("tornado" force-field ring) with 2 yellow ball
  clusters beneath. Scored balls respawn here, swirl, drop out randomly.
- Two vertical tubes of red/blue balls flanking center = bonus 3-PT balls, released when
  5 balls are scored quickly (short time window).

**Robot**
- White REV-style bot, green mecanum wheels, headlight, black top manipulator, bottom-center.

**Chrome / controls**
- Left-edge collapsed side tabs: `Diagnostic`, `Path Capture`, `Driver Hub`.
- Bottom pop-up tabs: `MENU`, `Simulation`.
- Bottom-right tiny red square = likely record/status indicator.

**Backend implications (draft)**
- Need score model + per-mode high-score table (persist to localStorage).
- Phase/timer state machine feeding the GAME STATUS ladder highlight.
- Respawner + streak/bonus logic lives in the sim/scoring layer (not student code).

---

## 2. driver_hub_popout_tab.jpg — emulated REV Driver Hub (red panel)
Slides out from the left `Driver Hub` side tab. Panel is alliance-colored (red here).

**Layout (top -> bottom)**
- Header row: `Auto` dropdown (left) | `RED` alliance label (center) | `TeleOp` dropdown (right).
- OpMode label line: `No OpMode` (shows selected op, or "none").
- Big round `INIT` button (REV style) -> becomes RUN when pressed.
- Footer row: `CODE` dropdown (left) | `STOPPED` status (center) | `Recompile` (right, circular-arrow).
- Large black region below = telemetry output (red-team telemetry prints here).

**Maps to existing v2 lifecycle backend**
- INIT/RUN/STOP -> already have pollCommand(1/2/3) + status(1..5).
- STOPPED/RUNNING/etc center label -> status codes already emit this.
- Telemetry panel -> already have telemetry native (upgrade to structured lines later).

**New backend needs**
- OpMode REGISTRY: two independent selections (Auto list + TeleOp list), grouped, populated
  from the student's compiled code package(s) -> feeds the two dropdowns.
- `CODE` dropdown = code-package selector (switch which package/framework is active).
- `Recompile` = explicit rebuild action, separate from INIT (INIT = build+construct+arm;
  Recompile = rebuild current selection without leaving init state?). Clarify semantics.
- Alliance theming (red/blue) as a UI parameter; telemetry routed per alliance.

---

## 3. driverhub_dropdown.jpg — Driver Hub close-up, TeleOp dropdown open
- `TeleOp` dropdown options shown: `-- NONE --`, `MecanumDrive`. Populated from the student's
  discovered `@TeleOp` classes (list grows as they add ops). Auto dropdown = same for
  `@Autonomous`. Always includes a `-- NONE --` entry.
- `CODE` dropdown tinted olive/green; `Recompile` shows a circular-arrow icon; center `STOPPED`.

**Backend confirm**
- Registry = scan compiled classes for `@TeleOp` / `@Autonomous`, expose {name, fqn, group,
  type} lists to the UI; UI renders NONE + each. Selecting one sets the active op for INIT.
- v2 already scrapes annotations client-side; extend from single-buffer to multi-class registry.

---

## 4. menu_popout_tab.jpg — `MENU` bottom toolbar (FMS + view + sim config)
Slides up from the `MENU` bottom tab.

**Big action buttons**
- `FMS START` (green) — start the match via the FMS (kicks off the 2:30 phase sequence).
- `FIELD RESET` (red) — reset balls / score / field to initial state.
- `PIT MODE` (yellow) — enter pit/lesson mode (lessons + IDE + driver hub).

**Config row**
- `1UP` toggle — player count 1 <-> 2.
- `Driver Stn` dropdown — camera view: Driver Station / FPV / Top-down / Chase.
- `dzb5 Music` — background music track selector.
- `AUTO ON` toggle — (auto camera-follow / idle auto-wander?) confirm meaning.
- `FOV OFF` toggle — camera FOV option.
- `NOISE ON` toggle — inject sensor noise (realism).
- Speed slider `1.0x` — simulation time-scale.
- Gear icon — settings.

**Backend needs**
- FMS controller: START -> run phase clock (auto 0:30 -> transition -> teleop 2:00 -> endgame),
  gate opmode enable per phase, drive GAME STATUS ladder + scoreboard timer.
- Field reset hook (balls, score, robot pose).
- Camera view enum + setter; sim time-scale multiplier applied to physics step + clock.
- Toggles (auto/fov/noise/music) are mostly UI/sim-local; NOISE feeds sensor shims.

---

## 5. pit_mode.jpg — PIT mode (lessons <-> editor <-> driver hub <-> test bench)
The keystone teaching screen. Top tabs: `PROGRAMMING` (active), `ELECTRICAL`, `MECHANICAL`
(`vdev` version tag top-right). Three working columns:

**Left column — lesson browser**
- Robot TRACK tabs: `Shooter Track` (active) / `Dumper Track`. Lessons differ per robot type.
- Difficulty GROUPS (collapsible):
  - `EASIER — Watch & Change Values`: Welcome to the PIT, FTC Robot Basics, Your Code Package,
    A Tour of Your Code, Telemetry, Tank Drive (checkbox CHECKED = done), Auto vs TeleOp.
  - `INTERMEDIATE — Build New Features`: IMU Heading, Mecanum Drive, ...
- Each row = title + one-line description + `Open` button (green easier / blue intermediate)
  + completion checkbox.

**Bottom-left — BENCH ROBOT (test bench)**
- 3D turntable/bench with labeled interactive props: `PIT TOOLS`, `CAMERA`, `FORCES`,
  `ROTATE - / +`, angle markings (`0 rad`, `0`, `90 pi/2`). Robot sits on a rotating turntable.
- Purpose: exercise sensors/mechanisms outside a match (spin to test IMU heading, trigger
  camera, apply forces). A sandbox harness, not scored.

**Bottom-center — DRIVER HUB**
- Same red hub; here `CODE: _starter` = active code package name. Auto/TeleOp/INIT/Recompile.

**Right column — CODE EDITOR**
- `File: MecanumDrive.java` dropdown = multi-file package (student edits multiple files).
- Buttons: `Save` (green), `Revert Code` (red, back to starter), `Export` (blue), `Import` (purple).
- Syntax-highlighted Java. Starter is a BLANK-ish framework with heavy guiding comments and
  NAMED INSERTION ANCHORS, e.g.:
    // Indexer Servo : Imports / Do not remove this comment / Add your CRServo import below
    // Dump Servo : Imports ...
    // IMU Heading : Imports ...
    // OTOS : Imports ...
- Header block teaches: "This is your first OpMode! Right now the robot does not move." + a
  MOTOR MAP (Port 0 -> back_left, 1 -> front_left, 2 -> front_right, 3 -> back_right).

**`Return to Match`** button -> back to sim/match view.

**Backend needs (big)**
- LESSON MODEL: tracks (shooter/dumper) x difficulty groups x lessons
  {id, title, desc, contentRef, completed}. Completion persisted.
- CODE PACKAGE: named starter (e.g. `_starter`) = a SET of Java files + a manifest `.json`
  tracking lesson progress and which package is active. `CODE` dropdown switches package.
- EDITOR: multi-file open/save/revert-to-starter/export/import. Autosave per file.
- CROSS-LINK ANCHOR SCHEME (answers the open question!): named comment markers of the form
  `// <Feature> : <Section>` + `// Add your ... below`. A lesson link references an anchor id;
  clicking it opens the right file, scrolls to the anchor, and flashes the target line. The
  "copy this code block" affordance (next screen) pastes at/after the anchor.
- BENCH/TEST harness: programmatic hooks to rotate the robot, fire camera, apply forces, so
  sensor lessons can be validated without a match.

---

## 6. lesson_cross_links_into_ide.jpg — opened lesson + live cross-link
Left column switches from lesson LIST to an opened lesson VIEW.

**Opened-lesson chrome**
- Top bar: `Back to Lessons`, `Reset Lesson Code` (orange = revert just this lesson's code),
  breadcrumb `Your Code Package`.
- Lesson title (`MecanumDrive.java — The Main Program`) + prose.
- `>> TO DO` callout (blue border) containing the cross-link, rendered as a hyperlink:
  `[ MecanumDrive.java : MecanumDrive : Starter TeleOp ]`.

**CROSS-LINK CONTRACT (confirmed)**
- Link text format: `[ <file> : <anchor text> : <label> ]`.
- On click: open `<file>` in the editor, find the comment line CONTAINING `<anchor text>`,
  scroll to it and flash/highlight it. In the shot, clicking highlights line 2
  `* MecanumDrive : Starter TeleOp` (grey highlight bar).
- So anchors are matched by SUBSTRING against in-file comments (the `// <Feature> : <Section>`
  markers from screen 5), not by line number -> robust to edits.
- Pair with a "copy this code block" affordance so the student can paste at the anchor.

**Backend/UI needs**
- Lesson content = rich text/markdown with a custom link syntax `impulse:open?file=..&anchor=..`.
- Editor API: openFile(file), findAnchor(text) -> line, flashLine(line), and
  insertAtAnchor(text) for copy-paste helpers.
- `Reset Lesson Code` = per-lesson revert (distinct from whole-package Revert Code).

---

## 7. 2player_tabs_out.jpg — 2-player (2UP) mode, both hubs out + MENU open
- TWO independent Driver Hubs: RED docked left, BLUE docked right. Each has its OWN
  Auto/TeleOp dropdowns, INIT, CODE package, STOPPED status, Recompile, and telemetry panel.
- Each alliance gets its own `Driver Hub` side tab (red left / blue right); left also `Diagnostic`.
- Both robots on field (RED station front-left, BLUE front-right, each with alliance beacon).
- Shared back-wall boards (SCORING, 1UP/2UP high scores, GAME STATUS ladder) + center respawner,
  ball tubes, AprilTags, per-alliance parking zones (blue + red floor squares).
- MENU bar open, `2UP` selected.

**Key implication**
- 2-player = TWO fully independent lifecycle instances (own package, opmode selection,
  telemetry stream, gamepad) under ONE shared FMS / score / field.
- Current single `sim.OpModeHost` must become MULTI-INSTANCE keyed by alliance (red/blue).

---

# SYNTHESIS — answers to the six backend questions (from the screenshots)
1. OpMode selection: REGISTRY, two lists (Auto + TeleOp), populated from discovered
   annotations, per code package, per alliance. Each hub picks independently.
2. Match phases: YES, real FMS clock — AUTONOMOUS 0:30 -> TRANSITION -> TELEOP 2:00 -> END GAME,
   started by `FMS START`, drives GAME STATUS ladder + scoreboard timer, gates opmode enable.
3. Telemetry: per-alliance stream into that hub's black panel. Upgrade single-string to lines.
4. Gamepads: at least one pad per alliance (2UP = two). Confirm gamepad2 per player later.
5. Extra readouts: score, timer/phase, high scores, hub status (STOPPED/RUNNING). Sensor
   NOISE toggle. Battery/per-motor not seen in these shots (optional later).
6. Cross-links: SOLVED. Link `[ file : anchorText : label ]` -> open file, substring-match a
   comment anchor (`// <Feature> : <Section>`), flash the line; plus copy-to-anchor helper.

# ARCHITECTURE DELTAS from current v2
- Multi-instance runner keyed by alliance (red/blue) — biggest change.
- OpMode registry (multi-class discovery) feeding Auto/TeleOp dropdowns.
- Code PACKAGE concept: named multi-file set + progress `.json`; CODE dropdown switches it.
- FMS phase/timer state machine + scoring + high-score persistence (localStorage).
- Structured telemetry (key->value lines) per alliance.
- Editor service: multi-file, save/revert/reset-lesson/export/import, anchor find+flash+insert.
- Lesson engine: tracks (shooter/dumper) x difficulty groups x lessons + completion; rich
  content with the custom cross-link syntax.
- Bench/test harness hooks (rotate, camera, forces) for sensor lessons.
- Camera view enum (driver stn / FPV / top-down / chase), sim time-scale, sensor-noise inject.

# OPEN QUESTIONS FOR USER
- INIT vs Recompile exact semantics (does Recompile keep init state?).
- `AUTO ON` toggle meaning (auto-camera vs idle auto-drive?).
- Robot type "shooter" vs "dumper": how it changes shims/mechanisms + which is default.
- Gamepad2 per player? keyboard mapping for Chromebooks?
- Is the ELECTRICAL / MECHANICAL tab content in scope for v2, or PROGRAMMING-first?
