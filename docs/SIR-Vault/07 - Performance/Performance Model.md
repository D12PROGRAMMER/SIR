---
kind: performance
generated: false
---

# Performance Model

Where time and memory actually go, per operation class — grounded in [[Baseline (2026-07-14)]].

## Cost hierarchy

1. **D-Bus round trips dominate everything.** One AT-SPI call ≈ 0.5–2 ms on a quiet bus; a tree walk is *n* nodes × ~4 calls ([[accessibility.inspect]]: role, state, name, id[, attributes], children). The optimization pass removed a fifth per-node call (`get_interfaces`) whose result nothing consumed. That's why the cache exists and why walks are lazy, capped, and event-invalidated rather than repeated per query.
2. **Cache operations are ~free.** A warm `ui_find` (resolution + candidate assembly + one liveness `GetState` + action fetch) is ~1 ms.
3. **Deliberate sleeps.** Press 150 ms settle, focus 100 ms, wait_for 250 ms poll — policy constants for observability, not overhead ([[Timeout Model]]).
4. **Memory is a non-issue** at current scale: ~7 MB RSS with the cache populated; `NodeEntry` is small and capped at 5000/app.

## Consequences for callers

- Prefer `id`/`ref` targets: they resolve from the cache with one verification round trip.
- `ui_wait_for` is cheap while waiting (cache polls) but forces a re-walk every ~2 s — bounded by walk caps.
- The first query against a freshly-launched big app pays the walk; subsequent queries don't.

## Known non-optimizations (rejected)

- Caching action lists: staleness risk for a rarely-repeated call — actions are fetched fresh at press time.
- Parallel per-node walk fan-out: would multiply in-flight calls on the shared call connection during floods; the dual-connection design isolates events, not method storms ([[ADR - Dual D-Bus Connections]]).
