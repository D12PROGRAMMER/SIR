---
kind: architecture
generated: false
---

# Resolution and References

Targets name controls; the resolver ([[resolver.resolve]]) turns a target into exactly one live node or a precise error. **SIR never silently picks between multiple matches.**

## Target shape

```json
{ "app": "example-editor", "window": "…", "id": "save-project",
  "ref": "app-4:node-182", "role": "button", "name": "Save" }
```

All fields optional; at least one of `id`/`ref`/`role`/`name` required.

## Precedence (strict)

1. **Application-provided accessible ID** — exact match first; if none, a **leaf-segment** match (Qt publishes `QApplication.QMainWindow.QWidget.qt-save`; the leaf is the developer-chosen part — [[ADR - Qt Leaf ID Matching]])
2. **Session ref** from a previous result
3. **app + window + role + exact name**
4. Multiple survivors at any strategy → `ambiguous` **with candidates**; zero everywhere → `not_found`

A strategy that matches exactly one node wins immediately; zero matches falls through to the next strategy.

## Session refs

- Format `app-N:node-M`; allocated once per `(bus name, object path)`, never reused
- **Stable across re-walks** of a living app ([[Cache and Enumeration]])
- Die when: the object goes `Defunct`, its subtree is removed, its app leaves the bus, or the accessibility connection is rebuilt

## Liveness verification

Every successful resolution ends in [[resolver.verify_live]]: a real `GetState` round-trip against the object. Errors or `Defunct` → the node is evicted and the caller gets `stale_target`. As a side effect the node's `enabled/visible/focused` are refreshed — so [[actions.Service.press]] preconditions use live data, not cache age.

## Stale target detection summary

| Cause | Detected by |
|---|---|
| Widget destroyed | Defunct event (eager) or `verify_live` (on use) |
| App exited/restarted | [[cache.Cache.sync_apps]] drops the bus name → nodes removed |
| Bus reconnected | cache cleared wholesale by the supervisor |
| Ref never existed | plain map miss → `stale_target` |

Verified by acceptance checks: *removed widget ref → stale_target*, *ref to exited app → stale_target*. Flow: [[Flow - Target Resolution]].
