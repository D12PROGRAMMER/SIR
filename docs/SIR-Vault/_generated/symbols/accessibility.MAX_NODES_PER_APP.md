---
kind: const
module: accessibility
symbol: MAX_NODES_PER_APP
source: src/accessibility.rs
line: 40
visibility: public
async: false
generated: true
---

# `MAX_NODES_PER_APP`

Per-app walk limits so one huge app (browsers) can't blow up the cache.

```rust
pub const MAX_NODES_PER_APP: usize = 5000;
```

[source](../../../../src/accessibility.rs#L40) · parent module: [[Module - accessibility]]
