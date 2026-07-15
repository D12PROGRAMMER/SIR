---
kind: fn
module: cache
symbol: Cache.patch_state
source: src/cache.rs
line: 402
visibility: public
async: false
generated: true
---

# `Cache.patch_state`

```rust
    pub fn patch_state(&mut self, key: &NodeKey, state: State, on: bool)
```

[source](../../../../src/cache.rs#L402) · parent module: [[Module - cache]]

**Calls:** [[cache.Cache.remove_subtree]]

**Called from:** actions.rs:566

**Exercised by:** acceptance suite (indirect)
