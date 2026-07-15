---
kind: flow
generated: false
---

# Flow: Event Processing

Traced from the pump loop in [[actions.supervisor]] → [[actions.handle_event]]. Policy: [[Event Processing]].

```mermaid
flowchart TD
    BUS[AT-SPI signals<br/>dedicated event connection] --> SEL{select!}
    PING[15s liveness ping] --> SEL
    SEL -- "stream item" --> EV{ObjectEvents}
    SEL -- "stream ended / ping failed" --> RECON[reconnect path<br/>see Flow - Bus Restart Recovery]
    EV -- "StateChanged(enabled/visible/focused)" --> PS[patch_state — in-memory]
    EV -- "StateChanged(Defunct)" --> RM[remove_subtree — refs stale]
    EV -- "PropertyChange(accessible-name)" --> PN[patch_name — in-memory]
    EV -- "ChildrenChanged(Insert)" --> DIRTY[mark_app_dirty<br/>lazy re-walk later]
    EV -- "ChildrenChanged(Delete, child known)" --> RM
    EV -- "ChildrenChanged(Delete, NULL child)" --> DIRTY
    EV -- other --> IGN[ignored]
    PS & PN & RM & DIRTY --> CACHE[(Cache)]
    QUERY[next tool call] --> EW[ensure_walked] --> CACHE
    EW -- "dirty app" --> REWALK[ref-stable re-walk + prune<br/>D-Bus I/O happens HERE, not in the pump]
```

Facts:

- The handler performs **no D-Bus I/O** — the single most important rule in the file ([[ADR - No IO in Event Handler]]).
- Insert is lazy (dirty flag), Delete is eager (in-memory prune) — rationale in [[Event Processing]].
- Verified live: window-title rename propagated to a `ui_wait_for` match; *dynamically added widget found via events* check.
