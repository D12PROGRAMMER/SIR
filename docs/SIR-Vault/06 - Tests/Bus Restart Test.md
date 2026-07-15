---
kind: test
source: test/bus_restart.py
generated: false
---

# Bus Restart Test

[test/bus_restart.py](../../../test/bus_restart.py) — 4 checks proving SIR survives the death of the accessibility bus itself. **Runs separately** from the acceptance suite because it kills the session's AT-SPI registry (`pkill -9 at-spi2-registryd` + `at-spi-bus-launcher`), which disrupts every a11y consumer in the session.

## Sequence

1. **Baseline** — fixture app enumerated by a live server
2. **Kill** the registry + bus launcher under the running server
3. **Outage** — a tool call must return cleanly (error or empty), not hang, not crash the process
4. **Recovery** — relaunch the fixture; poll `ui_list_apps` up to 30s: the supervisor must reconnect and rebuild **without a server restart**
5. **Proof** — a `ui_press` by id succeeds against the recovered session

Covers: [[Reconnection]], [[Flow - Bus Restart Recovery]], the supervisor's stream-end/ping detection, fail-fast outage behavior, and D-Bus activation revival.

The fixture is deliberately relaunched in step 4: a killed registry orphans prior app registrations, and whether unrestarted apps re-embed on their own is toolkit-dependent — recorded in [[Unresolved Questions]].

Baseline result: **4/4** in 10.3s — [[Baseline (2026-07-14)]].
