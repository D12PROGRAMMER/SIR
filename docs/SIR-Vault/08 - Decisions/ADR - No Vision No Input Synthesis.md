---
kind: adr
status: accepted (project constraint)
date: 2026-07-14
generated: false
---

# ADR: No vision, no input synthesis

**Context.** The owning project (aios) mandates a text-only control plane: vision models cause more confusion than help, and synthetic mouse/keyboard input bypasses the semantic layer this system exists to use.

**Decision.** SIR's only effectors are AT-SPI interfaces: `Action.DoAction`, `Value.SetCurrentValue`, `EditableText.SetTextContents`, `Component.GrabFocus`. When a control offers none of these, SIR returns `control_not_accessible` — it does **not** move a pointer, send keys, screenshot, or OCR.

**Consequences.**
- Applications with no accessibility exposure are honestly unreachable; that is a report, not a bug.
- Every SIR action is attributable to a semantic operation the application itself declared — auditable and deterministic.
- The human's visual channel (the noVNC looking glass) is strictly one-way and for humans only ([[Desktop Service and Looking Glass]]).
