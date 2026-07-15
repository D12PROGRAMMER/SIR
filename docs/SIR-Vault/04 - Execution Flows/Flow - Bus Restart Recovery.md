---
kind: flow
generated: false
---

# Flow: Bus Restart Recovery

The accessibility bus itself dies (registry crash, session restart). Code: [[actions.supervisor]], [[actions.liveness_ping]]. Policy: [[Reconnection]].

```mermaid
stateDiagram-v2
    [*] --> Connected
    Connected: Connected\nevent pump + 15s liveness ping\ntools serve normally
    Connected --> Detected: event stream ends\nOR ping fails
    Detected: Detected\nconn slot = None → tools fail fast\ncache.clear_all()
    Detected --> Reconnecting
    Reconnecting: Reconnecting\nbackoff 0.5s → 10s\nD-Bus activation revives the a11y bus
    Reconnecting --> Reconnecting: connect / register fails
    Reconnecting --> Rebuilding: both connections up\nObjectEvents re-registered
    Rebuilding: Rebuilding\nfull re-enumeration (sync + walk all)\nstderr "connected: enumerated N apps, M nodes"
    Rebuilding --> Connected
```

During the outage window every tool call returns
`atspi_error: accessibility bus disconnected; reconnecting — retry shortly` — fail-fast, no hang, no panic.

All session refs from before the reconnect are stale by construction (the bus reassigns unique names); clients re-find by ID.

Verified by [[Bus Restart Test]] (4/4): baseline press → `pkill -9 at-spi2-registryd at-spi-bus-launcher` → outage calls return cleanly → recovery detected by polling `ui_list_apps` → press works again. The test relaunches the fixture app because a killed registry orphans existing app registrations (see [[Unresolved Questions]] on in-place re-embedding).
