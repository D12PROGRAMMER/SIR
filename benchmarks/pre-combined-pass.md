# SIR Combined-Pass Baseline (Stage A) — 2026-07-14

Machine-readable: [pre-combined-pass.json](pre-combined-pass.json) · checkpoint: `checkpoint-0-pre-combined.zip` · harness: `test/bench2.py` (schema `sir-combined-pass-v1`)

## Provenance

| | |
|---|---|
| Date (UTC) | 2026-07-14T22:59:16Z |
| Source SHA-256 (Cargo.toml + src/*) | `0cfe2c72effb89161480097fe97788515215b694892d7f665fee8c7a4a0d6105` |
| Source lines (src/*.rs) | 1,765 |
| Toolchain | rustc 1.97.0, cargo 1.97.0 |
| Environment | aios VM: Debian 13, Linux 6.12.95, 6 vCPU, QEMU/WHPX |
| Command | `systemd-run --unit=sirbench2 … python3 -u test/bench2.py` in the desktop session |

## Gate (verified live from this source, not copied)

- Acceptance suite: **34/34 checks passed**
- Bus-restart suite: **4/4 checks passed** (run last — it kills the session registry)

## Build & dependencies

| Metric | Value |
|---|---|
| Release binary | 5,460,408 B (lto=thin, strip=symbols) |
| Clean release build | 83.3 s |
| Runtime dependency crates | 89 |
| Duplicate dependency versions | **none** |

## Runtime (median / p95, sample counts in JSON)

| Metric | Median | p95 |
|---|---|---|
| Startup → initialize (n=5) | 28.6 ms | 30.2 ms |
| Initial enumeration — GTK, 10 nodes (n=5) | 28.2 ms | 29.8 ms |
| Initial enumeration — Chromium, 225 nodes (n=3) | 492 ms | — |
| Initial enumeration — Firefox, 848 nodes (n=3) | 1,972 ms (min 1,168 / max 4,429 — tree still churning) | — |
| Warm `ui_find` by id, small cache (n=30) | 1.32 ms | 1.81 ms |
| Warm `ui_find` by id, 848-node cache (n=30) | 0.97 ms | 1.46 ms |
| Warm `ui_read` by ref (n=30) | 2.24 ms | 2.99 ms |
| `ui_list_controls` small / large (n=20/10) | 0.89 / 1.43 ms | 1.33 / 2.04 ms |
| `ui_press` total (n=10) | 157.6 ms | 158.2 ms |
| `ui_press` non-settle (total − 150 ms) | **7.6 ms** | 8.2 ms |
| Control latency **during Chromium event flood** (n=146) | 1.53 ms | 2.21 ms (max 11.5) |
| Control latency quiet (n=10) | 1.49 ms | 1.74 ms |
| RSS idle / peak | 6.9 / 7.6 MB | |

## Response sizes (regression guardrail)

find 143 B · read 129 B · press 78 B · list_controls small 557 B · list_controls large 26,467 B

## Notable conclusions feeding Stage B

1. Walks are D-Bus-bound at ≈2.2 ms/node (Chromium 225→492 ms; Firefox 848→~2 s).
2. Cache scans are **not** a bottleneck: warm find is no slower with an 848-node cache.
3. The dual-connection design holds under real floods: control p95 2.21 ms while Chromium storms.
4. Firefox enumeration variance (1.2–4.4 s) is application churn, not SIR CPU — do not attribute it to SIR.
