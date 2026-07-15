---
kind: fn
module: actions
symbol: handle_event
source: src/actions.rs
line: 561
visibility: private
async: true
generated: true
---

# `handle_event`

Event handling is strictly in-memory: NO D-Bus I/O here. An app under heavy load (Firefox opening) floods hundreds of ChildrenChanged events; doing a subtree walk (D-Bus round trips) per event — while holding the cache lock — saturates the shared connection and starves real tool calls until their walk budget trips. So structural changes only flip a cheap dirty flag; the next ensure_walked re-walks the app lazily, when the connection is calm.

```rust
async fn handle_event(inner: &Arc<Inner>, _conn: &zbus::Connection, ev: Event)
```

[source](../../../../src/actions.rs#L561) · parent module: [[Module - actions]]

**Calls:** [[cache.Cache.mark_app_dirty]], [[cache.Cache.patch_name]], [[cache.Cache.patch_state]], [[cache.Cache.remove_subtree]], [[cache.key_of]], [[types.Target.is_empty]]

**Called from:** actions.rs:528

**Types in signature:** [[actions.Inner]]

**Execution flows:** [[Flow - Event Processing]], [[Flow - Cache Invalidation]]
