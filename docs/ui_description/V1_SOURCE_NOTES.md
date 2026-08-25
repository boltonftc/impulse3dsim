# Impulse 3D Sim v1 — Source Deep-Dive Notes

Notes taken while reading the FULL v1 source + docs, to inform the v2 (browser) rebuild.
Companion to UI_NOTES.md (which covers the screenshots). This file = how it actually works
under the hood, subsystem by subsystem.

## Architecture (from README.md)
- TWO-PROCESS hybrid over UDP localhost, ports 5005-5008, ~60 Hz compact binary packets.
  - JAVA process = "robot brain": student OpMode execution, FTC SDK shims, motor models,
    kinematics, Pedro Pathing path following, runtime compiler.
  - GODOT 4.6 process = "physical world": 3D render/physics, robot rendering, camera, UI/HUD,
    scoring/match (FMS), gamepad input.
- Runs REAL Java FTC OpMode code unchanged (LinearOpMode/OpMode).
- Pedro Pathing v2.0.6 integrated (SimLocalizer / SimDrivetrain) for autonomous.
- Capture Mode = teach-pendant: record poses -> export compilable Pedro Pathing auto OpMode.
- FMS match flow: autonomous -> teleop -> endgame ceremony + scoring; high scores persisted.
- REV Driver Hub slide-out UI; jukebox music.

### v2 implication
- v2 collapses these two processes into ONE browser page: CheerpJ (Java brain) + Rapier/three
  (Godot's world/physics/UI). The UDP IPC becomes in-page JS<->Java natives (already built).
  So v1's IPC packet spec = the SEMANTIC contract of what state crosses the boundary; I can
  reuse it as the data model even though transport changes.

## Directory map (v1)
- godot/scripts/ = ALL game logic (.gd) — FMS, scoring, driver hub, lessons, camera, HUD.
- godot/scenes/ = .tscn scene files.
- java/src/main/java/{sim, com/qualcomm, org/firstinspires/ftc/{robotcore,teamcode}, pedroPathing}
- contracts/ = IPC_PACKET_SPEC.md, COORDINATE_SYSTEMS.md, SDK_SHIM_API.md (the boundary specs).
- packages/ = code packages (student starter frameworks + progress json).
- games/ = game configuration JSONs (scoring/field config).
- modules/, surfaces/ = TBD (inspect).
- music/ = jukebox tracks. ftc_fms_sounds/ = FMS ceremony audio.
- howto/ = runbooks incl AUTHORING_GUIDE.md (lesson authoring).
- pedro_src/ = Pedro Pathing reference decompilations.

## TODO subsystems to detail
- [ ] FMS / phases / scoring
- [ ] Driver Hub + OpMode registry + runtime compile/exec
- [ ] Lessons / code packages / progress json
- [ ] Cross-links + editor + anchors
- [ ] Robot types (shooter/dumper) + shims
- [ ] Bench robot / pit tools / sensors
- [ ] Camera views / 2-player / IPC packet spec

---

# FMS / MATCH FLOW / SCORING (godot/scripts)

## Phase state machine (main.gd ~L28-44)
Phases: NONE -> PREMATCH -> AUTO_INIT -> AUTO_RUNNING -> AUTO_ENDED -> TELEOP_INIT ->
TELEOP_RUNNING -> ENDGAME -> MATCH_ENDED.
- Durations (game.json, e.g. games/free_practice/game.json): autonomous 30s, teleop 120s,
  endgame_warning 20s (fires at 20s remaining), match_duration 150s.
- Clock is HUD-driven countdown (hud.gd); each phase runs its own timer; HUD signals transitions.
- FMS START button -> ceremony (audio+visual countdown, foghorn, GO overlay) -> on ceremony
  finish, scoring goes active + AUTO START is injected by the FMS (not the student).
- Transition auto->teleop ~7s, then TeleOp START injected by FMS.
- Gamepads ZEROED during AUTO_RUNNING (code-only), ENABLED in TELEOP/ENDGAME.
- OpMode status enum over IPC: STOPPED/IDLE 0, INIT 1, RUNNING 2, CRASHED->0.
- "AUTO OFF" toggle = teleop-only 2:30: skip auto, jump to TELEOP_RUNNING w/ 150s.

## Scoring (ball_recycler.gd)
- 50 balls total, split 25 red / 25 blue. Ball r=0.05715m (2.25"), mass 0.05kg, bounce 0.6,
  friction 0.4, damp 0.5, CCD on.
- Regular ball = 1 pt; bonus (striped) ball = 3 pt. TROUGH_MULTIPLIER=5 => trough/high-goal
  scoring = 5 pt (regular) / 15 pt (bonus). Trough requires ball dropping (vy < -0.5).
- Goal detection = Area3D sensor at each goal opening; one-shot per ball via instance_id set
  (_balls_scored_hole). Red goal west (X~-1.85), blue goal east (X~+1.85).
- scoring_active gates real points (true at match start, false at end).
- Signals: score_changed(red,blue), goal_scored(side), trough_scored(side,multiplier).
- Center "combiner"/respawner: cylinder r=0.75 h=0.75 (Y 0.80->1.55); centripetal 1.8N +
  tangential 0.8N => balls spiral (tornado). Goal "vacuum": 1.2N suction within 0.60m of goal.
- Out-of-field (Y<-1 or |X|>2.5 or |Z|>2.5) -> respawn after 0.5s inside combiner (random
  angle, sqrt-weighted radius, Y~1.45). NOTE: the "5-balls-fast bonus tube release" from the
  screenshots wasn't explicitly found here — trough multiplier + bonus balls may be the actual
  mechanic; CONFIRM the fast-5 streak vs bonus-ball spawn logic.
- Endgame parking bonus: arena_aesthetics.check_parking_bonus() -> {red,blue} added at end.
- Per-alliance: red_score / blue_score tracked separately, emitted together.

## High scores (high_score_manager.gd)
- user://high_scores.json, {"1up":[{name,score}...], "2up":[...]}, MAX_SCORES=16 per mode.
- 1UP = red only; 2UP = both, each alliance checked separately.
- Arcade 3-char initials ("A-Z0-9 "), keyboard arrows/enter + gamepad dpad/A.

## Ceremony / jukebox
- FMS sound cues: match_start buzzer, auto_complete, tele_start, match_end (ftc_fms_sounds/).
- Jukebox: user-selected music folder (user://jukebox_settings.json), play_random mp3/ogg,
  5s fade to -40dB at match end; spectrum-driven EQ bars/sparkles on the combiner cylinder.
- Closing ceremony ~7s with winner (red/blue/tie), trophy, then high-score entry if qualified.

### v2 mapping
- FMS = a JS match-clock state machine; phase gates the runner enable + injects auto/teleop
  START (students don't press START during a real match — FMS does). Keep the manual INIT/START
  path for PIT practice, but MATCH mode is FMS-driven.
- Scoring/respawner/goals = Rapier sensors + a scoring module (NOT student code).
- High scores + jukebox settings -> localStorage. FMS sounds -> ftc_fms_sounds assets.

---

# DRIVER HUB + OPMODE REGISTRY + RUNTIME COMPILE/EXEC

## OpMode discovery (java sim/OpModeRegistry.java)
- Uses org.reflections to scan classpath for @TeleOp / @Autonomous, skips @Disabled, validates
  OpMode subclass. Stores per op: {displayName, className(fqn), type: teleop|autonomous}.
- getAvailableOpModes()/getAvailableClassNames()/getType(). ONE registry PER robot (registries[]).
- Sent to Godot in RobotState.opmodeList = List<String[]{displayName,className,type}> each tick.
- Godot driver_hub_panel.update_opmode_list() -> _populate_dropdowns() (Auto list vs TeleOp list).

## Runtime compile (java sim/TeamcodeCompiler.java + SimMain.compileTeamcode)
- Uses javax.tools.JavaCompiler (system javac, needs JDK not JRE), source/target 17, -Xlint:none.
- Source root must contain org/firstinspires/ftc/teamcode/; output = per-robot temp dir
  impulse_compiled_r{idx}_*. Timestamp cache skips unchanged .java.
- Recompile flow: Godot compile_requested -> WorldState CMD_COMPILE w/ sourcePath in
  selectedOpmode field -> Java compileTeamcode(path, robotIdx):
  set COMPILING -> close old URLClassLoader (win file lock) -> clean stale .class -> compile ->
  new URLClassLoader -> registries[r].scan() -> status SUCCESS/ERROR, compileSeq++ (wrap 255).
- Errors: RobotState.compileStatus (0 none/1 compiling/2 ok/3 err) + compileMessage (diagnostics);
  Godot compile_overlay.gd shows fullscreen error overlay.

## Lifecycle (java sim/OpModeRunnerImpl.java, IPC WorldState commands)
- Commands: CMD_INIT_OPMODE=1, CMD_START=2, CMD_STOP=3 (+ CMD_COMPILE). One-shot, preserved
  across packet drain so they aren't shadowed.
- INIT: big REV center button -> opmode_command(CMD_INIT, className) -> runner.init(className):
  load via URLClassLoader, newInstance, inject hardwareMap/gamepad1/gamepad2/telemetry,
  resetStartTime; LinearOpMode -> internalStart() (worker thread blocks at waitForStart);
  iterative -> init(). status=INIT. Button then shows RUN.
- START: runner.start(): LinearOpMode internalNotifyStart() (unblocks waitForStart) / iterative
  start(); status=RUNNING.
- tick() @60Hz: LinearOpMode watchdog 500ms + thread-alive + capture uncaught exception;
  iterative init_loop()/loop(); auto telemetry.update().
- STOP: runner.stop(): internalRequestStop, join(1000ms), interrupt, last-resort Thread.stop;
  zero chassis+mechanism motors; status=IDLE. CRASH: exception/watchdog -> CRASHED.
- Standalone Driver Hub (non-FMS) auto-advances INIT->START in a practice flow.

## Telemetry (java sim/TelemetryImpl.java)
- addData(caption,value) -> ConcurrentLinkedQueue; update() drains to activeItems snapshot.
- toString() = newline-delimited "caption : value", capped ~900 bytes. PLAIN strings (not a
  structured map on the wire). RobotState.telemetryText (uint16 len). Godot renders in the black
  RichTextLabel (~50 cols x 16 lines) per alliance panel. Per-alliance, no merging.

## CODE dropdown = package selection
- driver_hub_panel _on_code_picker_pressed() -> FileDialog folder -> package_selected(path) ->
  main _on_package_selected(): clear dropdowns (stale guard) -> CMD_COMPILE w/ folder path ->
  Java auto-detects source root (flat vs org/.../teamcode), compiles, rescans registry ->
  new opmodeList flows back -> dropdowns repopulate.

## 2 players / two robots
- NUM_ROBOTS=2; per-robot arrays runners[]/registries[]/transports[]/hardwareMaps[]/compileStatuses[].
- Ports: robot0 5005(J->G)/5006(G->J), robot1 5007/5008. Red=robot0, Blue=robot1.
- Independent compile + registry + classloader per robot (each alliance its own code package).
- No "alliance" tag in IPC — it's just which port pair. Both robot bodies live in one Godot scene.

### v2 mapping (BIG)
- Registry -> multi-class annotation scan client-side (already scrape single buffer; generalize).
- Runtime compile -> already ECJ in-browser (v1 used system javac; v2 uses ECJ, fine).
- MULTI-INSTANCE: current single sim.OpModeHost must become 2 instances keyed by alliance,
  each with own FilesLoader/registry/telemetry/gamepad. IPC ports -> two JS command/status
  channels (namespaced natives or an alliance arg).
- Telemetry -> keep simple string now; the wire was plain strings in v1 too. Structured optional.

---

# LESSONS / CODE PACKAGES / CROSS-LINKS / EDITOR
(Full detailed reference also saved by subagent at /memories/repo/ftc_pit_system.md)

## Code package (packages/<name>/)
- Files: editable Java source(s) + package.json + lesson_progress.json.
- package.json: { name, track: "shooter"|"dumper", files[], builtin(bool), version }.
- lesson_progress.json: { "<lesson_id>": bool, "__active_track": "shooter"|"dumper" }.
- Protected packages: _starter, _competition (cannot be overwritten on import).
- _starter = blank-ish framework, heavy comments + named anchors; competition pkg always shooter.

## Lesson model (modules/ftc_starter_course/module.json)
- module.json lists lessons: { id, title, description, tier: easier|intermediate|advanced,
  category: programming|electrical|mechanical, folder }.
- Grouped in UI by 3 CATEGORY tabs (PROGRAMMING/ELECTRICAL/MECHANICAL) x TIER within each.
- Track (shooter/dumper) is per-package, user-selectable, persisted in progress json.
- Completion checkbox persisted per lesson_id in lesson_progress.json.

## Lesson content = Markdown + HTML ACTION comment tags
- Themed blocks: KEY_IDEA, CODE (with Copy button), TODO, HINT, CHALLENGE.
- Images in lessons/<id>/images/, referenced ![alt](file.jpg).
- ACTION tags (the cross-link mechanic):
    <!-- ACTION:scroll_to "Section Name : Label" -->
    <!-- ACTION:open_file "MecanumDrive.java|Section Name : Label" -->
    <!-- ACTION:complete "Mark Complete" -->
- Rendered to the student as a link like `[ MecanumDrive.java : marker label ]`.

## Cross-link resolver (pit_mode.gd ~L4200 _on_lesson_action)
- Parser regex: <!--\s*ACTION:(\w+)\s+"([^"]+)"\s*--> -> BBCode [url=action:...].
- scroll_to: SUBSTRING match the marker text against file lines -> set caret -> FLASH line
  (gold -> normal over ~1s).
- open_file: parse "filename|marker" -> stash current buffer -> load file -> scroll to marker.
- ANCHOR CONVENTION (HARD RULE): Java comment `// Short Name : Label` (space-colon-space).
  Matching is by substring, robust to edits. This is the target scheme for v2.
- "Copy this code block": CODE blocks in lessons get a Copy button; student pastes at the anchor.

## Editor ops (pit_mode.gd)
- Multi-file open (File dropdown). Save = all dirty files -> disk.
- Revert Code = file from lesson revert.java, then per-file overrides, then rest from base_code.
- Reset Lesson Code = per-lesson revert (distinct from whole Revert).
- Export = ZIP (pkgname/file.java...). Import = extract ZIP, reject protected pkgs, resolve name
  conflicts.
- Progress saved on: lesson completion, track change, package load; loaded on set_package_dir().

## AUTHORING_GUIDE.md
- Lessons authored as master files with @begin/@fill/@end tags + the ACTION grammar above;
  authoring pipeline places them under modules/<course>/lessons/<id>/.

### v2 mapping
- Lesson engine = markdown renderer + ACTION-tag parser producing clickable links.
- Editor service API needed: openFile(name), findAnchor(substr)->line, flashLine(line),
  copyBlock(text). Anchors are substring-matched comments `// X : Y`.
- Packages + progress -> a virtual FS in the browser (localStorage/IndexedDB); import/export ZIP.
- Course/module registry (module.json) drives the lesson browser (category tabs x tier x track).

---

# ROBOT TYPES + SDK SHIM SURFACE + SENSORS + PIT BENCH

## Shim surface (what student code can call)
- opmode: LinearOpMode (runOpMode/waitForStart/opModeIsActive/opModeInInit/sleep/idle/getRuntime/
  resetStartTime), OpMode (init/init_loop/start/loop/stop), @TeleOp/@Autonomous/@Disabled.
- hardware: DcMotor (+modes RUN_WITHOUT/USING_ENCODER, RUN_TO_POSITION, STOP_AND_RESET_ENCODER;
  setPower/getPower/setMode/getCurrentPosition/setTargetPosition/isBusy/setZeroPowerBehavior),
  DcMotorEx (setVelocity/getVelocity/getCurrent/setPIDFCoefficients), DcMotorSimple (setDirection),
  Servo (setPosition/getPosition/setDirection/scaleRange), CRServo (setPower),
  IMU (initialize/resetYaw/getRobotYawPitchRollAngles/getRobotAngularVelocity),
  DistanceSensor (getDistance(DistanceUnit)), Gamepad (sticks/triggers/buttons + PS4 aliases +
  wasJustPressed edge detect), HardwareMap (get(Class,name) case-insensitive; dcMotor/servo/crservo).
- external: Telemetry (addData/update/clear/addLine/log). navigation: AngleUnit, DistanceUnit,
  YawPitchRollAngles, AngularVelocity, Pose2D/3D, Position, CurrentUnit.
- (v1 uses real system javac; v2 uses ECJ. Shim jar is the same idea.)

## Robot types (skins swapped by package.json track; default shooter)
- Shared: 4-wheel mecanum + intake motor + IMU + 4 distance sensors.
- SHOOTER (default; goBILDA RI3D DECODE style): intake roller bank, star-wheel feeder
  (indexer_servo CRServo), vertical ball channel (<=3 balls), flywheel (shooter_motor DcMotor),
  fixed ~35deg hood. Scores into backboard holes (x3/x5) or floor goal (x1).
- DUMPER: intake roller bank, dump bucket (dump_servo positional Servo 0=down..1=tilt ~70deg),
  no flywheel/indexer. Scores by tilting bucket in a delivery zone.

## Hardware map names (students type these)
- Drive: front_left_motor(0) front_right_motor(1) back_left_motor(2) back_right_motor(3).
- Mechanisms: intake_motor(4), shooter_motor(5, shooter), indexer_servo(CRServo, shooter),
  dump_servo(Servo, dumper).
- Sensors: front/left/back/right_distance (DistanceSensor, RayCast3D ~2m), imu (yaw from
  physics rotation.y, pitch/roll=0, range +/-180), sensor_otos (SparkFunOTOS, legacy),
  pinpoint (GoBildaPinpointDriver odometry), aprilTagProcessor (AprilTagProcessor),
  "Webcam 1" (WebcamName), limelight (Limelight3A).
- (HardwareMapFactory.java builds these per robot type.)

## Sensors + noise
- Distance: 4 RayCast3D on robot frame, meters, capped 2.0m, sent each tick.
- IMU: yaw = Godot rotation.y; pitch/roll 0; resetYaw stores offset (IMUImpl.java); +/-pi.
- Color: stub -> floor grey [0.45,0.45,0.48] (future).
- Gamepad: deadzone 0.2 proportional; left Y inverted; volatile fields; updateFromState.
- NOISE toggle: infrastructure/flags in IPC per sensor (distance jitter, tag pos uncertainty,
  heading drift) but NOT fully wired to student API yet (framework-ready).

## PIT bench harness (pit_mode.gd)
- ROTATE turntable: CW/CCW mushroom buttons, 0.6 rad/s, spins robot-on-jackstands to test
  heading code; emits turntable_heading_changed -> compass rose + heading line.
- CAMERA/FOV toggle: shows camera frustum cone + downfield detection line; highlights on
  AprilTag in view; ping sound; only in apriltag lesson.
- FORCES toggle: 4 per-wheel force arrows (resultant white + Fx red + Fy blue) + net vector,
  updated from motor/servo state; teaches kinematics.
- Whiteboard/bulletin: lesson-driven live data viz (mecanum wheel table / apriltag grid /
  RPM-vs-target graph / tank table).
- Compass rose: N/S/E/W, rotates on IMU reset, green heading line + degree label.
- Bench robot skin swaps shooter/dumper by lesson track; stored-ball indicators (<=3);
  shooter shows projectile arc when RPM>0; orbit camera around turntable.

### v2 mapping
- Grow the v2 shim jar to this surface incrementally (already have DcMotor/Servo-ish + gamepad +
  telemetry). Priority: DcMotor modes/encoders, Servo, CRServo, DcMotorEx velocity, IMU yaw,
  DistanceSensor, HardwareMap.get by name.
- Robot type = a config that selects skin + which hardware-map names exist + scoring behavior.
- Bench harness = a separate PIT scene with turntable/FOV/forces hooks driving the same runner.

---

# IPC DATA MODEL / COORDINATES / CAMERAS / CAPTURE

## IPC packet spec (contracts/IPC_PACKET_SPEC.md) — THE SEMANTIC CONTRACT
Little-endian, UDP 60Hz, seq numbers drop stale, last-known-good on miss. (v2 = in-page, but
these FIELDS are exactly the JS<->Java state I must pass.)

RobotState (Java -> Godot), the "what the brain wants":
- seq; velocity_x (strafe, robot-frame m/s, +right); velocity_y (forward, +fwd);
  angular_velocity (rad/s CCW+); wheel_speeds[4] FL/FR/BL/BR normalized (RENDER only);
  servo_position [0..1]; opmode_state (0 stopped/1 init/2 running); telemetry_length; telemetry_text
  (UTF-8, \n-delimited, ~<=960 bytes).
- NOTE spec is v0.1 DRAFT (single servo); RUNTIME extended to multi-mechanism (intake/shooter
  motors, indexer/dump servos, ball counts, compileStatus/seq, opmodeList). Treat runtime as truth.

WorldState (Godot -> Java), the "what the world is":
- seq; robot_x, robot_y (world m, origin field center); robot_heading (rad CCW+); robot_angular_vel;
  actual_velocity_x/y (robot-frame post-collision, for ENCODERS); distance_sensors[4]
  front/left/back/right (m, max 2.0, -1 none); color_sensor_rgb[3]; gamepad1[56]; gamepad2[56];
  command (0 none/1 init/2 start/3 stop/4 reset); opmode_name_length; selected_opmode (fqn UTF-8).

## Coordinate systems (contracts/COORDINATE_SYSTEMS.md) — CRITICAL, avoids "drives sideways"
- THREE frames: FTC field 2D (x right, y forward, origin center, heading CCW+), Godot 3D Y-up
  (floor = XZ plane, forward = -Z), robot-frame 2D (rotates with robot).
- Field = 12x12 ft = 3.6576m; X,Y in [-1.83, 1.83].
- Mapping IPC->Godot: godot.x = ipc.x; godot.z = -ipc.y (NEGATE); godot.rotation.y = heading;
  velocity same with z=-vy. Godot->IPC reverses it. World->robot frame:
  rvx = wx*cos+wz*sin; rvz = -wx*sin+wz*cos; ipc = (rvx, -rvz).
- (v2 uses Rapier; three.js is also Y-up so this mapping largely carries over. Encoders derive
  from ACTUAL post-physics velocity, not commanded.)

## Camera views (camera_controller.gd)
- Modes: BIRD (top-down ortho, scroll zoom 2..8), CHASE (1.5m back / 0.8m up, lerp 8),
  DRIVER_STN (south wall, kid eye ~5ft, DEFAULT), FREE (WASD + RMB look), ROBOT_CAM (FPV,
  0.6m back/0.45m up, fov75, shows FOV + AprilTag lines). Selected via bottom-bar dropdown.
- Ceremony camera zoom (driver-stn pull-back + swirl) ~10s.

## Capture / teach-pendant (capture_panel.gd) = "Path Capture" tab
- Drive robot with gamepad, record POSE (LB) + ACTION (A/B/X/Y) entries, then EXPORT a
  compilable Pedro Pathing autonomous Java file. Per-alliance path preview colors.
- Entry types: pose{x,y,heading}, action{BUTTON_A..Y}. FTC units inches (39.37/m),
  FTC_heading = rotation.y + pi/2.
- This is the "record an auto by driving it" feature -> generates student-editable auto OpMode.

## Other scripts of note
- apriltag_manager.gd (AprilTag field markers), path_visualizer.gd (draw Pedro paths),
  monte_carlo_runner.gd (batch-run autos for tuning/stats), graphics_settings.gd, splash_screen.gd,
  compile_overlay.gd (fullscreen compile error), debug_overlay.gd, collision_debug.gd,
  arena_aesthetics.gd (ceremony/lights/foghorn), game_element_manager.gd (non-ball elements).

---

# ================= V2 SYNTHESIS / BUILD ORDER =================
The v1 two-process UDP design collapses into ONE browser page in v2:
- CheerpJ = the Java "brain" (already have runtime ECJ compile + lifecycle runner).
- Rapier + three.js = the Godot "world" (already have mecanum force controller + field).
- The UDP RobotState/WorldState packets become in-page JS<->Java natives. The PACKET FIELDS are
  the exact data contract to expose (velocity/heading/sensors/gamepad/command/telemetry/opmodeList).

## Foundational deltas from current v2 (in dependency order)
1. OpMode REGISTRY (multi-class @TeleOp/@Autonomous scan) -> feeds Auto + TeleOp dropdowns.
2. CODE PACKAGE model = virtual multi-file FS + package.json + lesson_progress.json in
   localStorage/IndexedDB; CODE dropdown switches package; import/export ZIP.
3. MULTI-INSTANCE runner keyed by alliance (red/blue) for 1UP/2UP; each own registry/telemetry/
   gamepad. (Current single OpModeHost -> parameterize by alliance.)
4. FMS match clock/state machine (auto 0:30 -> transition 7s -> teleop 2:00 -> endgame @20s ->
   end) that INJECTS start (students don't press START in a match) + gates gamepads + drives
   scoreboard/ladder. Keep manual INIT/START for PIT practice.
5. SCORING module (Rapier sensors at goals/troughs; 1/3pt balls, x5 trough; respawner tornado;
   parking bonus) + per-alliance score + high scores (localStorage, 1up/2up tables, arcade initials).
6. SHIM growth to v1 surface: DcMotor(modes/encoders), DcMotorEx(velocity), Servo, CRServo, IMU
   (yaw), DistanceSensor(4), HardwareMap.get(name). Hardware-map names per robot type.
7. LESSON engine: module.json registry (category tabs x tier x track) + markdown renderer +
   ACTION-tag parser (scroll_to/open_file/complete) + editor service (openFile/findAnchor(substr
   `// X : Y`)/flashLine/copyBlock). Anchor = substring-matched comment.
8. ROBOT TYPES shooter/dumper: skin + hardware-map set + scoring behavior selected by package track.
9. CAMERAS (driver-stn default, chase, bird, FPV/robot-cam, free) + PIT bench harness
   (turntable/FOV/forces/compass) + capture/teach-pendant auto export (later).

## Reusable-as-is semantics (copy the numbers)
- Phase durations 30/7/120, endgame@20; field 3.6576m, X/Y +/-1.83; ball r=0.05715 m=0.05
  bounce0.6 fric0.4 damp0.5; 50 balls 25/25; trough x5; combiner r0.75 h0.75 centripetal1.8
  tangential0.8; goal suction1.2N r0.60; high scores 16/mode; hardware-map names + indices;
  coordinate mapping godot.z=-ipc.y & heading CCW+.

## Open confirmations (for user)
- Fast-5-balls bonus-tube release: is it a real mechanic, or did screenshots show bonus balls +
  trough x5? (subagent didn't find explicit streak-tube logic.)
- INIT vs Recompile semantics; AUTO ON/OFF exact meaning; default robot type (shooter).
- v2 scope: PROGRAMMING-first, or also ELECTRICAL/MECHANICAL lesson categories?
- 2UP for Chromebooks: two gamepads realistic, or keyboard-share? Capture mode priority?

---

# ================= V2 GAME + UI DIRECTION (confirmed by user 2026-08-08) =================
Design intent: v2 rebases the GAME (like FIRST releases a new game yearly) but keeps the same
platform format. The backend/plumbing is game-agnostic; the game layer is a swappable module.

## The v2 game = "BIOBUZZ" (replaces kickball/shooter DECODE-style game)
- Elements: BIOBUZZ = YELLOW WHIFFLE balls, single color (no red/blue balls). Target ~50 balls;
  MVP starts with ~20.
- Central HIVE: one shared structure where balls are deposited / recycled back into play.
  This REPLACES v1's TWO per-alliance mechanisms (combiner "tornado" + per-goal vacuum) with ONE
  shared recycler. Reuse v1 recycler physics numbers as a starting point (centripetal/tangential
  forces, out-of-field respawn) but single + central.
- SCORING MODEL = STILL OPEN. User specified recycling, not yet how points are earned. Do NOT
  assume v1's two-goal 1/3pt + trough x5. Leave scoring pluggable until user decides.
- Field/MVP: 12x12 ft field (3.6576m; X/Y +/-1.83) + FIRST-FTC-style TRANSLUCENT perimeter wall
  as close to real dimensions as possible. MVP = field + wall + ~20 yellow balls, no complex
  scoring yet. Physics-only sandbox first, game rules layered on later.
- Whiffle balls -> lighter/draggier than kickballs; tune mass/drag later (not r=0.057/m=0.05).

## UI direction (follow the SHAPE, not the exact placement; graphics may differ in v2)
- 1UP and 2UP support (one bot per "alliance"; red + blue).
- MENU tab = control functions (init/start/stop/reset/mode toggles, camera select, etc.).
- DRIVER HUB tab per alliance (red + blue) = look/feel as close to the REV DRIVER HUB as possible.
  GOAL: students practice on the virtual hub so the REAL hardware feels familiar. This fidelity is
  the point, not decoration.
- PIT MODE (mandatory) = lessons + IDE + robot on a TEST STAND. Loop: edit code -> recompile ->
  run in the (pit) driver hub -> watch the mechanism actuate (e.g. intake spins). Then EXIT pit
  and take the SAME code/robot PACKAGE into the simulator GAME for a 2:30 FIRST-style match.
- Layout/positioning of tabs need NOT match v1; v2 can improve. Different framework => graphics
  will differ; that's acceptable. Keep the FUNCTIONAL structure (menu / driver-hub / pit).

## Impact on the build order above
- Game layer (balls, hive, scoring) is the LAST/most-swappable piece; build the plumbing +
  driver-hub + pit + code-package first, then drop BIOBUZZ in as the game module.
- Ball element + hive = a game module implementing: spawn N balls, hive deposit/recycle, (later)
  scoring. Keep it behind a small "Game" interface so future annual games swap cleanly.
- The pit->match "carry the package" flow is a first-class requirement: a code PACKAGE (multi-file
  + package.json + progress) is the unit that moves between PIT and GAME.
