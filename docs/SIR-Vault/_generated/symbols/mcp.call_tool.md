---
kind: fn
module: mcp
symbol: call_tool
source: src/mcp.rs
line: 84
visibility: private
async: true
generated: true
---

# `call_tool`

```rust
async fn call_tool(svc: &Service, name: &str, args: &Value) -> Result<Value, UiError>
```

[source](../../../../src/mcp.rs#L84) · parent module: [[Module - mcp]]

**Calls:** [[actions.Service.find]], [[actions.Service.focus]], [[actions.Service.list_apps]], [[actions.Service.list_controls]], [[actions.Service.list_windows]], [[actions.Service.press]], [[actions.Service.read]], [[actions.Service.set_value]], [[actions.Service.wait_for]]

**Called from:** mcp.rs:180

**Types in signature:** [[actions.Service]], [[types.UiError]]

**Errors produced:** `invalid_argument` (see Error Model)

**Execution flows:** [[Flow - MCP Request Handling]]
