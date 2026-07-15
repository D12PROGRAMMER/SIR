---
kind: home
generated: false
---

# SIR

**SIR** is the project codename for the accessibility-to-MCP adapter in this repository (crate and binary name: `ui-mcp`). It lets an AI agent **query and operate desktop controls directly through the Linux accessibility system (AT-SPI)** — no screenshots, no OCR, no pointer movement, no keyboard simulation, no other model in the loop.

```
Claude → MCP tool → AT-SPI accessible object → direct accessibility action
```

## What SIR does

- Enumerates accessible applications, windows, and controls on the AT-SPI bus
- Resolves targets by application-provided ID, session ref, or app/window/role/name
- Invokes semantic actions (press/click/activate), sets values, grabs focus, reads state
- Maintains an event-patched in-memory cache with stable session references
- Survives application restarts and accessibility-bus restarts without a server restart
- Speaks MCP (JSON-RPC 2.0 over stdio) — see [[MCP Interface]]

## What SIR does not do

- No computer vision, screenshots, or OCR
- No mouse or keyboard synthesis — if a control has no semantic action, SIR reports [[Error Model|control_not_accessible]] rather than faking input
- No custom compositor, no new accessibility protocol, no model-to-model translation
- No workarounds for applications that expose nothing over AT-SPI

## Start here

- [[Graph View Guide]] — color legend and how to read the graph
- [[System Overview]] — the one-page architecture
- [[Process and Connection Model]] — processes, sockets, and why there are two D-Bus connections
- [[Flow - Press Action]] — the canonical end-to-end operation
- [[Symbols Overview]] — every function/type, generated from source
- [[Acceptance Suite]] — how 34/34 + 4/4 is verified
- [[Baseline (2026-07-14)]] — measured performance baseline
- [[Unresolved Questions]] — what we don't know for sure

## Sections

| Folder | Contents |
|---|---|
| `01 - Architecture` | Subsystem design: [[System Overview]], [[AT-SPI Integration]], [[Cache and Enumeration]], [[Event Processing]], [[Resolution and References]], [[Reconnection]], [[Timeout Model]], [[Error Model]], [[MCP Interface]], [[Process and Connection Model]] |
| `02 - Modules` | Readable per-module docs ([[Module - actions]], [[Module - cache]], …) |
| `03 - Symbols` | [[Symbols Overview]] → generated per-symbol reference |
| `04 - Execution Flows` | Mermaid flows traced from real code paths |
| `05 - Interfaces` | [[MCP Tools Reference]], [[CLI Reference]], [[Output Conventions]] |
| `06 - Tests` | [[Acceptance Suite]], [[Bus Restart Test]], [[Test Harness]], [[Toolkit Behavior Matrix]] |
| `07 - Performance` | [[Baseline (2026-07-14)]], [[Optimization Pass (2026-07-14)]], [[Combined Optimization Pass]], [[Combined Optimization Candidates]], [[Performance Model]] |
| `08 - Decisions` | ADRs — each starts with "ADR-" |
| `09 - Operations` | [[Running SIR]], [[Desktop Service and Looking Glass]], [[Troubleshooting]] |
| `_generated` | Machine-generated symbol pages (`test/gen_symbols.py`) |

Source of truth: [src/](../../src/) and [test/](../../test/). The vault describes; the code decides.
