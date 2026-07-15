---
kind: fn
module: actions
symbol: Service.new
source: src/actions.rs
line: 99
visibility: public
async: true
generated: true
---

# `Service.new`

```rust
    pub async fn new() -> UiResult<Self>
```

[source](../../../../src/actions.rs#L99) · parent module: [[Module - actions]]

**Calls:** [[actions.supervisor]]

**Called from:** accessibility.rs:146, accessibility.rs:46, actions.rs:101, actions.rs:102, actions.rs:103, actions.rs:214, actions.rs:323, actions.rs:471, actions.rs:52, actions.rs:605, cache.rs:163, cache.rs:215

**Errors produced:** `atspi_error` (see Error Model)

**Execution flows:** [[Flow - Startup and Initial Enumeration]]

**Exercised by:** acceptance suite (indirect)
