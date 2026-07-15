---
kind: fn
module: main
symbol: run_cli
source: src/main.rs
line: 67
visibility: private
async: true
generated: true
---

# `run_cli`

```rust
async fn run_cli(svc: &Service, args: &[String]) -> types::UiResult<serde_json::Value>
```

[source](../../../../src/main.rs#L67) · parent module: [[Module - main]]

**Calls:** [[actions.Service.find]], [[actions.Service.focus]], [[actions.Service.list_apps]], [[actions.Service.list_controls]], [[actions.Service.list_windows]], [[actions.Service.press]], [[actions.Service.read]], [[actions.Service.set_value]], [[actions.Service.wait_for]]

**Called from:** main.rs:27

**Types in signature:** [[actions.Service]]

**Errors produced:** `invalid_argument` (see Error Model)
