---
kind: fn
module: cache
symbol: Cache.ensure_walked
source: src/cache.rs
line: 290
visibility: public
async: true
generated: true
---

# `Cache.ensure_walked`

Ensure apps are listed and the relevant apps' trees are walked.

```rust
    pub async fn ensure_walked(
        &mut self,
        conn: &zbus::Connection,
        app_filter: Option<&str>,
    ) -> UiResult<()>
```

[source](../../../../src/cache.rs#L290) · parent module: [[Module - cache]]

**Calls:** [[cache.Cache.sync_apps]], [[cache.Cache.walk_app]]

**Called from:** actions.rs:165, actions.rs:182, actions.rs:204, actions.rs:420, actions.rs:503, resolver.rs:36

**Execution flows:** [[Flow - Target Resolution]]

**Exercised by:** acceptance suite (indirect)
