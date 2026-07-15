---
kind: interface
generated: false
---

# Output Conventions

Payloads are compacted for token economy **without shortening semantic meaning**. Absence always means "the default"; anything exceptional is present. Implemented in [[types.ControlRef]] serialization, [[actions.controls_result]], [[actions.snapshot]], [[resolver.describe]].

## ControlRef fields

| Field | Present when |
|---|---|
| `ref`, `role` | always |
| `id` | control has an accessible/DOM id |
| `name` | non-empty |
| `enabled`, `visible` | **only when `false`** |
| `actions` | non-empty (normalized names) |
| `app`, `window` | not hoisted (see below) |

## List/find results

- `app` / `window` are **hoisted to the top level** when every item agrees; items then omit the field. An item that genuinely has none (e.g. an application root outside any window) carries an explicit `null` — absent ≠ null.
- `total` appears only when it differs from the list length (i.e. when capped); `truncated` only when `true`.

## Action results

- `press`/`focus` state snapshots contain only exceptional values (`enabled:false`, `visible:false`, `focused:true`, non-empty `name`); a vanished object is `{gone: true}`.
- Identical before/after collapses to a single `state`; a difference yields `state_before` + `state_after`.

## Error messages

Plain criteria, no transport or debug internals:

```
no control matches app=test-app id=nope        ✓
target "app=Some(\"test-app\") … ref=None"     ✗ (banned)
```

These conventions are stated in the tool descriptions (`tools/list`) so client models parse absence correctly.
