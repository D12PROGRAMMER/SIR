---
kind: adr
status: accepted
date: 2026-07-14
generated: false
---

# ADR: Normalized action names

**Context.** Toolkits disagree wildly: GTK `Click`, Qt `Press`/`SetFocus`, Chromium-family **empty strings** for every action. Raw names made press-selection fragile and reporting inconsistent.

**Decision.** [[actions.action_names]] lowercases all names; unnamed actions become `default` (index 0) or `action-N`. `find`/`read`/`press` all report these normalized names, and press matches its priority list (`default, dodefault, press, click, activate, push`) against them; a single action or an all-unnamed list falls back to index 0.

**Consequences.**
- `actions: ["default"]` is meaningful for Chromium/Firefox instead of `["", ""]`.
- The name a caller sees is exactly the name press will match — no hidden mapping.
- Original casing is not preserved; deemed cosmetic. Localized action names (non-English sessions) are untested — see [[Unresolved Questions]].
