---
kind: flow
generated: false
---

# Flow: Dual Connection Architecture

Why event and control traffic use **separate D-Bus connections** — the failure this prevents, drawn from the actual incident. Decision record: [[ADR - Dual D-Bus Connections]].

## The failure with one shared socket (historical)

```mermaid
sequenceDiagram
    participant FF as Firefox (loading)
    participant SOCK as single shared socket
    participant PUMP as event pump
    participant WALK as tree walk (holds cache lock)

    FF->>SOCK: hundreds of ChildrenChanged signals
    WALK->>SOCK: GetState (method call)
    Note over PUMP: wants cache lock —<br/>held by WALK → stops draining
    Note over SOCK: undrained signals queue up…
    Note over WALK: …method REPLY stuck behind them
    WALK->>WALK: 2s per-call timeout, node after node
    WALK->>WALK: "walk hit time budget at 6 nodes" — target never reached
```

Deadlock-by-congestion: the walk starves itself through the pump it blocked.

## The fix (current)

```mermaid
flowchart LR
    subgraph SIR
        PUMP[event pump]
        TOOLS[tool calls + walks]
    end
    PUMP -- "event connection<br/>(signals only)" --> BUS[(AT-SPI bus)]
    TOOLS -- "call connection<br/>(methods only)" --> BUS
```

A signal flood now saturates only the event socket; method replies flow unimpeded on their own connection. Combined with the no-I/O event handler ([[ADR - No IO in Event Handler]]), a chatty application can at worst delay cache freshness — never control.

Measured effect: long-lived server finds Firefox's button ~10s after launch (previously: never). Both connections are built, registered, torn down, and rebuilt together by [[actions.supervisor]].
