#!/bin/bash
# Headless a11y test harness. Run as:
#   dbus-run-session -- bash harness.sh <command...>
# Starts Xvfb, the GTK test app, and (if PyQt6 is installed) the Qt test app
# inside this D-Bus session, runs the command with the same session bus
# (so it sees the same a11y bus), then cleans up.
set -u
export DISPLAY=:9
Xvfb :9 -screen 0 1280x800x24 2>/dev/null &
XVFB_PID=$!
export GTK_MODULES=gail:atk-bridge
export NO_AT_BRIDGE=0
export GNOME_ACCESSIBILITY=1
export QT_LINUX_ACCESSIBILITY_ALWAYS_ON=1
sleep 1
HERE="$(cd "$(dirname "$0")" && pwd)"
python3 "$HERE/test-app.py" &
APP_PID=$!
QT_PID=""
if [ "${WITH_QT:-0}" = "1" ] && python3 -c "import PyQt6" 2>/dev/null; then
    python3 "$HERE/qt-test-app.py" &
    QT_PID=$!
fi
CHROMIUM_PID=""
if [ "${WITH_CHROMIUM:-0}" = "1" ] && command -v chromium >/dev/null; then
    chromium --no-sandbox --disable-gpu --no-first-run --force-renderer-accessibility \
        "file://$HERE/chromium-test.html" >/dev/null 2>&1 &
    CHROMIUM_PID=$!
fi
sleep 3

"$@"
RC=$?

kill $APP_PID $QT_PID $CHROMIUM_PID $XVFB_PID 2>/dev/null
wait 2>/dev/null
exit $RC
