---
kind: adr
status: accepted
date: 2026-07-14
generated: false
---

# ADR: Exact-then-leaf accessible ID matching

**Context.** Qt publishes `AccessibleId` as a dotted widget-ancestry path (`QApplication.QMainWindow.QWidget.qt-save`); the developer-chosen `objectName` is only the final segment. Exact matching made `id=qt-save` a `not_found`.

**Decision.** ID resolution runs the filter twice ([[cache.Cache.find]]): exact `accessible_id` equality first; only if that yields nothing, match the segment after the last `.`. Both passes are ambiguity-checked — leaf matching never silently picks among collisions.

**Consequences.**
- One target vocabulary works across GTK (plain ids), Qt (dotted), and browsers (DOM ids).
- Exact matches always win over leaf matches — deterministic when both exist.
- A hypothetical app using literal dots in plain ids still resolves exactly (pass 1).
