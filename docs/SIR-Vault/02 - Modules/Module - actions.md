---
kind: module
module: actions
source: src/actions.rs
generated: false
---

# Module: actions

[source](../../../src/actions.rs) — the service layer and the connection lifecycle. Largest module; the only one that spawns tasks.

## Contents

- **`Service`** — the 9 tool operations behind MCP and the CLI: [[actions.Service.list_apps]], [[actions.Service.list_windows]], [[actions.Service.list_controls]], [[actions.Service.find]], [[actions.Service.read]], [[actions.Service.press]], [[actions.Service.set_value]], [[actions.Service.focus]], [[actions.Service.wait_for]]
- **[[actions.supervisor]]** — owns both AT-SPI connections; startup enumeration, event pump, liveness ping, reconnect backoff ([[Reconnection]], [[Process and Connection Model]])
- **[[actions.handle_event]]** — in-memory cache patching from `ObjectEvents` ([[Event Processing]])
- **[[actions.action_names]]** — cross-toolkit action-name normalization ([[ADR - Action Name Normalization]])
- **[[actions.controls_result]]** — output compaction: hoists shared `app`/`window`, elides redundant `total`/`truncated` ([[Output Conventions]])
- **[[actions.snapshot]]** — compact live-state capture around actions
- **[[actions.liveness_ping]]** — registry-root round trip used as the health probe

## Press action selection

`PRESS_ACTION_PRIORITY = ["default", "dodefault", "press", "click", "activate", "push"]` — first name match wins; a single action, or all-unnamed actions (Chromium), falls back to index 0. See [[Flow - Press Action]].

## Concurrency shape

`Service` is a thin handle over `Inner { conn: RwLock<Option<Arc<AccessibilityConnection>>>, cache: Mutex<Cache>, ready_tx }`. Tool calls acquire a connection clone via [[actions.Service.zconn]] (fails fast during outages), then the cache mutex. `Service::new` blocks up to 15s for the supervisor's first successful connect + enumeration.

The four target-addressed operations (read/press/set_value/focus) share one prologue — [[actions.Service.resolve_node]]: connection, strict resolution, node snapshot, stale guard — introduced by the combined optimization pass ([[Combined Optimization Pass]]) to replace four verbatim copies. The cache lock is released before any AT-SPI work.

Full symbol list: [[Symbol Index]] § actions.
