#!/usr/bin/env python3
"""ui-mcp cross-toolkit acceptance suite.

Runs INSIDE the desktop session (needs DISPLAY + DBUS_SESSION_BUS_ADDRESS from
/root/.desktop-env). Speaks MCP JSON-RPC to a fresh `ui-mcp` server per case
group over a stdio pipe, and drives real apps (GTK, Qt, Firefox, Electron,
Chromium) already running in the session.

Usage:
  source /root/.desktop-env
  python3 test/suite.py [gtk qt chromium electron firefox core]
Exit code 0 iff every selected case passes.
"""
import json
import os
import subprocess
import sys
import time

BIN = os.environ.get("UI_MCP_BIN", "/usr/local/bin/ui-mcp")
HERE = os.path.dirname(os.path.abspath(__file__))
PASS, FAIL = "\033[32mPASS\033[0m", "\033[31mFAIL\033[0m"

results = []


class Mcp:
    """One ui-mcp server process; newline-delimited JSON-RPC over stdio."""

    def __init__(self):
        self.p = subprocess.Popen(
            [BIN], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL, text=True, bufsize=1,
        )
        self._id = 0
        self._rpc("initialize", {
            "protocolVersion": "2025-06-18", "capabilities": {},
            "clientInfo": {"name": "suite", "version": "0"},
        })
        self._notify("notifications/initialized")

    def _send(self, obj):
        self.p.stdin.write(json.dumps(obj) + "\n")
        self.p.stdin.flush()

    def _rpc(self, method, params=None):
        self._id += 1
        self._send({"jsonrpc": "2.0", "id": self._id, "method": method,
                    "params": params or {}})
        line = self.p.stdout.readline()
        return json.loads(line)

    def _notify(self, method, params=None):
        self._send({"jsonrpc": "2.0", "method": method, "params": params or {}})

    def call(self, tool, args):
        """Return (payload_dict, is_error)."""
        resp = self._rpc("tools/call", {"name": tool, "arguments": args})
        if "error" in resp:  # protocol-level error
            return resp["error"], True
        content = resp["result"]["content"][0]["text"]
        return json.loads(content), resp["result"].get("isError", False)

    def raw(self, line):
        """Send a raw line, return the parsed response (for protocol tests)."""
        self.p.stdin.write(line + "\n")
        self.p.stdin.flush()
        return json.loads(self.p.stdout.readline())

    def close(self):
        try:
            self.p.stdin.close()
            self.p.wait(timeout=5)
        except Exception:
            self.p.kill()


def total(resp, key="matches"):
    """Match count: `total` is omitted when it equals len(items)."""
    return resp.get("total", len(resp.get(key, []) or []))


def check(name, cond, detail=""):
    ok = bool(cond)
    results.append(ok)
    print(f"  {PASS if ok else FAIL}  {name}" + (f"  — {detail}" if detail and not ok else ""))
    return ok


def spawn_app(cmd, env_extra=None, settle=3.0):
    env = dict(os.environ)
    env.update({
        "GTK_MODULES": "gail:atk-bridge", "NO_AT_BRIDGE": "0",
        "GNOME_ACCESSIBILITY": "1", "QT_LINUX_ACCESSIBILITY_ALWAYS_ON": "1",
    })
    if env_extra:
        env.update(env_extra)
    p = subprocess.Popen(cmd, env=env, stdout=subprocess.DEVNULL,
                         stderr=subprocess.DEVNULL)
    time.sleep(settle)
    return p


def kill(p):
    if p is None:
        return
    try:
        p.terminate()
        p.wait(timeout=5)
    except Exception:
        try:
            p.kill()
        except Exception:
            pass


# ---------------- per-toolkit capability battery ----------------

def battery(app_name, ids, m, want_dynamic=False, proc=None):
    """Common checks parameterized by the app's fixture ids."""
    apps, _ = m.call("ui_list_apps", {})
    check(f"[{app_name}] app enumerated automatically",
          any(a["name"] == app_name for a in apps["apps"]),
          f"apps={[a['name'] for a in apps['apps']]}")

    found, err = m.call("ui_find", {"app": app_name, "id": ids["save"]})
    save_ok = (not err) and total(found) >= 1
    check(f"[{app_name}] find Save by id", save_ok,
          f"resp={found}")
    ref = found["matches"][0]["ref"] if save_ok else None

    # action names normalized & non-empty
    if save_ok:
        acts = found["matches"][0].get("actions", [])
        check(f"[{app_name}] action names reported & normalized",
              acts and all(a == a.lower() and a for a in acts), f"actions={acts}")

    # press by id -> observable state change (title or flag)
    flag = ids.get("flag")
    if flag and os.path.exists(flag):
        os.remove(flag)
    res, err = m.call("ui_press", {"target": {"app": app_name, "id": ids["save"]}})
    press_ok = (not err) and res.get("pressed")
    check(f"[{app_name}] press Save by id", press_ok, f"resp={res}")
    if flag:
        time.sleep(0.8)
        check(f"[{app_name}] press caused observable side effect",
              os.path.exists(flag), f"flag {flag} missing")

    # set_value on the text field
    if ids.get("text"):
        res, err = m.call("ui_set_value",
                          {"target": {"app": app_name, "id": ids["text"]},
                           "value": "typed.txt"})
        check(f"[{app_name}] set_value on text field", (not err) and res.get("set"),
              f"resp={res}")

    # stale ref: reuse a resolved ref after removing target (dynamic apps only)
    if want_dynamic and ref:
        pass  # dynamic handled separately below

    return ref


def run_gtk(m):
    print("== GTK3 ==")
    p = spawn_app(["python3", os.path.join(HERE, "test-app.py")])
    try:
        _run_gtk(m)
    finally:
        kill(p)


def _run_gtk(m):
    ids = {"save": "save-project", "text": "filename", "flag": "/tmp/save-pressed"}
    battery("test-app", ids, m)

    # Ambiguity: two "Copy" buttons, no ids.
    res, err = m.call("ui_press", {"target": {"role": "button", "name": "Copy"}})
    check("[GTK] duplicate controls -> ambiguous",
          err and res.get("error") == "ambiguous" and len(res.get("candidates", [])) == 2,
          f"resp={res}")
    # Disambiguate via a returned candidate ref.
    if err and res.get("error") == "ambiguous":
        cand = res["candidates"][0]["ref"]
        r2, e2 = m.call("ui_press", {"target": {"ref": cand}})
        check("[GTK] disambiguation by ref succeeds", (not e2) and r2.get("pressed"),
              f"resp={r2}")

    # Disabled control
    res, err = m.call("ui_press", {"target": {"app": "test-app", "id": "locked"}})
    check("[GTK] disabled control -> not_actionable",
          err and res.get("error") == "not_actionable", f"resp={res}")

    # Unknown id
    res, err = m.call("ui_press", {"target": {"app": "test-app", "id": "nope"}})
    check("[GTK] unknown id -> not_found",
          err and res.get("error") == "not_found", f"resp={res}")

    # --- event-driven appear/disappear + stale ref ---
    m.call("ui_press", {"target": {"app": "test-app", "id": "spawn"}})
    res, err = m.call("ui_wait_for",
                     {"query": {"app": "test-app", "id": "dynamic-1"}, "timeout_ms": 6000})
    appeared = (not err) and res.get("found")
    check("[GTK] dynamically added widget found via events", appeared, f"resp={res}")
    dyn_ref = res["found"]["ref"] if appeared else None

    m.call("ui_press", {"target": {"app": "test-app", "id": "despawn"}})
    time.sleep(1.5)
    if dyn_ref:
        res, err = m.call("ui_read", {"target": {"ref": dyn_ref}})
        check("[GTK] removed widget ref -> stale_target",
              err and res.get("error") == "stale_target", f"resp={res}")
    res, err = m.call("ui_find", {"app": "test-app", "id": "dynamic-1"})
    check("[GTK] removed widget no longer found",
          (not err) and total(res) == 0, f"resp={res}")


def run_qt(m):
    print("== Qt6 ==")
    p = spawn_app(["python3", os.path.join(HERE, "qt-test-app.py")])
    try:
        ids = {"save": "qt-save", "text": "qt-filename", "flag": "/tmp/qt-save-pressed"}
        battery("qt-test-app", ids, m)
    finally:
        kill(p)


def web_press_confirmed(m, app_name, timeout_ms=10000):
    """True iff the DOM onclick actually ran: the fixture's heading text
    becomes the exact sentinel PRESS-CONFIRMED only from the handler. A press
    that returns pressed=true but never fires onclick yields a timeout here."""
    res, err = m.call("ui_wait_for",
                      {"query": {"app": app_name, "role": "heading",
                                 "name": "PRESS-CONFIRMED"},
                       "timeout_ms": timeout_ms})
    return (not err) and bool(res.get("found"))


def run_web(app_name, m):
    ids = {"save": "save-project", "text": "web-filename"}
    found, err = m.call("ui_find", {"app": app_name, "role": "button", "name": "Save"})
    check(f"[{app_name}] web button enumerated",
          (not err) and total(found) >= 1, f"resp={found}")
    res, err = m.call("ui_press", {"target": {"app": app_name, "id": "save-project"}})
    check(f"[{app_name}] press web button by DOM id",
          (not err) and res.get("pressed"), f"resp={res}")
    # Observable side effect: the onclick handler sets a heading to an exact
    # sentinel. This must actually appear — a timeout is a FAILURE, not a pass.
    # (Previously this asserted a title update but accepted a timeout, so a press
    #  that returned pressed=true without firing onclick slipped through.)
    check(f"[{app_name}] press fired the DOM onclick handler",
          web_press_confirmed(m, app_name), "sentinel heading never appeared")


def run_chromium(m):
    print("== Chromium ==")
    page = "file://" + os.path.join(HERE, "chromium-test.html")
    p = spawn_app(["chromium", "--no-sandbox", "--disable-gpu", "--no-first-run",
                   "--force-renderer-accessibility", page], settle=6.0)
    try:
        run_web("Chromium", m)
    finally:
        kill(p)


def run_electron(m):
    print("== Electron ==")
    elec = "/root/electron-test/node_modules/.bin/electron"
    p = spawn_app([elec, os.path.join(HERE, "electron-main.js"),
                   "--no-sandbox", "--force-renderer-accessibility"], settle=6.0)
    try:
        _run_electron(m)
    finally:
        kill(p)


def _run_electron(m):
    # Electron registers as app "electron"; web content bridges lazily.
    res, err = m.call("ui_wait_for",
                     {"query": {"app": "electron", "id": "save-project"}, "timeout_ms": 15000})
    ok = (not err) and res.get("found")
    check("[Electron] web Save button enumerated (by DOM id)", ok, f"resp={res}")
    if not ok:
        return
    check("[Electron] action names normalized",
          all(a == a.lower() and a for a in res["found"].get("actions", []) or ["default"]))
    res, err = m.call("ui_press", {"target": {"app": "electron", "id": "save-project"}})
    check("[Electron] press web button by DOM id",
          (not err) and res.get("pressed"), f"resp={res}")


FF_PREFS = """\
user_pref("accessibility.force_disabled", 0);
user_pref("browser.shell.checkDefaultBrowser", false);
user_pref("browser.startup.homepage_override.mstone", "ignore");
user_pref("startup.homepage_welcome_url", "");
user_pref("startup.homepage_welcome_url.additional", "");
user_pref("browser.startup.firstrunSkipsHomepage", true);
user_pref("datareporting.policy.dataSubmissionEnabled", false);
user_pref("toolkit.telemetry.reportingpolicy.firstRun", false);
user_pref("browser.aboutwelcome.enabled", false);
"""


def spawn_detached(cmd, env_extra=None, settle=3.0):
    """Launch fully detached (own session) so a fork-heavy app can't ride/kill
    the parent's channel. Returns a pkill pattern instead of a Popen handle."""
    env = dict(os.environ)
    env.update({"GNOME_ACCESSIBILITY": "1", "GTK_MODULES": "gail:atk-bridge",
                "NO_AT_BRIDGE": "0"})
    if env_extra:
        env.update(env_extra)
    subprocess.Popen(cmd, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                     stdin=subprocess.DEVNULL, start_new_session=True)
    time.sleep(settle)


def run_firefox(m):
    print("== Firefox ==")
    page = "file://" + os.path.join(HERE, "chromium-test.html")
    prof = "/tmp/ff-profile"
    subprocess.run(["rm", "-rf", prof])
    os.makedirs(prof, exist_ok=True)
    with open(os.path.join(prof, "user.js"), "w") as f:
        f.write(FF_PREFS)
    subprocess.run(["pkill", "-9", "firefox"])
    time.sleep(2)
    # First run of a fresh profile + lazy a11y enablement is slow; give it room.
    spawn_detached(["firefox", "--profile", prof, "--new-window", page], settle=4.0)
    try:
        # Firefox enables a11y lazily once it sees the AT; poll generously.
        res, err = m.call("ui_wait_for",
                         {"query": {"app": "Firefox", "role": "button", "name": "Save"},
                          "timeout_ms": 60000})
        ok = (not err) and res.get("found")
        check("[Firefox] web Save button enumerated", ok, f"resp={res}")
        if ok:
            ref = res["found"]["ref"]
            r2, e2 = m.call("ui_press", {"target": {"ref": ref}})
            check("[Firefox] press web button by ref",
                  (not e2) and r2.get("pressed"), f"resp={r2}")
            # Firefox reports the button's action name oddly (";;"); assert the
            # DOM handler actually fired, not just that DoAction returned true.
            check("[Firefox] press fired the DOM onclick handler",
                  web_press_confirmed(m, "Firefox"), "sentinel heading never appeared")
    finally:
        subprocess.run(["pkill", "-9", "firefox"])


def run_core(m):
    """Transport / protocol robustness — no apps needed."""
    print("== MCP protocol ==")
    r = m.raw('{"jsonrpc":"2.0","id":99,"method":"tools/list"}')
    check("[MCP] tools/list returns 9 tools",
          len(r.get("result", {}).get("tools", [])) == 9)
    r = m.raw("{ this is not json")
    check("[MCP] malformed JSON -> -32700",
          r.get("error", {}).get("code") == -32700, f"resp={r}")
    r = m.raw('{"jsonrpc":"2.0","id":100,"method":"no/such/method"}')
    check("[MCP] unknown method -> -32601",
          r.get("error", {}).get("code") == -32601, f"resp={r}")
    payload, err = m.call("ui_bogus", {})
    check("[MCP] unknown tool -> tool error invalid_argument",
          err and payload.get("error") == "invalid_argument", f"resp={payload}")
    # notification (no id) must produce no response line; test by pipelining a ping after
    m._notify("notifications/whatever")
    r = m.raw('{"jsonrpc":"2.0","id":101,"method":"ping"}')
    check("[MCP] notification ignored, next request answered",
          r.get("id") == 101, f"resp={r}")


def run_restart(m_factory):
    """Kill+relaunch GTK app: refs go stale, new instance resolvable — no server restart."""
    print("== app restart recovery ==")
    m = m_factory()
    p = spawn_app(["python3", os.path.join(HERE, "test-app.py")])
    found, err = m.call("ui_find", {"app": "test-app", "id": "save-project"})
    if not ((not err) and total(found)):
        check("[restart] baseline find", False, f"resp={found}")
        kill(p); m.close(); return
    ref = found["matches"][0]["ref"]
    kill(p)
    time.sleep(2.0)
    res, err = m.call("ui_read", {"target": {"ref": ref}})
    check("[restart] ref to exited app -> stale_target",
          err and res.get("error") == "stale_target", f"resp={res}")
    p2 = spawn_app(["python3", os.path.join(HERE, "test-app.py")])
    res, err = m.call("ui_wait_for",
                     {"query": {"app": "test-app", "id": "save-project"}, "timeout_ms": 8000})
    check("[restart] relaunched app resolvable without server restart",
          (not err) and res.get("found"), f"resp={res}")
    kill(p2)
    m.close()


ALL = ["gtk", "qt", "chromium", "electron", "firefox", "core", "restart"]


def cleanup_leftovers():
    """Kill stray test-app instances (e.g. demo apps left in the desktop) so
    duplicate apps don't make every fixture id legitimately ambiguous."""
    for pat in ["test-app.py", "qt-test-app.py"]:
        subprocess.run(["pkill", "-9", "-f", pat],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1)


def main():
    sel = [a for a in sys.argv[1:] if a in ALL] or ALL
    cleanup_leftovers()
    m = Mcp()
    try:
        if "gtk" in sel:
            run_gtk(m)
        if "qt" in sel:
            run_qt(m)
        if "chromium" in sel:
            run_chromium(m)
        if "electron" in sel:
            run_electron(m)
        if "firefox" in sel:
            run_firefox(m)
        if "core" in sel:
            run_core(m)
    finally:
        m.close()
    if "restart" in sel:
        run_restart(Mcp)

    total, passed = len(results), sum(results)
    print(f"\n{passed}/{total} checks passed")
    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()
