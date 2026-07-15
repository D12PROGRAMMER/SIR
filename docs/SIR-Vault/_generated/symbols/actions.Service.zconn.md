---
kind: fn
module: actions
symbol: Service.zconn
source: src/actions.rs
line: 124
visibility: private
async: true
generated: true
---

# `Service.zconn`

A live zbus connection, or a clear error while reconnecting.

```rust
    async fn zconn(&self) -> UiResult<zbus::Connection>
```

[source](../../../../src/actions.rs#L124) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.read]]

**Called from:** actions.rs:138, actions.rs:150, actions.rs:163, actions.rs:180, actions.rs:202, actions.rs:402

**Errors produced:** `atspi_error` (see Error Model)
