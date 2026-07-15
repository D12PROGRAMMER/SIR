#!/bin/bash
# Build a Debian package for SIR. Packaging artifact only — no production code.
# Usage: scripts/package-deb.sh <amd64|arm64>
# Output: dist/sir_<version>-1_<arch>.deb
#
# Builds a STATIC musl binary: SIR has no C dependencies (zbus/atspi/tokio/serde
# are pure Rust), so a fully static binary runs on any Linux of the target arch
# regardless of glibc version. This matters because the build host (Debian 13,
# glibc 2.41) is newer than common deploy targets (e.g. Raspberry Pi OS Bookworm,
# glibc 2.36) — a glibc-linked binary would fail with "GLIBC_2.39 not found".
set -euo pipefail

ARCH="${1:?usage: package-deb.sh <amd64|arm64>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(grep -m1 '^version' "$ROOT/Cargo.toml" | cut -d'"' -f2)"
CARGO="$HOME/.cargo/bin/cargo"

case "$ARCH" in
  amd64) TARGET="x86_64-unknown-linux-musl";  LINKER=rust-lld ;;
  arm64) TARGET="aarch64-unknown-linux-musl"; LINKER=rust-lld ;;
  *) echo "unsupported arch: $ARCH" >&2; exit 1 ;;
esac

cd "$ROOT"
# Pure-Rust static link via rust-lld self-contained crt (no C cross-toolchain).
CARGO_TARGET_X86_64_UNKNOWN_LINUX_MUSL_LINKER="$LINKER" \
CARGO_TARGET_AARCH64_UNKNOWN_LINUX_MUSL_LINKER="$LINKER" \
RUSTFLAGS="-C target-feature=+crt-static -C link-self-contained=yes" \
  "$CARGO" build --release --target "$TARGET"
BIN="$ROOT/target/$TARGET/release/ui-mcp"

PKG="sir_${VERSION}-1_${ARCH}"
STAGE="$(mktemp -d)/$PKG"
mkdir -p "$STAGE/DEBIAN" "$STAGE/usr/bin" "$STAGE/usr/share/doc/sir" \
         "$STAGE/usr/lib/systemd/user"

install -m755 "$BIN" "$STAGE/usr/bin/ui-mcp"
ln -s ui-mcp "$STAGE/usr/bin/sir"

cat > "$STAGE/DEBIAN/control" << EOF
Package: sir
Version: ${VERSION}-1
Architecture: ${ARCH}
Maintainer: aios project <root@aios>
Recommends: at-spi2-core
Built-Using: rust (static musl, no libc dependency)
Section: utils
Priority: optional
Description: SIR - AT-SPI accessibility-to-MCP adapter
 Lets an AI agent query and operate desktop controls directly through the
 Linux accessibility system (AT-SPI). MCP server over stdio; also provides
 a diagnostic CLI (ui-mcp cli ...). No screenshots, OCR, or input synthesis.
EOF

cat > "$STAGE/usr/share/doc/sir/README" << 'EOF'
SIR (binary: ui-mcp, alias: sir)

MCP server over stdio for AT-SPI desktop control.

Requirements at runtime:
  - a session D-Bus with an AT-SPI accessibility bus (at-spi2-core)
  - DBUS_SESSION_BUS_ADDRESS pointing at the target desktop session

Quick check:      ui-mcp cli apps
MCP server:       ui-mcp          (newline-delimited JSON-RPC 2.0 on stdio)
User service:     systemctl --user enable --now sir.service
Docs:             docs/SIR-Vault in the source tree
EOF

# Optional user service: runs SIR bound to the invoking user's session bus.
cat > "$STAGE/usr/lib/systemd/user/sir.service" << 'EOF'
[Unit]
Description=SIR accessibility-to-MCP adapter (stdio via socket activation not supported; use as template)
Documentation=file:///usr/share/doc/sir/README

[Service]
# SIR speaks MCP on stdio; this unit exists for environments that wrap it
# (e.g. a supervisor providing a socket). For direct MCP use, launch ui-mcp
# from the MCP client instead of via systemd.
ExecStart=/usr/bin/ui-mcp cli apps
Type=oneshot

[Install]
WantedBy=default.target
EOF

dpkg-deb --build --root-owner-group "$STAGE" "$ROOT/dist/${PKG}.deb" > /dev/null
echo "built dist/${PKG}.deb"
dpkg-deb --info "$ROOT/dist/${PKG}.deb" | sed -n '1,12p'
