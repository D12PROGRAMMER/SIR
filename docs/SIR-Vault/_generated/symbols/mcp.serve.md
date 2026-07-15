---
kind: fn
module: mcp
symbol: serve
source: src/mcp.rs
line: 138
visibility: public
async: true
generated: true
---

# `serve`

```rust
pub async fn serve(svc: Service) -> std::io::Result<()>
```

[source](../../../../src/mcp.rs#L138) · parent module: [[Module - mcp]]

**Calls:** [[mcp.call_tool]], [[mcp.rpc_error]], [[mcp.rpc_result]], [[mcp.tool_text_result]], [[mcp.tools]], [[types.Target.is_empty]], [[types.UiError.to_json]]

**Called from:** main.rs:41

**Types in signature:** [[actions.Service]]

**Execution flows:** [[Flow - MCP Request Handling]]

**Exercised by:** acceptance suite (indirect)
