---
kind: fn
module: actions
symbol: liveness_ping
source: src/actions.rs
line: 547
visibility: private
async: true
generated: true
---

# `liveness_ping`

```rust
async fn liveness_ping(conn: &zbus::Connection) -> UiResult<()>
```

[source](../../../../src/actions.rs#L547) · parent module: [[Module - actions]]

**Called from:** actions.rs:533

**Errors produced:** `atspi_error` (see Error Model)

**Execution flows:** [[Flow - Bus Restart Recovery]]
