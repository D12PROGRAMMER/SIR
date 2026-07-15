---
kind: module
module: types
source: src/types.rs
generated: false
---

# Module: types

[source](../../../src/types.rs) — the shared vocabulary. No logic beyond serialization and error formatting.

## Contents

- **[[types.Target]]** — how callers name a control (`app`, `window`, `id`, `ref`, `role`, `name`; all optional). Deserialization shape of every action tool's `target` argument
- **[[types.ControlRef]]** — the compact search result: `ref`, `id?`, `role`, `name`, `enabled`, `visible`, `app?`, `window?`, `actions[]`. Serialization elides defaults: `enabled`/`visible` omitted when true, `name` when empty, `actions` when empty ([[Output Conventions]])
- **[[types.UiError]]** — the 8-variant operational error enum with `code()` and `to_json()`; `Ambiguous` carries candidate `ControlRef`s ([[Error Model]])
- **`UiResult<T>`** — the ubiquitous alias

## Conversions

`From<zbus::Error> for UiError` maps any raw D-Bus failure to `atspi_error`, which is why lower layers can use `?` freely.

Full symbol list: [[Symbol Index]] § types.
