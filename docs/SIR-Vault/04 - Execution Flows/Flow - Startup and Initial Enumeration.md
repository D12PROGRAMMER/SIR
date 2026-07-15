---
kind: flow
generated: false
---

# Flow: Startup and Initial Enumeration

Traced from [[main.main]] → [[actions.Service.new]] → [[actions.supervisor]] → [[cache.Cache.ensure_walked]].

```mermaid
sequenceDiagram
    participant M as main
    participant S as Service::new
    participant SUP as supervisor task
    participant BUS as AT-SPI bus
    participant C as Cache

    M->>S: Service::new()
    S->>SUP: tokio::spawn(supervisor)
    S->>S: await ready (watch channel, 15s cap)
    SUP->>BUS: connect (call connection)
    SUP->>BUS: connect (event connection)
    SUP->>BUS: register ObjectEvents (event conn)
    SUP->>C: clear_all()
    SUP->>C: ensure_walked(None)
    C->>BUS: registry root GetChildren → apps
    loop each app
        C->>BUS: BFS walk (name/role/state/id/children per node)
    end
    SUP-->>S: ready = true (stderr: "connected: enumerated N apps, M nodes")
    S-->>M: Service
    M->>M: mcp::serve(stdio) — initialize now answerable
```

Facts:

- Enumeration is **automatic and complete before the first MCP response** — `Service::new` blocks on the supervisor's ready signal (15s cap, else fatal).
- The same path re-runs after every reconnect ([[Flow - Bus Restart Recovery]]).
- Walk caps apply from the very first walk ([[Timeout Model]]).
- Measured: see [[Baseline (2026-07-14)]] `startup_to_initialize_ms` / `initial_enumeration_ms`.
