---
kind: module
module: accessibility
source: src/accessibility.rs
generated: false
---

# Module: accessibility

[source](../../../src/accessibility.rs) — the only module that talks raw AT-SPI. Everything above it deals in cache entries and refs.

## Contents

- **[[accessibility.connect]]** — `AccessibilityConnection::new()` with a clear error
- **Proxy constructors** (macro-generated, all with `CacheProperties::No`): [[accessibility.accessible_proxy]], [[accessibility.action_proxy]], [[accessibility.component_proxy]], [[accessibility.value_proxy]], [[accessibility.editable_text_proxy]], [[accessibility.text_proxy]], [[accessibility.registry_root]]
- **[[accessibility.call]]** — the 2s per-call timeout wrapper; every round trip in the codebase goes through it ([[Timeout Model]])
- **[[accessibility.inspect]]** — one-visit node read: name, role, states, interfaces, accessible id (property, then attributes `id` fallback for Chromium-family — [[ADR - DOM ID Fallback]]), children
- **Semantics**: [[accessibility.enabled]], [[accessibility.visible]], [[accessibility.is_window_role]], [[accessibility.norm_role]], [[accessibility.norm_role_query]] (input aliases: `push-button`→`button`, `entry|textbox|textfield`→`text`)
- **[[accessibility.list_app_refs]]** — registry root children = applications
- **Constants**: [[accessibility.CALL_TIMEOUT]] (2s), [[accessibility.WALK_BUDGET]] (20s), [[accessibility.MAX_NODES_PER_APP]] (5000), [[accessibility.MAX_DEPTH]] (60), [[accessibility.REGISTRY_DEST]], [[accessibility.ROOT_PATH]]

## Design rule

No policy here: this module never decides *which* object to touch or *whether* an action is appropriate — it only makes individual AT-SPI operations safe (timeouts) and convenient (proxies, `RawNode`). Policy lives in [[Module - resolver]] and [[Module - actions]].

Full symbol list: [[Symbol Index]] § accessibility.
