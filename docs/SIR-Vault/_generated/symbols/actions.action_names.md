---
kind: fn
module: actions
symbol: action_names
source: src/actions.rs
line: 81
visibility: public
async: false
generated: true
---

# `action_names`

Normalize action names across toolkits: lowercase; unnamed actions (Chromium exposes these) become "default" (index 0) or "action-N".

```rust
pub fn action_names(actions: &[atspi::Action]) -> Vec<String>
```

[source](../../../../src/actions.rs#L81) · parent module: [[Module - actions]]

**Calls:** [[types.Target.is_empty]]

**Called from:** actions.rs:221, actions.rs:267, actions.rs:306

**Execution flows:** [[Flow - Press Action]]

**Exercised by:** acceptance suite (indirect)
