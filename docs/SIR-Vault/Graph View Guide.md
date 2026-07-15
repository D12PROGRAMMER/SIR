---
kind: guide
generated: false
---

# Graph View Guide

The graph is pre-configured (`.obsidian/graph.json`) to show **architecture, not a hairball**. Close and reopen the graph view (or restart Obsidian) to pick up the settings.

## Color legend

| Color | Section | What the cluster means |
|---|---|---|
| 🟡 Gold | 00 - SIR Home | entry point |
| 🔵 Blue | 01 - Architecture | subsystem design |
| 🟢 Green | 02 - Modules | source-module boundaries |
| 🟠 Orange | 04 - Execution Flows | runtime behavior |
| 🩵 Teal | 05 - Interfaces | MCP/CLI surface |
| 🔴 Red | 06 - Tests | verification |
| 🟣 Purple | 07 - Performance | measurements |
| 🩷 Pink | 08 - Decisions | ADRs |
| 🟤 Brown | 09 - Operations | runbooks |
| ⚪ Gray | 03 - Symbols / _generated | the generated reference layer |
| 💛 Yellow | Unresolved Questions | known unknowns |

## Why the graph is readable now

1. **`_generated` is filtered out by default** (`-path:"_generated"` in the graph search box). The 90 generated symbol pages are a navigation layer, not architecture — with them visible, every graph question was answered "symbols".
2. **Boilerplate links were demoted to plain text** in generated pages (every page linked *Error Model* and *Acceptance Suite*, so those two notes dominated the graph regardless of filters). Remaining edges are real structure: symbol → parent module, symbol → symbols it calls, symbol → types it uses.
3. **Forces tuned** for cluster separation (higher repel, shorter links, arrows on).

## How to read it

- **Module boundaries**: green nodes and their blue architecture neighbors — e.g. [[Module - cache]] sits between [[Cache and Enumeration]] and the flows that walk it.
- **Execution flow**: follow orange; each flow note chains the architecture notes and ADRs it exercises.
- **Why-questions**: pink ADRs attach to exactly the notes they justify.

## Useful variations

- **See the code layer**: clear the search box — gray symbol nodes cluster around their green parent modules (each generated page links its module).
- **One subsystem**: open any note and use **local graph** (depth 1–2); on e.g. [[actions.Service.press]] this shows its real call tree.
- **Only architecture + flows**: `path:"01 - Architecture" OR path:"04 - Execution Flows"`.
