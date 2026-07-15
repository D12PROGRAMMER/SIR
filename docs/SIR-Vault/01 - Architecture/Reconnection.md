---
kind: architecture
generated: false
---

# Reconnection

The supervisor ([[actions.supervisor]]) owns both AT-SPI connections and treats them as disposable. Two distinct recovery scenarios:

## Application restart

No connection is lost — the app simply leaves and rejoins the bus with a new unique name.

- Old refs: [[cache.Cache.sync_apps]] notices the vanished bus name on the next query and drops the app's nodes → `stale_target`
- New instance: appears as a new `app-N` on the next sync; ID lookups resolve again

No supervisor involvement. Verified by the acceptance `restart` battery (2 checks). Flow: [[Flow - App Restart Recovery]].

## Accessibility-bus restart

The bus daemon or registry dies (session restart, registryd crash). Detection, per [[actions.supervisor]]:

- the event stream ends, **or**
- the 15s liveness ping ([[actions.liveness_ping]] — `name()` on the registry root over the call connection) fails

Then:

1. connection slot → `None`; in-flight and new tool calls fail fast with `atspi_error: accessibility bus disconnected; reconnecting — retry shortly`
2. cache cleared wholesale (all refs stale by construction)
3. reconnect loop with exponential backoff 0.5s → 10s; AT-SPI is D-Bus-activated, so touching the session bus revives it
4. on success: re-register events, full re-enumeration, ready again

Verified by [[Bus Restart Test]] (4/4): kill `at-spi2-registryd` + `at-spi-bus-launcher` under a live server; the server recovers and a press works — without restarting SIR. Flow: [[Flow - Bus Restart Recovery]].

## What is deliberately NOT recovered

Session refs across a bus reconnect. The bus assigns new unique names; old `(bus, path)` pairs are meaningless. Callers re-find by ID — which is why IDs are the preferred addressing mode ([[Resolution and References]]).
