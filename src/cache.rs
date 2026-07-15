//! In-memory model of the accessible desktop: apps, windows, controls.
//! Populated by on-demand subtree walks, patched incrementally by AT-SPI events.
//!
//! Ref stability contract: a node_ref stays valid for as long as the underlying
//! accessible object is alive, across re-walks (node_by_key reuses refs) and
//! incremental event updates. Refs die only when the object (or its app) does.

use std::collections::{HashMap, HashSet, VecDeque};

use atspi::{ObjectRefOwned, State};

use crate::accessibility as ax;
use crate::types::{ControlRef, UiError, UiResult};

pub type NodeKey = (String, String); // (bus name, object path)

pub fn key_of(obj: &ObjectRefOwned) -> NodeKey {
    (
        obj.name_as_str().unwrap_or_default().to_string(),
        obj.path_as_str().to_string(),
    )
}

#[derive(Debug, Clone)]
pub struct AppEntry {
    pub app_ref: String,
    pub obj: ObjectRefOwned,
    pub name: String,
    pub bus_name: String,
    pub walked: bool,
}

#[derive(Debug, Clone)]
pub struct NodeEntry {
    pub node_ref: String,
    pub app_ref: String,
    pub obj: ObjectRefOwned,
    pub parent: Option<String>,
    /// Nearest ancestor (or self) that is a top-level window.
    pub window_ref: Option<String>,
    pub name: String,
    pub role_str: String,
    pub accessible_id: Option<String>,
    pub enabled: bool,
    pub visible: bool,
    pub focused: bool,
    pub is_window: bool,
    pub children: Vec<String>,
}

#[derive(Default)]
pub struct Cache {
    pub apps: HashMap<String, AppEntry>,       // app_ref -> app
    pub app_by_bus: HashMap<String, String>,   // bus name -> app_ref
    pub nodes: HashMap<String, NodeEntry>,     // node_ref -> node
    pub node_by_key: HashMap<NodeKey, String>, // (bus, path) -> node_ref
    app_counter: u64,
    node_counter: u64,
}

/// Search filter used by find/list/resolve.
#[derive(Debug, Clone, Default)]
pub struct Filter {
    pub app: Option<String>,
    pub window: Option<String>,
    pub id: Option<String>,
    pub role: Option<String>, // pre-normalized
    pub name: Option<String>,
}

impl Cache {
    /// Drop everything (used when the accessibility bus connection is rebuilt).
    pub fn clear_all(&mut self) {
        self.apps.clear();
        self.app_by_bus.clear();
        self.nodes.clear();
        self.node_by_key.clear();
    }

    /// Refresh the application list from the registry root.
    pub async fn sync_apps(&mut self, conn: &zbus::Connection) -> UiResult<()> {
        let refs = ax::list_app_refs(conn).await?;
        let mut seen: HashSet<String> = HashSet::new();
        for obj in refs {
            let bus = obj.name_as_str().unwrap_or_default().to_string();
            if bus.is_empty() {
                continue;
            }
            seen.insert(bus.clone());
            if self.app_by_bus.contains_key(&bus) {
                continue;
            }
            // A brand-new app: fetch its name (ignore apps that error out).
            let name = match ax::accessible_proxy(conn, &obj).await {
                Ok(p) => p.name().await.unwrap_or_default(),
                Err(_) => continue,
            };
            self.app_counter += 1;
            let app_ref = format!("app-{}", self.app_counter);
            self.app_by_bus.insert(bus.clone(), app_ref.clone());
            self.apps.insert(
                app_ref.clone(),
                AppEntry {
                    app_ref,
                    obj,
                    name,
                    bus_name: bus,
                    walked: false,
                },
            );
        }
        // Drop apps that vanished from the registry (their refs become stale).
        let gone: Vec<String> = self
            .apps
            .values()
            .filter(|a| !seen.contains(&a.bus_name))
            .map(|a| a.app_ref.clone())
            .collect();
        for app_ref in gone {
            self.remove_app(&app_ref);
        }
        Ok(())
    }

    pub fn remove_app(&mut self, app_ref: &str) {
        if let Some(app) = self.apps.remove(app_ref) {
            self.app_by_bus.remove(&app.bus_name);
        }
        let node_refs: Vec<String> = self
            .nodes
            .values()
            .filter(|n| n.app_ref == app_ref)
            .map(|n| n.node_ref.clone())
            .collect();
        for nr in node_refs {
            if let Some(n) = self.nodes.remove(&nr) {
                self.node_by_key.remove(&key_of(&n.obj));
            }
        }
    }

    /// Mark an app for re-walk. Nodes are KEPT so refs stay stable; the next
    /// walk refreshes surviving nodes in place and prunes the dead ones.
    pub fn mark_app_dirty(&mut self, bus_name: &str) {
        if let Some(app_ref) = self.app_by_bus.get(bus_name) {
            if let Some(app) = self.apps.get_mut(app_ref) {
                app.walked = false;
            }
        }
    }

    /// Shared BFS used by full walks and incremental subtree additions.
    /// Seeds: (object, parent node_ref, window_ref, depth). Returns visited keys.
    async fn walk_from(
        &mut self,
        conn: &zbus::Connection,
        app_ref: &str,
        seeds: Vec<(ObjectRefOwned, Option<String>, Option<String>, usize)>,
        max_nodes: usize,
    ) -> UiResult<HashSet<NodeKey>> {
        let mut queue: VecDeque<(ObjectRefOwned, Option<String>, Option<String>, usize)> =
            seeds.into();
        let mut visited: HashSet<NodeKey> = HashSet::new();
        let mut count = 0usize;
        // Overall wall-clock budget for one walk: bounds how long the cache lock
        // is held even if many nodes are individually slow.
        let deadline = tokio::time::Instant::now() + ax::WALK_BUDGET;

        while let Some((obj, parent, window, depth)) = queue.pop_front() {
            if count >= max_nodes || depth > ax::MAX_DEPTH {
                break;
            }
            if tokio::time::Instant::now() >= deadline {
                eprintln!("[ui-mcp] walk of {app_ref} hit time budget at {count} nodes");
                break;
            }
            let key = key_of(&obj);
            if !visited.insert(key.clone()) {
                continue;
            }
            let raw = match ax::inspect(conn, &obj).await {
                Ok(r) => r,
                Err(_) => continue, // node died mid-walk
            };
            if raw.states.contains(State::Defunct) {
                continue;
            }
            count += 1;
            // Reuse an existing ref for a known object: refs must be stable.
            let node_ref = if let Some(existing) = self.node_by_key.get(&key) {
                existing.clone()
            } else {
                self.node_counter += 1;
                format!("{}:node-{}", app_ref, self.node_counter)
            };
            let is_window = ax::is_window_role(raw.role);
            let my_window = if is_window {
                Some(node_ref.clone())
            } else {
                window.clone()
            };
            let entry = NodeEntry {
                node_ref: node_ref.clone(),
                app_ref: app_ref.to_string(),
                obj: obj.clone(),
                parent: parent.clone(),
                window_ref: my_window.clone(),
                name: raw.name.clone(),
                role_str: ax::norm_role(raw.role),
                accessible_id: raw.accessible_id.clone(),
                enabled: ax::enabled(&raw.states),
                visible: ax::visible(&raw.states),
                focused: raw.states.contains(State::Focused),
                is_window,
                children: Vec::new(),
            };
            if let Some(p) = &parent {
                if let Some(pn) = self.nodes.get_mut(p) {
                    if !pn.children.contains(&node_ref) {
                        pn.children.push(node_ref.clone());
                    }
                }
            }
            self.node_by_key.insert(key, node_ref.clone());
            self.nodes.insert(node_ref.clone(), entry);
            for child in raw.children {
                queue.push_back((child, Some(node_ref.clone()), my_window.clone(), depth + 1));
            }
        }
        Ok(visited)
    }

    /// Full breadth-first walk of one application's accessible tree.
    /// Refs of surviving nodes are preserved; nodes that vanished are pruned.
    pub async fn walk_app(&mut self, conn: &zbus::Connection, app_ref: &str) -> UiResult<()> {
        let app = self
            .apps
            .get(app_ref)
            .ok_or_else(|| UiError::NotFound(format!("unknown app ref {app_ref}")))?
            .clone();
        if app.walked {
            return Ok(());
        }
        // Children lists are rebuilt from scratch during the walk.
        for n in self.nodes.values_mut().filter(|n| n.app_ref == app.app_ref) {
            n.children.clear();
        }
        let visited = self
            .walk_from(
                conn,
                &app.app_ref,
                vec![(app.obj.clone(), None, None, 0)],
                ax::MAX_NODES_PER_APP,
            )
            .await?;
        // Prune nodes of this app that no longer exist in the live tree.
        let dead: Vec<NodeKey> = self
            .nodes
            .values()
            .filter(|n| n.app_ref == app.app_ref && !visited.contains(&key_of(&n.obj)))
            .map(|n| key_of(&n.obj))
            .collect();
        for key in dead {
            self.remove_node(&key);
        }
        if let Some(a) = self.apps.get_mut(app_ref) {
            a.walked = true;
        }
        Ok(())
    }

    /// Incremental: a subtree disappeared (event-driven). Refs inside become stale.
    pub fn remove_subtree(&mut self, root_key: &NodeKey) {
        let Some(root_ref) = self.node_by_key.get(root_key).cloned() else {
            return;
        };
        let mut queue = vec![root_ref];
        while let Some(nr) = queue.pop() {
            if let Some(n) = self.nodes.remove(&nr) {
                self.node_by_key.remove(&key_of(&n.obj));
                queue.extend(n.children.iter().cloned());
                if let Some(p) = n.parent.and_then(|p| self.nodes.get_mut(&p)) {
                    p.children.retain(|c| c != &nr);
                }
            }
        }
    }

    /// Ensure apps are listed and the relevant apps' trees are walked.
    pub async fn ensure_walked(
        &mut self,
        conn: &zbus::Connection,
        app_filter: Option<&str>,
    ) -> UiResult<()> {
        self.sync_apps(conn).await?;
        let targets: Vec<String> = self
            .apps
            .values()
            .filter(|a| match app_filter {
                Some(f) => a.name.eq_ignore_ascii_case(f),
                None => true,
            })
            .filter(|a| !a.walked)
            .map(|a| a.app_ref.clone())
            .collect();
        for app_ref in targets {
            self.walk_app(conn, &app_ref).await?;
        }
        Ok(())
    }

    pub fn app_name_of(&self, node: &NodeEntry) -> Option<String> {
        self.apps.get(&node.app_ref).map(|a| a.name.clone())
    }

    pub fn window_name_of(&self, node: &NodeEntry) -> Option<String> {
        node.window_ref
            .as_ref()
            .and_then(|w| self.nodes.get(w))
            .map(|w| w.name.clone())
    }

    pub fn matches(&self, node: &NodeEntry, f: &Filter, id_leaf: bool) -> bool {
        if let Some(id) = &f.id {
            let matched = match node.accessible_id.as_deref() {
                Some(nid) if !id_leaf => nid == id,
                // Qt prefixes IDs with widget ancestry ("QApplication.QMainWindow.QWidget.qt-save");
                // leaf mode matches the application-chosen final segment.
                Some(nid) => nid.rsplit('.').next() == Some(id.as_str()),
                None => false,
            };
            if !matched {
                return false;
            }
        }
        if let Some(role) = &f.role {
            if &node.role_str != role {
                return false;
            }
        }
        if let Some(name) = &f.name {
            if !node.name.eq_ignore_ascii_case(name) {
                return false;
            }
        }
        if let Some(app) = &f.app {
            match self.app_name_of(node) {
                Some(an) if an.eq_ignore_ascii_case(app) => {}
                _ => return false,
            }
        }
        if let Some(win) = &f.window {
            match self.window_name_of(node) {
                Some(wn) if wn.eq_ignore_ascii_case(win) => {}
                _ => return false,
            }
        }
        true
    }

    pub fn find(&self, f: &Filter) -> Vec<&NodeEntry> {
        let mut out: Vec<&NodeEntry> = self
            .nodes
            .values()
            .filter(|n| self.matches(n, f, false))
            .collect();
        if out.is_empty() && f.id.is_some() {
            out = self
                .nodes
                .values()
                .filter(|n| self.matches(n, f, true))
                .collect();
        }
        out.sort_by(|a, b| a.node_ref.cmp(&b.node_ref));
        out
    }

    pub fn control_ref(&self, node: &NodeEntry) -> ControlRef {
        ControlRef {
            node_ref: node.node_ref.clone(),
            id: node.accessible_id.clone(),
            role: node.role_str.clone(),
            name: node.name.clone(),
            enabled: node.enabled,
            visible: node.visible,
            app: self.app_name_of(node),
            window: self.window_name_of(node),
            actions: Vec::new(),
        }
    }

    // ---- incremental updates from AT-SPI events ----

    pub fn patch_name(&mut self, key: &NodeKey, name: String) {
        if let Some(nr) = self.node_by_key.get(key) {
            if let Some(n) = self.nodes.get_mut(nr) {
                n.name = name;
            }
        }
    }

    pub fn patch_state(&mut self, key: &NodeKey, state: State, on: bool) {
        if state == State::Defunct && on {
            self.remove_subtree(key);
            return;
        }
        if let Some(nr) = self.node_by_key.get(key) {
            if let Some(n) = self.nodes.get_mut(nr) {
                match state {
                    State::Enabled | State::Sensitive => n.enabled = on,
                    State::Showing | State::Visible => n.visible = on,
                    State::Focused => n.focused = on,
                    _ => {}
                }
            }
        }
    }

    pub fn remove_node(&mut self, key: &NodeKey) {
        if let Some(nr) = self.node_by_key.remove(key) {
            if let Some(n) = self.nodes.remove(&nr) {
                if let Some(p) = n.parent.and_then(|p| self.nodes.get_mut(&p)) {
                    p.children.retain(|c| c != &nr);
                }
            }
        }
    }

    pub fn stats(&self) -> (usize, usize) {
        (self.apps.len(), self.nodes.len())
    }
}
