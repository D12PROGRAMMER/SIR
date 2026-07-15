---
kind: architecture
generated: false
---

# AT-SPI Integration

SIR consumes AT-SPI exclusively through the [`atspi` crate](https://crates.io/crates/atspi) v0.30 (features `tokio`, `proxies`, `connection`) and its underlying zbus 5 connection. No custom protocol, no direct socket handling.

## Bus discovery

`AccessibilityConnection::new()` (wrapped by [[accessibility.connect]]) resolves the a11y bus address from the session bus (`org.a11y.Bus.GetAddress`) — requires `DBUS_SESSION_BUS_ADDRESS` of the target desktop session. See [[Running SIR]].

## Object addressing

Every accessible object is a `(bus name, object path)` pair — `ObjectRefOwned` in atspi 0.30. SIR keys its cache on exactly that pair ([[cache.key_of]]).

The **desktop root** is `org.a11y.atspi.Registry` at `/org/a11y/atspi/accessible/root`; its children are the accessible applications ([[accessibility.list_app_refs]]).

## Interfaces used

| AT-SPI interface | Used for | Via |
|---|---|---|
| `Accessible` | name, role, states, children, AccessibleId, attributes, description | [[accessibility.inspect]] |
| `Action` | `GetActions` + `DoAction(i)` — the press path | [[actions.Service.press]] |
| `Component` | `GrabFocus` | [[actions.Service.focus]] |
| `Value` | numeric get/set (sliders, spinners) | [[actions.Service.set_value]] |
| `EditableText` | `SetTextContents` (text fields) | [[actions.Service.set_value]] |
| `Text` | reading text content | [[actions.Service.read]] |

## Semantic state mapping

From `StateSet` ([[accessibility.enabled]], [[accessibility.visible]]):

- `enabled` = `Enabled` **or** `Sensitive` (GTK reports Sensitive)
- `visible` = `Showing` **or** `Visible`
- window roles = `Frame | Window | Dialog | Alert | FileChooser` ([[accessibility.is_window_role]])
- `Defunct` state = object is dead → removed from cache, refs become stale

## Accessible ID sources (per toolkit)

1. The `AccessibleId` property (GTK via ATK `set_accessible_id`, Qt as a dotted ancestry path)
2. Fallback: the object attributes map key `id` — this is where Chromium/Electron/Firefox publish the DOM id ([[ADR - DOM ID Fallback]])

Full behavior differences: [[Toolkit Behavior Matrix]].

Every proxy is built with `CacheProperties::No` — SIR does its own caching and does not want zbus property-cache signals. All calls are timeout-bounded ([[Timeout Model]]).
