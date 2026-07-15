---
kind: fn
module: actions
symbol: Service.read
source: src/actions.rs
line: 230
visibility: public
async: true
generated: true
---

# `Service.read`

```rust
    pub async fn read(&self, t: &Target) -> UiResult<Value>
```

[source](../../../../src/actions.rs#L230) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.resolve_node]], [[actions.action_names]], [[cache.Cache.control_ref]], [[types.Target.is_empty]]

**Called from:** actions.rs:125, main.rs:74, mcp.rs:99

**Types in signature:** [[types.Target]]

**Exercised by:** acceptance suite (indirect)
