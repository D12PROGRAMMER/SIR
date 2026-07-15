# Pre-Packaging Baseline Tag

| | |
|---|---|
| Tag date (UTC) | 2026-07-15 |
| Source SHA-256 (Cargo.toml + src/*) | `7b4fef380d21a08b569f79f0c86de65d0cfb025f1dace33d10bd4a175df0fc0f` |
| Verified totals at this state | acceptance **34/34**, bus-restart **4/4** (benchmarks/post-combined-pass.json, re-verified live) |
| Checkpoint archive | `checkpoint-3-pre-packaging-7b4fef38.zip` |
| Rule in force | Packaging changes only (manifests, build scripts, service files, docs, naming, platform conditionals). Any production-code change must be isolated, explained, and re-validated with the full 34/34 + 4/4. |
