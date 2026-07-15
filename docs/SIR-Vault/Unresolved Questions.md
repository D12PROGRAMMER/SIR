---
kind: index
generated: false
---

# Unresolved Questions

Things the vault deliberately does **not** claim, because no source, test, or observation settles them. Each entry says what would resolve it.

1. **Do already-running apps re-embed after a registry restart?** [[Bus Restart Test]] relaunches its fixture, so in-place re-embedding (atk-bridge re-registration without an app restart) is unverified per toolkit. *Resolve:* extend `bus_restart.py` with a non-relaunched second fixture and assert its reappearance.
2. **Is `DoAction(0)` always the default action in Chromium-family apps?** Observed for buttons (unnamed action 0 = activate). Other roles (links, menu items, comboboxes) unmeasured. *Resolve:* extend `chromium-test.html` with more roles and assert effects.
3. **Localized action names.** The press priority list is English (`click`, `press`, …). On a non-English session GTK/Qt may localize `GetActions` names; the all-unnamed and single-action fallbacks would still fire, but named matching could degrade. *Resolve:* run the GTK battery under `LANG=de_DE.UTF-8`.
4. **Role-alias completeness.** [[accessibility.norm_role_query]] aliases only `push-button` and text-input synonyms. Other AT-SPI role-name drift between toolkits is unmapped. *Resolve:* diff `Role::name()` output across toolkits for common widgets.
5. **Cap sufficiency for very large apps.** `MAX_NODES_PER_APP = 5000` covered Firefox-with-one-tab (838 nodes). A many-tab browser or IDE may exceed it; behavior is documented truncation, but *which* nodes get cut is BFS-order-dependent. *Resolve:* measure node counts on heavy real apps; consider per-window walks.
6. **Event-socket backpressure limits.** The dual-connection design isolates floods, but zbus's internal buffering on the event connection under sustained storms (dropped signals? unbounded memory?) is unmeasured. *Resolve:* synthetic flood benchmark while sampling RSS and event loss.
7. **`ui_set_value` on web inputs.** EditableText via Chromium/Electron/Firefox is not asserted by the suite (native GTK/Qt only). *Resolve:* add a browser set_value check to `run_web`.
8. **Windows title collisions.** `window` filtering is by exact title; two same-titled windows in one app make window-scoped queries ambiguous (correctly), but there is no window-ref-scoped query today. *Resolve:* decide whether `ui_list_controls` should accept a window `ref`.
9. **What "SIR" expands to.** The codename was supplied by the project owner; no expansion is recorded. The binary/crate name remains `ui-mcp`.
