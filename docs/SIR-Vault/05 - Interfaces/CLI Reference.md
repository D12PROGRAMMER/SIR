---
kind: interface
generated: false
---

# CLI Reference

The development CLI ([[main.run_cli]]) drives the **same `Service` methods** as MCP — one implementation, two frontends. Useful over SSH into the aios VM without an MCP client.

```
ui-mcp                       # MCP server on stdio (default mode)
ui-mcp cli apps
ui-mcp cli windows [app]
ui-mcp cli controls [window]
ui-mcp cli find   [app=X] [window=X] [id=X] [role=X] [name=X]
ui-mcp cli read   k=v...
ui-mcp cli press  k=v...
ui-mcp cli focus  k=v...
ui-mcp cli set-value <value> k=v...
ui-mcp cli wait-for <timeout_ms> k=v...
```

- Targets are `k=v` pairs (`app=`, `window=`, `id=`, `ref=`, `role=`, `name=`)
- Output: pretty-printed JSON, same payloads as MCP tool results
- Exit codes: `0` success, `2` tool error (payload still printed), `1` fatal (bus unreachable)
- Requires the desktop session env: `source /root/.desktop-env` first ([[Running SIR]])

Example:

```bash
source /root/.desktop-env
ui-mcp cli press app=test-app id=save-project
```
