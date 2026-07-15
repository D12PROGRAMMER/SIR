# SIR

**SIR** is an accessibility-to-MCP adapter (crate/binary: `ui-mcp`). It lets an AI agent **query and operate Linux desktop controls directly through the accessibility system (AT-SPI)** — no screenshots, no OCR, no pointer movement, no keyboard simulation, no second model.

```
Agent → MCP tool → AT-SPI accessible object → direct accessibility action
```

```jsonc
// the project's success condition — executes the app's Save handler, no synthetic input
ui_press({ "app": "example-editor", "id": "save-project" })
```

## What it does

- Enumerates accessible applications, windows, and controls on the AT-SPI bus
- Resolves targets by application-provided ID, session ref, or app/window/role/name — **never silently guesses** (ambiguous matches return candidates)
- Invokes semantic actions (press/click/activate), sets values, grabs focus, reads state
- Event-patched in-memory cache with **session refs stable across re-walks** while the object lives
- Recovers from application restarts and accessibility-bus restarts without a server restart
- Speaks MCP (JSON-RPC 2.0 over stdio); also ships a diagnostic CLI (`ui-mcp cli …`)

## What it does not do

No computer vision, screenshots, or OCR. No mouse/keyboard synthesis — a control with no semantic action returns `control_not_accessible` rather than faking input. No custom compositor, no new accessibility protocol.

## Build

```bash
cargo build --release          # native
scripts/package-deb.sh amd64   # static musl .deb → dist/
scripts/package-deb.sh arm64   # static musl .deb (runs on any glibc, incl. Pi/Bookworm)
```

Static musl builds carry no libc dependency, so one binary runs across Linux distributions regardless of glibc version. SIR has no C dependencies.

## Run

```bash
ui-mcp                # MCP server on stdio (newline-delimited JSON-RPC 2.0)
ui-mcp cli apps       # quick check against the current desktop session
```

Requires a session D-Bus with an AT-SPI bus (`at-spi2-core`) and `DBUS_SESSION_BUS_ADDRESS` pointing at the target desktop.

## Architecture

Single Rust process, seven modules:

| Module | Role |
|---|---|
| `mcp` | stdio JSON-RPC framing, tool schemas, dispatch |
| `actions` | the 9 tool operations; connection supervisor; event pump |
| `resolver` | target → node with strict precedence, no silent disambiguation |
| `cache` | apps/windows/controls model; walks; event patching |
| `accessibility` | AT-SPI proxies, node inspection, per-call timeouts |
| `types` | `Target`, `ControlRef`, `UiError` |
| `main` | entrypoint (server + CLI) |

Two design points worth knowing: **event traffic and control traffic use separate D-Bus connections** (a signal flood from a busy app can't stall control operations), and **every AT-SPI call is timeout-bounded** (one wedged app can't freeze the server).

## Documentation

Full knowledge base — architecture, per-symbol reference, execution-flow diagrams, decision records, performance baselines — is an Obsidian vault under [`docs/SIR-Vault/`](docs/SIR-Vault). Open that folder as a vault, or start at [`docs/SIR-Vault/00 - SIR Home.md`](docs/SIR-Vault/00%20-%20SIR%20Home.md).

Measured performance and the optimization history live in [`benchmarks/`](benchmarks).

## MCP tools

`ui_list_apps`, `ui_list_windows`, `ui_list_controls`, `ui_find`, `ui_read`, `ui_press`, `ui_set_value`, `ui_focus`, `ui_wait_for`. Full reference: [MCP Tools Reference](docs/SIR-Vault/05%20-%20Interfaces/MCP%20Tools%20Reference.md).
