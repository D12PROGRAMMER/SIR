---
kind: fn
module: actions
symbol: Service.list_apps
source: src/actions.rs
line: 149
visibility: public
async: true
generated: true
---

# `Service.list_apps`

```rust
    pub async fn list_apps(&self) -> UiResult<Value>
```

[source](../../../../src/actions.rs#L149) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.zconn]], [[cache.Cache.sync_apps]]

**Called from:** main.rs:70, mcp.rs:86

**Exercised by:** app enumerated automatically ([[Acceptance Suite]])
