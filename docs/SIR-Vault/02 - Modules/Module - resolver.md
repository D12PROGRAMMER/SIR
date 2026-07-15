---
kind: module
module: resolver
source: src/resolver.rs
generated: false
---

# Module: resolver

[source](../../../src/resolver.rs) — target resolution with strict precedence and no silent disambiguation. Small on purpose (~150 lines): the entire "which control did you mean" policy lives here and nowhere else.

## Contents

- **[[resolver.resolve]]** — the precedence machine: accessible ID → session ref → app/window/role/exact-name → error. Exactly-one wins; multiple → `ambiguous` with candidates; zero everywhere → `not_found` ([[Resolution and References]])
- **[[resolver.verify_live]]** — post-resolution `GetState` liveness check; evicts dead nodes (→ `stale_target`) and refreshes `enabled/visible/focused` so action preconditions use live truth
- **[[resolver.Resolved]]** — the (deliberately minimal) success type: just the node_ref
- **[[resolver.describe]]** — human-readable criteria for error messages (`app=test-app id=nope`), no debug formatting

## Notable behavior

- ID strategy runs `Filter` twice: exact `accessible_id` match first, then leaf-segment match (Qt's dotted ancestry) — both ambiguity-checked ([[ADR - Qt Leaf ID Matching]])
- A `ref` that isn't in the cache map is *always* `stale_target`, never `not_found` — the distinction tells the caller "re-find" vs "doesn't exist"

Exercised by: unknown id → not_found; duplicate controls → ambiguous; removed widget ref → stale_target ([[Acceptance Suite]]).

Full symbol list: [[Symbol Index]] § resolver.
