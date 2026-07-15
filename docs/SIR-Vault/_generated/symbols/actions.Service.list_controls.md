---
kind: fn
module: actions
symbol: Service.list_controls
source: src/actions.rs
line: 179
visibility: public
async: true
generated: true
---

# `Service.list_controls`

```rust
    pub async fn list_controls(&self, window: Option<String>) -> UiResult<Value>
```

[source](../../../../src/actions.rs#L179) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.find]], [[actions.Service.zconn]], [[actions.controls_result]], [[cache.Cache.control_ref]], [[cache.Cache.ensure_walked]]

**Called from:** main.rs:72, mcp.rs:96

**Exercised by:** acceptance suite (indirect)
