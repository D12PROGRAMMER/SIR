#!/usr/bin/env python3
"""SIR baseline measurement harness.

Run inside the desktop session (systemd-run with DISPLAY/DBUS env) in the VM.
Produces /tmp/baseline.json. Order matters: the clean build first (no session
needed), then the acceptance suite, then latency/memory against a quiet
session, then the disruptive bus-restart test LAST.
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
out = {"schema": "sir-baseline-v1"}


def sh(cmd, cwd=None, timeout=900):
    return subprocess.run(cmd, shell=isinstance(cmd, str), capture_output=True,
                          text=True, cwd=cwd, timeout=timeout)


def ms(samples):
    return {
        "median_ms": round(statistics.median(samples), 2),
        "p95_ms": round(sorted(samples)[max(0, int(len(samples) * 0.95) - 1)], 2),
        "min_ms": round(min(samples), 2),
        "max_ms": round(max(samples), 2),
        "n": len(samples),
    }


# ---- environment ----
out["date_utc"] = datetime.datetime.now(datetime.timezone.utc).isoformat()
out["kernel"] = sh("uname -sr").stdout.strip()
out["rustc"] = sh([RUSTC, "--version"]).stdout.strip()
out["cargo"] = sh([CARGO, "--version"]).stdout.strip()
out["cpus"] = int(sh("nproc").stdout.strip())
out["mem_total_mb"] = int(sh("awk '/MemTotal/{print int($2/1024)}' /proc/meminfo").stdout.strip())
out["build_mode"] = "release"

# ---- source snapshot hash ----
files = [os.path.join(ROOT, "Cargo.toml")]
for d, _, fns in os.walk(os.path.join(ROOT, "src")):
    files += [os.path.join(d, f) for f in fns]
files.sort()
h = hashlib.sha256()
per_file = {}
for p in files:
    b = open(p, "rb").read()
    h.update(b)
    per_file[os.path.relpath(p, ROOT)] = hashlib.sha256(b).hexdigest()[:16]
out["source_sha256"] = h.hexdigest()
out["source_files"] = per_file

# ---- cargo metadata: deps + features ----
tree = sh([CARGO, "tree", "-e", "normal", "--prefix", "none"], cwd=ROOT).stdout
crates = {l.strip().split(" (")[0] for l in tree.splitlines() if l.strip()}
out["runtime_dependency_crates"] = len(crates) - 1  # exclude the root crate
toml = open(os.path.join(ROOT, "Cargo.toml")).read()
out["cargo_dependencies_section"] = toml.split("[dependencies]", 1)[1].strip()

# ---- clean release build duration + binary size ----
sh([CARGO, "clean"], cwd=ROOT)
t0 = time.time()
r = sh([CARGO, "build", "--release"], cwd=ROOT)
out["clean_release_build_seconds"] = round(time.time() - t0, 1)
out["build_ok"] = r.returncode == 0
out["binary_bytes"] = os.path.getsize(BIN)
out["dynamic_libs"] = len([l for l in sh(["ldd", BIN]).stdout.splitlines() if "=>" in l])

# ---- acceptance suite ----
t0 = time.time()
r = sh(["python3", os.path.join(ROOT, "test/suite.py"),
        "gtk", "qt", "chromium", "electron", "firefox", "core", "restart"],
       cwd=ROOT, timeout=900)
out["acceptance_suite_seconds"] = round(time.time() - t0, 1)
m = re.search(r"(\d+)/(\d+) checks passed", r.stdout)
out["acceptance_checks"] = m.group(0) if m else f"PARSE FAIL rc={r.returncode}"
out["acceptance_pass"] = bool(m) and m.group(1) == m.group(2)

# ---- runtime latency + memory (fresh GTK fixture, fresh server) ----
env = dict(os.environ, GTK_MODULES="gail:atk-bridge", NO_AT_BRIDGE="0",
           GNOME_ACCESSIBILITY="1")
app = subprocess.Popen(["python3", os.path.join(ROOT, "test/test-app.py")],
                       env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                       start_new_session=True)
time.sleep(3)

stderr_ts = {}
proc = subprocess.Popen([BIN], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                        stderr=subprocess.PIPE, text=True, bufsize=1)
t_spawn = time.perf_counter()


def drain_stderr():
    for line in proc.stderr:
        if "connected:" in line and "enum" not in stderr_ts:
            stderr_ts["enum"] = time.perf_counter()
            stderr_ts["enum_line"] = line.strip()


threading.Thread(target=drain_stderr, daemon=True).start()

_id = 0


def rpc(method, params=None):
    global _id
    _id += 1
    proc.stdin.write(json.dumps({"jsonrpc": "2.0", "id": _id, "method": method,
                                 "params": params or {}}) + "\n")
    proc.stdin.flush()
    return json.loads(proc.stdout.readline())


def call(tool, args):
    return rpc("tools/call", {"name": tool, "arguments": args})


r = rpc("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                       "clientInfo": {"name": "bench", "version": "0"}})
t_init = time.perf_counter()
out["startup_to_initialize_ms"] = round((t_init - t_spawn) * 1000, 1)
if "enum" in stderr_ts:
    out["initial_enumeration_ms"] = round((stderr_ts["enum"] - t_spawn) * 1000, 1)
    out["initial_enumeration_line"] = stderr_ts["enum_line"]
proc.stdin.write(json.dumps({"jsonrpc": "2.0",
                             "method": "notifications/initialized"}) + "\n")
proc.stdin.flush()

time.sleep(2)  # idle settle
status = open(f"/proc/{proc.pid}/status").read()
out["idle_rss_kb"] = int(re.search(r"VmRSS:\s+(\d+)", status).group(1))

# MCP request latency: protocol-only round trip (ping)
samples = []
for _ in range(30):
    t = time.perf_counter()
    rpc("ping")
    samples.append((time.perf_counter() - t) * 1000)
out["mcp_request_latency"] = ms(samples)

# Cached lookup latency: ui_find by id against warm cache
samples = []
for _ in range(30):
    t = time.perf_counter()
    call("ui_find", {"app": "test-app", "id": "save-project"})
    samples.append((time.perf_counter() - t) * 1000)
out["cached_lookup_latency"] = ms(samples)

# AT-SPI action latency: ui_press (includes the server's 150ms settle sleep)
samples = []
for _ in range(10):
    t = time.perf_counter()
    call("ui_press", {"target": {"app": "test-app", "id": "save-project"}})
    samples.append((time.perf_counter() - t) * 1000)
out["press_latency_note"] = "includes intentional 150ms post-action settle in press()"
out["atspi_action_latency"] = ms(samples)

status = open(f"/proc/{proc.pid}/status").read()
out["peak_rss_kb"] = int(re.search(r"VmHWM:\s+(\d+)", status).group(1))

proc.stdin.close()
app.terminate()

# ---- bus restart test (disruptive; last) ----
t0 = time.time()
r = sh(["python3", os.path.join(ROOT, "test/bus_restart.py")], cwd=ROOT, timeout=300)
out["bus_restart_seconds"] = round(time.time() - t0, 1)
m = re.search(r"(\d+)/(\d+) checks passed", r.stdout)
out["bus_restart_checks"] = m.group(0) if m else f"PARSE FAIL rc={r.returncode}"
out["bus_restart_pass"] = bool(m) and m.group(1) == m.group(2)

with open("/tmp/baseline.json", "w") as f:
    json.dump(out, f, indent=2, sort_keys=True)
print("BENCH_DONE")
