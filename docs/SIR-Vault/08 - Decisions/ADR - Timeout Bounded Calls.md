---
kind: adr
status: accepted
date: 2026-07-14
generated: false
---

# ADR: Every AT-SPI call is timeout-bounded

**Context.** zbus has no default method-call timeout. A wedged application (Firefox mid-load) hung the entire server indefinitely — discovered as a `pipe_read`-blocked client waiting on a `ui_wait_for` that never returned.

**Decision.** All round trips go through [[accessibility.call]] (`tokio::time::timeout`, `CALL_TIMEOUT = 2s`); tree walks additionally carry a 20 s wall-clock budget ([[cache.Cache.walk_from]]). Timeouts surface as `atspi_error` with the operation name; walks skip failed nodes and log budget hits.

**Consequences.**
- One unresponsive app can no longer freeze the control plane — the core availability property of an OS-control server.
- Legitimately slow objects (>2 s per property read) are treated as unavailable; considered acceptable and revisitable via the constant.
- No retries by design: the caller owns retry policy. [[Timeout Model]] has the full constant table.
