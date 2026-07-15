---
kind: adr
status: accepted
date: 2026-07-14
generated: false
---

# ADR: `ui_press`, not `ui.press`

**Context.** The original specification wrote tool names with dots (`ui.press`). MCP clients — including Claude, whose tool-name charset is `[a-zA-Z0-9_-]{1,64}` — cannot call dotted names; the tools would be unreachable by the primary consumer.

**Decision.** All nine tools use underscores: `ui_list_apps` … `ui_wait_for`. The only deliberate deviation from the original interface spec.

**Consequences.** None functional; the dot form appears nowhere. Documented so future readers don't "fix" it back.
