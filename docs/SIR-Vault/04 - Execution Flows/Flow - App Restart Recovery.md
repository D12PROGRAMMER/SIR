---
kind: flow
generated: false
---

# Flow: App Restart Recovery

An application exits and relaunches. No SIR connection is involved — the app just leaves and rejoins the bus under a new unique name. Code: [[cache.Cache.sync_apps]], [[resolver.verify_live]].

```mermaid
sequenceDiagram
    participant CL as client
    participant SIR as SIR
    participant BUS as AT-SPI bus
    participant OLD as app (pid 1)
    participant NEW as app (pid 2)

    Note over OLD: exits — bus name :1.42 vanishes
    CL->>SIR: ui_read {ref: app-2:node-10}
    alt sync already noticed
        SIR-->>CL: stale_target (node gone with its app)
    else ref still cached
        SIR->>BUS: verify_live GetState → error
        SIR->>SIR: evict node
        SIR-->>CL: stale_target
    end
    Note over NEW: launches — registers as :1.57
    CL->>SIR: ui_wait_for {app, id: save-project}
    SIR->>BUS: sync_apps → sees :1.57, drops :1.42
    SIR->>BUS: walk new app (fresh refs app-4:*)
    SIR-->>CL: found {ref: app-4:node-30, …}
    CL->>SIR: ui_press {app, id} — works
```

Facts:

- Old refs answer `stale_target` (re-find), never `not_found` (doesn't exist) — the distinction matters to callers ([[Resolution and References]]).
- The new instance gets **new refs**; IDs are the durable addressing mode across restarts.
- Verified by the `restart` battery: *ref to exited app → stale_target*, *relaunched app resolvable without server restart*.
