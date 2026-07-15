---
kind: fn
module: actions
symbol: Service.wait_for
source: src/actions.rs
line: 397
visibility: public
async: true
generated: true
---

# `Service.wait_for`

```rust
    pub async fn wait_for(&self, query: &Target, timeout_ms: u64) -> UiResult<Value>
```

[source](../../../../src/actions.rs#L397) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.find]], [[actions.Service.zconn]], [[cache.Cache.control_ref]], [[cache.Cache.ensure_walked]], [[cache.Cache.mark_app_dirty]]

**Called from:** main.rs:92, mcp.rs:114

**Types in signature:** [[types.Target]]

**Errors produced:** `ambiguous`, `timeout` (see Error Model)

**Execution flows:** [[Flow - Event Processing]]

**Exercised by:** dynamically added widget found via events; web button waits (Firefox/Electron) ([[Acceptance Suite]])
