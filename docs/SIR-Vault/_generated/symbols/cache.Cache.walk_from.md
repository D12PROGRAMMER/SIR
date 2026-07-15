---
kind: fn
module: cache
symbol: Cache.walk_from
source: src/cache.rs
line: 154
visibility: private
async: true
generated: true
---

# `Cache.walk_from`

Shared BFS used by full walks and incremental subtree additions. Seeds: (object, parent node_ref, window_ref, depth). Returns visited keys.

```rust
    async fn walk_from(
        &mut self,
        conn: &zbus::Connection,
        app_ref: &str,
        seeds: Vec<(ObjectRefOwned, Option<String>, Option<String>, usize)>,
        max_nodes: usize,
    ) -> UiResult<HashSet<NodeKey>>
```

[source](../../../../src/cache.rs#L154) · parent module: [[Module - cache]]

**Calls:** [[cache.key_of]]

**Called from:** cache.rs:249

**Execution flows:** [[Flow - Startup and Initial Enumeration]]
