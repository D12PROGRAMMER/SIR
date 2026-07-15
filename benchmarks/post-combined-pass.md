# SIR Combined-Pass Result (Stage E) — 2026-07-14

Pre: [pre-combined-pass.md](pre-combined-pass.md) (`0cfe2c72…`) → Post: [post-combined-pass.json](post-combined-pass.json) (`7b4fef38…`). Same harness (`test/bench2.py`), same environment, same commands. Checkpoints: `checkpoint-0-pre-combined.zip`, `checkpoint-1-c01-dedupe.zip`, `checkpoint-2-c02-futures-util.zip`.

## Accepted changes (2 of a permitted 3)

1. **C-01 (SOURCE_SIMPLIFICATION)** — `src/actions.rs`: the identical 7-line prologue in `read`/`press`/`set_value`/`focus` (zconn → lock → resolve → NodeEntry fetch → stale guard) extracted into one `Service::resolve_node` helper. Plus hygiene: `cargo fmt` across the tree, clippy `is_multiple_of` lint, `src/resolver.rs` doc-comment structure. Clippy: **4 warnings → 0**. Maintainability change; latencies unchanged within noise (as predicted).
2. **C-02 (DEPENDENCY)** — `Cargo.toml` + `src/actions.rs` (2 lines): `futures` facade → `futures-util` (default-features off, `std`), `futures::pin_mut!` → `tokio::pin!`. **89 → 84 dependency crates.**

Rejected/deferred candidates with evidence: [Combined Optimization Candidates](../docs/SIR-Vault/07%20-%20Performance/Combined%20Optimization%20Candidates.md) (C-03…C-09 rejected on measurement or invariants; C-04 deferred pending a per-toolkit AccessibleId probe study).

## Behavior (verified live, per patch and at end)

| Gate | Pre | After C-01 | After C-02 | Post (final) |
|---|---|---|---|---|
| Acceptance | 34/34 | 34/34 | 34/34 | **34/34** |
| Bus restart | 4/4 | 4/4 | 4/4 | **4/4** |

## Before → after

| Metric | Pre | Post | Classification |
|---|---|---|---|
| Dependency crates | 89 | **84** | measured improvement |
| Binary size | 5,460,408 B | **5,437,528 B** (−22.9 KB) | measured improvement (small) |
| Clippy warnings | 4 | **0** | measured improvement |
| Duplicated prologue blocks | 4 | 0 | measured improvement (maintainability) |
| Clean release build | 83.3 s | 70.5 s | improved direction, but build times varied 83–95 s across runs; attribute cautiously to −5 crates, not claimed as a firm speedup |
| Source lines | 1,765 | 1,821 | +56 — **fmt normalization**, not added logic (the dedupe alone removed ~24 duplicated lines; `cargo fmt` re-expanded compact constructs tree-wide) |
| Startup → initialize | 28.6 ms | 27.1 ms | unchanged within noise |
| Enum GTK (10 nodes) | 28.2 ms | 26.7 ms | unchanged within noise |
| Enum Chromium (~230 nodes) | 492 ms | 498 ms | unchanged within noise |
| Enum Firefox (848 nodes) | 1,972 ms | 1,601 ms | unchanged within noise (app churn dominates; min–max 1.0–3.6 s) |
| Warm find by id (small / large cache) | 1.32 / 0.97 ms | 1.10 / 0.87 ms | unchanged within noise |
| Warm read by ref | 2.24 ms | 2.64 ms | unchanged within noise |
| Press total / non-settle | 157.6 / 7.6 ms | 156.0 / 6.0 ms | unchanged within noise (150 ms settle is policy, reported separately) |
| list_controls small / large | 0.89 / 1.43 ms | 0.93 / 1.16 ms | unchanged within noise |
| Flood: control p95 during Chromium load | 2.21 ms | 1.65 ms | unchanged within noise; both runs confirm no starvation |
| RSS idle / peak | 6.9 / 7.6 MB | 6.9 / 7.8 MB | unchanged within noise |
| Response bytes find/read/press/list-small | 143/129/78/557 | **identical** | guardrail passed (list-large differs only because Chromium exposed 239 vs 225 nodes) |

Not measured: none of the required metrics were skipped.

## New tests

None added: both accepted candidates are fully covered by existing checks (C-01's resolve/stale/press semantics by the GTK battery's stale_target, not_actionable, press, read checks; C-02 is a link-level change proven by build + full suites). `cargo test` runs clean (0 unit tests exist; recorded).

## Prohibited-change confirmation

Seven modules intact; nine tool names unchanged; ambiguity/stale/ref-stability semantics untouched (suite-verified); zero-I/O event handler and dual connections untouched; all timeout/cap constants unchanged; requests remain sequential; no action-list caching; no walk parallelization; no `panic = "abort"`; output shapes byte-identical on the guardrail payloads.
