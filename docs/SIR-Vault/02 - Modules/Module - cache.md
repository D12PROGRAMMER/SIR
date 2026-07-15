---
kind: module
module: cache
source: src/cache.rs
generated: false
---

# Module: cache

[source](../../../src/cache.rs) — the in-memory desktop model. Pure data structure plus walk logic; the only D-Bus I/O it performs is inside walks (via [[Module - accessibility]] helpers).

## Contents

- **[[cache.Cache]]** — apps, nodes, and both indexes ([[Cache and Enumeration]])
- **[[cache.AppEntry]] / [[cache.NodeEntry]]** — the stored records
- **[[cache.Filter]]** — search predicate used by find/list/resolve; ID matching supports exact-then-leaf ([[ADR - Qt Leaf ID Matching]])
- **[[cache.key_of]]** — `(bus name, object path)` identity
- Walks: [[cache.Cache.walk_from]] (BFS with node/depth/time caps), [[cache.Cache.walk_app]] (ref-stable re-walk + prune), [[cache.Cache.ensure_walked]] (lazy)
- Enumeration: [[cache.Cache.sync_apps]] (add new apps, drop vanished — app-restart detection)
- Invalidation: [[cache.Cache.mark_app_dirty]] (keep nodes, re-walk lazily), [[cache.Cache.remove_subtree]], [[cache.Cache.remove_app]], [[cache.Cache.remove_node]], [[cache.Cache.clear_all]]
- Event patches: [[cache.Cache.patch_name]], [[cache.Cache.patch_state]]

## The ref-stability contract

This module owns invariant #2 of [[System Overview]]: `node_by_key` reuse in `walk_from` + prune-only-unvisited in `walk_app` means a re-walk of a living app **never changes existing refs**. `mark_app_dirty` deliberately does *not* delete nodes — deletion is decided by the next walk (or an explicit Defunct/Delete event).

Full symbol list: [[Symbol Index]] § cache.
