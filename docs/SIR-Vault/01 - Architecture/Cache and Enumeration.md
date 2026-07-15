---
kind: architecture
generated: false
---

# Cache and Enumeration

The cache ([[Module - cache]]) is SIR's in-memory model of the accessible desktop: applications, windows, controls, and the session refs that name them.

## Data model

```
Cache
├── apps:        app_ref  → AppEntry { obj, name, bus_name, walked }
├── app_by_bus:  bus name → app_ref
├── nodes:       node_ref → NodeEntry { obj, parent, window_ref, name, role,
│                                        accessible_id, enabled, visible,
│                                        focused, is_window, children }
└── node_by_key: (bus name, object path) → node_ref
```

Refs are `app-N` / `app-N:node-M`, allocated from monotonic counters — session-local, never reused ([[Resolution and References]]).

## Application enumeration

[[cache.Cache.sync_apps]] lists the registry root's children, adds new apps (fetching their names), and **removes apps whose bus name vanished** — that is how app exits/restarts invalidate refs ([[Flow - App Restart Recovery]]).

Enumeration happens:

1. **Automatically at startup** — the supervisor walks every app before the server answers `initialize` ([[Flow - Startup and Initial Enumeration]])
2. Automatically after every reconnect
3. Lazily on demand — every resolving operation calls [[cache.Cache.ensure_walked]]

## Tree walks

[[cache.Cache.walk_from]] is a breadth-first walk with three caps: `MAX_NODES_PER_APP = 5000`, `MAX_DEPTH = 60`, and a 20s wall-clock budget ([[Timeout Model]]). Truncation is logged to stderr, never silent.

[[cache.Cache.walk_app]] wraps it with the **ref-stability contract**:

- existing `(bus, path)` keys **reuse their node_ref** (`node_by_key` lookup)
- children lists are rebuilt from scratch
- nodes not visited by the walk are pruned (their refs become stale)

So a re-walk of a living app updates it in place; only real disappearance kills refs.

## Dirty marking

[[cache.Cache.mark_app_dirty]] flips `walked = false` **without deleting nodes**. The next `ensure_walked` re-walks and prunes. Used by the event pump for structural changes ([[Event Processing]]) and by [[actions.Service.wait_for]]'s periodic forced re-walk (every ~2s while waiting).

## Caps and limits

| Constant | Value | Where |
|---|---|---|
| `MAX_NODES_PER_APP` | 5000 | [[accessibility.MAX_NODES_PER_APP]] |
| `MAX_DEPTH` | 60 | [[accessibility.MAX_DEPTH]] |
| `WALK_BUDGET` | 20s | [[accessibility.WALK_BUDGET]] |
| `LIST_CAP` (list_controls) | 500 | [[Module - actions]] |
| `FIND_CAP` (find) | 50 | [[Module - actions]] |
