---
kind: index
generated: false
---

# Symbols Overview

Function-level documentation is **generated from source**, not handwritten — one page per function, method, type, trait, enum, and constant, each with frontmatter, signature, real line anchors, callers, callees, project types, error codes, flow links, and test links.

- **[[Symbol Index]]** — all 91 symbols, grouped by module
- Pages live in `_generated/symbols/` and are named `<module>.<symbol>.md` (methods: `<module>.<Type>.<method>.md`)
- Regenerate after code changes: `python3 test/gen_symbols.py` (runs against `src/`, deterministic)

## Hand-curated deep dives

Only symbols that are architecturally, concurrency-, or performance-critical get prose beyond the generated page — via their module and architecture notes:

| Symbol | Why it matters | Prose |
|---|---|---|
| [[actions.supervisor]] | connection lifecycle, dual sockets, reconnect | [[Process and Connection Model]], [[Reconnection]] |
| [[actions.handle_event]] | the no-I/O rule; cache correctness under floods | [[Event Processing]], [[ADR - No IO in Event Handler]] |
| [[actions.Service.press]] | the canonical end-to-end action | [[Flow - Press Action]] |
| [[cache.Cache.walk_from]] / [[cache.Cache.walk_app]] | caps, budget, ref stability | [[Cache and Enumeration]] |
| [[resolver.resolve]] | the no-silent-choice precedence machine | [[Resolution and References]] |
| [[accessibility.call]] | the 2s bound that keeps SIR unfreezable | [[Timeout Model]] |
| [[mcp.serve]] | transport robustness | [[MCP Interface]] |
