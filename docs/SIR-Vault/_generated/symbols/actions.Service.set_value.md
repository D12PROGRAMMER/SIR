---
kind: fn
module: actions
symbol: Service.set_value
source: src/actions.rs
line: 338
visibility: public
async: true
generated: true
---

# `Service.set_value`

```rust
    pub async fn set_value(&self, t: &Target, value: &Value) -> UiResult<Value>
```

[source](../../../../src/actions.rs#L338) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.resolve_node]]

**Called from:** main.rs:88, mcp.rs:106

**Types in signature:** [[types.Target]]

**Errors produced:** `atspi_error`, `control_not_accessible`, `not_actionable` (see Error Model)

**Exercised by:** set_value on text field (GTK, Qt) ([[Acceptance Suite]])
