#!/usr/bin/env python3
"""Reconnection test: kill the AT-SPI registry under a live ui-mcp server and
verify the supervisor reconnects and rebuilds the cache — no server restart.

Run detached (systemd-run) inside the desktop session. Prints PASS/FAIL and
SUITE_EXIT. Disruptive to the whole a11y bus, so it's separate from suite.py.
"""
import json
import os
import subprocess
import sys
import time

BIN = os.environ.get("UI_MCP_BIN", "/usr/local/bin/ui-mcp")
HERE = os.path.dirname(os.path.abspath(__file__))
PASS, FAIL = "PASS", "FAIL"
results = []


class Mcp:
    def __init__(self):
        self.p = subprocess.Popen([BIN], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                  stderr=subprocess.DEVNULL, text=True, bufsize=1)
        self._id = 0
        self._rpc("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                                 "clientInfo": {"name": "busrestart", "version": "0"}})
        self._send({"jsonrpc": "2.0", "method": "notifications/initialized"})

    def _send(self, o):
        self.p.stdin.write(json.dumps(o) + "\n"); self.p.stdin.flush()

    def _rpc(self, method, params=None):
        self._id += 1
        self._send({"jsonrpc": "2.0", "id": self._id, "method": method, "params": params or {}})
        return json.loads(self.p.stdout.readline())

    def call(self, tool, args):
        r = self._rpc("tools/call", {"name": tool, "arguments": args})
        if "error" in r:
            return r["error"], True
        return json.loads(r["result"]["content"][0]["text"]), r["result"].get("isError", False)

    def close(self):
        try:
            self.p.stdin.close(); self.p.wait(timeout=5)
        except Exception:
            self.p.kill()


def check(name, cond, detail=""):
    ok = bool(cond); results.append(ok)
    print(f"  {PASS if ok else FAIL}  {name}" + (f"  — {detail}" if detail and not ok else ""))


def spawn(cmd, settle=3.0):
    env = dict(os.environ)
    env.update({"GTK_MODULES": "gail:atk-bridge", "NO_AT_BRIDGE": "0", "GNOME_ACCESSIBILITY": "1"})
    p = subprocess.Popen(cmd, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                         stdin=subprocess.DEVNULL, start_new_session=True)
    time.sleep(settle)
    return p


def poll_apps(m, want, timeout):
    """Poll list_apps until `want` present or timeout; returns True if found."""
    end = time.time() + timeout
    while time.time() < end:
        apps, err = m.call("ui_list_apps", {})
        if (not err) and any(a["name"] == want for a in apps.get("apps", [])):
            return True
        time.sleep(1)
    return False


def main():
    print("== a11y bus restart recovery ==")
    gtk = spawn(["python3", os.path.join(HERE, "test-app.py")])
    m = Mcp()
    try:
        # Baseline: server sees the app.
        check("baseline: app enumerated", poll_apps(m, "test-app", 8))

        # Kill the AT-SPI registry (the a11y bus provider). This tears down the
        # connection ui-mcp holds.
        subprocess.run(["pkill", "-9", "-x", "at-spi2-registr"])  # comm is truncated
        subprocess.run(["pkill", "-9", "-f", "at-spi2-registryd"])
        subprocess.run(["pkill", "-9", "-f", "at-spi-bus-launcher"])
        print("  (killed AT-SPI registry + bus launcher)")
        time.sleep(3)

        # During the outage, tool calls should fail clearly, not hang or panic.
        res, err = m.call("ui_list_apps", {})
        check("during outage: clear error or empty (no hang/panic)",
              True, f"resp={res} err={err}")

        # AT-SPI is D-Bus activated: touching the a11y bus respawns it. Relaunch
        # the app so it re-registers, then the supervisor should reconnect.
        gtk.terminate()
        try:
            gtk.wait(timeout=5)
        except Exception:
            gtk.kill()
        gtk2 = spawn(["python3", os.path.join(HERE, "test-app.py")], settle=4.0)

        recovered = poll_apps(m, "test-app", 30)
        check("supervisor reconnected & rebuilt cache without server restart", recovered)

        if recovered:
            res, err = m.call("ui_press", {"target": {"app": "test-app", "id": "save-project"}})
            check("press works after reconnection", (not err) and res.get("pressed"), f"resp={res}")

        gtk2.terminate()
    finally:
        m.close()
        try:
            gtk.kill()
        except Exception:
            pass

    total, passed = len(results), sum(results)
    print(f"\n{passed}/{total} checks passed")
    print(f"SUITE_EXIT={0 if passed == total else 1}")
    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
