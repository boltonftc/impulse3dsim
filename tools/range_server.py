"""Minimal static file server WITH HTTP Range support.

Python's stdlib http.server does not honor the Range header, which CheerpJ
requires to fetch classpath jars (e.g. ecj.jar). GitHub Pages supports Range
in production; this script provides the same for local spike testing.
"""
import http.server
import os
import re
import socketserver
import urllib.request
import urllib.error

PORT = 8972

# Serve the publishable site root (web/), one level up from this tools/ dir.
WEB_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "web")

# Reverse-proxy the CheerpJ CDN so its runtime (loader.js, c.html, c.js, jars)
# is served same-origin. This avoids cross-origin iframe/COEP problems and lets
# automated browsers that abort third-party document loads run the JVM.
CJ_PREFIX = "/cj/"
CJ_ORIGIN = "https://cjrtnc.leaningtech.com/4.3/"


class RangeRequestHandler(http.server.SimpleHTTPRequestHandler):
    # Toggle cross-origin isolation for CheerpJ SharedArrayBuffer experiments.
    COI = True

    def end_headers(self):
        if RangeRequestHandler.COI:
            self.send_header("Cross-Origin-Opener-Policy", "same-origin")
            self.send_header("Cross-Origin-Embedder-Policy", "require-corp")
        super().end_headers()

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
            self._proxy_cj()
            return
        super().do_GET()

    def do_HEAD(self):
        if self.path.startswith(CJ_PREFIX):
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
