---
kind: flow
generated: false
---

# Flow: Test Suite Execution

How [test/suite.py](../../../test/suite.py) validates the system end-to-end. Details: [[Acceptance Suite]], [[Test Harness]].

```mermaid
flowchart TD
    START[systemd-run --unit=uisuite<br/>DISPLAY + DBUS env from /root/.desktop-env] --> CLEAN[cleanup_leftovers:<br/>kill stray fixture apps<br/>duplicates would make every id legitimately ambiguous]
    CLEAN --> MCP0[spawn ui-mcp<br/>speak MCP over stdio pipes]
    MCP0 --> GTK[GTK battery — 13 checks<br/>spawn test-app.py]
    GTK --> QT[Qt battery — 6 checks<br/>spawn qt-test-app.py]
    QT --> CR[Chromium — 3 checks<br/>--force-renderer-accessibility]
    CR --> EL[Electron — 3 checks<br/>app name electron, DOM id]
    EL --> FF[Firefox — 2 checks<br/>prepped profile, 60s lazy-bridge wait]
    FF --> CORE[MCP protocol — 5 checks<br/>no desktop apps needed]
    CORE --> RST[app-restart battery — 2 checks<br/>fresh server, kill+relaunch fixture]
    RST --> SUM["N/34 checks passed → exit code"]
    BUSTEST[test/bus_restart.py — 4 checks<br/>SEPARATE run: kills the registry] -.disruptive, runs alone.-> SUM2["4/4"]
```

Facts:

- Each toolkit runner **owns its app's lifecycle** (spawn → test → kill); browsers launch detached (`start_new_session`) because their fork storms can kill a parent's SSH channel.
- The suite runs as a **systemd transient unit**, never on an interactive SSH channel — survives connection drops, output via journald/file.
- [[Bus Restart Test]] is deliberately excluded from the main run: it kills the session's AT-SPI registry.
- Expected totals, verified at baseline: **34/34** and **4/4** ([[Baseline (2026-07-14)]]).
