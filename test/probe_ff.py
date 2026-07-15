#!/usr/bin/env python3
"""Reproduce the suite scenario: ui-mcp connects BEFORE Firefox launches, then
poll ui_find every 5s to see when (if) the web Save button enters the cache."""
import json
import os
import subprocess
import time

errf = open("/tmp/uimcp.stderr", "w")
p = subprocess.Popen(["/usr/local/bin/ui-mcp"], stdin=subprocess.PIPE,
                     stdout=subprocess.PIPE, stderr=errf,
                     text=True, bufsize=1)
_id = 0


def rpc(m, pa=None):
    global _id
    _id += 1
    p.stdin.write(json.dumps({"jsonrpc": "2.0", "id": _id, "method": m,
                              "params": pa or {}}) + "\n")
    p.stdin.flush()
    line = p.stdout.readline()
    if line == "":
        print(f"!! ui-mcp EOF/crash (exit={p.poll()}) during {m} {pa}", flush=True)
        raise SystemExit(3)
    return json.loads(line)


rpc("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                   "clientInfo": {"name": "probe", "version": "0"}})
p.stdin.write(json.dumps({"jsonrpc": "2.0", "method": "notifications/initialized"}) + "\n")
p.stdin.flush()


def call(t, a):
    r = rpc("tools/call", {"name": t, "arguments": a})
    return json.loads(r["result"]["content"][0]["text"])


# server is connected now; launch Firefox AFTER (like the suite)
os.system("rm -rf /tmp/ff-probe; mkdir -p /tmp/ff-probe")
with open("/tmp/ff-probe/user.js", "w") as f:
    f.write('user_pref("accessibility.force_disabled", 0);\n')
subprocess.Popen(["firefox", "--profile", "/tmp/ff-probe", "--new-window",
                  "file:///root/ui-mcp/test/chromium-test.html"],
                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                 stdin=subprocess.DEVNULL, start_new_session=True)

for t in range(0, 70, 5):
    time.sleep(5)
    apps = call("ui_list_apps", {})
    ff = [a["name"] for a in apps["apps"] if "irefox" in a["name"]]
    # total node count for firefox
    allc = call("ui_find", {"app": "Firefox"})
    r = call("ui_find", {"app": "Firefox", "role": "button", "name": "Save"})
    doc = call("ui_find", {"app": "Firefox", "role": "document-web"})
    print(f"t={t+5}s ff={ff} ff_nodes={allc.get('total')} "
          f"doc_web={doc.get('total')} save={r.get('total')}", flush=True)
    if r.get("total"):
        print("FOUND at", t + 5)
        break

p.stdin.close()
