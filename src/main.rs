//! ui-mcp: AT-SPI accessibility-to-MCP adapter.
//! Default mode: MCP server over stdio. `ui-mcp cli ...` is the temporary
//! development CLI (spec step 4).

mod accessibility;
mod actions;
mod cache;
mod mcp;
mod resolver;
mod types;

use actions::Service;
use types::Target;

#[tokio::main]
async fn main() {
    let args: Vec<String> = std::env::args().collect();
    let svc = match Service::new().await {
        Ok(s) => s,
        Err(e) => {
            eprintln!("fatal: {e}");
            std::process::exit(1);
        }
    };

    if args.len() > 1 && args[1] == "cli" {
        let out = run_cli(&svc, &args[2..]).await;
        match out {
            Ok(v) => println!("{}", serde_json::to_string_pretty(&v).unwrap_or_default()),
            Err(e) => {
                println!(
                    "{}",
                    serde_json::to_string_pretty(&e.to_json()).unwrap_or_default()
                );
                std::process::exit(2);
            }
        }
        return;
    }

    if let Err(e) = mcp::serve(svc).await {
        eprintln!("stdio loop failed: {e}");
        std::process::exit(1);
    }
}

/// k=v pairs -> Target (keys: app, window, id, ref, role, name)
fn kv_target(pairs: &[String]) -> Target {
    let mut t = Target::default();
    for p in pairs {
        if let Some((k, v)) = p.split_once('=') {
            let v = v.to_string();
            match k {
                "app" => t.app = Some(v),
                "window" => t.window = Some(v),
                "id" => t.id = Some(v),
                "ref" => t.node_ref = Some(v),
                "role" => t.role = Some(v),
                "name" => t.name = Some(v),
                _ => eprintln!("ignoring unknown key '{k}'"),
            }
        }
    }
    t
}

async fn run_cli(svc: &Service, args: &[String]) -> types::UiResult<serde_json::Value> {
    let cmd = args.first().map(String::as_str).unwrap_or("help");
    match cmd {
        "apps" => svc.list_apps().await,
        "windows" => svc.list_windows(args.get(1).cloned()).await,
        "controls" => svc.list_controls(args.get(1).cloned()).await,
        "find" => svc.find(&kv_target(&args[1..])).await,
        "read" => svc.read(&kv_target(&args[1..])).await,
        "press" => svc.press(&kv_target(&args[1..])).await,
        "focus" => svc.focus(&kv_target(&args[1..])).await,
        "set-value" => {
            let value = args
                .get(1)
                .ok_or_else(|| {
                    types::UiError::InvalidArgument("usage: set-value <value> k=v...".into())
                })?
                .clone();
            let v: serde_json::Value = value
                .parse::<f64>()
                .map(Into::into)
                .unwrap_or(serde_json::Value::String(value));
            svc.set_value(&kv_target(&args[2..]), &v).await
        }
        "wait-for" => {
            let timeout: u64 = args.get(1).and_then(|s| s.parse().ok()).unwrap_or(5000);
            svc.wait_for(&kv_target(&args[2..]), timeout).await
        }
        _ => Ok(serde_json::json!({
            "usage": [
                "ui-mcp                      run MCP server on stdio",
                "ui-mcp cli apps",
                "ui-mcp cli windows [app]",
                "ui-mcp cli controls [window]",
                "ui-mcp cli find [app=X] [window=X] [id=X] [role=X] [name=X]",
                "ui-mcp cli read|press|focus k=v...",
                "ui-mcp cli set-value <value> k=v...",
                "ui-mcp cli wait-for <timeout_ms> k=v..."
            ]
        })),
    }
}
