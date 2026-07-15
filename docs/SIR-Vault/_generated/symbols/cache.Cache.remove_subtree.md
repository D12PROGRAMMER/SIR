---
kind: fn
module: cache
symbol: Cache.remove_subtree
source: src/cache.rs
line: 273
visibility: public
async: false
generated: true
---

# `Cache.remove_subtree`

Incremental: a subtree disappeared (event-driven). Refs inside become stale.

```rust
    pub fn remove_subtree(&mut self, root_key: &NodeKey)
```

[source](../../../../src/cache.rs#L273) · parent module: [[Module - cache]]

**Called from:** actions.rs:589, cache.rs:404

**Execution flows:** [[Flow - Cache Invalidation]], [[Flow - Event Processing]]

**Exercised by:** acceptance suite (indirect)
