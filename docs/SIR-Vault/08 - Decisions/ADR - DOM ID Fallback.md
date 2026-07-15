---
kind: adr
status: accepted
date: 2026-07-14
generated: false
---

# ADR: Object-attributes `id` as AccessibleId fallback

**Context.** Chromium, Electron, and Firefox do not set the AT-SPI `AccessibleId` property from the DOM `id`; they publish it in the object's **attributes map** under the key `id`. Without it, `ui_press {app, id: "save-project"}` — the project's success condition — was unreachable for web content.

**Decision.** [[accessibility.inspect]] reads the `AccessibleId` property first; when empty, it falls back to `GetAttributes()["id"]`. Still pure AT-SPI — the DOM id *is* the application-provided ID; only the transport slot differs.

**Consequences.**
- One extra D-Bus call per node during walks, only for nodes without an AccessibleId. Accepted; walks remain within budget at baseline.
- Web content addressable identically to native toolkits: verified on all three browser-family targets in the [[Acceptance Suite]].
