//! Service layer: the operations exposed as MCP tools, plus the connection
//! supervisor that owns the AT-SPI connection lifecycle (initial enumeration,
//! event pump, reconnection with backoff).

use std::sync::Arc;
use std::time::Duration;

use atspi::connection::AccessibilityConnection;
use atspi::events::ObjectEvents;
use atspi::{Event, Operation};
use futures_util::StreamExt;
use serde_json::{json, Value};
use tokio::sync::{watch, Mutex, RwLock};

use crate::accessibility as ax;
use crate::cache::{key_of, Cache, Filter, NodeEntry};
use crate::resolver;
use crate::types::{ControlRef, Target, UiError, UiResult};

const PRESS_ACTION_PRIORITY: &[&str] =
    &["default", "dodefault", "press", "click", "activate", "push"];
const LIST_CAP: usize = 500;
const FIND_CAP: usize = 50;
const PING_INTERVAL: Duration = Duration::from_secs(15);

struct Inner {
    conn: RwLock<Option<Arc<AccessibilityConnection>>>,
    cache: Mutex<Cache>,
    ready_tx: watch::Sender<bool>,
}

pub struct Service {
    inner: Arc<Inner>,
}

/// Build a result object from a list of controls under `key`, hoisting `app`
/// and `window` to the top level when every item that has one agrees. Items
/// then omit the field (absent = the hoisted value); an item that genuinely
/// has none carries an explicit `null`. `total`/`truncated` appear only when
/// they add information beyond the list length.
fn controls_result(key: &str, items: Vec<ControlRef>, total: usize, cap: usize) -> Value {
    let mut vals: Vec<serde_json::Map<String, Value>> = items
        .iter()
        .map(|c| {
            serde_json::to_value(c)
                .ok()
                .and_then(|v| v.as_object().cloned())
                .unwrap_or_default()
        })
        .collect();
    let n = vals.len();
    let mut out = serde_json::Map::new();
    if n > 1 {
        for field in ["app", "window"] {
            let distinct: std::collections::HashSet<String> = vals
                .iter()
                .filter_map(|m| m.get(field).and_then(|v| v.as_str()).map(String::from))
                .collect();
            if distinct.len() == 1 {
                out.insert(field.into(), json!(distinct.iter().next().unwrap()));
                for m in vals.iter_mut() {
                    if m.remove(field).is_none() {
                        m.insert(field.into(), Value::Null);
                    }
                }
            }
        }
    }
    out.insert(key.into(), json!(vals));
    if total != n {
        out.insert("total".into(), json!(total));
    }
    if total > cap {
        out.insert("truncated".into(), json!(true));
    }
    Value::Object(out)
}

/// Normalize action names across toolkits: lowercase; unnamed actions
/// (Chromium exposes these) become "default" (index 0) or "action-N".
pub fn action_names(actions: &[atspi::Action]) -> Vec<String> {
    actions
        .iter()
        .enumerate()
        .map(|(i, a)| {
            let n = a.name.trim().to_lowercase();
            if !n.is_empty() {
                n
            } else if i == 0 {
                "default".to_string()
            } else {
                format!("action-{i}")
            }
        })
        .collect()
}

impl Service {
    pub async fn new() -> UiResult<Self> {
        let (ready_tx, mut ready_rx) = watch::channel(false);
        let inner = Arc::new(Inner {
            conn: RwLock::new(None),
            cache: Mutex::new(Cache::default()),
            ready_tx,
        });
        tokio::spawn(supervisor(Arc::clone(&inner)));
        // Wait for the first successful connection + initial enumeration.
        let deadline = tokio::time::timeout(Duration::from_secs(15), async {
            while !*ready_rx.borrow() {
                if ready_rx.changed().await.is_err() {
                    break;
                }
            }
        });
        if deadline.await.is_err() {
            return Err(UiError::Atspi(
                "could not reach the AT-SPI accessibility bus within 15s".into(),
            ));
        }
        Ok(Service { inner })
    }

    /// A live zbus connection, or a clear error while reconnecting.
    async fn zconn(&self) -> UiResult<zbus::Connection> {
        match self.inner.conn.read().await.as_ref() {
            Some(ac) => Ok(ac.connection().clone()),
            None => Err(UiError::Atspi(
                "accessibility bus disconnected; reconnecting — retry shortly".into(),
            )),
        }
    }

    /// Shared prologue for every target-addressed operation (read/press/
    /// set_value/focus): live connection, strict resolution, and a snapshot
    /// of the resolved node. The cache lock is released before returning so
    /// callers do their AT-SPI work without holding it.
    async fn resolve_node(&self, t: &Target) -> UiResult<(zbus::Connection, NodeEntry)> {
        let conn = self.zconn().await?;
        let mut c = self.inner.cache.lock().await;
        let r = resolver::resolve(&conn, &mut c, t).await?;
        let node = c.nodes.get(&r.node_ref).cloned().ok_or_else(|| {
            UiError::StaleTarget(format!("ref '{}' vanished during resolution", r.node_ref))
        })?;
        Ok((conn, node))
    }

    // ---- tools ----

    pub async fn list_apps(&self) -> UiResult<Value> {
        let conn = self.zconn().await?;
        let mut c = self.inner.cache.lock().await;
        c.sync_apps(&conn).await?;
        let mut apps: Vec<Value> = c
            .apps
            .values()
            .map(|a| json!({ "ref": a.app_ref, "name": a.name }))
            .collect();
        apps.sort_by_key(|v| v["ref"].as_str().unwrap_or_default().to_string());
        Ok(json!({ "apps": apps }))
    }

    pub async fn list_windows(&self, app: Option<String>) -> UiResult<Value> {
        let conn = self.zconn().await?;
        let mut c = self.inner.cache.lock().await;
        c.ensure_walked(&conn, app.as_deref()).await?;
        let hits = c.find(&Filter {
            app: app.clone(),
            ..Default::default()
        });
        let windows: Vec<ControlRef> = hits
            .iter()
            .filter(|n| n.is_window)
            .map(|n| c.control_ref(n))
            .collect();
        let total = windows.len();
        Ok(controls_result("windows", windows, total, usize::MAX))
    }

    pub async fn list_controls(&self, window: Option<String>) -> UiResult<Value> {
        let conn = self.zconn().await?;
        let mut c = self.inner.cache.lock().await;
        c.ensure_walked(&conn, None).await?;
        let f = Filter {
            window: window.clone(),
            ..Default::default()
        };
        let hits = c.find(&f);
        let eligible: Vec<_> = hits
            .iter()
            .filter(|n| !n.is_window && n.role_str != "application")
            .collect();
        let total = eligible.len();
        let controls: Vec<ControlRef> = eligible
            .iter()
            .take(LIST_CAP)
            .map(|n| c.control_ref(n))
            .collect();
        Ok(controls_result("controls", controls, total, LIST_CAP))
    }

    pub async fn find(&self, t: &Target) -> UiResult<Value> {
        let conn = self.zconn().await?;
        let mut c = self.inner.cache.lock().await;
        c.ensure_walked(&conn, t.app.as_deref()).await?;
        let f = Filter {
            app: t.app.clone(),
            window: t.window.clone(),
            id: t.id.clone(),
            role: t.role.as_deref().map(ax::norm_role_query),
            name: t.name.clone(),
        };
        let hits = c.find(&f);
        let total = hits.len();
        let mut out: Vec<ControlRef> = Vec::new();
        for n in hits.iter().take(FIND_CAP) {
            let mut cr = c.control_ref(n);
            // Best-effort action list for the first few hits.
            if out.len() < 10 {
                if let Ok(ap) = ax::action_proxy(&conn, &n.obj).await {
                    if let Ok(actions) = ax::call("get_actions", ap.get_actions()).await {
                        cr.actions = action_names(&actions);
                    }
                }
            }
            out.push(cr);
        }
        Ok(controls_result("matches", out, total, FIND_CAP))
    }

    pub async fn read(&self, t: &Target) -> UiResult<Value> {
        let (conn, node) = self.resolve_node(t).await?;
        let mut out = {
            let c = self.inner.cache.lock().await;
            serde_json::to_value(c.control_ref(&node)).unwrap_or_default()
        };

        let acc = ax::accessible_proxy(&conn, &node.obj).await?;
        if let Ok(d) = acc.description().await {
            if !d.is_empty() {
                out["description"] = json!(d);
            }
        }
        if let Ok(ifaces) = acc.get_interfaces().await {
            if ifaces.contains(atspi::Interface::Value) {
                if let Ok(vp) = ax::value_proxy(&conn, &node.obj).await {
                    out["value"] = json!({
                        "current": vp.current_value().await.ok(),
                        "min": vp.minimum_value().await.ok(),
                        "max": vp.maximum_value().await.ok(),
                    });
                }
            }
            if ifaces.contains(atspi::Interface::Text) {
                if let Ok(tp) = ax::text_proxy(&conn, &node.obj).await {
                    if let Ok(count) = tp.character_count().await {
                        let end = count.min(4000);
                        if let Ok(text) = tp.get_text(0, end).await {
                            out["text"] = json!(text);
                            out["text_length"] = json!(count);
                        }
                    }
                }
            }
            if ifaces.contains(atspi::Interface::Action) {
                if let Ok(ap) = ax::action_proxy(&conn, &node.obj).await {
                    if let Ok(actions) = ap.get_actions().await {
                        out["actions"] = json!(action_names(&actions));
                    }
                }
            }
        }
        Ok(out)
    }

    pub async fn press(&self, t: &Target) -> UiResult<Value> {
        let (conn, node) = self.resolve_node(t).await?;
        // resolve() refreshed these states from the live object.
        if !node.visible {
            return Err(UiError::NotActionable(format!(
                "'{}' ({}) exists but is not visible",
                node.name, node.node_ref
            )));
        }
        if !node.enabled {
            return Err(UiError::NotActionable(format!(
                "'{}' ({}) exists but is disabled",
                node.name, node.node_ref
            )));
        }

        let ap = ax::action_proxy(&conn, &node.obj).await?;
        let actions = ax::call("get_actions", ap.get_actions())
            .await
            .map_err(|_| {
                UiError::ControlNotAccessible(format!(
                    "'{}' ({}) does not expose the AT-SPI Action interface",
                    node.name, node.node_ref
                ))
            })?;
        if actions.is_empty() {
            return Err(UiError::ControlNotAccessible(format!(
                "'{}' ({}) exposes no accessibility actions",
                node.name, node.node_ref
            )));
        }
        let names = action_names(&actions);
        let chosen = PRESS_ACTION_PRIORITY
            .iter()
            .find_map(|want| names.iter().position(|n| n == want))
            .or(if actions.len() == 1 { Some(0) } else { None })
            .ok_or_else(|| {
                UiError::ControlNotAccessible(format!(
                    "no press-like action on '{}'; available: {:?}",
                    node.name, names
                ))
            })?;

        let before = snapshot(&conn, &node.obj).await;
        let ok = ax::call("do_action", ap.do_action(chosen as i32)).await?;
        tokio::time::sleep(Duration::from_millis(150)).await;
        let after = snapshot(&conn, &node.obj).await;

        let mut out = serde_json::Map::new();
        out.insert("pressed".into(), json!(ok));
        out.insert("ref".into(), json!(node.node_ref));
        out.insert("action".into(), json!(names[chosen]));
        // Report state once when the action changed nothing observable;
        // before/after only when they differ.
        if before == after {
            out.insert("state".into(), after);
        } else {
            out.insert("state_before".into(), before);
            out.insert("state_after".into(), after);
        }
        Ok(Value::Object(out))
    }

    pub async fn set_value(&self, t: &Target, value: &Value) -> UiResult<Value> {
        let (conn, node) = self.resolve_node(t).await?;
        if !node.enabled {
            return Err(UiError::NotActionable(format!(
                "'{}' ({}) is disabled",
                node.name, node.node_ref
            )));
        }

        let acc = ax::accessible_proxy(&conn, &node.obj).await?;
        let ifaces = acc
            .get_interfaces()
            .await
            .unwrap_or(atspi::InterfaceSet::empty());

        if let Some(num) = value.as_f64() {
            if ifaces.contains(atspi::Interface::Value) {
                let vp = ax::value_proxy(&conn, &node.obj).await?;
                vp.set_current_value(num)
                    .await
                    .map_err(|e| UiError::Atspi(format!("set value failed: {e}")))?;
                let now = vp.current_value().await.ok();
                return Ok(json!({ "set": true, "ref": node.node_ref, "value": now }));
            }
        }
        if let Some(text) = value.as_str() {
            if ifaces.contains(atspi::Interface::EditableText) {
                let ep = ax::editable_text_proxy(&conn, &node.obj).await?;
                let ok = ep
                    .set_text_contents(text)
                    .await
                    .map_err(|e| UiError::Atspi(format!("set text failed: {e}")))?;
                return Ok(json!({ "set": ok, "ref": node.node_ref, "text": text }));
            }
        }
        Err(UiError::ControlNotAccessible(format!(
            "'{}' ({}) accepts neither numeric Value nor EditableText for {:?}",
            node.name, node.node_ref, value
        )))
    }

    pub async fn focus(&self, t: &Target) -> UiResult<Value> {
        let (conn, node) = self.resolve_node(t).await?;

        let cp = ax::component_proxy(&conn, &node.obj).await.map_err(|_| {
            UiError::ControlNotAccessible(format!(
                "'{}' ({}) does not expose the Component interface",
                node.name, node.node_ref
            ))
        })?;
        let ok = cp
            .grab_focus()
            .await
            .map_err(|e| UiError::Atspi(format!("GrabFocus failed: {e}")))?;
        tokio::time::sleep(Duration::from_millis(100)).await;
        let after = snapshot(&conn, &node.obj).await;
        Ok(json!({ "focused": ok, "ref": node.node_ref, "state": after }))
    }

    pub async fn wait_for(&self, query: &Target, timeout_ms: u64) -> UiResult<Value> {
        let deadline = tokio::time::Instant::now() + Duration::from_millis(timeout_ms);
        let mut iter: u32 = 0;
        loop {
            // Reconnects may happen mid-wait: fetch the connection each round.
            if let Ok(conn) = self.zconn().await {
                let mut c = self.inner.cache.lock().await;
                // Events handle appear/disappear; every ~2s force a re-walk of the
                // queried app anyway in case events were missed. Refs survive re-walks.
                if iter > 0 && iter.is_multiple_of(8) {
                    let dirty: Vec<String> = c
                        .apps
                        .values()
                        .filter(|a| match &query.app {
                            Some(f) => a.name.eq_ignore_ascii_case(f),
                            None => true,
                        })
                        .map(|a| a.bus_name.clone())
                        .collect();
                    for bus in dirty {
                        c.mark_app_dirty(&bus);
                    }
                }
                if c.ensure_walked(&conn, query.app.as_deref()).await.is_ok() {
                    let f = Filter {
                        app: query.app.clone(),
                        window: query.window.clone(),
                        id: query.id.clone(),
                        role: query.role.as_deref().map(ax::norm_role_query),
                        name: query.name.clone(),
                    };
                    let hits = c.find(&f);
                    match hits.len() {
                        1 => {
                            let cr = c.control_ref(hits[0]);
                            return Ok(json!({
                                "found": serde_json::to_value(cr).unwrap_or_default(),
                                "waited_ms": iter as u64 * 250
                            }));
                        }
                        0 => {}
                        _ => {
                            let cands: Vec<_> =
                                hits.iter().take(10).map(|n| c.control_ref(n)).collect();
                            return Err(UiError::Ambiguous(
                                format!("{} controls match while waiting", hits.len()),
                                cands,
                            ));
                        }
                    }
                }
            }
            if tokio::time::Instant::now() >= deadline {
                return Err(UiError::Timeout(format!(
                    "no control matched the query within {timeout_ms}ms"
                )));
            }
            iter += 1;
            tokio::time::sleep(Duration::from_millis(250)).await;
        }
    }
}

/// Owns the AT-SPI connections: one dedicated to reading the event stream, a
/// SEPARATE one for tool calls / tree walks. They must be independent D-Bus
/// connections — sharing a socket means a signal flood from a busy app (Firefox
/// opening emits hundreds of events) backs up the socket while a walk holds the
/// cache lock and stops draining, stalling that same walk's method replies until
/// every call times out. Two sockets decouple the flood from control operations.
async fn supervisor(inner: Arc<Inner>) {
    let mut backoff = Duration::from_millis(500);
    loop {
        // Call connection (walks, tool ops).
        let ac = match ax::connect().await {
            Ok(c) => Arc::new(c),
            Err(e) => {
                eprintln!("[ui-mcp] AT-SPI connect failed: {e}; retrying in {backoff:?}");
                tokio::time::sleep(backoff).await;
                backoff = (backoff * 2).min(Duration::from_secs(10));
                continue;
            }
        };
        // Event connection (signal stream only) — independent socket.
        let ev_ac = match ax::connect().await {
            Ok(c) => c,
            Err(e) => {
                eprintln!("[ui-mcp] AT-SPI event-connect failed: {e}; retrying in {backoff:?}");
                tokio::time::sleep(backoff).await;
                backoff = (backoff * 2).min(Duration::from_secs(10));
                continue;
            }
        };
        backoff = Duration::from_millis(500);

        if let Err(e) = ev_ac.register_event::<ObjectEvents>().await {
            eprintln!("[ui-mcp] event registration failed: {e}; reconnecting");
            tokio::time::sleep(Duration::from_secs(1)).await;
            continue;
        }
        let zc = ac.connection().clone();
        *inner.conn.write().await = Some(Arc::clone(&ac));

        // Automatic initial enumeration: apps + full trees, before first query.
        {
            let mut c = inner.cache.lock().await;
            c.clear_all();
            match c.ensure_walked(&zc, None).await {
                Ok(()) => {
                    let (apps, nodes) = c.stats();
                    eprintln!("[ui-mcp] connected: enumerated {apps} apps, {nodes} nodes");
                }
                Err(e) => eprintln!("[ui-mcp] initial enumeration incomplete: {e}"),
            }
        }
        let _ = inner.ready_tx.send(true);

        // Event pump with liveness ping; ends on stream end or ping failure.
        // Stream comes from the dedicated event connection.
        let stream = ev_ac.event_stream();
        tokio::pin!(stream);
        let mut ping = tokio::time::interval(PING_INTERVAL);
        ping.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
        ping.tick().await; // first tick is immediate; skip it
        loop {
            tokio::select! {
                ev = stream.next() => {
                    match ev {
                        None => {
                            eprintln!("[ui-mcp] event stream ended; reconnecting");
                            break;
                        }
                        Some(Ok(event)) => handle_event(&inner, &zc, event).await,
                        Some(Err(_)) => {} // malformed event; ignore
                    }
                }
                _ = ping.tick() => {
                    if let Err(e) = liveness_ping(&zc).await {
                        eprintln!("[ui-mcp] liveness ping failed ({e}); reconnecting");
                        break;
                    }
                }
            }
        }

        // Connection is gone: make tools fail fast until we're back.
        *inner.conn.write().await = None;
        inner.cache.lock().await.clear_all();
    }
}

async fn liveness_ping(conn: &zbus::Connection) -> UiResult<()> {
    let root = ax::registry_root(conn).await?;
    root.name()
        .await
        .map(|_| ())
        .map_err(|e| UiError::Atspi(e.to_string()))
}

/// Event handling is strictly in-memory: NO D-Bus I/O here. An app under heavy
/// load (Firefox opening) floods hundreds of ChildrenChanged events; doing a
/// subtree walk (D-Bus round trips) per event — while holding the cache lock —
/// saturates the shared connection and starves real tool calls until their
/// walk budget trips. So structural changes only flip a cheap dirty flag; the
/// next ensure_walked re-walks the app lazily, when the connection is calm.
async fn handle_event(inner: &Arc<Inner>, _conn: &zbus::Connection, ev: Event) {
    let Event::Object(oe) = ev else { return };
    let mut c = inner.cache.lock().await;
    match oe {
        ObjectEvents::StateChanged(e) => {
            c.patch_state(&key_of(&e.item), e.state, e.enabled);
        }
        ObjectEvents::PropertyChange(e) => {
            if e.property == "accessible-name" {
                if let atspi::events::object::Property::Name(name) = e.value {
                    c.patch_name(&key_of(&e.item), name);
                }
            }
        }
        ObjectEvents::ChildrenChanged(e) => {
            let parent_key = key_of(&e.item);
            match e.operation {
                Operation::Insert => {
                    // New subtree appeared: re-walk this app lazily (no I/O now).
                    c.mark_app_dirty(&parent_key.0);
                }
                Operation::Delete => {
                    let child_key = key_of(&e.child);
                    // In-memory subtree prune keeps refs stale immediately; a NULL
                    // child just dirties the app for the next lazy re-walk.
                    if child_key.1.is_empty() || child_key.1 == "/org/a11y/atspi/null" {
                        c.mark_app_dirty(&parent_key.0);
                    } else {
                        c.remove_subtree(&child_key);
                    }
                }
            }
        }
        _ => {}
    }
}

/// Small live-state snapshot used for before/after comparison around actions.
/// Compact: only exceptional values appear (enabled/visible only when false,
/// focused only when true, name only when non-empty; absence = the default).
async fn snapshot(conn: &zbus::Connection, obj: &atspi::ObjectRefOwned) -> Value {
    match ax::accessible_proxy(conn, obj).await {
        Ok(acc) => match ax::call("snapshot get_state", acc.get_state()).await {
            Ok(states) => {
                let mut m = serde_json::Map::new();
                if !ax::enabled(&states) {
                    m.insert("enabled".into(), json!(false));
                }
                if !ax::visible(&states) {
                    m.insert("visible".into(), json!(false));
                }
                if states.contains(atspi::State::Focused) {
                    m.insert("focused".into(), json!(true));
                }
                let name = ax::call("snapshot name", acc.name())
                    .await
                    .unwrap_or_default();
                if !name.is_empty() {
                    m.insert("name".into(), json!(name));
                }
                Value::Object(m)
            }
            Err(_) => json!({ "gone": true }),
        },
        Err(_) => json!({ "gone": true }),
    }
}
