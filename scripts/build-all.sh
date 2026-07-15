#!/bin/bash
# Build SIR static binaries for every supported Linux architecture and package
# each as a portable .tar.gz (always) and a Debian .deb (where a clean Debian
# arch name exists). Packaging artifact only — no production code touched.
#
# Static musl builds: SIR has no C dependencies, so rust-lld self-contained
# linking produces a fully static binary per target, runnable on any Linux of
# that architecture regardless of glibc.
#
#   scripts/build-all.sh          # build everything into dist/
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(grep -m1 '^version' "$ROOT/Cargo.toml" | cut -d'"' -f2)"
CARGO="$HOME/.cargo/bin/cargo"
DIST="$ROOT/dist"
mkdir -p "$DIST"
cd "$ROOT"

# rust-target | debian-arch (- = tarball only) | friendly label
TARGETS="
x86_64-unknown-linux-musl|amd64|x86_64
aarch64-unknown-linux-musl|arm64|aarch64
armv7-unknown-linux-musleabihf|armhf|armv7-hardfloat
arm-unknown-linux-musleabihf|-|armv6-hardfloat
i686-unknown-linux-musl|i386|i686
riscv64gc-unknown-linux-musl|riscv64|riscv64
powerpc64le-unknown-linux-musl|ppc64el|ppc64le
"

export RUSTFLAGS="-C target-feature=+crt-static -C link-self-contained=yes"
SUMMARY=""

for row in $TARGETS; do
  [ -z "$row" ] && continue
  TARGET="${row%%|*}"; rest="${row#*|}"; DEBARCH="${rest%%|*}"; LABEL="${rest#*|}"
  echo "=== $TARGET ($LABEL) ==="

  # Pure-Rust link via bundled rust-lld; no C cross-toolchain required.
  eval "export CARGO_TARGET_$(echo "$TARGET" | tr 'a-z-' 'A-Z_')_LINKER=rust-lld"
  if ! "$CARGO" build --release --target "$TARGET" >/tmp/build-$TARGET.log 2>&1; then
    echo "  BUILD FAILED (see /tmp/build-$TARGET.log)"; SUMMARY="$SUMMARY\n$LABEL\tFAILED"; continue
  fi
  BIN="$ROOT/target/$TARGET/release/ui-mcp"
  [ -f "$BIN" ] || { echo "  no binary"; SUMMARY="$SUMMARY\n$LABEL\tNO-BIN"; continue; }

  # portable tarball (always)
  TARDIR="$(mktemp -d)/sir-$VERSION-$LABEL"
  mkdir -p "$TARDIR"
  cp "$BIN" "$TARDIR/ui-mcp"; ln -sf ui-mcp "$TARDIR/sir"
  cp "$ROOT/README.md" "$TARDIR/README.md"
  tar -C "$(dirname "$TARDIR")" -czf "$DIST/sir_${VERSION}_${LABEL}.tar.gz" "$(basename "$TARDIR")"

  # .deb where a Debian arch name applies
  DEB=""
  if [ "$DEBARCH" != "-" ]; then
    PKG="sir_${VERSION}-1_${DEBARCH}"
    S="$(mktemp -d)/$PKG"
    mkdir -p "$S/DEBIAN" "$S/usr/bin" "$S/usr/share/doc/sir"
    install -m755 "$BIN" "$S/usr/bin/ui-mcp"; ln -sf ui-mcp "$S/usr/bin/sir"
    cp "$ROOT/README.md" "$S/usr/share/doc/sir/README.md"
    cat > "$S/DEBIAN/control" << EOF
Package: sir
Version: ${VERSION}-1
Architecture: ${DEBARCH}
Maintainer: D12PROGRAMMER
Recommends: at-spi2-core
Section: utils
Priority: optional
Description: SIR - AT-SPI accessibility-to-MCP adapter
 Operate Linux desktop controls directly through AT-SPI via MCP. Static
 binary, no libc dependency. No screenshots, OCR, or input synthesis.
EOF
    dpkg-deb --build --root-owner-group "$S" "$DIST/$PKG.deb" >/dev/null && DEB="$PKG.deb"
  fi

  SZ=$(stat -c%s "$BIN")
  echo "  ok: $(basename "$BIN") ${SZ}B → tar.gz${DEB:+ + $DEB}"
  SUMMARY="$SUMMARY\n$LABEL\t${SZ}B\ttar.gz${DEB:+, $DEB}"
done

echo -e "\n=== artifacts ==="
ls -la "$DIST"
echo -e "\n=== summary ==="
echo -e "arch\tbinary\tpackages$SUMMARY"
