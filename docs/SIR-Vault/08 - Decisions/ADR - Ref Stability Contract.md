---
kind: adr
status: accepted
date: 2026-07-14
generated: false
---

# ADR: Session refs survive re-walks

**Context.** The first cache implementation deleted an app's nodes on any structural change and re-created them on re-walk — every `ChildrenChanged` silently invalidated **all** refs in that app, making `stale_target` meaningless (live buttons went "stale" because a sibling appeared).

**Decision.** Refs are keyed by `(bus name, object path)` in `node_by_key`; [[cache.Cache.walk_from]] **reuses** the existing ref for a known key, and [[cache.Cache.walk_app]] prunes only nodes the walk didn't visit. [[cache.Cache.mark_app_dirty]] flips a flag and keeps every node.

**Consequences.**
- `stale_target` now means exactly "your object is gone", never "the cache reorganized".
- Slightly more walk bookkeeping (visited-set + prune pass).
- The contract is load-bearing for `ui_wait_for`'s periodic forced re-walks and for the event pump's dirty-marking — both would otherwise break in-flight refs. Verified by *removed widget ref → stale_target* passing **while** dynamic-widget churn re-walks the same app.
