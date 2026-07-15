---
kind: architecture
generated: false
---

# Timeout Model

zbus imposes **no default timeout** on D-Bus method calls. Without bounds, one wedged application (a busy renderer, a stopped process, a hung event loop) blocks whatever SIR operation touched it — observed in practice as a permanently hung server before this model existed ([[ADR - Timeout Bounded Calls]]).

## The two bounds

| Bound | Value | Scope | Where |
|---|---|---|---|
| Per-call timeout | **2s** | every single AT-SPI round trip | [[accessibility.call]] wrapping `CALL_TIMEOUT` |
| Walk budget | **20s** | one tree walk's total wall clock | [[cache.Cache.walk_from]] using `WALK_BUDGET` |

`accessibility::call(what, fut)` wraps any proxy future in `tokio::time::timeout`; on expiry it returns `atspi_error: <what>: AT-SPI call timed out`. Node-level failures during a walk skip that node rather than aborting the walk.

The walk budget bounds **cache-lock hold time**: even 5000 individually-fast nodes, or many individually-timed-out slow ones, cannot hold the lock indefinitely. Budget hits log `walk of <app> hit time budget at N nodes` to stderr — truncation is never silent.

## Other time constants

| Constant | Value | Purpose |
|---|---|---|
| `PING_INTERVAL` | 15s | supervisor liveness ping ([[Reconnection]]) |
| press settle | 150ms | wait before the post-action state snapshot ([[actions.Service.press]]) |
| focus settle | 100ms | same, for [[actions.Service.focus]] |
| wait_for poll | 250ms | cache poll cadence; forced re-walk every 8th poll |
| reconnect backoff | 0.5s → 10s | exponential, supervisor |

## What the model does NOT do

It does not retry. A timed-out call surfaces as `atspi_error` and the caller (the agent) decides. Retry policy belongs above SIR.
