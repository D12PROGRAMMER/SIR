# SIR Optimization Pass — 2026-07-14

Baseline: [baseline.md](baseline.md) (`9b34056…`) → After: [after.json](after.json) (`3b642f0…`, snapshot `source-snapshot-3b642f0.zip`). Same harness (`test/bench.py`), same environment, same commands as the baseline. **Not a redesign** — architecture and behavior preserved; every change is a removal of proven-unused work or a build-profile setting.

## Behavior: unchanged (re-verified live)

| Test | Baseline | After |
|---|---|---|
| Acceptance suite | 34/34 (52.7 s) | **34/34** (52.9 s) |
| Bus restart | 4/4 (10.3 s) | **4/4** (10.3 s) |

## Changes applied

1. **Removed `tokio-stream` dependency** — zero references in the codebase (`grep tokio_stream src/` = none). Deps 90 → 89.
2. **Removed a wasted D-Bus round trip per walked node**: `inspect()` fetched `GetInterfaces` into `RawNode.interfaces`, which no consumer ever read (rustc dead-field warning). Walks now do 4 calls/node instead of 5 (−20% walk traffic). Interfaces are still fetched where actually used (`read`, `set_value`).
3. **Removed dead code**: `RawNode.obj`, `NodeEntry.role` (the `Role` enum copy; `role_str` is what's used), `has_iface()`. Build is now **0 warnings** (was 3).
4. **Release profile**: `lto = "thin"`, `strip = "symbols"` (opt-level 2 unchanged).
5. **Drift fix found by the pass**: the host `Cargo.toml` still pinned `atspi 0.22` (the 0.30 upgrade had been done via `cargo add` only in the VM). Now pinned to `0.30.0` in the authoritative copy.

## Measurements

| Metric | Baseline | After | Δ |
|---|---|---|---|
| Binary size | 7,828,208 B | **5,460,408 B** | **−30.2%** |
| Runtime dep crates | 90 | 89 | −1 |
| Clean release build | 90.1 s | 94.7 s | +5.1% (thin LTO; accepted for the size win) |
| Startup → initialize | 29.2 ms | 28.0 ms | noise |
| Initial enumeration | 28.8 ms | 27.3 ms | noise |
| MCP round trip (median) | 0.30 ms | 0.49 ms | within run-to-run noise (sub-ms) |
| Cached lookup (median) | 1.09 ms | 1.28 ms | within noise |
| Press (median) | 156.3 ms | 156.3 ms | unchanged (150 ms is policy) |
| RSS idle / peak | 6.8 / 7.0 MB | 6.7 / 7.0 MB | noise |

The walk-traffic reduction (change 2) is invisible on the 10-node bench fixture; its effect scales with tree size (e.g. ~840 fewer calls enumerating the 838-node Firefox tree). Recorded as a structural fact, not a latency claim.

## Considered and rejected

- `panic = "abort"` — smaller binary, but changes crash semantics (a panicking task would kill the whole server instead of being contained by tokio). Behavior preservation wins.
- `codegen-units = 1` — additional size/speed for significantly slower builds; thin LTO captures most of the benefit.
- Trimming tokio features — all six enabled features are used (macros, rt-multi-thread, io-std, io-util, sync, time).
- Caching action lists / parallel walks — rejected on staleness and flood-isolation grounds; see the vault's Performance Model note.
