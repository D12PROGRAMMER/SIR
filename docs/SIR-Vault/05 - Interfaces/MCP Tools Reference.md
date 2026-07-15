---
kind: interface
generated: false
---

# MCP Tools Reference

Nine tools, defined in [[Module - mcp]]. Transport behavior: [[MCP Interface]]. Payload compaction rules: [[Output Conventions]].

## Target object

Accepted by `ui_read`, `ui_press`, `ui_set_value`, `ui_focus` (as `target`) and `ui_wait_for` (as `query`); `ui_find` takes the same fields flat.

| Field | Meaning | Notes |
|---|---|---|
| `app` | application name on the a11y bus | case-insensitive exact |
| `window` | window title (accessible name of top-level) | case-insensitive exact |
| `id` | application-provided accessible ID | **preferred**; exact then leaf-segment |
| `ref` | session ref from a previous result | `app-N:node-M` |
| `role` | AT-SPI role | aliases: `push-button`→`button`, `entry/textbox/textfield`→`text` |
| `name` | exact accessible name | case-insensitive |

## Tools

| Tool | Arguments | Returns |
|---|---|---|
| `ui_list_apps` | — | `{apps: [{ref, name}]}` |
| `ui_list_windows` | `app?` | `{windows: [ControlRef]}` |
| `ui_list_controls` | `window?` | `{controls: […]}`, cap 500 |
| `ui_find` | flat target fields | `{matches: […]}`, cap 50, actions filled for first 10 |
| `ui_read` | `target` | ControlRef + `description?`, `value {current,min,max}?`, `text?` (≤4000 chars) + `text_length`, `actions` |
| `ui_press` | `target` | `{pressed, ref, action, state \| state_before+state_after}` |
| `ui_set_value` | `target`, `value` (number→Value iface, string→EditableText) | `{set, ref, value\|text}` |
| `ui_focus` | `target` | `{focused, ref, state}` |
| `ui_wait_for` | `query`, `timeout_ms` (default 5000) | `{found: ControlRef, waited_ms}` |

## Example (the project's success condition)

```json
→ {"method":"tools/call","params":{"name":"ui_press",
   "arguments":{"target":{"app":"test-app","id":"save-project"}}}}
← {"pressed":true,"ref":"app-1:node-5","action":"click","state":{"name":"Save"}}
```

Errors: structured payloads with `isError: true` — see [[Error Model]]. Ambiguity responses carry up to 10 `candidates` for disambiguation by `ref`.
