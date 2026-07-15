#!/bin/bash
# Deploy + smoke-test SIR on the Raspberry Pi (arm64, Bookworm, Labwc/Wayland).
# Run FROM the Windows host (Git Bash). Requires the sir_pi key authorized on
# the Pi. Packaging/ops script — touches no production code.
#
#   scripts/deploy-pi.sh
#
set -euo pipefail
PI_USER="aiden"
PI_HOST="192.168.12.213"
KEY="/c/ai-os/pi-keys/sir_pi_ed25519"
DEB="/c/ai-os/ui-mcp/dist/sir_0.1.0-1_arm64.deb"
SSH="ssh -i $KEY -o StrictHostKeyChecking=accept-new $PI_USER@$PI_HOST"

echo "== 1. reachability + arch =="
$SSH 'echo host=$(hostname) arch=$(uname -m) glibc=$(ldd --version|head -1); \
      test "$(uname -m)" = aarch64 || { echo "NOT arm64"; exit 1; }'

echo "== 2. storage health (saved warning: prior emergency-mode/RO remount) =="
$SSH 'mount | grep " on / " ; \
      findmnt -no OPTIONS / | grep -q " ro,\| ro$\|^ro," && echo "WARN: / is READ-ONLY" || echo "/ is read-write"; \
      df -h / | tail -1; \
      dmesg 2>/dev/null | grep -iE "read-only|EXT4-fs error|I/O error" | tail -3 || true'

echo "== 3. copy + install the static arm64 package =="
scp -i "$KEY" "$DEB" "$PI_USER@$PI_HOST:/tmp/sir_arm64.deb"
$SSH 'sudo dpkg -i /tmp/sir_arm64.deb; file /usr/bin/ui-mcp; ui-mcp cli 2>&1 | head -1 || true'

echo "== 4. ensure at-spi + accessibility on the live Wayland/Labwc session =="
$SSH 'sudo apt-get install -y -q at-spi2-core >/dev/null 2>&1 || true; \
      systemctl --user list-units 2>/dev/null | grep -i a11y || true'

# SIR must share the desktop session bus. On the Pi that is the graphical
# session for user aiden (WAYLAND_DISPLAY under Labwc). We locate its bus.
echo "== 5. run SIR against the live desktop; open Chromium on the TV =="
$SSH 'bash -s' << 'REMOTE'
set -e
# Find the graphical session DBus for the logged-in user.
UID_N=$(id -u)
export XDG_RUNTIME_DIR=/run/user/$UID_N
export DBUS_SESSION_BUS_ADDRESS=unix:path=$XDG_RUNTIME_DIR/bus
export GNOME_ACCESSIBILITY=1 QT_LINUX_ACCESSIBILITY_ALWAYS_ON=1
# Wayland display for launching a VISIBLE window on the attached TV.
export WAYLAND_DISPLAY=$(ls $XDG_RUNTIME_DIR/wayland-* 2>/dev/null | grep -v '\.lock' | head -1 | xargs -n1 basename)

echo "runtime=$XDG_RUNTIME_DIR wayland=$WAYLAND_DISPLAY"
# Open a real Chromium window with a known DOM button, visible on the TV.
cat > /tmp/sir-pi-demo.html << 'HTML'
<!doctype html><meta charset=utf-8><title>SIR Pi Demo</title>
<body style="font:48px sans-serif;text-align:center;padding-top:20vh">
<h1 id=status>SIR on Pi 5</h1>
<button id="save-project" style="font-size:40px;padding:20px"
 onclick="document.getElementById('status').textContent='PRESSED BY SIR';document.title='SIR Saved'">Save</button>
HTML
pkill -f sir-pi-demo || true; sleep 1
chromium-browser --force-renderer-accessibility --start-maximized \
  file:///tmp/sir-pi-demo.html >/tmp/sir-chromium.log 2>&1 &
sleep 8

# Now drive it through SIR, entirely via AT-SPI.
echo "--- SIR enumerates the live desktop ---"
ui-mcp cli apps
echo "--- SIR waits for the DOM button and presses it (no pointer/keys) ---"
ui-mcp cli wait-for 20000 app=Chromium id=save-project
ui-mcp cli press app=Chromium id=save-project
echo "--- observable: window title should now be 'SIR Saved' ---"
ui-mcp cli wait-for 5000 app=Chromium role=frame name="SIR Saved - Chromium" | head -6 || true
REMOTE

echo "== deploy-pi complete =="
