---
kind: fn
module: resolver
symbol: verify_live
source: src/resolver.rs
line: 101
visibility: private
async: true
generated: true
---

# `verify_live`

Confirm the resolved object is still alive on the bus; refresh its states.

```rust
async fn verify_live(
    conn: &zbus::Connection,
    cache: &mut Cache,
    node_ref: &str,
) -> UiResult<Resolved>
```

[source](../../../../src/resolver.rs#L101) · parent module: [[Module - resolver]]

**Calls:** [[cache.Cache.remove_node]]

**Called from:** resolver.rs:49, resolver.rs:64, resolver.rs:82

**Types in signature:** [[cache.Cache]], [[resolver.Resolved]]

**Errors produced:** `stale_target` (see Error Model)

**Execution flows:** [[Flow - Target Resolution]], [[Flow - App Restart Recovery]]
