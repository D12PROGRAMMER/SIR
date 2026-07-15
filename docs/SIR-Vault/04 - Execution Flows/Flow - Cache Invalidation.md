---
kind: flow
generated: false
---

# Flow: Cache Invalidation

Every path by which cache entries stop being served, and what each does to session refs. Code: [[Module - cache]].

```mermaid
flowchart LR
    subgraph triggers
        E1[Defunct state event]
        E2[ChildrenChanged Delete]
        E3[ChildrenChanged Insert]
        E4[app vanished from registry<br/>sync_apps]
        E5[verify_live failure on use]
        E6[bus reconnect<br/>supervisor]
        E7[wait_for periodic force<br/>every 8th poll]
    end
    E1 --> RS[remove_subtree]
    E2 --> RS
    E3 --> MD[mark_app_dirty<br/>walked=false, nodes KEPT]
    E7 --> MD
    E4 --> RA[remove_app<br/>all its nodes dropped]
    E5 --> RN[remove_node]
    E6 --> CA[clear_all]
    MD --> RW[next ensure_walked:<br/>ref-stable re-walk + prune unvisited]
    RS & RA & RN & CA --> STALE[affected refs → stale_target]
    RW -- "surviving nodes" --> KEEP[refs unchanged]
    RW -- "vanished nodes" --> STALE
```

The invariant across all paths: **a ref dies only when its object is genuinely gone** (or the whole connection was rebuilt). Dirty-marking and re-walking never invalidate refs of living objects — enforced by `node_by_key` reuse and prune-only-unvisited in [[cache.Cache.walk_app]].
