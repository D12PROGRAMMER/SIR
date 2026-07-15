---
kind: flow
generated: false
---

# Flow: Target Resolution

Traced from [[resolver.resolve]] + [[resolver.verify_live]]. Policy: [[Resolution and References]].

```mermaid
flowchart TD
    T[Target] --> EMPTY{any of id/ref/role/name?}
    EMPTY -- no --> INVALID[invalid_argument]
    EMPTY -- yes --> WALK[ensure_walked for app filter<br/>if id/role/name present]
    WALK --> ID{id given?}
    ID -- yes --> IDX[exact accessible_id match]
    IDX -- none --> LEAF[leaf-segment match<br/>Qt dotted ids]
    IDX -- one --> LIVE
    IDX -- many --> AMB[ambiguous + candidates]
    LEAF -- one --> LIVE
    LEAF -- many --> AMB
    LEAF -- none --> REF
    ID -- no --> REF{ref given?}
    REF -- "yes, in cache" --> LIVE[verify_live:<br/>GetState round trip]
    REF -- "yes, unknown" --> STALE[stale_target]
    REF -- no --> NAME{role/name given?}
    NAME -- yes --> F[Filter: app+window+role+exact name]
    F -- one --> LIVE
    F -- many --> AMB
    F -- none --> NF[not_found]
    NAME -- no --> NF
    LIVE -- "alive (states refreshed)" --> OK[Resolved node_ref]
    LIVE -- "Defunct / error" --> EVICT[evict node] --> STALE
```

Facts:

- The winning strategy must produce **exactly one** node; multiple always error with candidates — never a silent pick.
- `verify_live` refreshes `enabled/visible/focused` on success, so action preconditions ([[Flow - Press Action]]) are live, not cached.
- Exercised by: *find Save by id*, *duplicate controls → ambiguous*, *unknown id → not_found*, *removed widget ref → stale_target*.
