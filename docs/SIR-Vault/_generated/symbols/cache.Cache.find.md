---
kind: fn
module: cache
symbol: Cache.find
source: src/cache.rs
line: 361
visibility: public
async: false
generated: true
---

# `Cache.find`

```rust
    pub fn find(&self, f: &Filter) -> Vec<&NodeEntry>
```

[source](../../../../src/cache.rs#L361) · parent module: [[Module - cache]]

**Calls:** [[cache.Cache.matches]], [[types.Target.is_empty]]

**Called from:** actions.rs:166, actions.rs:187, actions.rs:201, actions.rs:212, actions.rs:428, main.rs:73, mcp.rs:98, resolver.rs:47, resolver.rs:80

**Types in signature:** [[cache.Filter]], [[cache.NodeEntry]]

**Exercised by:** acceptance suite (indirect)
