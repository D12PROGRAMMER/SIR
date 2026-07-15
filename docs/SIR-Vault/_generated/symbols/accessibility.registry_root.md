---
kind: fn
module: accessibility
symbol: registry_root
source: src/accessibility.rs
line: 85
visibility: public
async: true
generated: true
---

# `registry_root`

The desktop root object: its children are the accessible applications.

```rust
pub async fn registry_root<'a>(conn: &zbus::Connection) -> UiResult<AccessibleProxy<'a>>
```

[source](../../../../src/accessibility.rs#L85) · parent module: [[Module - accessibility]]

**Called from:** accessibility.rs:171, actions.rs:548

**Exercised by:** acceptance suite (indirect)
