---
kind: test
generated: false
---

# Test Harness

How the tests get a desktop to test against, and how they must be launched. Files: [test/](../../../test/).

## The headless desktop

Tests run inside the persistent desktop session provided by the guest's `desktop.service`: Xvfb `:9` + openbox + one D-Bus session whose address is exported at `/root/.desktop-env` ([[Desktop Service and Looking Glass]]). The AT-SPI bus belongs to that session — SIR and the fixture apps must share it.

Required env for fixtures: `GTK_MODULES=gail:atk-bridge`, `NO_AT_BRIDGE=0`, `GNOME_ACCESSIBILITY=1`, `QT_LINUX_ACCESSIBILITY_ALWAYS_ON=1`.

## Fixtures

| File | Toolkit | Exposes |
|---|---|---|
| `test-app.py` | GTK3 | `save-project`, `filename`, `locked` (disabled), 2× id-less "Copy" (ambiguity), `spawn`/`despawn` → dynamic `dynamic-1` |
| `qt-test-app.py` | Qt6 | `qt-save`, `qt-filename` (dotted AccessibleIds) |
| `chromium-test.html` | Chromium/Electron/Firefox | DOM `save-project`, `web-filename`; `onclick` retitles the document |
| `electron-main.js` | Electron shell for the HTML page | |

All fixtures make effects **observable without vision**: flag files and title changes.

## Launch rules (hard-won)

1. **Never run the suite on an interactive SSH channel.** Browser fork-storms can kill the channel and the suite with it. Use a systemd transient unit:
   ```bash
   source /root/.desktop-env
   systemd-run --unit=uisuite --collect \
     --setenv=DISPLAY=$DISPLAY --setenv=DBUS_SESSION_BUS_ADDRESS=$DBUS_SESSION_BUS_ADDRESS \
     --setenv=GNOME_ACCESSIBILITY=1 --setenv=GTK_MODULES=gail:atk-bridge \
     --working-directory=/root/ui-mcp /usr/bin/python3 -u test/suite.py
   # poll: systemctl is-active uisuite ; results: journalctl -u uisuite -o cat
   ```
2. **`python3 -u`** — journald/file output is useless until exit otherwise.
3. **Never `pkill -f firefox`** — it matches your own shell's command line and kills your session; use `pkill -x firefox-esr`.
4. systemd `StandardOutput=file:` **overwrites without truncating** — delete the output file between runs or you'll read stale tails.

## Other harness pieces

- `harness.sh` — legacy per-run `dbus-run-session` wrapper (superseded by the persistent desktop for most uses)
- `bench.py` — the measurement harness behind [[Baseline (2026-07-14)]]
- `gen_symbols.py` — regenerates `_generated/symbols/`
- `probe_ff.py` — the diagnostic that isolated the dual-connection bug ([[ADR - Dual D-Bus Connections]])
