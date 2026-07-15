---
kind: architecture
generated: false
---

# MCP Interface

SIR is an MCP server over **stdio**: one JSON-RPC 2.0 message per line (`\n`-delimited), responses written the same way. Implemented by hand in [[Module - mcp]] (~200 lines) — no SDK dependency. Protocol version echoed from the client, default `2025-06-18`.

## Methods

| Method | Behavior |
|---|---|
| `initialize` | capabilities `{tools:{}}`, serverInfo `ui-mcp` + crate version |
| `notifications/initialized` (and any notification) | ignored, no response line |
| `ping` | `{}` |
| `tools/list` | the 9 tool definitions with JSON schemas |
| `tools/call` | dispatch to [[actions.Service|Service]]; tool errors are `isError: true` results, not protocol errors |
| unknown method with id | `-32601` |
| unparseable line | `-32700` |

Malformed input never kills the loop; EOF on stdin ends the process cleanly. Verified by [[Acceptance Suite]] (`core` battery).

## Tools

`ui_list_apps`, `ui_list_windows`, `ui_list_controls`, `ui_find`, `ui_read`, `ui_press`, `ui_set_value`, `ui_focus`, `ui_wait_for` — full schemas and examples in [[MCP Tools Reference]].

Tool names use underscores, not the `ui.press` dot form: MCP clients (including Claude) restrict tool names to `[a-zA-Z0-9_-]` ([[ADR - Underscored Tool Names]]).

## Result shape

Tool results are `content: [{type: "text", text: "<compact JSON>"}]`. Payloads follow the token-economy rules in [[Output Conventions]]: default-valued fields omitted, shared `app`/`window` hoisted, debug formatting banned from error messages.

## Error mapping

Every operational failure is a structured payload `{error: <code>, message, candidates?}` with `isError: true` — codes in [[Error Model]]. Protocol-level errors (parse, unknown method) use JSON-RPC error objects.

Request handling is sequential by design; see [[Process and Connection Model]].
