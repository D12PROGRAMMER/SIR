---
kind: reference
generated: false
---

# Toolkit Behavior Matrix

Observed behavior of each supported toolkit on the AT-SPI bus — every row verified by the [[Acceptance Suite]] against real applications.

| | GTK3 | Qt6 | Chromium | Electron | Firefox |
|---|---|---|---|---|---|
| App name on bus | prgname (`test-app`) | applicationName (`qt-test-app`) | `Chromium` | `electron` | `Firefox` |
| Enable a11y | install `libatk-adaptor`; `GTK_MODULES=gail:atk-bridge`, `NO_AT_BRIDGE=0` | `QT_LINUX_ACCESSIBILITY_ALWAYS_ON=1` | `--force-renderer-accessibility` | same + `--no-sandbox` as root | profile pref `accessibility.force_disabled=0`; bridges lazily when an AT is present |
| Accessible ID | `AccessibleId` property (ATK `set_accessible_id`) | `AccessibleId` = **dotted ancestry** `QApplication.QMainWindow.QWidget.qt-save` → leaf matching | DOM id in **attributes `id`**, not AccessibleId | same as Chromium | DOM id in attributes `id` |
| Button role | `button` | `button` | `button` | `button` | `button` |
| Action names | `Click` | `Press`, `SetFocus` | **unnamed** (`""`×2) → `default`, `action-1` | unnamed → `default` | single unnamed → `default` |
| Text input | EditableText ✓ | EditableText ✓ | (not asserted) | (not asserted) | (not asserted) |
| Bridging latency | immediate | immediate | seconds after load | seconds (poll via `ui_wait_for`) | 10–60 s first run with fresh profile |
| Quirks | role from ATK; `Sensitive` used for enabled | window title excluded from dotted id | huge trees — caps matter | needs Chromium flags through Electron CLI | fork storm can kill SSH-attached parents; needs prepped profile |

Normalization that makes these uniform for callers: [[actions.action_names]] (names), [[accessibility.norm_role_query]] (role aliases), exact-then-leaf ID matching ([[Resolution and References]]), attributes-`id` fallback ([[AT-SPI Integration]]).
