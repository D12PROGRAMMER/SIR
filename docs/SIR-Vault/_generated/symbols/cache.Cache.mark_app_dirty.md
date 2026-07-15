---
kind: fn
module: cache
symbol: Cache.mark_app_dirty
source: src/cache.rs
line: 144
visibility: public
async: false
generated: true
---

# `Cache.mark_app_dirty`

Mark an app for re-walk. Nodes are KEPT so refs stay stable; the next walk refreshes surviving nodes in place and prunes the dead ones.

```rust
    pub fn mark_app_dirty(&mut self, bus_name: &str)
```

[source](../../../../src/cache.rs#L144) · parent module: [[Module - cache]]

**Called from:** actions.rs:417, actions.rs:580, actions.rs:587

**Execution flows:** [[Flow - Cache Invalidation]], [[Flow - Event Processing]]

**Exercised by:** acceptance suite (indirect)
