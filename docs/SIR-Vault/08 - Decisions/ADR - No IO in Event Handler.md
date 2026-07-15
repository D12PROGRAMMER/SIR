---
kind: adr
status: accepted
date: 2026-07-14
generated: false
---

# ADR: The event handler performs no D-Bus I/O

**Context.** The first implementation walked new subtrees inside `ChildrenChanged(Insert)` handling — D-Bus round trips per event, while holding the cache lock. Under Firefox's hundreds-of-events load flood this saturated the connection and starved real tool calls.

**Decision.** [[actions.handle_event]] is strictly in-memory: state/name patches, subtree prunes, and dirty-flag flips. Structural discovery (walking) happens **lazily** in [[cache.Cache.ensure_walked]] on the next query, when the bus is calm. The incremental `add_subtree` walk was deleted outright.

**Consequences.**
- Event processing is O(cache op) regardless of desktop chaos.
- New widgets appear on the next query or `ui_wait_for` poll rather than instantly — acceptance check *"dynamically added widget found via events"* proves the latency is well under user-visible thresholds (one 250 ms poll).
- Delete stays eager (pure memory), Insert stays lazy — asymmetry documented in [[Event Processing]].
