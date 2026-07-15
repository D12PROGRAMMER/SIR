---
kind: fn
module: actions
symbol: controls_result
source: src/actions.rs
line: 41
visibility: private
async: false
generated: true
---

# `controls_result`

Build a result object from a list of controls under `key`, hoisting `app` and `window` to the top level when every item that has one agrees. Items then omit the field (absent = the hoisted value); an item that genuinely has none carries an explicit `null`. `total`/`truncated` appear only when they add information beyond the list length.

```rust
fn controls_result(key: &str, items: Vec<ControlRef>, total: usize, cap: usize) -> Value
```

[source](../../../../src/actions.rs#L41) · parent module: [[Module - actions]]

**Called from:** actions.rs:176, actions.rs:198, actions.rs:227

**Types in signature:** [[types.ControlRef]]
