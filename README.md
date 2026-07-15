# SIR

An accessibility-to-MCP adapter (binary: `ui-mcp`). It lets an AI agent operate Linux desktop controls directly through the accessibility system (AT-SPI) — no screenshots, OCR, or input synthesis.

```
Agent → MCP tool → AT-SPI accessible object → direct accessibility action
```

## Build

```bash
cargo build --release
```

Requires a session D-Bus with an AT-SPI bus (`at-spi2-core`) at runtime.

## Usage

Run as an MCP server (JSON-RPC 2.0 over stdio), or use the CLI for quick checks:

```bash
ui-mcp                                   # MCP server on stdio
ui-mcp cli apps                          # list accessible applications
ui-mcp cli find app=myapp id=save        # search controls
ui-mcp cli press app=myapp id=save       # invoke a control's action
```

Tools: `ui_list_apps`, `ui_list_windows`, `ui_list_controls`, `ui_find`, `ui_read`, `ui_press`, `ui_set_value`, `ui_focus`, `ui_wait_for`.

A target names a control by application-provided `id`, session `ref`, or `app`/`window`/`role`/`name`:

```jsonc
ui_press({ "app": "example-editor", "id": "save-project" })
```

## MCP setup

Add SIR as a stdio MCP server. It must run where the target desktop session's AT-SPI bus lives, with `DBUS_SESSION_BUS_ADDRESS` set for that session.

**Local:**

```json
{
  "mcpServers": {
    "sir": { "command": "ui-mcp" }
  }
}
```

**Over SSH** (server runs on another machine's desktop session):

```json
{
  "mcpServers": {
    "sir": {
      "command": "ssh",
      "args": ["user@host", "bash -lc 'source ~/.desktop-env && exec ui-mcp'"]
    }
  }
}
```

`~/.desktop-env` should export `DISPLAY` and `DBUS_SESSION_BUS_ADDRESS` for the target session.

## Documentation

Full architecture, per-symbol reference, and design notes are an Obsidian vault under [`docs/SIR-Vault/`](docs/SIR-Vault) — start at `00 - SIR Home.md`.
