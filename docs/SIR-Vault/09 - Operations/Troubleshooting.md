---
kind: operations
generated: false
---

# Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `ui_list_apps` returns `[]` but apps are open | app lacks a11y bridge, or SIR is on a different session bus | check env: app needs the toolkit vars ([[Toolkit Behavior Matrix]]); both SIR and the app must use the `/root/.desktop-env` bus |
| GTK3 app invisible on the bus | `libatk-adaptor` missing or `GTK_MODULES` unset | `apt install libatk-adaptor`; relaunch with `GTK_MODULES=gail:atk-bridge NO_AT_BRIDGE=0` |
| Firefox never appears / appears without content | a11y disabled in profile; lazy bridging | profile pref `accessibility.force_disabled=0`; wait via `ui_wait_for` (10–60 s first run) |
| Chromium/Electron content subtree empty | renderer a11y off, or page failed to load | `--force-renderer-accessibility`; check the page URL is absolute (`file://test/...` = the classic relative-path "Network error") |
| `atspi_error: … disconnected; reconnecting` | bus outage window | retry shortly; supervisor recovers automatically ([[Reconnection]]) |
| `atspi_error: <op>: AT-SPI call timed out` | one object/app wedged (2 s bound) | the app is busy or hung; SIR stays responsive by design ([[Timeout Model]]) |
| `walk of app-N hit time budget` on stderr | enormous/slow tree | coverage truncated at the caps; raise constants only with measurement |
| Every id suddenly `ambiguous` | **two instances** of the app running (e.g. leftover demo fixtures) | kill duplicates; this is correct behavior, not a bug |
| Suite dies mid-run over SSH | browser fork storm killed the channel | run as a systemd unit ([[Test Harness]]) |
| Killed your own SSH session with pkill | `pkill -f firefox` matches your shell's cmdline | `pkill -x firefox-esr` |
| Stale test output after a rerun | systemd `StandardOutput=file:` overwrites without truncating | delete the file between runs |
| VM SSH refused | VM not running / hypervisor off | `vm.ps1 status`; `-cpu max` crashes WHPX on this host — the script pins Skylake-Client-v3 |
