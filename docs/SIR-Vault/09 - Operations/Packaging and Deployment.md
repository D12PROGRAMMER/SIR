---
kind: operations
generated: false
---

# Packaging and Deployment

Packaging phase over the pre-packaging baseline (source `7b4fef38…`, 34/34 + 4/4). **No production code changed** — only manifests, build scripts, service files, docs, and platform conditionals.

## Artifacts

| File | Arch | Type | Size | Status |
|---|---|---|---|---|
| `dist/sir_0.1.0-1_amd64.deb` | amd64 | static musl | 1.36 MB | **validated 34/34 + 4/4** in the VM against the installed `/usr/bin/ui-mcp` |
| `dist/sir_0.1.0-1_arm64.deb` | arm64 | static musl | 1.22 MB | binary proven-executable under qemu-aarch64; glibc-independent; awaiting on-Pi acceptance |

Both packages install `/usr/bin/ui-mcp` + a `sir` symlink, a README, and a template user unit.

## Why static musl (build-toolchain decision)

The build host is Debian 13 (glibc **2.41**). A normal glibc build references `GLIBC_2.39`, which is **absent on Raspberry Pi OS Bookworm (glibc 2.36)** — the binary would fail to start. SIR has no C dependencies (zbus/atspi/tokio/serde are pure Rust), so `*-unknown-linux-musl` with `-C target-feature=+crt-static -C link-self-contained=yes` produces a fully static binary (verified: `objdump -T` shows zero GLIBC symbols) that runs on any Linux of the target arch. Build script: [scripts/package-deb.sh](../../../scripts/package-deb.sh). Linker is `rust-lld` self-contained — no C cross-toolchain needed.

## Build commands

```bash
# in the VM (build host)
bash scripts/package-deb.sh amd64   # → dist/sir_0.1.0-1_amd64.deb
bash scripts/package-deb.sh arm64   # → dist/sir_0.1.0-1_arm64.deb
```

## Raspberry Pi deployment

Target (saved 2026-07-11): Pi 5, Bookworm, `192.168.12.213`, user `aiden`, Labwc/Wayland, Chromium at `/usr/bin/chromium-browser`, attached Vizio TV.

Deploy + visible-Chromium smoke test: [scripts/deploy-pi.sh](../../../scripts/deploy-pi.sh). Per the owner's display preference, it opens a real Chromium window **on the TV** and has SIR press a DOM button via AT-SPI (no pointer/keys) — the meaningful "acceptance behavior" for a headed Pi, rather than replaying the synthetic GTK/Qt fixtures. The script also checks storage health first (the Pi has a prior emergency-mode/read-only-remount warning).

**Prerequisite**: the `sir-deploy@aios` key (`C:\ai-os\pi-keys\sir_pi_ed25519.pub`) must be authorized for `aiden@192.168.12.213`. Key auth is currently denied; the Pi steps are blocked on this.

## Codex → SIR (MCP)

Wired in `~/.codex/config.toml` as `[mcp_servers.sir]` (backup at `config.toml.bak-sir`): Codex runs `ssh … root@127.0.0.1 "bash -lc 'source /root/.desktop-env && exec /usr/bin/ui-mcp'"`. Verified through the exact command: `initialize` → serverInfo `ui-mcp`, `tools/list` → all 9 tools, and `ui_press` fired the target app's handler (flag file written) — full MCP-to-AT-SPI path over SSH.

## Tailscale

Not installed on the Windows host — the SSH-over-Tailscale test cannot run until Tailscale is installed and authenticated (an owner action; account auth is out of scope for the agent). Once up, the Codex `sir` server's ssh target changes from `127.0.0.1`/LAN to the tailnet name.
