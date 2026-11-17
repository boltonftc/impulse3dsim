/* PodConfig -- finds every place a code package sets up the goBILDA Pinpoint (pod offsets, pod type,
 * encoder directions) and keeps them to ONE source.
 *
 * Why: TeleOp's Localization and auto's PinpointLocalizer each used to carry their own copy of these
 * settings. Edit one and not the other, and TeleOp and auto disagree about where the robot is -- on a
 * real robot every turn then "slides" the pose (the sim ignores pod settings, so it never shows).
 *
 *   analyze(files)               what each file sets, whether they disagree, what can be fixed
 *   delegate(src, mode, recv)    rewrite a file's hard-coded setup into ONE call:
 *                                  'robotconfig'  -> RobotConfig.configurePinpoint(pinpoint);
 *                                  'localization' -> new Localization(hardwareMap);   (PinpointLocalizer)
 *   planRobotConfigFix(files, cfg)  the full set of file edits the review prompt applies
 *
 * Pure text in, text out (no DOM), so it runs in the browser (window.PodConfig) and in node tests.
 */
(function (root) {
  'use strict';

  const MM_PER = { MM: 1, CM: 10, METER: 1000, INCH: 25.4 };
  const SETUP_CALLS = ['setOffsets', 'setEncoderResolution', 'setEncoderDirections'];

  // Same-length copy with comments and string/char literals blanked out, so regexes never match inside
  // them and every index still points at the same spot in the original source.
  function mask(src) {
    const out = src.split('');
    let i = 0;
    const n = src.length;
    const blank = (a, b) => { for (let k = a; k < b; k++) if (out[k] !== '\n') out[k] = ' '; };
    while (i < n) {
      const c = src[i], d = src[i + 1];
      if (c === '/' && d === '/') { const e = src.indexOf('\n', i); const end = e < 0 ? n : e; blank(i, end); i = end; }
      else if (c === '/' && d === '*') { const e = src.indexOf('*/', i + 2); const end = e < 0 ? n : e + 2; blank(i, end); i = end; }
      else if (c === '"' || c === '\'') {
        let j = i + 1;
        while (j < n && src[j] !== c && src[j] !== '\n') j += src[j] === '\\' ? 2 : 1;
        blank(i + 1, Math.min(j, n)); i = j + 1;
      } else i++;
    }
    return out.join('');
  }

  // split "a, f(b, c), d" at top-level commas
  function splitArgs(s) {
    const out = []; let depth = 0, cur = '';
    for (const ch of s) {
      if (ch === '(') depth++;
      if (ch === ')') depth--;
      if (ch === ',' && depth === 0) { out.push(cur.trim()); cur = ''; } else cur += ch;
    }
    if (cur.trim()) out.push(cur.trim());
    return out;
  }

  function readConsts(m) {
    const nums = {}, enums = {};
    let r;
    const reNum = /\bfinal\s+double\s+(\w+)\s*=\s*([^;]+);/g;
    while ((r = reNum.exec(m))) nums[r[1]] = r[2].trim();
    const reEnum = /\b(?:EncoderDirection|GoBildaOdometryPods)\s+(\w+)\s*=\s*([\w.\s]+);/g;
    while ((r = reEnum.exec(m))) enums[r[1]] = r[2].replace(/\s+/g, '').split('.').pop();
    return { nums, enums };
  }

  // evaluate a plain arithmetic expression, resolving this file's double constants (no code is run
  // unless only digits and + - * / ( ) remain)
  function evalNum(expr, consts, depth) {
    if (depth > 6) return NaN;
    let s = String(expr).replace(/\b[A-Z_][A-Z0-9_]*\b/g, id => {
      if (id in consts.nums) { const v = evalNum(consts.nums[id], consts, (depth || 0) + 1); return isFinite(v) ? '(' + v + ')' : 'NaN'; }
      return id;
    });
    s = s.replace(/(\d)[dDfF]\b/g, '$1');
    if (!/^[\d.\s+\-*/()eE]+$/.test(s)) return NaN;
    try { const v = Function('"use strict";return (' + s + ');')(); return typeof v === 'number' && isFinite(v) ? v : NaN; }
    catch (e) { return NaN; }
  }

  function enumVal(tok, consts, allowed) {
    const last = String(tok).replace(/\s+/g, '').split('.').pop();
    if (allowed.includes(last)) return last;
    if (last in consts.enums && allowed.includes(consts.enums[last])) return consts.enums[last];
    return null;
  }

  // the statement (receiver.call(...);) at each setup call, with its index range in the source
  function findCalls(m, name) {
    const re = new RegExp('(\\w+)\\s*\\.\\s*' + name + '\\s*\\(', 'g');
    const out = [];
    let r;
    while ((r = re.exec(m))) {
      let i = re.lastIndex, depth = 1;
      while (i < m.length && depth) { if (m[i] === '(') depth++; else if (m[i] === ')') depth--; i++; }
      const argEnd = i - 1;
      let j = i; while (j < m.length && /\s/.test(m[j])) j++;
      if (m[j] !== ';') continue;
      out.push({ recv: r[1], start: r.index, end: j + 1, args: m.slice(re.lastIndex, argEnd) });
    }
    return out;
  }

  /* What one file does to the Pinpoint. */
  function analyzeFile(name, src) {
    const m = mask(src || '');
    const consts = readConsts(m);
    const info = { file: name, hard: false, delegate: null, recv: null, offsets: null, dirs: null, pods: null, unreadable: [], calls: [] };
    if (/\bRobotConfig\s*\.\s*configurePinpoint\s*\(/.test(m)) info.delegate = 'robotconfig';
    else if (name === 'PinpointLocalizer.java' && /\bnew\s+Localization\s*\(\s*hardwareMap\s*\)/.test(m)) info.delegate = 'localization';

    for (const c of findCalls(m, 'setOffsets')) {
      info.calls.push(c); info.hard = true; info.recv = info.recv || c.recv;
      const a = splitArgs(c.args);
      const unit = a.length === 3 ? enumVal(a[2], consts, Object.keys(MM_PER)) : 'MM';
      const x = evalNum(a[0], consts, 0), y = evalNum(a[1], consts, 0);
      if (a.length >= 2 && unit && isFinite(x) && isFinite(y)) info.offsets = { x: x * MM_PER[unit], y: y * MM_PER[unit] };
      else info.unreadable.push('offsets');
    }
    for (const c of findCalls(m, 'setEncoderDirections')) {
      info.calls.push(c); info.hard = true; info.recv = info.recv || c.recv;
      const a = splitArgs(c.args), D = ['FORWARD', 'REVERSED'];
      const dx = enumVal(a[0], consts, D), dy = enumVal(a[1], consts, D);
      if (dx && dy) info.dirs = [dx, dy]; else info.unreadable.push('directions');
    }
    for (const c of findCalls(m, 'setEncoderResolution')) {
      info.calls.push(c); info.hard = true; info.recv = info.recv || c.recv;
      const a = splitArgs(c.args);
      const p = a.length === 1 ? enumVal(a[0], consts, ['goBILDA_4_BAR_POD', 'goBILDA_SWINGARM_POD']) : null;
      info.pods = p || ('custom: ' + c.args.replace(/\s+/g, ' ').trim());
    }
    info.calls.sort((p, q) => p.start - q.start);
    return info;
  }

  const sameCfg = (a, b) => !!a && !!b
    && (a.offsets && b.offsets ? Math.abs(a.offsets.x - b.offsets.x) < 0.5 && Math.abs(a.offsets.y - b.offsets.y) < 0.5 : a.offsets === b.offsets)
    && JSON.stringify(a.dirs) === JSON.stringify(b.dirs) && a.pods === b.pods;

  const USED_BY = {
    'Localization.java': 'TeleOp (Localization)',
    'PinpointLocalizer.java': 'Autonomous (PinpointLocalizer)',
    'RobotConfig.java': 'RobotConfig',
  };

  /* Every Pinpoint setup in a package, what each OpMode really ends up using, and what to do about it. */
  function analyze(files) {
    files = files || {};
    const sites = [];
    for (const name of Object.keys(files)) {
      if (!/\.java$/.test(name) || typeof files[name] !== 'string') continue;
      const f = analyzeFile(name, files[name]);
      if (f.hard || f.delegate) sites.push(f);
    }
    const byName = Object.fromEntries(sites.map(s => [s.file, s]));
    const rcSrc = files['RobotConfig.java'];
    const rc = byName['RobotConfig.java'] || null;
    const hasRC = typeof rcSrc === 'string';
    const rcHasMethod = hasRC && /\bstatic\s+void\s+configurePinpoint\s*\(/.test(mask(rcSrc));
    const locSrc = files['Localization.java'];
    const locHasCtor = typeof locSrc === 'string' && /\bpublic\s+Localization\s*\(\s*HardwareMap\s+\w+\s*\)/.test(mask(locSrc));

    // what each setup site effectively applies (a delegate means "whatever the target sets")
    const resolve = s => {
      if (s.hard && !s.delegate) return s;
      if (s.delegate === 'robotconfig') return rc;
      if (s.delegate === 'localization') { const l = byName['Localization.java']; return l && l.delegate === 'robotconfig' ? rc : l; }
      return s;
    };
    const effective = sites.filter(s => s.file !== 'RobotConfig.java' || !sites.some(o => o.delegate === 'robotconfig'))
      .map(s => ({ site: s, cfg: resolve(s) }));
    // distinct configurations actually in use (ignore RobotConfig's own values when nobody calls it)
    const used = effective.filter(e => e.cfg && e.site.file !== 'RobotConfig.java');
    const groups = [];
    for (const e of used) {
      const g = groups.find(gr => sameCfg(gr.cfg, e.cfg));
      if (g) g.files.push(e.site.file); else groups.push({ cfg: e.cfg, files: [e.site.file] });
    }
    const unreadable = sites.filter(s => s.unreadable.length).map(s => s.file);
    const mismatch = groups.length > 1;

    // files (other than RobotConfig) that still carry their own hard-coded copy
    const hardCoded = sites.filter(s => s.file !== 'RobotConfig.java' && s.hard && !s.delegate);
    const needsPrompt = hasRC && hardCoded.length > 0;

    // no RobotConfig: an auto PinpointLocalizer with its own copy that AGREES with Localization can be
    // switched to "new Localization(hardwareMap)" without changing what the robot does
    const pl = byName['PinpointLocalizer.java'], loc = byName['Localization.java'];
    const canSilentFix = !hasRC && !!pl && pl.hard && !pl.delegate && !!loc && loc.hard && !loc.delegate
      && locHasCtor && !pl.unreadable.length && !loc.unreadable.length && sameCfg(pl, loc);

    return { sites, groups, mismatch, unreadable, hardCoded, needsPrompt, canSilentFix, hasRC, rcHasMethod, rc, usedBy: USED_BY };
  }

  const fmtCfg = c => !c ? '(unknown)' :
    (c.offsets ? `offsets ${c.offsets.x.toFixed(1)} / ${c.offsets.y.toFixed(1)} mm` : 'offsets not set')
    + ', directions ' + (c.dirs ? c.dirs.join('/') : 'not set') + ', pods ' + (c.pods || 'not set');

  /* Console-ready warning lines for a package (empty when everything agrees). */
  function warnings(files) {
    const a = analyze(files), out = [];
    if (a.mismatch) {
      out.push('Pinpoint setup DISAGREES between files -- TeleOp and autonomous will think the robot is in different places (real robot only; the sim ignores pod settings):');
      for (const g of a.groups) out.push('   ' + g.files.join(', ') + ': ' + fmtCfg(g.cfg));
      out.push(a.hasRC ? '   Fix: open the "Robot setup" review (banner above the editor) to move them into RobotConfig.java.'
                       : '   Fix: make them match -- set your pod offsets/directions in Localization.java and have PinpointLocalizer use them.');
    } else if (a.needsPrompt) {
      out.push('Pinpoint setup is hard-coded in ' + a.hardCoded.map(s => s.file).join(' and ') + ' instead of coming from RobotConfig.java. They agree today, but an edit to one copy will split them. Review: "Robot setup" banner above the editor.');
    }
    if (a.unreadable.length) out.push('Could not read the Pinpoint setup values in ' + a.unreadable.join(', ') + ' -- check them by hand.');
    return out;
  }

  /* Rewrite a file's hard-coded Pinpoint setup into one delegating call. The whole setup region (from the
   * first setup statement to the last, plus a reset right after it -- the delegate resets too) becomes
   * the call; comment lines INSIDE that region describe the old copy of the settings, so they go too.
   * Comments above the region are kept. Returns the source unchanged if there's nothing to rewrite. */
  function delegate(src, mode) {
    const f = analyzeFile('X.java', src);
    if (!f.calls.length) return src;
    const m = mask(src);
    const recv = f.recv || 'pinpoint';
    const call = mode === 'robotconfig'
      ? 'RobotConfig.configurePinpoint(' + recv + ');   // pod offsets, pod type, directions + reset (HOLD STILL) -- all from RobotConfig.java'
      : 'new Localization(hardwareMap);   // Localization\'s constructor sets the Pinpoint up (HOLD STILL) -- its pod settings are the ONE copy';
    const lineStart = i => src.lastIndexOf('\n', i - 1) + 1;
    const lineEnd = i => { const e = src.indexOf('\n', i); return e < 0 ? src.length : e; };
    const first = f.calls[0], last = f.calls[f.calls.length - 1];
    const lineFirst = lineStart(first.start);
    const indent = /^[ \t]*/.exec(src.slice(lineFirst))[0];
    if (src.slice(lineFirst, first.start).trim() !== '') {
      // something else shares the line: only splice the statements themselves
      let out = src;
      f.calls.slice().reverse().forEach((c, k, arr) => {
        out = out.slice(0, c.start) + (k === arr.length - 1 ? call : '') + out.slice(c.end);
      });
      return out;
    }
    // swallow a reset that follows (only blank/comment lines in between)
    let stmtEnd = last.end;
    const resetRe = new RegExp('^(?:[ \\t\\r]*(?://[^\\n]*)?\\n)*[ \\t]*' + recv + '\\s*\\.\\s*resetPosAndIMU\\s*\\(\\s*\\)\\s*;');
    const afterLine = lineEnd(last.end);
    const r = resetRe.exec(m.slice(afterLine + 1));
    if (r) stmtEnd = afterLine + 1 + r[0].length;
    const regionEnd = lineEnd(stmtEnd);
    const tail = src.slice(stmtEnd, regionEnd);
    const tailIsNoise = /^[ \t]*(\/\/[^\n]*)?\s*$/.test(tail);
    const keepTail = tailIsNoise ? (tail.endsWith('\r') ? '\r' : '') : tail;
    return src.slice(0, lineFirst) + indent + call + keepTail + src.slice(regionEnd);
  }

  /* RobotConfig.java with the chosen values. Updates the existing constants if they're there; otherwise
   * adds the constants and configurePinpoint() at the end of the class. */
  function robotConfigWith(src, cfg) {
    const m = mask(src);
    const fmt = mm => (Math.round(mm * 10) / 10).toFixed(1);
    const inch = mm => (mm / 25.4).toFixed(2);
    let out = src;
    const setConst = (re, value) => {
      const mm = mask(out), r = re.exec(mm);
      if (!r) return false;
      const valStart = r.index + r[0].length - r[2].length - 1, valEnd = r.index + r[0].length - 1;
      out = out.slice(0, valStart) + value + out.slice(valEnd);
      return true;
    };
    const has = /\bPOD_X_OFFSET_MM\s*=/.test(m) && /\bPOD_Y_OFFSET_MM\s*=/.test(m) && /\bstatic\s+void\s+configurePinpoint\s*\(/.test(m);
    if (has) {
      setConst(/(\bPOD_X_OFFSET_MM\s*=)([^;]+);/, ' ' + fmt(cfg.offsets.x));
      setConst(/(\bPOD_Y_OFFSET_MM\s*=)([^;]+);/, ' ' + fmt(cfg.offsets.y));
      if (cfg.dirs) {
        setConst(/(\bX_POD_DIRECTION\s*=)([^;]+);/, '\n            GoBildaPinpointDriver.EncoderDirection.' + cfg.dirs[0]);
        setConst(/(\bY_POD_DIRECTION\s*=)([^;]+);/, '\n            GoBildaPinpointDriver.EncoderDirection.' + cfg.dirs[1]);
      }
      if (cfg.pods && !/^custom/.test(cfg.pods)) setConst(/(\bPOD_TYPE\s*=)([^;]+);/, '\n            GoBildaPinpointDriver.GoBildaOdometryPods.' + cfg.pods);
      // note the inch equivalent next to each offset so a tape-measure number is easy to check
      out = out.replace(/(\bPOD_X_OFFSET_MM\s*=\s*-?[\d.]+;)[^\n]*/, `$1   // forward pod: left of center +, right -  (${inch(cfg.offsets.x)} in)`)
               .replace(/(\bPOD_Y_OFFSET_MM\s*=\s*-?[\d.]+;)[^\n]*/, `$1   // strafe pod: forward of center +, back -  (${inch(cfg.offsets.y)} in)`);
      return out;
    }
    const dirs = cfg.dirs || ['FORWARD', 'FORWARD'];
    const pods = cfg.pods && !/^custom/.test(cfg.pods) ? cfg.pods : 'goBILDA_4_BAR_POD';
    const block = [
      '',
      '    // ── PINPOINT SETUP (the ONE copy -- TeleOp and auto both call configurePinpoint) ──',
      '    // Pod offsets from the robot\'s center, in MILLIMETERS (inches x 25.4). The sim ignores these.',
      '    //   POD_X_OFFSET_MM: how far LEFT of center the FORWARD pod is  (left +, right -)',
      '    //   POD_Y_OFFSET_MM: how far FORWARD of center the STRAFE pod is (forward +, back -)',
      '    //   CHECK: spin the robot in place a full turn -- x and y should stay within ~25 mm (1").',
      `    public static final double POD_X_OFFSET_MM = ${fmt(cfg.offsets.x)};   // (${inch(cfg.offsets.x)} in)`,
      `    public static final double POD_Y_OFFSET_MM = ${fmt(cfg.offsets.y)};   // (${inch(cfg.offsets.y)} in)`,
      '    public static final GoBildaPinpointDriver.GoBildaOdometryPods POD_TYPE =',
      '            GoBildaPinpointDriver.GoBildaOdometryPods.' + pods + ';',
      '    // push the robot FORWARD -> X counts UP; push it LEFT -> Y counts UP. Wrong? flip that pod here.',
      '    public static final GoBildaPinpointDriver.EncoderDirection X_POD_DIRECTION =',
      '            GoBildaPinpointDriver.EncoderDirection.' + dirs[0] + ';',
      '    public static final GoBildaPinpointDriver.EncoderDirection Y_POD_DIRECTION =',
      '            GoBildaPinpointDriver.EncoderDirection.' + dirs[1] + ';',
      '',
      '    /** Apply the pod setup to a Pinpoint and zero it. HOLD THE ROBOT STILL (it calibrates the gyro). */',
      '    public static void configurePinpoint(GoBildaPinpointDriver pinpoint) {',
      '        pinpoint.setOffsets(POD_X_OFFSET_MM, POD_Y_OFFSET_MM, DistanceUnit.MM);',
      '        pinpoint.setEncoderResolution(POD_TYPE);',
      '        pinpoint.setEncoderDirections(X_POD_DIRECTION, Y_POD_DIRECTION);',
      '        pinpoint.resetPosAndIMU();',
      '    }',
    ].join('\n');
    const close = m.lastIndexOf('}');
    out = out.slice(0, close).replace(/\s*$/, '\n') + block + '\n' + out.slice(close);
    const imports = ['import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;',
                     'import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;'];
    for (const imp of imports) {
      if (out.includes(imp)) continue;
      const pk = /^package [^;]+;[^\n]*\n/m.exec(out);
      out = pk ? out.slice(0, pk.index + pk[0].length) + '\n' + imp + out.slice(pk.index + pk[0].length) : imp + '\n' + out;
    }
    return out;
  }

  /* Everything the review prompt changes: RobotConfig gets the chosen values; every other file with its
   * own copy calls RobotConfig.configurePinpoint instead. Returns { files: {name: newSrc}, changed: [names] }. */
  function planRobotConfigFix(files, cfg) {
    const a = analyze(files), next = {}, changed = [];
    const rcNew = robotConfigWith(files['RobotConfig.java'], cfg);
    if (rcNew !== files['RobotConfig.java']) { next['RobotConfig.java'] = rcNew; changed.push('RobotConfig.java'); }
    for (const s of a.hardCoded) {
      const v = delegate(files[s.file], 'robotconfig');
      if (v !== files[s.file]) { next[s.file] = v; changed.push(s.file); }
    }
    // an auto PinpointLocalizer that defers to Localization is fine: Localization now calls RobotConfig
    return { files: next, changed };
  }

  /* Which PinpointLocalizer flavor a package should get from Path Capture. */
  function templateMode(files) {
    files = files || {};
    if (typeof files['RobotConfig.java'] === 'string' && /\bstatic\s+void\s+configurePinpoint\s*\(/.test(mask(files['RobotConfig.java']))) return 'robotconfig';
    if (typeof files['Localization.java'] === 'string' && /\bpublic\s+Localization\s*\(\s*HardwareMap\s+\w+\s*\)/.test(mask(files['Localization.java']))) return 'localization';
    return 'standalone';
  }

  const api = { analyze, analyzeFile, warnings, delegate, robotConfigWith, planRobotConfigFix, templateMode, fmtCfg, mask, sameCfg };
  if (typeof module === 'object' && module.exports) module.exports = api; else root.PodConfig = api;
})(typeof window !== 'undefined' ? window : this);
