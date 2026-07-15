---
kind: fn
module: actions
symbol: Service.resolve_node
source: src/actions.rs
line: 137
visibility: private
async: true
generated: true
---

# `Service.resolve_node`

Shared prologue for every target-addressed operation (read/press/ set_value/focus): live connection, strict resolution, and a snapshot of the resolved node. The cache lock is released before returning so callers do their AT-SPI work without holding it.

```rust
    async fn resolve_node(&self, t: &Target) -> UiResult<(zbus::Connection, NodeEntry)>
```

[source](../../../../src/actions.rs#L137) · parent module: [[Module - actions]]

**Calls:** [[actions.Service.zconn]]

**Called from:** actions.rs:231, actions.rs:276, actions.rs:339, actions.rs:380

**Types in signature:** [[cache.NodeEntry]], [[types.Target]]

**Errors produced:** `stale_target` (see Error Model)
