---
kind: fn
module: resolver
symbol: resolve
source: src/resolver.rs
line: 21
visibility: public
async: true
generated: true
---

# `resolve`

```rust
pub async fn resolve(
    conn: &zbus::Connection,
    cache: &mut Cache,
    target: &Target,
) -> UiResult<Resolved>
```

[source](../../../../src/resolver.rs#L21) · parent module: [[Module - resolver]]

**Calls:** [[actions.Service.find]], [[cache.Cache.control_ref]], [[cache.Cache.ensure_walked]], [[resolver.describe]], [[resolver.verify_live]], [[types.Target.is_empty]]

**Called from:** actions.rs:140, actions.rs:277

**Types in signature:** [[cache.Cache]], [[resolver.Resolved]], [[types.Target]]

**Errors produced:** `ambiguous`, `invalid_argument`, `not_found`, `stale_target` (see Error Model)

**Execution flows:** [[Flow - Target Resolution]]

**Exercised by:** duplicate controls -> ambiguous; unknown id -> not_found; removed widget ref -> stale_target ([[Acceptance Suite]])
