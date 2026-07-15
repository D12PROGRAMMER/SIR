---
kind: fn
module: accessibility
symbol: list_app_refs
source: src/accessibility.rs
line: 170
visibility: public
async: true
generated: true
---

# `list_app_refs`

List the accessible applications (children of the desktop root).

```rust
pub async fn list_app_refs(conn: &zbus::Connection) -> UiResult<Vec<ObjectRefOwned>>
```

[source](../../../../src/accessibility.rs#L170) · parent module: [[Module - accessibility]]

**Calls:** [[accessibility.call]], [[accessibility.registry_root]]

**Called from:** cache.rs:82

**Exercised by:** acceptance suite (indirect)
