---
kind: flow
generated: false
---

# Flow: Press Action

The canonical end-to-end operation, traced from [[actions.Service.press]]. The success condition of the whole project: `ui_press {app, id}` executes the app's handler with zero synthetic input.

```mermaid
sequenceDiagram
    participant CL as MCP client
    participant SV as Service::press
    participant RS as resolver
    participant AX as accessibility (call conn)
    participant APP as target application

    CL->>SV: ui_press {target}
    SV->>RS: resolve(target)
    RS->>AX: verify_live → GetState
    AX->>APP: (D-Bus, 2s bound)
    RS-->>SV: node_ref (states refreshed)
    SV->>SV: precondition: visible? enabled?
    Note over SV: hidden → not_actionable<br/>disabled → not_actionable
    SV->>AX: ActionProxy::GetActions
    AX->>APP: (2s bound)
    SV->>SV: choose action:<br/>priority [default, dodefault, press, click, activate, push]<br/>else single action → 0<br/>else all-unnamed → 0
    Note over SV: nothing suitable → control_not_accessible
    SV->>AX: snapshot (state before)
    SV->>AX: DoAction(i)
    AX->>APP: handler EXECUTES in the app
    SV->>SV: sleep 150ms (settle)
    SV->>AX: snapshot (state after)
    SV-->>CL: {pressed, ref, action, state | state_before+state_after}
```

Facts:

- Preconditions use **live** states from `verify_live`, then the action list is fetched fresh — a control disabled milliseconds ago fails correctly.
- Action names are normalized before matching ([[actions.action_names]], [[ADR - Action Name Normalization]]).
- Identical before/after snapshots collapse to a single `state` ([[Output Conventions]]).
- Latency: dominated by the deliberate 150ms settle — measured in [[Baseline (2026-07-14)]].
- Exercised on all five toolkits by the [[Acceptance Suite]].
