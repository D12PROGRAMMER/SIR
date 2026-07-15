---
kind: fn
module: actions
symbol: snapshot
source: src/actions.rs
line: 601
visibility: private
async: true
generated: true
---

# `snapshot`

Small live-state snapshot used for before/after comparison around actions. Compact: only exceptional values appear (enabled/visible only when false, focused only when true, name only when non-empty; absence = the default).

```rust
async fn snapshot(conn: &zbus::Connection, obj: &atspi::ObjectRefOwned) -> Value
```

[source](../../../../src/actions.rs#L601) · parent module: [[Module - actions]]

**Calls:** [[types.Target.is_empty]]

**Called from:** actions.rs:318, actions.rs:321, actions.rs:393

**Execution flows:** [[Flow - Press Action]]
