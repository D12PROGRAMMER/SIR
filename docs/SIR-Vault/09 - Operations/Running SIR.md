---
kind: operations
generated: false
---

# Running SIR

## Where it runs

SIR is Linux software (AT-SPI). In this project it runs inside the **aios VM** (Debian 13 under QEMU/WHPX on the Windows host). Host-side VM controls: `C:\ai-os\scripts\vm.ps1` (`create|start|stop|status|ssh`), SSH at `127.0.0.1:2222` with key `C:\ai-os\vm\keys\claude_ed25519`.

## Prerequisites in the guest

- Binary installed at `/usr/local/bin/ui-mcp` (build: `~/.cargo/bin/cargo build --release` in `/root/ui-mcp`, then `install -m755 target/release/ui-mcp /usr/local/bin/`)
- A desktop session with an AT-SPI bus — provided by `desktop.service` ([[Desktop Service and Looking Glass]])
- Session env: `source /root/.desktop-env` (exports `DISPLAY` and `DBUS_SESSION_BUS_ADDRESS`)
- Per-toolkit a11y env for the *applications* being controlled: see [[Toolkit Behavior Matrix]]

## As an MCP server for Claude

Register a stdio server whose command reaches the session:

```json
{
  "mcpServers": {
    "sir": {
      "command": "ssh",
      "args": ["-i", "C:\\ai-os\\vm\\keys\\claude_ed25519", "-p", "2222",
               "-o", "UserKnownHostsFile=C:\\ai-os\\vm\\known_hosts",
               "root@127.0.0.1",
               "bash -lc 'source /root/.desktop-env && exec ui-mcp'"]
    }
  }
}
```

Startup blocks up to 15 s for the first bus connection + enumeration; if the bus is unreachable the process exits 1 with a clear message.

## Ad-hoc use

`ui-mcp cli …` over SSH — see [[CLI Reference]]. Logs go to stderr (`[ui-mcp] connected: enumerated N apps, M nodes`, budget warnings, reconnect notices).
