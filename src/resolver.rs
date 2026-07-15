//! Target resolution with strict precedence and no silent disambiguation:
//!   1. application-provided accessible ID
//!   2. previously returned session ref
//!   3. app + window + role + exact accessible name
//!   4. ambiguity error
//!
//! A strategy that matches exactly one node wins. Multiple matches are an
//! error carrying the candidates. Zero matches falls through to the next
//! strategy; if all strategies produce nothing -> not_found.

use atspi::State;

use crate::accessibility as ax;
use crate::cache::{Cache, Filter};
use crate::types::{Target, UiError, UiResult};

pub struct Resolved {
    pub node_ref: String,
}

pub async fn resolve(
    conn: &zbus::Connection,
    cache: &mut Cache,
    target: &Target,
) -> UiResult<Resolved> {
    if target.is_empty() {
        return Err(UiError::InvalidArgument(
            "target must contain at least one of: id, ref, name, role (optionally app/window)"
                .into(),
        ));
    }

    // Strategy 2 needs no walk; 1 and 3 need the relevant apps walked.
    let needs_walk = target.id.is_some() || target.name.is_some() || target.role.is_some();
    if needs_walk {
        cache.ensure_walked(conn, target.app.as_deref()).await?;
    }

    // 1. accessible ID
    if let Some(id) = &target.id {
        let f = Filter {
            app: target.app.clone(),
            window: target.window.clone(),
            id: Some(id.clone()),
            ..Default::default()
        };
        let hits = cache.find(&f);
        match hits.len() {
            1 => return verify_live(conn, cache, &hits[0].node_ref.clone()).await,
            0 => {} // fall through
            _ => {
                let cands = hits.iter().take(10).map(|n| cache.control_ref(n)).collect();
                return Err(UiError::Ambiguous(
                    format!("{} controls share accessible id '{}'", hits.len(), id),
                    cands,
                ));
            }
        }
    }

    // 2. session ref
    if let Some(node_ref) = &target.node_ref {
        if cache.nodes.contains_key(node_ref) {
            return verify_live(conn, cache, node_ref).await;
        }
        return Err(UiError::StaleTarget(format!(
            "ref '{node_ref}' is no longer known (object destroyed or cache invalidated)"
        )));
    }

    // 3. app/window/role/exact-name
    if target.name.is_some() || target.role.is_some() {
        let f = Filter {
            app: target.app.clone(),
            window: target.window.clone(),
            id: None,
            role: target.role.as_deref().map(ax::norm_role_query),
            name: target.name.clone(),
        };
        let hits = cache.find(&f);
        match hits.len() {
            1 => return verify_live(conn, cache, &hits[0].node_ref.clone()).await,
            0 => {}
            _ => {
                let cands = hits.iter().take(10).map(|n| cache.control_ref(n)).collect();
                return Err(UiError::Ambiguous(
                    format!("{} controls match {}", hits.len(), describe(target)),
                    cands,
                ));
            }
        }
    }

    Err(UiError::NotFound(format!(
        "no control matches {}",
        describe(target)
    )))
}

/// Confirm the resolved object is still alive on the bus; refresh its states.
async fn verify_live(
    conn: &zbus::Connection,
    cache: &mut Cache,
    node_ref: &str,
) -> UiResult<Resolved> {
    let obj = cache
        .nodes
        .get(node_ref)
        .map(|n| n.obj.clone())
        .ok_or_else(|| UiError::StaleTarget(format!("ref '{node_ref}' vanished")))?;
    let key = crate::cache::key_of(&obj);
    let acc = match ax::accessible_proxy(conn, &obj).await {
        Ok(p) => p,
        Err(_) => {
            cache.remove_node(&key);
            return Err(UiError::StaleTarget(format!("ref '{node_ref}' is gone")));
        }
    };
    match ax::call("verify get_state", acc.get_state()).await {
        Ok(states) if !states.contains(State::Defunct) => {
            if let Some(n) = cache.nodes.get_mut(node_ref) {
                n.enabled = ax::enabled(&states);
                n.visible = ax::visible(&states);
                n.focused = states.contains(State::Focused);
            }
            Ok(Resolved {
                node_ref: node_ref.to_string(),
            })
        }
        _ => {
            cache.remove_node(&key);
            Err(UiError::StaleTarget(format!(
                "ref '{node_ref}' no longer resolves to a live object"
            )))
        }
    }
}

/// Human-readable target criteria: only the fields that were actually given.
fn describe(t: &Target) -> String {
    let mut parts = Vec::new();
    for (k, v) in [
        ("app", &t.app),
        ("window", &t.window),
        ("id", &t.id),
        ("ref", &t.node_ref),
        ("role", &t.role),
        ("name", &t.name),
    ] {
        if let Some(v) = v {
            parts.push(format!("{k}={v}"));
        }
    }
    parts.join(" ")
}
