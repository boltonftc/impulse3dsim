// Build-time entry — esbuild bundles this into web/vendor/codemirror.bundle.js.
// Exposes a small, stable API so index.html never imports CodeMirror internals directly.
import { EditorState, StateEffect, StateField, Compartment } from "@codemirror/state";
import {
  EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter,
  drawSelection, rectangularSelection, crosshairCursor, Decoration, hoverTooltip
} from "@codemirror/view";
import { defaultKeymap, history, historyKeymap, indentWithTab } from "@codemirror/commands";
import {
  indentOnInput, bracketMatching, foldGutter, foldKeymap,
  syntaxHighlighting, defaultHighlightStyle, indentUnit
} from "@codemirror/language";
import {
  closeBrackets, closeBracketsKeymap, autocompletion, completionKeymap
} from "@codemirror/autocomplete";
import { searchKeymap, highlightSelectionMatches } from "@codemirror/search";
import { lintKeymap, lintGutter, setDiagnostics as cmSetDiagnostics } from "@codemirror/lint";
import { java } from "@codemirror/lang-java";
import { oneDark } from "@codemirror/theme-one-dark";

// ---- transient line flash (used by lesson anchor cross-links) ----
const addFlash = StateEffect.define();
const clearFlash = StateEffect.define();
const flashDeco = Decoration.line({ class: "cm-flash-line" });
const flashField = StateField.define({
  create() { return Decoration.none; },
  update(deco, tr) {
    deco = deco.map(tr.changes);
    for (const e of tr.effects) {
      if (e.is(addFlash)) deco = Decoration.set([flashDeco.range(e.value)]);
      if (e.is(clearFlash)) deco = Decoration.none;
    }
    return deco;
  },
  provide: f => EditorView.decorations.from(f)
});

const readOnlyComp = new Compartment();

// ---- curated FTC API (v2 shim) for autocomplete + hover docs ----
// Flat, teaching-focused list matching what the sim shim actually exposes.
const ftcApi = [
  // op-mode structure
  { label: "LinearOpMode", type: "class", detail: "abstract class LinearOpMode", info: "Base class for your program. Extend it and override runOpMode()." },
  { label: "runOpMode", type: "method", detail: "void runOpMode() throws InterruptedException", info: "Your entry point. Runs once when you press INIT." },
  { label: "waitForStart", type: "method", detail: "void waitForStart()", info: "Pauses until the driver presses START (the ▶ button)." },
  { label: "opModeIsActive", type: "method", detail: "boolean opModeIsActive()", info: "True while the match is running — use it as your main loop condition." },
  { label: "opModeInInit", type: "method", detail: "boolean opModeInInit()", info: "True after INIT and before START." },
  { label: "isStopRequested", type: "method", detail: "boolean isStopRequested()", info: "True once STOP is pressed." },
  { label: "isStarted", type: "method", detail: "boolean isStarted()", info: "True after START is pressed." },
  { label: "idle", type: "method", detail: "void idle()", info: "Yield a moment to the system inside tight loops." },
  // telemetry
  { label: "telemetry", type: "variable", detail: "Telemetry telemetry", info: "Driver Station text output. Call addData(...) then update()." },
  { label: "addData", type: "method", detail: "Item addData(String caption, Object value)", info: "Queue one line of telemetry shown as \"caption : value\"." },
  { label: "update", type: "method", detail: "boolean update()", info: "Push the queued telemetry lines to the Driver Hub screen." },
  // hardware map
  { label: "hardwareMap", type: "variable", detail: "HardwareMap hardwareMap", info: "Look up configured devices by name." },
  { label: "get", type: "method", detail: "<T> T get(Class<T> type, String name)", info: "Fetch a device, e.g. hardwareMap.get(DcMotor.class, \"intake\")." },
  // gamepad
  { label: "gamepad1", type: "variable", detail: "Gamepad gamepad1", info: "Driver 1 controller inputs." },
  { label: "gamepad2", type: "variable", detail: "Gamepad gamepad2", info: "Driver 2 controller inputs." },
  { label: "left_stick_x", type: "property", detail: "float — left/right, -1.0 to 1.0", info: "Left stick horizontal. Right is +1.0." },
  { label: "left_stick_y", type: "property", detail: "float — up/down, -1.0 to 1.0", info: "Left stick vertical. Up is -1.0 (negate it for forward)." },
  { label: "right_stick_x", type: "property", detail: "float — left/right, -1.0 to 1.0", info: "Right stick horizontal (often used to turn)." },
  { label: "right_stick_y", type: "property", detail: "float — up/down, -1.0 to 1.0", info: "Right stick vertical." },
  { label: "left_trigger", type: "property", detail: "float — 0.0 to 1.0", info: "Left trigger pressure." },
  { label: "right_trigger", type: "property", detail: "float — 0.0 to 1.0", info: "Right trigger pressure." },
  { label: "left_bumper", type: "property", detail: "boolean", info: "Left bumper pressed." },
  { label: "right_bumper", type: "property", detail: "boolean", info: "Right bumper pressed." },
  { label: "dpad_up", type: "property", detail: "boolean", info: "D-pad up pressed." },
  { label: "dpad_down", type: "property", detail: "boolean", info: "D-pad down pressed." },
  { label: "dpad_left", type: "property", detail: "boolean", info: "D-pad left pressed." },
  { label: "dpad_right", type: "property", detail: "boolean", info: "D-pad right pressed." },
  // motors + servos
  { label: "DcMotor", type: "interface", detail: "interface DcMotor", info: "A drive/mechanism motor. setPower(-1..1); read encoder with getCurrentPosition()." },
  { label: "DcMotorEx", type: "interface", detail: "interface DcMotorEx extends DcMotor", info: "Adds closed-loop velocity control: setVelocity(radiansPerSec)." },
  { label: "CRServo", type: "interface", detail: "interface CRServo", info: "Continuous-rotation servo. Drive it with setPower(-1..1)." },
  { label: "Servo", type: "interface", detail: "interface Servo", info: "Positional servo. setPosition(0..1)." },
  { label: "setPower", type: "method", detail: "void setPower(double power)", info: "Motor/CRServo power, -1.0 (full reverse) to 1.0 (full forward)." },
  { label: "getPower", type: "method", detail: "double getPower()", info: "The last power you commanded." },
  { label: "setVelocity", type: "method", detail: "void setVelocity(double angularRate)", info: "DcMotorEx: target speed in radians/sec (closed loop)." },
  { label: "getVelocity", type: "method", detail: "double getVelocity()", info: "DcMotorEx: current commanded speed in radians/sec." },
  { label: "setDirection", type: "method", detail: "void setDirection(Direction d)", info: "FORWARD or REVERSE — flips which way positive power spins." },
  { label: "getCurrentPosition", type: "method", detail: "int getCurrentPosition()", info: "Encoder count in ticks." },
  { label: "setTargetPosition", type: "method", detail: "void setTargetPosition(int ticks)", info: "Goal encoder position for RUN_TO_POSITION." },
  { label: "setMode", type: "method", detail: "void setMode(RunMode mode)", info: "e.g. RUN_USING_ENCODER, STOP_AND_RESET_ENCODER." },
  { label: "setZeroPowerBehavior", type: "method", detail: "void setZeroPowerBehavior(ZeroPowerBehavior b)", info: "BRAKE holds position at 0 power; FLOAT coasts." },
  { label: "isBusy", type: "method", detail: "boolean isBusy()", info: "True while a RUN_TO_POSITION move is still going." },
  { label: "setPosition", type: "method", detail: "void setPosition(double position)", info: "Servo target from 0.0 to 1.0." },
  { label: "getPosition", type: "method", detail: "double getPosition()", info: "The servo's last commanded position." },
  { label: "scaleRange", type: "method", detail: "void scaleRange(double min, double max)", info: "Limit the servo to a sub-range of its travel." },
  // annotations + common enums
  { label: "@TeleOp", type: "keyword", detail: "@TeleOp(name=\"…\", group=\"…\")", info: "Registers a driver-controlled op mode (shows in the TeleOp list)." },
  { label: "@Autonomous", type: "keyword", detail: "@Autonomous(name=\"…\", group=\"…\")", info: "Registers an autonomous op mode (shows in the Auto list)." },
  { label: "@Override", type: "keyword", detail: "@Override", info: "Marks a method that overrides one from the superclass." },
  { label: "FORWARD", type: "constant", detail: "Direction.FORWARD", info: "Default spin direction." },
  { label: "REVERSE", type: "constant", detail: "Direction.REVERSE", info: "Flip the spin direction." },
  { label: "BRAKE", type: "constant", detail: "ZeroPowerBehavior.BRAKE", info: "Actively hold position at zero power." },
  { label: "RUN_USING_ENCODER", type: "constant", detail: "RunMode.RUN_USING_ENCODER", info: "Closed-loop speed control using the encoder." },
  { label: "RUN_WITHOUT_ENCODER", type: "constant", detail: "RunMode.RUN_WITHOUT_ENCODER", info: "Direct power, ignore the encoder." },
  { label: "STOP_AND_RESET_ENCODER", type: "constant", detail: "RunMode.STOP_AND_RESET_ENCODER", info: "Zero the encoder count." }
];
const ftcByLabel = {};
for (const e of ftcApi) ftcByLabel[e.label] = e;

// completion source merged into the Java language (keeps default keyword/word completion)
function ftcCompletionSource(context) {
  const word = context.matchBefore(/@?\w*/);
  if (!word || (word.from === word.to && !context.explicit)) return null;
  return { from: word.from, options: ftcApi, validFor: /^@?\w*$/ };
}

function escHtml(s) {
  return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// hover the word under the cursor -> signature + one-line doc
const ftcHover = hoverTooltip((view, pos) => {
  const line = view.state.doc.lineAt(pos);
  const text = line.text, base = line.from;
  const isW = c => /[\w@]/.test(c);
  let start = pos, end = pos;
  while (start > base && isW(text[start - base - 1])) start--;
  while (end < line.to && isW(text[end - base])) end++;
  if (start === end) return null;
  const entry = ftcByLabel[text.slice(start - base, end - base)];
  if (!entry) return null;
  return {
    pos: start, end, above: true,
    create() {
      const dom = document.createElement("div");
      dom.className = "cm-ftc-hover";
      dom.innerHTML = `<div class="cm-ftc-sig">${escHtml(entry.detail || entry.label)}</div>` +
        (entry.info ? `<div class="cm-ftc-doc">${escHtml(entry.info)}</div>` : "");
      return { dom };
    }
  };
});

const appTheme = EditorView.theme({
  "&": { height: "100%", fontSize: "12.5px" },
  ".cm-scroller": { overflow: "auto", fontFamily: "ui-monospace, Menlo, Consolas, monospace" },
  ".cm-flash-line": { backgroundColor: "#f0c04033", transition: "background-color .1s" },
  ".cm-ftc-hover": { maxWidth: "340px", padding: "6px 8px", lineHeight: "1.35" },
  ".cm-ftc-sig": { fontFamily: "ui-monospace, Menlo, Consolas, monospace", color: "#8bd3ff", fontWeight: "600", marginBottom: "3px" },
  ".cm-ftc-doc": { color: "#cbd5e1", fontSize: "12px" }
});

const javaSupport = java();

export function mountJavaEditor(parent, doc, onChange) {
  const state = EditorState.create({
    doc: doc || "",
    extensions: [
      lineNumbers(),
      highlightActiveLineGutter(),
      foldGutter(),
      drawSelection(),
      rectangularSelection(),
      crosshairCursor(),
      highlightActiveLine(),
      highlightSelectionMatches(),
      history(),
      indentOnInput(),
      indentUnit.of("    "),
      bracketMatching(),
      closeBrackets(),
      autocompletion({ activateOnTyping: true }),
      syntaxHighlighting(defaultHighlightStyle, { fallback: true }),
      javaSupport,
      javaSupport.language.data.of({ autocomplete: ftcCompletionSource }),
      ftcHover,
      oneDark,
      lintGutter(),
      flashField,
      readOnlyComp.of(EditorState.readOnly.of(false)),
      keymap.of([
        indentWithTab,
        ...closeBracketsKeymap,
        ...defaultKeymap,
        ...historyKeymap,
        ...foldKeymap,
        ...completionKeymap,
        ...searchKeymap,
        ...lintKeymap
      ]),
      EditorView.updateListener.of(u => { if (u.docChanged && onChange) onChange(u.state.doc.toString()); }),
      appTheme
    ]
  });
  return new EditorView({ state, parent });
}

export function getDoc(view) { return view.state.doc.toString(); }

export function setDoc(view, text) {
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: text || "" } });
}

export function setReadOnly(view, ro) {
  view.dispatch({ effects: readOnlyComp.reconfigure(EditorState.readOnly.of(!!ro)) });
}

// errors: [{ line(1-based), col(1-based, optional), endCol(optional), message, severity? }]
export function setErrors(view, errors) {
  const doc = view.state.doc;
  const diags = [];
  for (const e of (errors || [])) {
    const ln = Math.max(1, Math.min(doc.lines, e.line || 1));
    const line = doc.line(ln);
    let from = line.from + Math.max(0, (e.col || 1) - 1);
    if (from > line.to) from = line.to;
    let to = e.endCol ? line.from + (e.endCol - 1) : line.to;
    if (to <= from) to = Math.min(line.to, from + 1);
    diags.push({ from, to, severity: e.severity || "error", message: e.message || "error" });
  }
  view.dispatch(cmSetDiagnostics(view.state, diags));
}

// substring search over the whole doc; returns char index or -1
export function findAnchor(view, sub) {
  return view.state.doc.toString().indexOf(sub);
}

// select the line containing char index `pos`, scroll to it, and flash it briefly
export function revealPos(view, pos) {
  const line = view.state.doc.lineAt(pos);
  view.dispatch({ selection: { anchor: line.from, head: line.to }, scrollIntoView: true });
  view.dispatch({ effects: addFlash.of(line.from) });
  view.focus();
  setTimeout(() => { try { view.dispatch({ effects: clearFlash.of(null) }); } catch (_) {} }, 750);
  return line.number;
}
