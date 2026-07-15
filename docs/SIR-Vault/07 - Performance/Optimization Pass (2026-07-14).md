---
kind: performance
generated: false
source_hash: 3b642f05beae35a8a54129dbff88532f39a29ead9d0a43c1e698b64df287323b
---

# Optimization Pass (2026-07-14)

Authoritative record: [benchmarks/optimization.md](../../../benchmarks/optimization.md) + [benchmarks/after.json](../../../benchmarks/after.json). Measured against [[Baseline (2026-07-14)]]; **behavior re-verified 34/34 + 4/4 on the optimized build**.

| What changed | Proof | Result |
|---|---|---|
| `tokio-stream` removed | zero references in `src/` | 89 deps (−1) |
| `GetInterfaces` dropped from walks | dead `RawNode.interfaces` field — no consumer | 4 calls/node instead of 5 |
| Dead code removed (`RawNode.obj`, `NodeEntry.role`, `has_iface`) | rustc dead-code warnings | 0-warning build |
| `lto = "thin"`, `strip = "symbols"` | size measurement | binary **−30.2%** (7.83 → 5.46 MB) at +5% build time |
| `atspi` version drift fixed (host Cargo.toml said 0.22, real dep is 0.30) | build failure on sync | authoritative manifest correct |

Latency, memory, startup: unchanged within noise (expected — the removed walk call only pays off on large trees, and the profile changes don't affect hot paths at this scale).

Rejected candidates and reasons: see the benchmarks record and [[Performance Model]] §Known non-optimizations. Notably `panic = "abort"` was rejected because it would change crash containment semantics — preservation of behavior outranked the size win.
