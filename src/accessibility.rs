//! Thin layer over the `atspi` crate: connection, proxy construction, node
//! inspection and subtree walking. Everything here talks raw AT-SPI; no policy.

use atspi::connection::AccessibilityConnection;
use atspi::proxy::accessible::AccessibleProxy;
use atspi::proxy::action::ActionProxy;
use atspi::proxy::component::ComponentProxy;
use atspi::proxy::editable_text::EditableTextProxy;
use atspi::proxy::text::TextProxy;
use atspi::proxy::value::ValueProxy;
use atspi::{ObjectRefOwned, Role, State, StateSet};
use zbus::names::UniqueName;
use zbus::proxy::CacheProperties;
use zbus::zvariant::ObjectPath;

use crate::types::{UiError, UiResult};

pub const REGISTRY_DEST: &str = "org.a11y.atspi.Registry";
pub const ROOT_PATH: &str = "/org/a11y/atspi/accessible/root";

/// Per-call ceiling for any single AT-SPI D-Bus round trip. AT-SPI has no
/// built-in timeout, so an unresponsive application (busy renderer, wedged
/// event loop) would otherwise hang the whole server. Bounding every call
/// keeps one bad app from freezing the control plane.
pub const CALL_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(2);

/// Await a single AT-SPI call with a hard timeout. `what` labels the op in errors.
pub async fn call<F, T>(what: &str, fut: F) -> UiResult<T>
where
    F: std::future::Future<Output = zbus::Result<T>>,
{
    match tokio::time::timeout(CALL_TIMEOUT, fut).await {
        Ok(Ok(v)) => Ok(v),
        Ok(Err(e)) => Err(UiError::Atspi(format!("{what}: {e}"))),
        Err(_) => Err(UiError::Atspi(format!("{what}: AT-SPI call timed out"))),
    }
}

/// Per-app walk limits so one huge app (browsers) can't blow up the cache.
pub const MAX_NODES_PER_APP: usize = 5000;
pub const MAX_DEPTH: usize = 60;
/// Wall-clock ceiling for a single application tree walk.
pub const WALK_BUDGET: std::time::Duration = std::time::Duration::from_secs(20);

pub async fn connect() -> UiResult<AccessibilityConnection> {
    AccessibilityConnection::new()
        .await
        .map_err(|e| UiError::Atspi(format!("cannot connect to AT-SPI bus: {e}")))
}

/// Destination + path for building a proxy to this object.
fn parts(obj: &ObjectRefOwned) -> UiResult<(UniqueName<'static>, ObjectPath<'static>)> {
    let name = obj
        .name()
        .ok_or_else(|| UiError::Atspi("object ref has no bus name".into()))?
        .clone();
    Ok((name, obj.path().clone()))
}

macro_rules! proxy_fn {
    ($fn_name:ident, $proxy:ident) => {
        pub async fn $fn_name<'a>(
            conn: &zbus::Connection,
            obj: &ObjectRefOwned,
        ) -> UiResult<$proxy<'a>> {
            let (name, path) = parts(obj)?;
            Ok($proxy::builder(conn)
                .destination(name)?
                .path(path)?
                .cache_properties(CacheProperties::No)
                .build()
                .await?)
        }
    };
}

proxy_fn!(accessible_proxy, AccessibleProxy);
proxy_fn!(action_proxy, ActionProxy);
proxy_fn!(component_proxy, ComponentProxy);
proxy_fn!(value_proxy, ValueProxy);
proxy_fn!(editable_text_proxy, EditableTextProxy);
proxy_fn!(text_proxy, TextProxy);

/// The desktop root object: its children are the accessible applications.
pub async fn registry_root<'a>(conn: &zbus::Connection) -> UiResult<AccessibleProxy<'a>> {
    Ok(AccessibleProxy::builder(conn)
        .destination(REGISTRY_DEST)?
        .path(ROOT_PATH)?
        .cache_properties(CacheProperties::No)
        .build()
        .await?)
}

/// Raw per-node facts fetched from AT-SPI in one visit.
/// Interfaces are deliberately NOT fetched here: no walk consumer reads them,
/// and skipping the call removes one D-Bus round trip per visited node.
#[derive(Debug, Clone)]
pub struct RawNode {
    pub name: String,
    pub role: Role,
    pub states: StateSet,
    pub accessible_id: Option<String>,
    pub children: Vec<ObjectRefOwned>,
}

pub fn norm_role(role: Role) -> String {
    role.name().to_lowercase().replace([' ', '_'], "-")
}

/// Accept user-supplied role aliases ("button" == "push-button").
pub fn norm_role_query(input: &str) -> String {
    let r = input.trim().to_lowercase().replace([' ', '_'], "-");
    match r.as_str() {
        // observed: GTK buttons report role "button" (Role::Button)
        "push-button" => "button".into(),
        "textbox" | "textfield" | "entry" => "text".into(),
        _ => r,
    }
}

pub fn is_window_role(role: Role) -> bool {
    matches!(
        role,
        Role::Frame | Role::Window | Role::Dialog | Role::Alert | Role::FileChooser
    )
}

pub async fn inspect(conn: &zbus::Connection, obj: &ObjectRefOwned) -> UiResult<RawNode> {
    let acc = accessible_proxy(conn, obj).await?;
    // Every call is timeout-bounded: a wedged app must not stall the walk.
    let role = call("get_role", acc.get_role()).await?;
    let states = call("get_state", acc.get_state()).await?;
    let name = call("name", acc.name()).await.unwrap_or_default();
    // AccessibleId is optional; many toolkits/objects don't provide it.
    // Chromium/Electron publish the DOM id in the attributes map instead.
    let mut accessible_id = match call("accessible_id", acc.accessible_id()).await {
        Ok(s) if !s.is_empty() => Some(s),
        _ => None,
    };
    if accessible_id.is_none() {
        if let Ok(attrs) = call("get_attributes", acc.get_attributes()).await {
            accessible_id = attrs.get("id").filter(|v| !v.is_empty()).cloned();
        }
    }
    let children = if states.contains(State::Defunct) {
        Vec::new()
    } else {
        call("get_children", acc.get_children())
            .await
            .unwrap_or_default()
    };
    Ok(RawNode {
        name,
        role,
        states,
        accessible_id,
        children,
    })
}

pub fn enabled(states: &StateSet) -> bool {
    states.contains(State::Enabled) || states.contains(State::Sensitive)
}

pub fn visible(states: &StateSet) -> bool {
    states.contains(State::Showing) || states.contains(State::Visible)
}

/// List the accessible applications (children of the desktop root).
pub async fn list_app_refs(conn: &zbus::Connection) -> UiResult<Vec<ObjectRefOwned>> {
    let root = registry_root(conn).await?;
    call("list applications", root.get_children()).await
}
