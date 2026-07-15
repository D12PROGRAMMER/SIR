---
kind: performance
generated: false
pre_hash: 0cfe2c72effb89161480097fe97788515215b694892d7f665fee8c7a4a0d6105
post_hash: 7b4fef380d21a08b569f79f0c86de65d0cfb025f1dace33d10bd4a175df0fc0f
---

# Combined Optimization Pass (2026-07-14)

The measurement-driven analysis → simplification → optimization pass. Authoritative records: [benchmarks/pre-combined-pass.md](../../../benchmarks/pre-combined-pass.md) and [benchmarks/post-combined-pass.md](../../../benchmarks/post-combined-pass.md); candidate analysis in [[Combined Optimization Candidates]].

## Outcome in one paragraph

Two candidates survived their evidence (limit three): a **source dedupe** — the four-way duplicated resolve prologue in [[Module - actions]] became one `Service::resolve_node` helper (clippy now 0 warnings) — and a **dependency narrowing** — the `futures` facade replaced by `futures-util`, dropping the crate graph from 89 to **84**. Binary shrank 22.9 KB. Every latency, memory, and response-size metric is unchanged within noise, which is the *intended* result: the pass removed code and dependencies without touching behavior, proven by **34/34 + 4/4 after each patch and at the end**.

## What the measurements settled (as valuable as the patches)

- **Cache scans need no index**: warm `ui_find` with an 848-node tree cached (0.87–0.97 ms) is no slower than with 10 nodes — live-verification round trips dominate, exactly as [[Performance Model]] predicted (C-03 rejected).
- **The dual-connection design is quantified**: control p95 ≤2.21 ms *during* a real Chromium event flood, twice measured (C-06 rejected; [[ADR - Dual D-Bus Connections]] now has numbers).
- **Walks are D-Bus-bound (~2.2 ms/node)**: the only meaningful lever left is the attributes-`id` fallback call (C-04, **deferred** — gating it safely needs a per-toolkit AccessibleId probe study; wrong gating breaks DOM-id addressing, the project's success condition).
- **Firefox enumeration variance (1.0–4.4 s) is application churn**, not SIR CPU — recorded so nobody optimizes SIR to fix Firefox.

## Fidelity notes

- Press latency reported as total (≈156 ms) *and* non-settle (≈6–8 ms); the 150 ms settle is policy ([[Flow - Press Action]]).
- Source line count rose 1,765 → 1,821 solely from `cargo fmt` normalization applied in the same pass; the dedupe itself removed ~24 duplicated lines. Lines were never the goal.
- Checkpoints for every state: `benchmarks/checkpoint-{0,1,2}-*.zip`.
