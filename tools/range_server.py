"""Minimal static file server WITH HTTP Range support.

Python's stdlib http.server does not honor the Range header, which CheerpJ
requires to fetch classpath jars (e.g. ecj.jar). GitHub Pages supports Range
in production; this script provides the same for local spike testing.
"""
import http.server
import os
import re
import socketserver
import sys
import threading
import urllib.request
import urllib.error

PORT = 8972

# Serve the publishable site root (web/), one level up from this tools/ dir.
WEB_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "web")
WEB_ROOT_ABS = os.path.abspath(WEB_ROOT)

# Reverse-proxy the CheerpJ CDN so its runtime (loader.js, c.html, c.js, jars)
# is served same-origin. This avoids cross-origin iframe/COEP problems and lets
# automated browsers that abort third-party document loads run the JVM.
#
# CACHING MIRROR: on a cache miss we download the FULL upstream file into
# web/cj/<path> and thereafter serve it from disk (with Range support). Warming
# up the app (compile + run) thus builds a complete, committable self-hosted
# runtime under web/cj/ so the published GitHub Pages build can work offline.
CJ_PREFIX = "/cj/"
CJ_ORIGIN = "https://cjrtnc.leaningtech.com/4.3/"
_cj_locks = {}
_cj_locks_guard = threading.Lock()


def _cj_lock(rel):
    with _cj_locks_guard:
        lk = _cj_locks.get(rel)
        if lk is None:
            lk = _cj_locks[rel] = threading.Lock()
        return lk


class RangeRequestHandler(http.server.SimpleHTTPRequestHandler):
    # Toggle cross-origin isolation for CheerpJ SharedArrayBuffer experiments.
    COI = True

    def end_headers(self):
        if RangeRequestHandler.COI:
            self.send_header("Cross-Origin-Opener-Policy", "same-origin")
            self.send_header("Cross-Origin-Embedder-Policy", "require-corp")
        super().end_headers()

    def _cj_rel(self):
        rel = self.path[len(CJ_PREFIX):].split("?", 1)[0].split("#", 1)[0]
        parts = [p for p in rel.split("/") if p not in ("", ".", "..")]
        return "/".join(parts)

    def _cj_local_path(self):
        rel = self._cj_rel()
        if not rel:
            return None
        return os.path.join(WEB_ROOT_ABS, "cj", *rel.split("/"))

    def _ensure_cj_cached(self):
        """Download the full upstream file into web/cj/ if we don't have it yet.

        Returns True when a local mirror file exists afterward (so the normal
        static handler can serve it with Range support); False for anything not
        worth mirroring (upstream 404 probes, dirs, network errors) -- those are
        proxied straight through instead.
        """
        local = self._cj_local_path()
        if not local:
            return False
        if os.path.isfile(local):
            return True
        rel = self._cj_rel()
        with _cj_lock(rel):
            if os.path.isfile(local):
                return True
            upstream = CJ_ORIGIN + rel
            try:
                resp = urllib.request.urlopen(urllib.request.Request(upstream), timeout=60)
                if resp.status != 200:
                    return False
                data = resp.read()
            except Exception as e:
                # Upstream unreachable or the pinned CheerpJ revision moved: warn and
                # continue. Production never hits the CDN (web/cj/ is committed), so this
                # only affects a developer re-mirroring a not-yet-cached file.
                sys.stderr.write(f"[cj-mirror] WARN upstream fetch failed for {rel}: {e}\n")
                return False
            os.makedirs(os.path.dirname(local), exist_ok=True)
            tmp = local + ".part"
            with open(tmp, "wb") as fh:
                fh.write(data)
            os.replace(tmp, local)
            print(f"[cj-mirror] {rel} ({len(data):,} bytes)")
            return True

    def _proxy_cj(self):
        upstream = CJ_ORIGIN + self.path[len(CJ_PREFIX):]
        req = urllib.request.Request(upstream)
        rng = self.headers.get("Range")
        if rng:
            req.add_header("Range", rng)
        try:
            resp = urllib.request.urlopen(req, timeout=30)
        except urllib.error.HTTPError as e:
            resp = e
        except Exception as e:
            self.send_error(502, f"proxy error: {e}")
            return
        self.send_response(resp.status)
        for h in ("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges"):
            v = resp.headers.get(h)
            if v is not None:
                self.send_header(h, v)
        self.end_headers()
        while True:
            chunk = resp.read(64 * 1024)
            if not chunk:
                break
            try:
                self.wfile.write(chunk)
            except (BrokenPipeError, ConnectionResetError):
                break

    def do_GET(self):
        if self.path.startswith(CJ_PREFIX):
            if self._ensure_cj_cached():
                super().do_GET()  # serve the mirrored web/cj/ file (Range-capable)
                return
            self._proxy_cj()
            return
        super().do_GET()

    def do_HEAD(self):
        if self.path.startswith(CJ_PREFIX):
            if self._ensure_cj_cached():
                super().do_HEAD()
                return
            self._proxy_cj()
            return
        super().do_HEAD()

    def send_head(self):
        path = self.translate_path(self.path)
        if os.path.isdir(path):
            return super().send_head()
        if not os.path.exists(path):
            self.send_error(404, "File not found")
            return None

        ctype = self.guess_type(path)
        fs = os.stat(path)
        size = fs.st_size
        rng = self.headers.get("Range")

        f = open(path, "rb")
        if rng is None:
            self.send_response(200)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(size))
            self.send_header("Accept-Ranges", "bytes")
            self.end_headers()
            return f

        m = re.match(r"bytes=(\d*)-(\d*)", rng)
        start_s, end_s = m.group(1), m.group(2)
        if start_s == "":
            length = int(end_s)
            start = max(0, size - length)
            end = size - 1
        else:
            start = int(start_s)
            end = int(end_s) if end_s else size - 1
        end = min(end, size - 1)
        length = end - start + 1

        self.send_response(206)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.send_header("Content-Length", str(length))
        self.send_header("Accept-Ranges", "bytes")
        self.end_headers()
        f.seek(start)
        self._range_length = length
        return f

    def copyfile(self, source, outputfile):
        length = getattr(self, "_range_length", None)
        if length is None:
            return super().copyfile(source, outputfile)
        remaining = length
        while remaining > 0:
            chunk = source.read(min(64 * 1024, remaining))
            if not chunk:
                break
            outputfile.write(chunk)
            remaining -= len(chunk)


class ThreadingHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True


if __name__ == "__main__":
    os.chdir(os.path.abspath(WEB_ROOT))
    with ThreadingHTTPServer(("", PORT), RangeRequestHandler) as httpd:
        print(f"Range-capable server on http://localhost:{PORT}")
        httpd.serve_forever()
