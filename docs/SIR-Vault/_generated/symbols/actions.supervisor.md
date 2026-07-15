---
kind: fn
module: actions
symbol: supervisor
source: src/actions.rs
line: 466
visibility: private
async: true
generated: true
---

# `supervisor`

Owns the AT-SPI connections: one dedicated to reading the event stream, a SEPARATE one for tool calls / tree walks. They must be independent D-Bus connections — sharing a socket means a signal flood from a busy app (Firefox opening emits hundreds of events) backs up the socket while a walk holds the cache lock and stops draining, stalling that same walk's method replies until every call times out. Two sockets decouple the flood from control operations.

```rust
async fn supervisor(inner: Arc<Inner>)
```

[source](../../../../src/actions.rs#L466) · parent module: [[Module - actions]]

**Calls:** [[actions.handle_event]], [[actions.liveness_ping]], [[cache.Cache.clear_all]], [[cache.Cache.ensure_walked]], [[cache.Cache.stats]]

**Called from:** actions.rs:106

**Types in signature:** [[actions.Inner]]

**Execution flows:** [[Flow - Startup and Initial Enumeration]], [[Flow - Bus Restart Recovery]], [[Flow - Dual Connection Architecture]]

**Exercised by:** bus_restart.py: supervisor reconnected & rebuilt cache ([[Acceptance Suite]])
