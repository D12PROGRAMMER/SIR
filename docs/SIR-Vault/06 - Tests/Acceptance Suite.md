---
kind: test
source: test/suite.py
generated: false
---

# Acceptance Suite

[test/suite.py](../../../test/suite.py) — 34 checks across five real toolkits plus protocol and restart batteries. Speaks genuine MCP to a spawned `ui-mcp` over stdio pipes. Execution shape: [[Flow - Test Suite Execution]] · how to run: [[Test Harness]].

## Batteries and what each check proves

| Battery | Checks | Proves |
|---|---|---|
| GTK3 (13) | enumeration; find/press by id; observable side effect (flag file); set_value; **ambiguous** duplicates + disambiguation by ref; disabled → `not_actionable`; unknown → `not_found`; dynamic widget appears via events; removed ref → `stale_target`; removed widget unfindable | [[Resolution and References]], [[Event Processing]], [[Error Model]] |
| Qt6 (6) | same core battery against Qt's dotted AccessibleIds (leaf matching) | [[ADR - Qt Leaf ID Matching]] |
| Chromium (3) | web button by DOM id; press fires `onclick` (title change observed) | [[ADR - DOM ID Fallback]] |
| Electron (3) | app `electron`; lazy web bridging via `ui_wait_for`; normalized unnamed actions | [[ADR - Action Name Normalization]] |
| Firefox (2) | prepped-profile lazy bridging (≤60s); press by ref | [[Toolkit Behavior Matrix]] |
| MCP protocol (5) | tools/list count; `-32700`; `-32601`; unknown tool; notifications ignored | [[MCP Interface]] |
| App restart (2) | old ref → `stale_target`; relaunched instance resolvable, no server restart | [[Flow - App Restart Recovery]] |

**Side effects are asserted, not inferred**: fixture apps write flag files (`/tmp/save-pressed`) and retitle windows; the suite reads those back. A press that "succeeded" without the app's handler running would fail the check.

## Design points

- `cleanup_leftovers()` kills stray fixture instances first — duplicates make every id *legitimately* ambiguous (observed in practice when demo apps were left open).
- Each toolkit runner spawns and kills its own app; browsers launch detached (`start_new_session`).
- `total(resp)` helper: the compact output omits `total` when it equals the list length ([[Output Conventions]]).
- Exit code = 0 iff all selected checks pass; per-check `PASS/FAIL` lines with the failing payload.

Baseline result: **34/34** in 52.7s — [[Baseline (2026-07-14)]].
