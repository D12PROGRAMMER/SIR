---
generated: true
---

# Symbol Index

91 symbols across 7 modules. Every entry is generated from source; regenerate with `test/gen_symbols.py`.

## [[Module - accessibility|accessibility]]

| Symbol | Kind | Visibility | Async | Line |
|---|---|---|---|---|
| [[accessibility.CALL_TIMEOUT\|CALL_TIMEOUT]] | const | public |  | L25 |
| [[accessibility.MAX_DEPTH\|MAX_DEPTH]] | const | public |  | L41 |
| [[accessibility.MAX_NODES_PER_APP\|MAX_NODES_PER_APP]] | const | public |  | L40 |
| [[accessibility.REGISTRY_DEST\|REGISTRY_DEST]] | const | public |  | L18 |
| [[accessibility.ROOT_PATH\|ROOT_PATH]] | const | public |  | L19 |
| [[accessibility.RawNode\|RawNode]] | struct | public |  | L98 |
| [[accessibility.WALK_BUDGET\|WALK_BUDGET]] | const | public |  | L43 |
| [[accessibility.call\|call]] | fn | public | async | L28 |
| [[accessibility.connect\|connect]] | fn | public | async | L45 |
| [[accessibility.enabled\|enabled]] | fn | public |  | L161 |
| [[accessibility.inspect\|inspect]] | fn | public | async | L128 |
| [[accessibility.is_window_role\|is_window_role]] | fn | public |  | L121 |
| [[accessibility.list_app_refs\|list_app_refs]] | fn | public | async | L170 |
| [[accessibility.norm_role\|norm_role]] | fn | public |  | L106 |
| [[accessibility.norm_role_query\|norm_role_query]] | fn | public |  | L111 |
| [[accessibility.parts\|parts]] | fn | private |  | L52 |
| [[accessibility.proxy_fn\|proxy_fn]] | macro_rules | private |  | L60 |
| [[accessibility.registry_root\|registry_root]] | fn | public | async | L85 |
| [[accessibility.visible\|visible]] | fn | public |  | L165 |

## [[Module - actions|actions]]

| Symbol | Kind | Visibility | Async | Line |
|---|---|---|---|---|
| [[actions.FIND_CAP\|FIND_CAP]] | const | private |  | L23 |
| [[actions.Inner\|Inner]] | struct | private |  | L26 |
| [[actions.LIST_CAP\|LIST_CAP]] | const | private |  | L22 |
| [[actions.PING_INTERVAL\|PING_INTERVAL]] | const | private |  | L24 |
| [[actions.PRESS_ACTION_PRIORITY\|PRESS_ACTION_PRIORITY]] | const | private |  | L20 |
| [[actions.Service.find\|Service.find]] | fn | public | async | L201 |
| [[actions.Service.focus\|Service.focus]] | fn | public | async | L379 |
| [[actions.Service.list_apps\|Service.list_apps]] | fn | public | async | L149 |
| [[actions.Service.list_controls\|Service.list_controls]] | fn | public | async | L179 |
| [[actions.Service.list_windows\|Service.list_windows]] | fn | public | async | L162 |
| [[actions.Service.new\|Service.new]] | fn | public | async | L99 |
| [[actions.Service.press\|Service.press]] | fn | public | async | L275 |
| [[actions.Service.read\|Service.read]] | fn | public | async | L230 |
| [[actions.Service.resolve_node\|Service.resolve_node]] | fn | private | async | L137 |
| [[actions.Service.set_value\|Service.set_value]] | fn | public | async | L338 |
| [[actions.Service.wait_for\|Service.wait_for]] | fn | public | async | L397 |
| [[actions.Service.zconn\|Service.zconn]] | fn | private | async | L124 |
| [[actions.Service\|Service]] | struct | public |  | L32 |
| [[actions.action_names\|action_names]] | fn | public |  | L81 |
| [[actions.controls_result\|controls_result]] | fn | private |  | L41 |
| [[actions.handle_event\|handle_event]] | fn | private | async | L561 |
| [[actions.liveness_ping\|liveness_ping]] | fn | private | async | L547 |
| [[actions.snapshot\|snapshot]] | fn | private | async | L601 |
| [[actions.supervisor\|supervisor]] | fn | private | async | L466 |

## [[Module - cache|cache]]

| Symbol | Kind | Visibility | Async | Line |
|---|---|---|---|---|
| [[cache.AppEntry\|AppEntry]] | struct | public |  | L25 |
| [[cache.Cache.app_name_of\|Cache.app_name_of]] | fn | public |  | L312 |
| [[cache.Cache.clear_all\|Cache.clear_all]] | fn | public |  | L73 |
| [[cache.Cache.control_ref\|Cache.control_ref]] | fn | public |  | L378 |
| [[cache.Cache.ensure_walked\|Cache.ensure_walked]] | fn | public | async | L290 |
| [[cache.Cache.find\|Cache.find]] | fn | public |  | L361 |
| [[cache.Cache.mark_app_dirty\|Cache.mark_app_dirty]] | fn | public |  | L144 |
| [[cache.Cache.matches\|Cache.matches]] | fn | public |  | L323 |
| [[cache.Cache.patch_name\|Cache.patch_name]] | fn | public |  | L394 |
| [[cache.Cache.patch_state\|Cache.patch_state]] | fn | public |  | L402 |
| [[cache.Cache.remove_app\|Cache.remove_app]] | fn | public |  | L125 |
| [[cache.Cache.remove_node\|Cache.remove_node]] | fn | public |  | L419 |
| [[cache.Cache.remove_subtree\|Cache.remove_subtree]] | fn | public |  | L273 |
| [[cache.Cache.stats\|Cache.stats]] | fn | public |  | L429 |
| [[cache.Cache.sync_apps\|Cache.sync_apps]] | fn | public | async | L81 |
| [[cache.Cache.walk_app\|Cache.walk_app]] | fn | public | async | L235 |
| [[cache.Cache.walk_from\|Cache.walk_from]] | fn | private | async | L154 |
| [[cache.Cache.window_name_of\|Cache.window_name_of]] | fn | public |  | L316 |
| [[cache.Cache\|Cache]] | struct | public |  | L52 |
| [[cache.Filter\|Filter]] | struct | public |  | L63 |
| [[cache.NodeEntry\|NodeEntry]] | struct | public |  | L34 |
| [[cache.key_of\|key_of]] | fn | public |  | L17 |

## [[Module - main|main]]

| Symbol | Kind | Visibility | Async | Line |
|---|---|---|---|---|
| [[main.kv_target\|kv_target]] | fn | private |  | L48 |
| [[main.main\|main]] | fn | private | async | L16 |
| [[main.run_cli\|run_cli]] | fn | private | async | L67 |

## [[Module - mcp|mcp]]

| Symbol | Kind | Visibility | Async | Line |
|---|---|---|---|---|
| [[mcp.PROTOCOL_VERSION\|PROTOCOL_VERSION]] | const | private |  | L11 |
| [[mcp.call_tool\|call_tool]] | fn | private | async | L84 |
| [[mcp.parse_flat_target\|parse_flat_target]] | fn | private |  | L79 |
| [[mcp.parse_target\|parse_target]] | fn | private |  | L68 |
| [[mcp.rpc_error\|rpc_error]] | fn | private |  | L124 |
| [[mcp.rpc_result\|rpc_result]] | fn | private |  | L120 |
| [[mcp.serve\|serve]] | fn | public | async | L138 |
| [[mcp.target_schema\|target_schema]] | fn | private |  | L13 |
| [[mcp.tool_text_result\|tool_text_result]] | fn | private |  | L128 |
| [[mcp.tools\|tools]] | fn | private |  | L27 |

## [[Module - resolver|resolver]]

| Symbol | Kind | Visibility | Async | Line |
|---|---|---|---|---|
| [[resolver.Resolved\|Resolved]] | struct | public |  | L17 |
| [[resolver.describe\|describe]] | fn | private |  | L140 |
| [[resolver.resolve\|resolve]] | fn | public | async | L21 |
| [[resolver.verify_live\|verify_live]] | fn | private | async | L101 |

## [[Module - types|types]]

| Symbol | Kind | Visibility | Async | Line |
|---|---|---|---|---|
| [[types.ControlRef\|ControlRef]] | struct | public |  | L41 |
| [[types.From.from\|From.from]] | fn | private |  | L123 |
| [[types.Target.is_empty\|Target.is_empty]] | fn | public |  | L22 |
| [[types.Target\|Target]] | struct | public |  | L5 |
| [[types.UiError.code\|UiError.code]] | fn | public |  | L82 |
| [[types.UiError.to_json\|UiError.to_json]] | fn | public |  | L95 |
| [[types.UiError\|UiError]] | enum | public |  | L62 |
| [[types.is_true\|is_true]] | fn | private |  | L32 |
| [[types.std.fmt\|std.fmt]] | fn | private |  | L115 |
