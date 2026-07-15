---
kind: fn
module: accessibility
symbol: inspect
source: src/accessibility.rs
line: 128
visibility: public
async: true
generated: true
---

# `inspect`

```rust
pub async fn inspect(conn: &zbus::Connection, obj: &ObjectRefOwned) -> UiResult<RawNode>
```

[source](../../../../src/accessibility.rs#L128) · parent module: [[Module - accessibility]]

**Calls:** [[accessibility.call]], [[types.Target.is_empty]]

**Called from:** cache.rs:181

**Types in signature:** [[accessibility.RawNode]]

**Exercised by:** acceptance suite (indirect)
