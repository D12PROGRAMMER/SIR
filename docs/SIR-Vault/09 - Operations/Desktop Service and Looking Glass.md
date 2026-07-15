---
kind: operations
generated: false
---

# Desktop Service and Looking Glass

The guest runs a persistent headless desktop that SIR controls and the human watches.

## desktop.service (guest systemd unit)

`/root/desktop.sh`, enabled at boot: **Xvfb `:9`** (1280×800) + **openbox** + one **D-Bus session** whose address is written to `/root/.desktop-env`, plus **x11vnc `-viewonly`** (localhost:5900) and **noVNC/websockify** on `:6080`.

```
systemctl status desktop     # health
systemctl restart desktop    # rebuilds the session — NOTE: new DBUS address,
                             # apps must be relaunched, SIR reconnects
```

## The looking glass

Human view: **http://127.0.0.1:6080/vnc.html** on the host (QEMU forwards 6080). Properties:

- **View-only, enforced server-side** (`x11vnc -viewonly`) — no clicks or keys pass through; the desktop belongs to the agent, the glass belongs to the human
- Closing the browser tab affects nothing
- SIR never reads it: pixels are for people ([[ADR - No Vision No Input Synthesis]])

## Launching apps into the session

```bash
source /root/.desktop-env
export GTK_MODULES=gail:atk-bridge NO_AT_BRIDGE=0 GNOME_ACCESSIBILITY=1 QT_LINUX_ACCESSIBILITY_ALWAYS_ON=1
setsid some-app >/tmp/app.log 2>&1 </dev/null & disown
```

`setsid + disown` detaches from the SSH channel — fork-heavy apps (browsers) can otherwise die with it ([[Test Harness]] launch rules).
