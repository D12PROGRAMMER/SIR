---
kind: performance
generated: false
baseline: benchmarks/pre-combined-pass.json (source 0cfe2c72…)
---

# Combined Optimization Candidates

Stage B analysis for the combined pass. Evidence sources: [[Baseline (2026-07-14)]] superseded by `benchmarks/pre-combined-pass.json` (34/34 + 4/4 gate verified live), `cargo clippy --all-targets` (4 warnings: 1 lint + 3 doc nits), `cargo fmt --check` (minor diffs), `cargo tree -d` (**zero duplicate dependency versions**), source inspection of all seven modules.

Ranked by expected value ÷ risk. **Selected for implementation: C-01, C-02** (limit is three; no third candidate survived its evidence).

> **Final statuses (Stage E):** C-01 and C-02 **implemented and validated** — 34/34 + 4/4 after each patch; results in [[Combined Optimization Pass]]. All rejections below were confirmed by the post-pass measurements; C-04 remains deferred with its investigation prerequisite unchanged.

| ID | Category | Status |
|---|---|---|
| C-01 | SOURCE_SIMPLIFICATION | **accepted** |
| C-02 | DEPENDENCY | **accepted** |
| C-04 | DBUS_CALL_REDUCTION | deferred |
| C-03 | CACHE_LOOKUP | rejected |
| C-05 | DBUS_CALL_REDUCTION | rejected |
| C-06 | LOCKING | rejected |
| C-07 | SERIALIZATION | rejected |
| C-08 | ALLOCATION | rejected |
| C-09 | BUILD | rejected |

## C-01 — Deduplicate the target-operation prologue (SOURCE_SIMPLIFICATION) — accepted

- **Files/functions**: `src/actions.rs` — `Service::{read, press, set_value, focus}`
- **Evidence**: all four repeat the identical 7-line prologue (zconn → lock → resolve → fetch/clone `NodeEntry` → identical `StaleTarget` guard). Verbatim duplication ×4.
- **Expected benefit**: ~20 fewer source lines, one place to maintain the resolve-guard invariant. Maintainability, not a speedup.
- **Proof metric**: source lines; binary size; warm read/press medians unchanged within noise; 34/34 + 4/4.
- **Risks**: error-message text for the vanished-ref guard becomes uniform ("during resolution"). No invariant touched — resolution semantics live in `resolver.rs` and are untouched.
- **Protecting tests**: suite checks for stale_target, press by id, disabled → not_actionable, read.
- **Rollback**: checkpoint zip (`benchmarks/checkpoint-*.zip`), single-file patch.
- Includes hygiene: `cargo fmt` diffs + clippy `is_multiple_of` lint + doc-comment indents (cosmetic, same pass).

## C-02 — Replace `futures` facade with `futures-util` (DEPENDENCY) — accepted

- **Files**: `Cargo.toml`, `src/actions.rs` (2 use sites: `StreamExt`, `pin_mut!` → `tokio::pin!`)
- **Evidence**: `grep futures:: src/` → exactly two uses. The `futures` facade (default features) drags `futures-executor` into the graph solely for re-export; `futures-util` is **already compiled** as a zbus dependency.
- **Expected benefit**: −2 dependency crates, small clean-build reduction. No runtime effect expected.
- **Proof metric**: `cargo tree` crate count; clean build time; binary size; suites.
- **Risks**: minimal — same underlying code paths.
- **Rollback**: revert Cargo.toml + two use lines.

## C-04 — Gate the attributes-`id` fallback (DBUS_CALL_REDUCTION) — deferred

- **Evidence for cost**: enumeration ≈ 2.2 ms/node (Chromium 225 nodes → 492 ms; Firefox 848 → ~2.0 s); the `GetAttributes` fallback is one of 4–5 calls and fires for **every node without an AccessibleId property** (most GTK/Qt structural nodes).
- **Why deferred, not accepted**: the fallback is load-bearing for browsers ([[ADR - DOM ID Fallback]]) and there is **no verified discriminator** for "this app publishes DOM ids in attributes" — gating it on toolkit identity requires a per-toolkit probe study (does Chromium's `AccessibleId` property error, return empty, or not exist?). Wrong gating silently breaks `ui_press` by DOM id — the project's core success condition. Needs its own investigation with per-toolkit evidence; out of this pass's ≤3-candidate budget.

## C-03 — Per-app node index for cache scans (CACHE_LOOKUP) — rejected

- **Hypothesis**: `Cache::find` scans every node of every app; a large cached tree should slow warm lookups on other apps.
- **Measurement kills it**: warm `ui_find` by id, small cache (10 nodes): **1.32 ms** median; same query with an 848-node Firefox tree cached: **0.97 ms** median (n=30 each). The full scan is invisible next to the mandatory live `GetState` verification round trip. Index bookkeeping would touch the ref-stability walk paths ([[ADR - Ref Stability Contract]]) for no measurable gain.

## C-05 — Concurrent property fetches inside `inspect` (DBUS_CALL_REDUCTION) — rejected

- Joining the 4 per-node calls could cut walk latency ~3×, **but** it multiplies in-flight method calls on the shared call connection — precisely the method-storm availability risk the task forbids ("do not parallelize per-node AT-SPI walks"). Invariant outranks the win.

## C-06 — Shorten cache-lock hold in find/list (LOCKING) — rejected

- MCP requests are sequential by contract; the only contender is the event pump (in-memory, µs). Flood measurement proves absence of a problem: control p95 **2.21 ms during** Chromium's event flood vs 1.74 ms quiet, max 11.5 ms, n=146.

## C-07 — Single-pass result assembly in `controls_result` (SERIALIZATION) — rejected

- `list_controls` on the large tree: **1.43 ms** median, 26.5 KB response. The double pass (serialize + hoist scan) is µs-scale; a rewrite risks the documented [[Output Conventions]] for no measurable benefit.

## C-08 — Role-name allocation caching (ALLOCATION) — rejected

- Two small allocations per visited node vs 2.2 **ms** of D-Bus per node: five orders of magnitude below the noise floor.

## C-09 — `codegen-units = 1` (BUILD) — rejected

- Prior pass measured thin-LTO as the dominant size lever; codegen-units=1 trades materially slower builds for marginal size. Build time is already the developer-facing cost (83–95 s clean).
