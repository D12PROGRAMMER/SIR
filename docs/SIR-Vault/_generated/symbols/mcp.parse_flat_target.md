---
kind: fn
module: mcp
symbol: parse_flat_target
source: src/mcp.rs
line: 79
visibility: private
async: false
generated: true
---

# `parse_flat_target`

Flat args (ui_find) reuse the Target shape.

```rust
fn parse_flat_target(args: &Value) -> Result<Target, UiError>
```

[source](../../../../src/mcp.rs#L79) · parent module: [[Module - mcp]]

**Types in signature:** [[types.Target]], [[types.UiError]]

**Errors produced:** `invalid_argument` (see Error Model)
