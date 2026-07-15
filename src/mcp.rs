//! MCP server over stdio: newline-delimited JSON-RPC 2.0.
//! Tool names use underscores (ui_press, not ui.press) because MCP clients
//! (including Claude) restrict tool names to [a-zA-Z0-9_-].

use serde_json::{json, Value};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};

use crate::actions::Service;
use crate::types::{Target, UiError};

const PROTOCOL_VERSION: &str = "2025-06-18";

fn target_schema() -> Value {
    json!({
        "type": "object",
        "properties": {
            "app":    { "type": "string", "description": "Application name on the accessibility bus" },
            "window": { "type": "string", "description": "Window title (accessible name of the top-level)" },
            "id":     { "type": "string", "description": "Application-provided accessible ID (preferred)" },
            "ref":    { "type": "string", "description": "Session-local ref from a previous search, e.g. app-4:node-182" },
            "role":   { "type": "string", "description": "AT-SPI role, e.g. button, text, check-box" },
            "name":   { "type": "string", "description": "Exact accessible name, e.g. Save" }
        }
    })
}

fn tools() -> Value {
    let t = target_schema();
    json!([
        { "name": "ui_list_apps",
          "description": "List accessible applications on the desktop.",
          "inputSchema": { "type": "object", "properties": {} } },
        { "name": "ui_list_windows",
          "description": "List top-level windows, optionally for one application.",
          "inputSchema": { "type": "object", "properties": {
              "app": { "type": "string" } } } },
        { "name": "ui_list_controls",
          "description": "List controls, optionally restricted to one window title.",
          "inputSchema": { "type": "object", "properties": {
              "window": { "type": "string" } } } },
        { "name": "ui_find",
          "description": "Search controls by app/window/id/role/name. Returns compact refs. Output conventions: enabled/visible omitted when true; app/window hoisted to top level when all matches agree (absent on an item = the hoisted value, explicit null = item has none); total omitted when equal to matches length.",
          "inputSchema": { "type": "object", "properties": {
              "app": {"type":"string"}, "window": {"type":"string"}, "id": {"type":"string"},
              "role": {"type":"string"}, "name": {"type":"string"} } } },
        { "name": "ui_read",
          "description": "Read a control's state: role, name, states, value/text, actions.",
          "inputSchema": { "type": "object", "properties": { "target": t }, "required": ["target"] } },
        { "name": "ui_press",
          "description": "Invoke a control's semantic press/click/activate accessibility action. No mouse or keyboard simulation. Returns one `state` when the action changed nothing observable on the control, else state_before/state_after; state fields show only exceptional values (enabled/visible false, focused true).",
          "inputSchema": { "type": "object", "properties": { "target": t }, "required": ["target"] } },
        { "name": "ui_set_value",
          "description": "Set a control's value: number for sliders/spinners (Value interface), string for text fields (EditableText).",
          "inputSchema": { "type": "object", "properties": {
              "target": t, "value": { "type": ["string", "number"] } },
              "required": ["target", "value"] } },
        { "name": "ui_focus",
          "description": "Give a control keyboard focus via the accessibility Component interface.",
          "inputSchema": { "type": "object", "properties": { "target": t }, "required": ["target"] } },
        { "name": "ui_wait_for",
          "description": "Wait until a control matching the query exists (polls cache + AT-SPI events).",
          "inputSchema": { "type": "object", "properties": {
              "query": t, "timeout_ms": { "type": "integer", "default": 5000 } },
              "required": ["query"] } }
    ])
}

fn parse_target(args: &Value, key: &str) -> Result<Target, UiError> {
    let raw = args.get(key).cloned().unwrap_or(Value::Null);
    if raw.is_null() {
        return Err(UiError::InvalidArgument(format!(
            "missing '{key}' argument"
        )));
    }
    serde_json::from_value(raw).map_err(|e| UiError::InvalidArgument(format!("bad {key}: {e}")))
}

/// Flat args (ui_find) reuse the Target shape.
fn parse_flat_target(args: &Value) -> Result<Target, UiError> {
    serde_json::from_value(args.clone())
        .map_err(|e| UiError::InvalidArgument(format!("bad arguments: {e}")))
}

async fn call_tool(svc: &Service, name: &str, args: &Value) -> Result<Value, UiError> {
    match name {
        "ui_list_apps" => svc.list_apps().await,
        "ui_list_windows" => {
            let app = args.get("app").and_then(|v| v.as_str()).map(String::from);
            svc.list_windows(app).await
        }
        "ui_list_controls" => {
            let window = args
                .get("window")
                .and_then(|v| v.as_str())
                .map(String::from);
            svc.list_controls(window).await
        }
        "ui_find" => svc.find(&parse_flat_target(args)?).await,
        "ui_read" => svc.read(&parse_target(args, "target")?).await,
        "ui_press" => svc.press(&parse_target(args, "target")?).await,
        "ui_set_value" => {
            let value = args
                .get("value")
                .cloned()
                .ok_or_else(|| UiError::InvalidArgument("missing 'value'".into()))?;
            svc.set_value(&parse_target(args, "target")?, &value).await
        }
        "ui_focus" => svc.focus(&parse_target(args, "target")?).await,
        "ui_wait_for" => {
            let timeout = args
                .get("timeout_ms")
                .and_then(|v| v.as_u64())
                .unwrap_or(5000);
            svc.wait_for(&parse_target(args, "query")?, timeout).await
        }
        other => Err(UiError::InvalidArgument(format!("unknown tool '{other}'"))),
    }
}

fn rpc_result(id: &Value, result: Value) -> Value {
    json!({ "jsonrpc": "2.0", "id": id, "result": result })
}

fn rpc_error(id: &Value, code: i64, message: &str) -> Value {
    json!({ "jsonrpc": "2.0", "id": id, "error": { "code": code, "message": message } })
}

fn tool_text_result(id: &Value, payload: Value, is_error: bool) -> Value {
    rpc_result(
        id,
        json!({
            "content": [ { "type": "text", "text": payload.to_string() } ],
            "isError": is_error
        }),
    )
}

pub async fn serve(svc: Service) -> std::io::Result<()> {
    let stdin = BufReader::new(tokio::io::stdin());
    let mut stdout = tokio::io::stdout();
    let mut lines = stdin.lines();

    while let Some(line) = lines.next_line().await? {
        let line = line.trim().to_string();
        if line.is_empty() {
            continue;
        }
        let msg: Value = match serde_json::from_str(&line) {
            Ok(v) => v,
            Err(e) => {
                let resp = rpc_error(&Value::Null, -32700, &format!("parse error: {e}"));
                stdout.write_all(format!("{resp}\n").as_bytes()).await?;
                stdout.flush().await?;
                continue;
            }
        };
        let id = msg.get("id").cloned().unwrap_or(Value::Null);
        let method = msg.get("method").and_then(|m| m.as_str()).unwrap_or("");
        let is_notification = msg.get("id").is_none();

        let response: Option<Value> = match method {
            "initialize" => Some(rpc_result(
                &id,
                json!({
                    "protocolVersion": msg.pointer("/params/protocolVersion")
                        .and_then(|v| v.as_str()).unwrap_or(PROTOCOL_VERSION),
                    "capabilities": { "tools": {} },
                    "serverInfo": { "name": "ui-mcp", "version": env!("CARGO_PKG_VERSION") }
                }),
            )),
            "ping" => Some(rpc_result(&id, json!({}))),
            "tools/list" => Some(rpc_result(&id, json!({ "tools": tools() }))),
            "tools/call" => {
                let name = msg
                    .pointer("/params/name")
                    .and_then(|v| v.as_str())
                    .unwrap_or("");
                let empty = json!({});
                let args = msg.pointer("/params/arguments").unwrap_or(&empty).clone();
                match call_tool(&svc, name, &args).await {
                    Ok(v) => Some(tool_text_result(&id, v, false)),
                    Err(e) => Some(tool_text_result(&id, e.to_json(), true)),
                }
            }
            _ if is_notification => None, // notifications/initialized etc.
            other => Some(rpc_error(
                &id,
                -32601,
                &format!("method not found: {other}"),
            )),
        };

        if let Some(resp) = response {
            stdout.write_all(format!("{resp}\n").as_bytes()).await?;
            stdout.flush().await?;
        }
    }
    Ok(())
}
