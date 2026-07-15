---
kind: fn
module: actions
symbol: Service.find
source: src/actions.rs
line: 201
visibility: public
async: true
generated: true
---

# `Service.find`

```rust
    pub async fn find(&self, t: &Target) -> UiResult<Value>
```

[source](../../../../src/actions.rs#L201) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.zconn]], [[actions.action_names]], [[actions.controls_result]], [[cache.Cache.control_ref]], [[cache.Cache.ensure_walked]]

**Called from:** actions.rs:166, actions.rs:187, actions.rs:212, actions.rs:428, cache.rs:361, main.rs:73, mcp.rs:98, resolver.rs:47, resolver.rs:80

**Types in signature:** [[types.Target]]

**Exercised by:** find Save by id (all toolkits) ([[Acceptance Suite]])
