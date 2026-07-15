---
kind: adr
status: accepted
date: 2026-07-14
generated: false
---

# ADR: Separate D-Bus connections for events and control

**Context.** With one shared AT-SPI connection, Firefox's load-time signal flood produced a self-starvation loop: walk holds cache lock → pump can't drain → signals back up the socket → the walk's own method replies queue behind them → every call times out (`walk hit time budget at 6 nodes`). Diagnosed with `test/probe_ff.py`: a fresh server found Firefox's button in 6 s; a long-lived server connected before Firefox never did.

**Decision.** [[actions.supervisor]] opens **two** independent `AccessibilityConnection`s: one exclusively for the event stream, one for all method calls (walks, actions, reads). Both are registered, torn down, and rebuilt together.

**Consequences.**
- A signal flood can only delay cache freshness, never control operations. Verified: same scenario now resolves in ~10 s.
- Slightly more supervisor complexity and two sockets to the bus.
- Complements (does not replace) [[ADR - No IO in Event Handler]] — both were required.

Diagram: [[Flow - Dual Connection Architecture]].
