---
kind: architecture
generated: false
---

# Error Model

All operational errors are one enum — [[types.UiError]] — serialized as `{error: <code>, message, candidates?}` and returned as MCP tool results with `isError: true`. Messages are plain criteria (`no control matches app=test-app id=nope`), never Rust debug formatting ([[Output Conventions]]).

| Code | Meaning | Typical producer |
|---|---|---|
| `not_found` | no control matched the target | [[resolver.resolve]] |
| `ambiguous` | multiple matched; `candidates` (≤10 [[types.ControlRef]]) included so the caller can pick a `ref` | [[resolver.resolve]], [[actions.Service.wait_for]] |
| `stale_target` | a previously returned ref no longer resolves to a live object | [[resolver.verify_live]] |
| `control_not_accessible` | control exists but exposes no usable interface for the operation (no Action/Value/EditableText/Component) | [[actions.Service.press]], [[actions.Service.set_value]] |
| `not_actionable` | control resolved but is hidden or disabled | [[actions.Service.press]] |
| `invalid_argument` | malformed target/value, unknown tool | [[Module - mcp]], resolver |
| `timeout` | `ui_wait_for` deadline expired | [[actions.Service.wait_for]] |
| `atspi_error` | underlying D-Bus failure, per-call timeout, or "bus disconnected; reconnecting" | anywhere via [[accessibility.call]] |

## Design rules

1. **`ambiguous` always carries candidates.** The caller disambiguates; SIR never guesses ([[Resolution and References]]).
2. **`control_not_accessible` is the honest dead end** for apps that expose nothing usable — per the project brief, SIR does not fall back to synthetic input or vision.
3. **`not_actionable` ≠ `control_not_accessible`**: the former is a state problem (disabled/hidden, may change), the latter a capability problem (interface absent).
4. Transport-level failures (bad JSON, unknown method) are JSON-RPC protocol errors, not tool errors — see [[MCP Interface]].

Every code above is exercised by the [[Acceptance Suite]] except `atspi_error`, which is exercised by [[Bus Restart Test]] outage-window calls.
