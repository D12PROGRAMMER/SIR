---
kind: module
module: main
source: src/main.rs
generated: false
---

# Module: main

[source](../../../src/main.rs) — the entrypoint. Two modes:

- **Default: MCP server.** Builds [[actions.Service|Service]] (blocks up to 15s for first connect + enumeration), then runs [[mcp.serve]] on stdio.
- **`ui-mcp cli <cmd>`** — the development/diagnostic CLI kept from build step 4 of the original spec: `apps`, `windows [app]`, `controls [window]`, `find k=v…`, `read|press|focus k=v…`, `set-value <value> k=v…`, `wait-for <timeout_ms> k=v…`. Prints pretty JSON; exit code 2 on tool errors. See [[CLI Reference]]

## Contents

- **[[main.main]]** — mode selection, fatal-error handling (exit 1 if the accessibility bus is unreachable)
- **[[main.run_cli]]** — CLI dispatch over the same `Service` methods as MCP — there is exactly one implementation of every operation
- **[[main.kv_target]]** — `k=v` pairs → [[types.Target]]

Full symbol list: [[Symbol Index]] § main.
