---
kind: architecture
generated: false
---

# Event Processing

SIR registers for **all `ObjectEvents`** on a dedicated event connection ([[Process and Connection Model]]) and patches the cache incrementally as the desktop changes. Handler: [[actions.handle_event]].

## Rules

1. **Zero D-Bus I/O in the handler.** Every patch is pure in-memory work. This is load-bearing — see [[ADR - No IO in Event Handler]].
2. Events the cache doesn't know about (unknown keys) are silently ignored — the object will be discovered by the next walk if it matters.

## Event → patch mapping

| Event | Patch |
|---|---|
| `StateChanged(Enabled/Sensitive)` | `node.enabled = on` |
| `StateChanged(Showing/Visible)` | `node.visible = on` |
| `StateChanged(Focused)` | `node.focused = on` |
| `StateChanged(Defunct, on)` | remove the subtree — refs inside go stale |
| `PropertyChange("accessible-name")` | `node.name = new` (this is how window-title changes propagate — verified live by `ui_wait_for` matching a renamed window) |
| `ChildrenChanged(Insert)` | [[cache.Cache.mark_app_dirty]] — lazy re-walk on next query |
| `ChildrenChanged(Delete)` | [[cache.Cache.remove_subtree]] in-memory prune; NULL child → dirty-mark instead |

## Why Insert is lazy but Delete is eager

Delete can be honored purely in memory (we already have the subtree). Insert would require walking the new subtree — D-Bus I/O — so it only flips the dirty flag; [[cache.Cache.ensure_walked]] re-walks when the next operation actually needs the app, when the connection is calm. Ref stability across that re-walk is guaranteed ([[Cache and Enumeration]]).

## Interaction with wait_for

[[actions.Service.wait_for]] polls the cache every 250ms; events keep the cache fresh between polls, and every 8th iteration (~2s) it force-dirties the queried apps in case events were missed. Verified by the acceptance check *"dynamically added widget found via events"*.

Flow diagram: [[Flow - Event Processing]] · invalidation paths: [[Flow - Cache Invalidation]]
