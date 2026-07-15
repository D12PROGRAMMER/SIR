# SIR Baseline — 2026-07-14

Machine-readable data: [baseline.json](baseline.json) · source snapshot: [source-snapshot-9b34056.zip](source-snapshot-9b34056.zip)

## Provenance

| | |
|---|---|
| Date (UTC) | 2026-07-14T20:46:18Z |
| Source SHA-256 (Cargo.toml + src/*) | `9b34056458c0e47b2bf4b84625ad8ebd890e7a100d6df55cc72139c2e5dbad0d` |
| Build mode | release (`opt-level = 2`, otherwise default profile) |
| Toolchain | rustc 1.97.0 (2026-07-07), cargo 1.97.0 |
| Environment | aios VM: Debian 13, Linux 6.12.95+deb13-cloud-amd64, 6 vCPU, 7947 MB RAM, QEMU/WHPX |
| Harness | `test/bench.py`, run as `systemd-run --unit=sirbench … python3 -u test/bench.py` inside the desktop session (env from `/root/.desktop-env`) |

## Behavioral baseline (verified live, not copied)

| Test | Result | Duration |
|---|---|---|
| Cross-toolkit acceptance suite (`test/suite.py gtk qt chromium electron firefox core restart`) | **34/34 checks passed** | 52.7 s |
| Accessibility-bus restart test (`test/bus_restart.py`) | **4/4 checks passed** | 10.3 s |

## Size & build

| Metric | Value |
|---|---|
| Release binary (`target/release/ui-mcp`) | **7,828,208 B (7.47 MiB)**, unstripped |
| Clean release build (`cargo clean && cargo build --release`) | **90.1 s** |
| Runtime dependency crates (`cargo tree -e normal`) | **90** |
| Dynamic libraries (`ldd`) | 3 |
| Cargo features | zbus 5 (`tokio`); tokio (`macros, rt-multi-thread, io-std, io-util, sync, time`); atspi 0.30 (`tokio, proxies, connection`); serde (`derive`); plus `tokio-stream`, `futures`, `serde_json` |

## Runtime (median unless noted; fixture: GTK test app, warm session)

| Metric | Value |
|---|---|
| Startup → first `initialize` response | **29.2 ms** |
| Initial enumeration (spawn → "connected: enumerated …" on stderr) | **28.8 ms** (1 app, 10 nodes) |
| MCP request latency (`ping`, n=30) | **0.30 ms** median, 0.60 ms p95 |
| Cached lookup (`ui_find` by id, n=30) | **1.09 ms** median, 1.64 ms p95 |
| AT-SPI action (`ui_press`, n=10) | **156.3 ms** median — includes the intentional 150 ms post-action settle; net AT-SPI work ≈ 6 ms |
| Idle RSS (after ready, 2 s settle) | **6,836 kB** |
| Peak RSS (VmHWM after workload) | **6,992 kB** |

## Exact commands

```
# in the VM, inside the desktop session
systemd-run --unit=sirbench --collect \
  --setenv=DISPLAY=$DISPLAY --setenv=DBUS_SESSION_BUS_ADDRESS=$DBUS_SESSION_BUS_ADDRESS \
  --setenv=GNOME_ACCESSIBILITY=1 --setenv=GTK_MODULES=gail:atk-bridge \
  --working-directory=/root/ui-mcp \
  -p StandardOutput=file:/tmp/bench.out -p StandardError=file:/tmp/bench.err \
  /usr/bin/python3 -u test/bench.py
```

The harness performs, in order: environment capture → source hashing → `cargo tree` → `cargo clean` + timed release build → full acceptance suite → latency/memory sampling against a fresh server + GTK fixture → bus-restart test (disruptive, last).

## Notes

- Initial enumeration scales with desktop population; this baseline had one accessible app (the fixture). The suite exercises enumeration of 2–5 apps including browsers.
- Press latency is settle-dominated by design ([[Flow - Press Action]] in the vault); treat 150 ms as a policy constant, not overhead.
- No git repository exists in this working tree by owner's choice; provenance is by content hash + snapshot archive.
