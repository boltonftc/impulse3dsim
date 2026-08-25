#!/usr/bin/env python3
"""
build_course.py -- turn the tagged .java.master files into the browser course.

This is the v2 (browser) descendant of impulse_3dsim/tools/generate_reverts.py. Instead of
writing base_code/ + per-lesson revert.java files to disk (the v1 desktop model), it emits a
single JSON blob the static web app fetches once:

    web/course.json  =  { "snapshots": { lesson_id: { "active": file, "files": { name: src } } } }

Each lesson's snapshot is the WHOLE code package as it should look at the START of that lesson --
every earlier lesson's code filled in, this lesson's (and every later lesson's) answer code
stripped out. The lesson's "Reset to Start" button loads that snapshot into the editor.

It also copies the authored lessons (course/lessons/ -> web/lessons/) and the lesson list
(course/module.json -> web/lessons/module.json) so one command produces every web artifact.

Master file tags (identical grammar to the v1 tool):
    // @begin(lesson_id)   -- block (header + code) hidden until this lesson is REACHED
    // @fill(lesson_id)     -- answer code included only AFTER this lesson is completed
    // @fill(lesson_id, supersedes=other_id)
                            -- when this fill is included, suppress @fill(other_id) (replacement)
    // @end(lesson_id)      -- closes a @begin or @fill block; the tag lines are always stripped

A file that processes to nothing but whitespace for a given lesson is omitted from that lesson's
snapshot entirely (that is how a subsystem file "does not exist yet" before it is introduced).

Usage:
    python course/build_course.py            # write web/course.json + copy lessons
    python course/build_course.py --check    # print what would be written, don't touch web/
"""

import json
import os
import re
import sys
import shutil
from datetime import datetime, timezone

# -- Paths --------------------------------------------------------------------
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
COURSE_DIR = SCRIPT_DIR
MASTER_DIR = os.path.join(COURSE_DIR, "master")
LESSONS_SRC_DIR = os.path.join(COURSE_DIR, "lessons")
MODULE_JSON = os.path.join(COURSE_DIR, "module.json")

WEB_DIR = os.path.normpath(os.path.join(COURSE_DIR, "..", "web"))
WEB_LESSONS_DIR = os.path.join(WEB_DIR, "lessons")
COURSE_JSON_OUT = os.path.join(WEB_DIR, "course.json")

# -- Tag patterns -------------------------------------------------------------
RE_BEGIN = re.compile(r"^\s*//\s*@begin\((\S+)\)\s*$")
RE_FILL = re.compile(r"^\s*//\s*@fill\((\S+?)(?:,\s*supersedes=(\S+))?\)\s*$")
RE_END = re.compile(r"^\s*//\s*@end\((\S+)\)\s*$")


# -- module.json --------------------------------------------------------------
def load_module():
    with open(MODULE_JSON, "r", encoding="utf-8") as f:
        return json.load(f)


def lesson_order(module):
    return [l["id"] for l in module["lessons"]]


# -- master files -------------------------------------------------------------
def list_masters():
    if not os.path.isdir(MASTER_DIR):
        return []
    return sorted(f for f in os.listdir(MASTER_DIR) if f.endswith(".master"))


def output_filename(master_name):
    assert master_name.endswith(".master")
    return master_name[: -len(".master")]


def load_master(filename):
    with open(os.path.join(MASTER_DIR, filename), "r", encoding="utf-8") as f:
        return f.readlines()


def generate_for_lesson(lines, lesson_id, order):
    """Produce one file's source as it should look at the START of `lesson_id`.

    reached   = lessons up to and INCLUDING the target -> their @begin blocks are visible
    completed = lessons strictly BEFORE the target      -> their @fill answer code is included

    lesson_id=None gives the "base" state (nothing reached, nothing filled) -- the blank slate.
    """
    if lesson_id is None:
        completed, reached = set(), set()
    else:
        idx = order.index(lesson_id) if lesson_id in order else -1
        completed = set(order[:idx])
        reached = set(order[: idx + 1])

    out = []
    suppressed = set()   # lesson ids whose @fill blocks are suppressed by a supersedes=
    skip_depth = 0       # >0 while inside a not-yet-reached @begin block

    i = 0
    while i < len(lines):
        line = lines[i]

        m = RE_BEGIN.match(line)
        if m:
            ref = m.group(1)
            if skip_depth > 0:
                skip_depth += 1
                i += 1
                continue
            if lesson_id is None or ref not in reached:
                skip_depth = 1
                i += 1
                continue
            i += 1   # visible -- drop only the tag line
            continue

        m = RE_FILL.match(line)
        if m:
            ref, supersedes_ref = m.group(1), m.group(2)
            if skip_depth > 0:
                # A @fill nested inside a not-yet-reached @begin. Count it as an opener so its
                # matching @end is balanced -- otherwise that @end would close the @begin early
                # and later code would leak out. (Lets a growing file gate whole future sections.)
                skip_depth += 1
                i += 1
                continue
            if ref in completed and ref not in suppressed:
                if supersedes_ref:
                    suppressed.add(supersedes_ref)
                i += 1   # include the fill body -- drop only the tag line
                continue
            # strip the fill body up to its matching @end
            i += 1
            depth = 1
            while i < len(lines):
                if RE_END.match(lines[i]):
                    depth -= 1
                    if depth == 0:
                        i += 1
                        break
                elif RE_BEGIN.match(lines[i]) or RE_FILL.match(lines[i]):
                    depth += 1
                i += 1
            continue

        if RE_END.match(line):
            if skip_depth > 0:
                skip_depth -= 1
            i += 1   # @end tag lines are always stripped
            continue

        if skip_depth == 0:
            out.append(line)
        i += 1

    return out


def tidy(lines):
    """Collapse 3+ blank lines to 1, trim leading/trailing blanks, ensure a final newline."""
    text = "".join(lines)
    text = re.sub(r"\n{3,}", "\n\n", text)
    text = text.strip("\n")
    return text + "\n" if text else ""


# -- main ---------------------------------------------------------------------
def build_snapshots(module):
    order = lesson_order(module)
    masters = list_masters()
    # per-lesson override of which file the editor should focus (module.json "active")
    active_by_lesson = {l["id"]: l.get("active") for l in module["lessons"]}

    snapshots = {}
    for lesson_id in order:
        files = {}
        for master_name in masters:
            java_name = output_filename(master_name)
            src = tidy(generate_for_lesson(load_master(master_name), lesson_id, order))
            if src.strip():
                files[java_name] = src
        active = active_by_lesson.get(lesson_id)
        if active not in files:
            active = next(iter(files), None)   # fall back to the first present file
        snapshots[lesson_id] = {"active": active, "files": files}
    return snapshots


# -- cross-link checker -------------------------------------------------------
# Lesson HTML cross-links are <span class="act" data-action="open_file|scroll_to" data-arg="...">.
#   open_file  arg = "File.java|Anchor substring"  (the anchor part is optional)
#   scroll_to  arg = "Anchor substring"
# Every anchor a lesson points at must already exist in THAT lesson's start snapshot -- otherwise the
# link silently does nothing when a student (who may have just hit Reset) clicks it. We author lessons
# by "driving anchors backwards" into the skeleton, so this guard catches an anchor we forgot to seed.
RE_ACT_TAG = re.compile(r'<[^>]*\bclass="act"[^>]*>')


def _attr(tag, name):
    m = re.search(r'\b' + re.escape(name) + r'="([^"]*)"', tag)
    return m.group(1) if m else None


def _resolve_file(files, fname):
    if fname in files:
        return fname
    base = os.path.basename(fname)
    for k in files:
        if os.path.basename(k) == base:
            return k
    return None


def lesson_html_path(lesson):
    if not lesson.get("html"):
        return None
    folder = lesson.get("folder", lesson["id"])
    return os.path.join(LESSONS_SRC_DIR, folder, lesson.get("file", "lesson.html"))


def check_links(module, snapshots):
    """Return a list of (lesson_id, kind, arg, reason) for cross-links whose target is missing."""
    failures = []
    for lesson in module["lessons"]:
        path = lesson_html_path(lesson)
        if not path or not os.path.isfile(path):
            continue
        with open(path, "r", encoding="utf-8") as f:
            html = f.read()
        snap = snapshots.get(lesson["id"], {})
        files = snap.get("files", {})
        for tag in RE_ACT_TAG.findall(html):
            kind = _attr(tag, "data-action")
            arg = _attr(tag, "data-arg") or ""
            if kind == "open_file":
                fname, _, anchor = arg.partition("|")
                resolved = _resolve_file(files, fname.strip())
                if resolved is None:
                    failures.append((lesson["id"], kind, arg, f"file '{fname.strip()}' not in snapshot"))
                elif anchor.strip() and anchor.strip() not in files[resolved]:
                    failures.append((lesson["id"], kind, arg, f"anchor not found in {resolved}"))
            elif kind == "scroll_to":
                sub = arg.strip()
                if sub and not any(sub in src for src in files.values()):
                    failures.append((lesson["id"], kind, arg, "anchor not found in any snapshot file"))
    return failures


def report_links(failures):
    if not failures:
        print("Link check: all cross-links resolve. \u2713")
        return
    print(f"Link check: {len(failures)} BROKEN cross-link(s):")
    for lid, kind, arg, reason in failures:
        print(f"  [{lid}] {kind} \"{arg}\"  ->  {reason}")


def main():
    check = "--check" in sys.argv
    module = load_module()
    snapshots = build_snapshots(module)

    course = {
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "course": module.get("course", "FTC Course"),
        "snapshots": snapshots,
    }

    print(f"Lessons: {len(module['lessons'])}   Masters: {len(list_masters())}")
    for lid, snap in snapshots.items():
        n = len(snap["files"])
        print(f"  {lid:22s} {n:2d} file(s)  active={snap['active']}")

    link_failures = check_links(module, snapshots)
    print()
    report_links(link_failures)

    if check:
        print("\n--check: nothing written.")
        sys.exit(1 if link_failures else 0)

    os.makedirs(WEB_DIR, exist_ok=True)
    with open(COURSE_JSON_OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(course, f, indent=1, ensure_ascii=False)
    print(f"\nWrote {os.path.relpath(COURSE_JSON_OUT, WEB_DIR)} ({os.path.getsize(COURSE_JSON_OUT)} bytes)")

    # publish lessons + module.json to the web tree
    if os.path.isdir(LESSONS_SRC_DIR):
        if os.path.isdir(WEB_LESSONS_DIR):
            shutil.rmtree(WEB_LESSONS_DIR)
        shutil.copytree(LESSONS_SRC_DIR, WEB_LESSONS_DIR)
        # module.json lives beside the lessons in the web tree
        shutil.copyfile(MODULE_JSON, os.path.join(WEB_LESSONS_DIR, "module.json"))
        print(f"Published lessons -> {os.path.relpath(WEB_LESSONS_DIR, WEB_DIR)}/ (+ module.json)")

    if link_failures:
        sys.exit(1)


if __name__ == "__main__":
    main()
