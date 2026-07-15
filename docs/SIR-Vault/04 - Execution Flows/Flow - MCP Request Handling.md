---
kind: flow
generated: false
---

# Flow: MCP Request Handling

Traced from [[mcp.serve]] → [[mcp.call_tool]] → [[actions.Service|Service]].

```mermaid
flowchart TD
    IN[stdin line] --> P{parse JSON}
    P -- fail --> E32700[JSON-RPC error -32700] --> OUT
    P -- ok --> M{method}
    M -- initialize --> INIT[capabilities + serverInfo] --> OUT[stdout line]
    M -- ping --> PONG[empty result] --> OUT
    M -- tools/list --> TL[9 tool defs + schemas] --> OUT
    M -- "tools/call" --> CT[call_tool]
    M -- notification --> DROP[no response]
    M -- unknown w/ id --> E32601[-32601] --> OUT
    CT --> ZC{zconn — bus connected?}
    ZC -- no --> TE1["atspi_error: reconnecting (isError result)"] --> OUT
    ZC -- yes --> SVC[Service method<br/>resolve → act → observe]
    SVC -- Ok --> TR[content text = compact JSON] --> OUT
    SVC -- UiError --> TE2["error payload + candidates?, isError: true"] --> OUT
```

Facts:

- Requests are handled **sequentially** in arrival order; there is no per-request concurrency ([[Process and Connection Model]]).
- Tool-level failures never surface as protocol errors — a client always gets a structured [[Error Model]] payload it can act on.
- Verified by the `core` battery: malformed JSON, unknown method, unknown tool, ignored notification, clean EOF ([[Acceptance Suite]]).
