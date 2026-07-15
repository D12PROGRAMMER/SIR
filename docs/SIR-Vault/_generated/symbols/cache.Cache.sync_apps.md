---
kind: fn
module: cache
symbol: Cache.sync_apps
source: src/cache.rs
line: 81
visibility: public
async: true
generated: true
---

# `Cache.sync_apps`

Refresh the application list from the registry root.

```rust
    pub async fn sync_apps(&mut self, conn: &zbus::Connection) -> UiResult<()>
```

[source](../../../../src/cache.rs#L81) · parent module: [[Module - cache]]

**Calls:** [[cache.Cache.remove_app]], [[types.Target.is_empty]]

**Called from:** actions.rs:152, cache.rs:295

**Execution flows:** [[Flow - Startup and Initial Enumeration]], [[Flow - App Restart Recovery]]

**Exercised by:** restart: relaunched app resolvable ([[Acceptance Suite]])
