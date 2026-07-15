---
kind: module
module: mcp
source: src/mcp.rs
generated: false
---

# Module: mcp

[source](../../../src/mcp.rs) — the stdio transport: newline-delimited JSON-RPC 2.0, hand-rolled (no MCP SDK dependency). Protocol behavior documented in [[MCP Interface]].

## Contents

- **[[mcp.serve]]** — the request loop: read line → parse → dispatch → write line. Malformed JSON → `-32700`; unknown method with id → `-32601`; notifications produce no output; EOF ends cleanly
- **[[mcp.call_tool]]** — maps the 9 `ui_*` tool names to [[actions.Service|Service]] methods
- **[[mcp.tools]]** / **[[mcp.target_schema]]** — tool definitions with JSON schemas; descriptions document the [[Output Conventions]] so client models know absence = default
- **[[mcp.parse_target]]** / **[[mcp.parse_flat_target]]** — argument extraction (`ui_find` takes flat args; action tools take a `target` object)
- **[[mcp.rpc_result]]** / **[[mcp.rpc_error]]** / **[[mcp.tool_text_result]]** — response builders; tool failures are `isError: true` results carrying [[Error Model]] payloads, not protocol errors

## Rule

This module contains **no accessibility logic** — it cannot construct a proxy or touch the cache. Its entire job is framing, schemas, and dispatch, which is what keeps the transport independently testable (the `core` battery runs against a server with zero desktop apps).

Full symbol list: [[Symbol Index]] § mcp.
