# Impulse 3D Robot Simulator

An in-browser FIRST Tech Challenge programming course and 3D robot simulator.
Students write real Java, it compiles and runs **entirely in the browser**
(via [CheerpJ](https://cheerpj.com/)), and drives a physics-simulated mecanum
robot rendered with [three.js](https://threejs.org/) and
[Rapier](https://rapier.rs/) — no install, no Android Studio, no robot required.

Created by **Bolton Robotics FTC**.

## Live site

Served as a static site (e.g. GitHub Pages) from the [`web/`](web/) folder. The
CheerpJ loader in [`web/index.html`](web/index.html) auto-selects its source: the
local `range_server.py` proxy on `localhost`, and the CheerpJ CDN everywhere else
— so the same file works locally and in production with no edit. See
[Deploying](#deploying-to-github-pages).

## Quick start (local)

Requires Python 3. From this folder:

```powershell
python tools/range_server.py
```

Then open <http://localhost:8972/index.html>. The dev server adds HTTP `Range`
support (CheerpJ fetches classpath jars by range) and reverse-proxies the
CheerpJ CDN same-origin under `/cj/`.

## Project layout

| Path | What it is |
|------|-----------|
| `web/` | The shippable static site: `index.html`, compiled jars, lessons, assets. This is the GitHub Pages root. |
| `java/shim/` | Browser-side reimplementation of the FTC SDK API surface (`com.qualcomm.*`, `org.firstinspires.*`) the student code compiles against. |
| `java/sim/` | The simulator host (`OpModeHost`) and hardware implementations that bridge student code to the 3D physics world. |
| `java/examples/` | Reference competition OpModes, sanity-compiled by the build. |
| `course/` | Lesson **source**: `master/` tagged Java, `lessons/` HTML, `module.json` order, and `build_course.py`. |
| `tools/` | `build.ps1` (compiles jars), `range_server.py` (local dev server). |
| `docs/` | Design and planning notes. |

## Building

**Java / simulator** (after any change under `java/`):

```powershell
powershell -ExecutionPolicy Bypass -File tools\build.ps1
```

This compiles the shim and sim into `web/shim.jar` and `web/app.jar`, and
sanity-compiles the example and starter code. It prints `BUILD_OK` on success.

**Course** (after any change under `course/`):

```powershell
python course/build_course.py
```

This regenerates `web/course.json` (the per-lesson code snapshots) and
publishes the lesson HTML into `web/lessons/`. The committed `web/` artifacts
let the site run on a plain static host with no build step.

## Authoring lessons

Lesson code lives as **tagged master files** in `course/master/`. Tags like
`@begin(id)` / `@fill(id)` gate which lines appear (pre-provided) or are filled
in (typed by the student) at each lesson. Lesson prose is HTML in
`course/lessons/<id>/lesson.html`. `module.json` defines lesson order, titles,
and the active file. Run `python course/build_course.py` to publish.

## Deploying to GitHub Pages

This repo ships a workflow at [`.github/workflows/pages.yml`](.github/workflows/pages.yml)
that publishes the `web/` folder to Pages on every push to `main`.

1. Push this folder as the root of a repository (e.g. `boltonftc/impulse-3dsim`).
2. In the repo's **Settings → Pages**, set **Source** to **GitHub Actions**.
3. Push to `main` (or run the workflow manually). The site publishes to
   `https://<org>.github.io/<repo>/` — e.g. `https://boltonftc.github.io/impulse-3dsim/`.

The loader auto-selects the CheerpJ CDN off `localhost`, and CheerpJ resolves its
`.jar`s relative to the page, so hosting under a project subpath works as-is. If
the JVM ever fails to start with a `SharedArrayBuffer` / `crossOriginIsolated`
error, add the [`coi-serviceworker`](https://github.com/gzuidhof/coi-serviceworker)
shim, which enables cross-origin isolation on GitHub Pages.

## For coaches and mentors

The simulator itself is safe to use anywhere. **Deploy → Real Robot** is a
**beta** feature that changes a real Control Hub, so please supervise it:

- It writes the student's code into OnBotJava on the Control Hub (over USB or
  the WiFi Bridge helper) and builds it there. It may upload library jars.
- With on-bot test mode (`.ob`, the default) it only touches its own
  `teamcode/ob` folder and removes old files there. With test mode **off** it
  writes straight into `teamcode` and overwrites same-named files without asking.
  Back up first with **Import ← Real Robot**.
- Code that works in the sim can behave differently on hardware. Test with the
  wheels off the ground, keep people clear, and keep a hand on the Driver Station
  STOP.
- Every deploy requires the student to confirm that they accept these risks and
  that **an adult mentor has reviewed the code and is supervising**.
- The WiFi Bridge is a separate Windows helper. It only listens on
  `127.0.0.1`, and can switch the laptop's WiFi to the robot's network when asked
  to connect. To remove it, quit it from the tray and delete the `.exe` and
  `%AppData%\impulse-wifi-bridge`.

The simulator, the deploy feature and the WiFi Bridge are provided **as-is,
without warranty of any kind** — see the License below.

*FIRST*® and *FIRST*® Tech Challenge are registered trademarks of *FIRST*®,
which is not overseeing, involved with, or responsible for this software.

## License

Licensed under the [Apache License 2.0](LICENSE). Attribution to Bolton
Robotics FTC is required — see [NOTICE](NOTICE). Bundled third-party components
retain their own licenses (also listed in `NOTICE`).
