#!/usr/bin/env python3
"""SIR combined-pass measurement harness (Stage A / Stage E).

Richer than bench.py: per-toolkit initial enumeration with application
readiness separated from SIR processing, warm id AND ref lookups, press
total/non-settle split, list_controls on small and large trees, event-flood
control responsiveness, and representative response byte counts.

GATE: runs the acceptance suite and bus-restart suite FIRST and aborts with
gate_failed=true if either misses its expected total.

Run detached (systemd-run) inside the desktop session. Output: /tmp/bench2.json
"""
import datetime
import hashlib
import json
import os
import re
import statistics
import subprocess
import threading
import time

ROOT = "/root/ui-mcp"
CARGO = os.path.expanduser("~/.cargo/bin/cargo")
RUSTC = os.path.expanduser("~/.cargo/bin/rustc")
BIN = os.path.join(ROOT, "target/release/ui-mcp")
HERE = os.path.join(ROOT, "test")
PRESS_SETTLE_MS = 150.0
out = {"schema": "sir-combined-pass-v1"}


def sh(cmd, cwd=None, timeout=900):
    return subprocess.run(cmd, shell=isinstance(cmd, str), capture_output=True,
                          text=True, cwd=cwd, timeout=timeout)


def stats(samples):
    s = sorted(samples)
    return {"median_ms": round(statistics.median(s), 2),
            "p95_ms": round(s[max(0, int(len(s) * 0.95) - 1)], 2),
            "min_ms": round(s[0], 2), "max_ms": round(s[-1], 2), "n": len(s)}


def env_fix():
    e = dict(os.environ)
    e.update({"GTK_MODULES": "gail:atk-bridge", "NO_AT_BRIDGE": "0",
              "GNOME_ACCESSIBILITY": "1", "QT_LINUX_ACCESSIBILITY_ALWAYS_ON": "1"})
    return e


def spawn(cmd, settle=0.0):
    p = subprocess.Popen(cmd, env=env_fix(), stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL,
                         start_new_session=True)
    if settle:
        time.sleep(settle)
    return p


def kill_exact(*names):
    for n in names:
        subprocess.run(["pkill", "-9", "-x", n], stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL)


class Server:
    def __init__(self):
        self.enum_ts = None
        self.enum_line = None
        self.p = subprocess.Popen([BIN], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=subprocess.PIPE, text=True, bufsize=1)
        self.t0 = time.perf_counter()
        threading.Thread(target=self._drain, daemon=True).start()
        self._id = 0
        self.rpc("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                                "clientInfo": {"name": "bench2", "version": "0"}})
        self.t_init = time.perf_counter()
        self._send({"jsonrpc": "2.0", "method": "notifications/initialized"})

    def _drain(self):
        for line in self.p.stderr:
            if "connected:" in line and self.enum_ts is None:
                self.enum_ts = time.perf_counter()
                self.enum_line = line.strip()

    def _send(self, o):
        self.p.stdin.write(json.dumps(o) + "\n")
        self.p.stdin.flush()

    def rpc(self, method, params=None):
        self._id += 1
        self._send({"jsonrpc": "2.0", "id": self._id, "method": method,
                    "params": params or {}})
        line = self.p.stdout.readline()
        if not line:
            raise RuntimeError(f"server EOF during {method}")
        return json.loads(line)

    def call(self, tool, args):
        r = self.rpc("tools/call", {"name": tool, "arguments": args})
        text = r["result"]["content"][0]["text"]
        return json.loads(text), len(text.encode()), r["result"].get("isError", False)

    def timed(self, tool, args, n):
        samples, last = [], None
        for _ in range(n):
            t = time.perf_counter()
            last = self.call(tool, args)
            samples.append((time.perf_counter() - t) * 1000)
        return samples, last

    def rss(self):
        s = open(f"/proc/{self.p.pid}/status").read()
        return (int(re.search(r"VmRSS:\s+(\d+)", s).group(1)),
                int(re.search(r"VmHWM:\s+(\d+)", s).group(1)))

    def close(self):
        try:
            self.p.stdin.close()
            self.p.wait(timeout=5)
        except Exception:
            self.p.kill()


def fresh_startup_samples(n):
    """Spawn n fresh servers; return (startup_ms[], enum_ms[], enum_lines)."""
    su, en, lines = [], [], []
    for _ in range(n):
        s = Server()
        su.append((s.t_init - s.t0) * 1000)
        deadline = time.time() + 5
        while s.enum_ts is None and time.time() < deadline:
            time.sleep(0.01)
        if s.enum_ts:
            en.append((s.enum_ts - s.t0) * 1000)
            lines.append(s.enum_line)
        s.close()
    return su, en, lines


# ---------------- provenance ----------------
out["date_utc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
out["kernel"] = sh("uname -sr").stdout.strip()
out["rustc"] = sh([RUSTC, "--version"]).stdout.strip()
out["cargo"] = sh([CARGO, "--version"]).stdout.strip()
out["cpus"] = int(sh("nproc").stdout.strip())
files = [os.path.join(ROOT, "Cargo.toml")]
for d, _, fns in os.walk(os.path.join(ROOT, "src")):
    files += [os.path.join(d, f) for f in fns]
h = hashlib.sha256()
for p in sorted(files):
    h.update(open(p, "rb").read())
out["source_sha256"] = h.hexdigest()
out["src_lines"] = int(sh("cat src/*.rs | wc -l", cwd=ROOT).stdout.strip())

# ---------------- GATE part 1: acceptance suite ----------------
# bus_restart runs LAST: it kills the session's AT-SPI registry, which would
# poison every measurement that follows it.
kill_exact("firefox-esr", "chromium", "electron")
r = sh(["python3", os.path.join(HERE, "suite.py"),
        "gtk", "qt", "chromium", "electron", "firefox", "core", "restart"],
       cwd=ROOT, timeout=900)
m = re.search(r"(\d+)/(\d+) checks passed", r.stdout)
out["acceptance_checks"] = m.group(0) if m else f"PARSE FAIL rc={r.returncode}"
if not (m and m.group(1) == m.group(2) == "34"):
    out["gate_failed"] = True
    json.dump(out, open("/tmp/bench2.json", "w"), indent=2, sort_keys=True)
    print("GATE FAILED (acceptance)")
    raise SystemExit(1)

# ---------------- build & deps ----------------
tree = sh([CARGO, "tree", "-e", "normal", "--prefix", "none"], cwd=ROOT).stdout
out["runtime_dependency_crates"] = len({l.strip().split(" (")[0]
                                        for l in tree.splitlines() if l.strip()}) - 1
dup = sh([CARGO, "tree", "-d", "-e", "normal", "--prefix", "none"], cwd=ROOT).stdout
out["duplicate_dependency_versions"] = sorted(
    {l.strip().split(" (")[0] for l in dup.splitlines() if l.strip()})
sh([CARGO, "clean"], cwd=ROOT)
t0 = time.time()
r = sh([CARGO, "build", "--release"], cwd=ROOT)
out["clean_release_build_seconds"] = round(time.time() - t0, 1)
out["build_ok"] = r.returncode == 0
out["binary_bytes"] = os.path.getsize(BIN)

# ---------------- startup + GTK enumeration (small tree) ----------------
kill_exact("firefox-esr", "chromium", "electron")
subprocess.run(["pkill", "-9", "-f", "test/test-app.py"])
subprocess.run(["pkill", "-9", "-f", "test/qt-test-app.py"])
time.sleep(1)
gtk = spawn(["python3", os.path.join(HERE, "test-app.py")], settle=3)

# Wait until the fixture is actually on the a11y bus before measuring.
probe = Server()
r, _, err = probe.call("ui_wait_for",
                       {"query": {"app": "test-app", "id": "save-project"},
                        "timeout_ms": 20000})
probe.close()
if err:
    out["fixture_error"] = f"GTK fixture never registered: {r}"
    json.dump(out, open("/tmp/bench2.json", "w"), indent=2, sort_keys=True)
    raise SystemExit(1)

su, en, lines = fresh_startup_samples(5)
out["startup_to_initialize"] = stats(su)
out["initial_enumeration_gtk"] = stats(en)
out["initial_enumeration_gtk_line"] = lines[-1] if lines else None

# ---------------- warm lookups on persistent server (small cache) ----------
srv = Server()
time.sleep(1)
find_s, (payload, find_bytes, _) = srv.timed(
    "ui_find", {"app": "test-app", "id": "save-project"}, 30)
out["warm_find_by_id_small"] = stats(find_s)
out["resp_bytes_find"] = find_bytes
if not payload.get("matches"):
    out["fixture_error"] = f"warm find empty: {payload}"
    json.dump(out, open("/tmp/bench2.json", "w"), indent=2, sort_keys=True)
    raise SystemExit(1)
ref = payload["matches"][0]["ref"]
read_s, (_, read_bytes, _) = srv.timed("ui_read", {"target": {"ref": ref}}, 30)
out["warm_read_by_ref"] = stats(read_s)
out["resp_bytes_read"] = read_bytes
lc_s, (_, lc_bytes, _) = srv.timed("ui_list_controls", {}, 20)
out["list_controls_small"] = stats(lc_s)
out["resp_bytes_list_controls_small"] = lc_bytes
press_s, (_, press_bytes, _) = srv.timed(
    "ui_press", {"target": {"app": "test-app", "id": "save-project"}}, 10)
out["press_total"] = stats(press_s)
out["press_non_settle"] = stats([x - PRESS_SETTLE_MS for x in press_s])
out["resp_bytes_press"] = press_bytes
idle, peak = srv.rss()
out["idle_rss_kb"] = idle

# ---------------- event flood: control latency while Chromium loads -------
quiet, _ = srv.timed("ui_find", {"app": "test-app", "id": "save-project"}, 10)
flood_samples = []
stop_flag = {"stop": False}


def sampler():
    while not stop_flag["stop"]:
        t = time.perf_counter()
        try:
            srv_lock.acquire()
            srv.call("ui_find", {"app": "test-app", "id": "save-project"})
        finally:
            srv_lock.release()
        flood_samples.append((time.perf_counter() - t) * 1000)
        time.sleep(0.1)


srv_lock = threading.Lock()
th = threading.Thread(target=sampler, daemon=True)
th.start()
page = "file://" + os.path.join(HERE, "chromium-test.html")
cr = spawn(["chromium", "--no-sandbox", "--disable-gpu", "--no-first-run",
            "--force-renderer-accessibility", page])
time.sleep(15)
stop_flag["stop"] = True
th.join(timeout=10)
out["flood_quiet_baseline"] = stats(quiet)
out["flood_control_latency_during_chromium_load"] = stats(flood_samples)

# ---------------- large-tree measurements (Chromium ready) ----------------
with srv_lock:
    r, _, err = srv.call("ui_wait_for",
                         {"query": {"app": "Chromium", "id": "save-project"},
                          "timeout_ms": 30000})
out["chromium_ready"] = (not err)
lc2, (_, lc2_bytes, _) = srv.timed("ui_list_controls", {}, 10)
out["list_controls_large"] = stats(lc2)
out["resp_bytes_list_controls_large"] = lc2_bytes
find2, _ = srv.timed("ui_find", {"app": "test-app", "id": "save-project"}, 30)
out["warm_find_by_id_with_large_cache"] = stats(find2)
_, peak = srv.rss()
out["peak_rss_kb"] = peak
srv.close()

# fresh-server enumeration with the big Chromium tree present (app is ready,
# so this isolates SIR walk time from browser startup)
su, en, lines = fresh_startup_samples(3)
out["initial_enumeration_chromium"] = stats(en)
out["initial_enumeration_chromium_line"] = lines[-1] if lines else None
kill_exact("chromium")

# ---------------- Firefox enumeration ----------------
prof = "/tmp/ff-bench"
subprocess.run(["rm", "-rf", prof])
os.makedirs(prof, exist_ok=True)
open(os.path.join(prof, "user.js"), "w").write(
    'user_pref("accessibility.force_disabled", 0);\n'
    'user_pref("browser.shell.checkDefaultBrowser", false);\n'
    'user_pref("browser.aboutwelcome.enabled", false);\n')
ff = spawn(["firefox", "--profile", prof, "--new-window", page])
probe = Server()
r, _, err = probe.call("ui_wait_for",
                       {"query": {"app": "Firefox", "role": "button", "name": "Save"},
                        "timeout_ms": 60000})
probe.close()
out["firefox_ready"] = (not err)
if not err:
    su, en, lines = fresh_startup_samples(3)
    out["initial_enumeration_firefox"] = stats(en)
    out["initial_enumeration_firefox_line"] = lines[-1] if lines else None
kill_exact("firefox-esr")
gtk.terminate()

# ---------------- GATE part 2: bus restart (disruptive; last) ----------------
r2 = sh(["python3", os.path.join(HERE, "bus_restart.py")], cwd=ROOT, timeout=300)
m2 = re.search(r"(\d+)/(\d+) checks passed", r2.stdout)
out["bus_restart_checks"] = m2.group(0) if m2 else f"PARSE FAIL rc={r2.returncode}"
out["gate_ok"] = bool(m2 and m2.group(1) == m2.group(2) == "4")

json.dump(out, open("/tmp/bench2.json", "w"), indent=2, sort_keys=True)
print("BENCH2_DONE")
