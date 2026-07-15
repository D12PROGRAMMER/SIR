---
kind: fn
module: actions
symbol: Service.focus
source: src/actions.rs
line: 379
visibility: public
async: true
generated: true
---

# `Service.focus`

```rust
    pub async fn focus(&self, t: &Target) -> UiResult<Value>
```

[source](../../../../src/actions.rs#L379) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.resolve_node]], [[actions.snapshot]]

**Called from:** main.rs:76, mcp.rs:108

**Types in signature:** [[types.Target]]

**Errors produced:** `atspi_error`, `control_not_accessible` (see Error Model)

**Exercised by:** acceptance suite (indirect)
