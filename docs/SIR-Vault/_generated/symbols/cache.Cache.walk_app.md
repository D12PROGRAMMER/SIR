---
kind: fn
module: cache
symbol: Cache.walk_app
source: src/cache.rs
line: 235
visibility: public
async: true
generated: true
---

# `Cache.walk_app`

Full breadth-first walk of one application's accessible tree. Refs of surviving nodes are preserved; nodes that vanished are pruned.

```rust
    pub async fn walk_app(&mut self, conn: &zbus::Connection, app_ref: &str) -> UiResult<()>
```

[source](../../../../src/cache.rs#L235) · parent module: [[Module - cache]]

**Calls:** [[cache.Cache.remove_node]], [[cache.Cache.walk_from]], [[cache.key_of]]

**Called from:** cache.rs:307

**Errors produced:** `not_found` (see Error Model)

**Execution flows:** [[Flow - Startup and Initial Enumeration]], [[Flow - Cache Invalidation]]

**Exercised by:** acceptance suite (indirect)
