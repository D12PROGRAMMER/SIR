---
kind: const
module: accessibility
symbol: CALL_TIMEOUT
source: src/accessibility.rs
line: 25
visibility: public
async: false
generated: true
---

# `CALL_TIMEOUT`

Per-call ceiling for any single AT-SPI D-Bus round trip. AT-SPI has no built-in timeout, so an unresponsive application (busy renderer, wedged event loop) would otherwise hang the whole server. Bounding every call keeps one bad app from freezing the control plane.

```rust
pub const CALL_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(2);
```

[source](../../../../src/accessibility.rs#L25) · parent module: [[Module - accessibility]]
