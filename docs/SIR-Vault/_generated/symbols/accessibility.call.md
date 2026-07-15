---
kind: fn
module: accessibility
symbol: call
source: src/accessibility.rs
line: 28
visibility: public
async: true
generated: true
---

# `call`

Await a single AT-SPI call with a hard timeout. `what` labels the op in errors.

```rust
pub async fn call<F, T>(what: &str, fut: F) -> UiResult<T>
where
    F: std::future::Future<Output = zbus::Result<T>>,
```

[source](../../../../src/accessibility.rs#L28) · parent module: [[Module - accessibility]]

**Called from:** accessibility.rs:131, accessibility.rs:132, accessibility.rs:133, accessibility.rs:136, accessibility.rs:141, accessibility.rs:148, accessibility.rs:172, actions.rs:220, actions.rs:292, actions.rs:319, actions.rs:603, actions.rs:615

**Errors produced:** `atspi_error` (see Error Model)

**Exercised by:** acceptance suite (indirect)
