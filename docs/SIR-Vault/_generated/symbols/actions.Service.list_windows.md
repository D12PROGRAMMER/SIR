---
kind: fn
module: actions
symbol: Service.list_windows
source: src/actions.rs
line: 162
visibility: public
async: true
generated: true
---

# `Service.list_windows`

```rust
    pub async fn list_windows(&self, app: Option<String>) -> UiResult<Value>
```

[source](../../../../src/actions.rs#L162) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.find]], [[actions.Service.zconn]], [[actions.controls_result]], [[cache.Cache.control_ref]], [[cache.Cache.ensure_walked]]

**Called from:** main.rs:71, mcp.rs:89

**Exercised by:** acceptance suite (indirect)
