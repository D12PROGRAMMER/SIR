---
kind: fn
module: actions
symbol: Service.press
source: src/actions.rs
line: 275
visibility: public
async: true
generated: true
---

# `Service.press`

```rust
    pub async fn press(&self, t: &Target) -> UiResult<Value>
```

[source](../../../../src/actions.rs#L275) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.resolve_node]], [[actions.action_names]], [[actions.snapshot]], [[resolver.resolve]], [[types.Target.is_empty]]

**Called from:** main.rs:75, mcp.rs:100

**Types in signature:** [[types.Target]]

**Errors produced:** `control_not_accessible`, `not_actionable` (see Error Model)

**Execution flows:** [[Flow - Press Action]]

**Exercised by:** press Save by id (all toolkits); disabled control -> not_actionable; disambiguation by ref succeeds ([[Acceptance Suite]])
