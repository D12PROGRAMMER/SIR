use serde::{Deserialize, Serialize};

/// How a caller names a control. All fields optional; resolver enforces precedence.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct Target {
    /// Application name (as exposed on the accessibility bus) e.g. "example-editor"
    pub app: Option<String>,
    /// Window title / accessible name of a top-level window
    pub window: Option<String>,
    /// Application-provided accessible ID, e.g. "save-project" (highest precedence)
    pub id: Option<String>,
    /// Session-local reference previously returned by a search, e.g. "app-4:node-182"
    #[serde(rename = "ref")]
    pub node_ref: Option<String>,
    /// AT-SPI role name, e.g. "button" (lowercase, dashes or spaces accepted)
    pub role: Option<String>,
    /// Exact accessible name, e.g. "Save"
    pub name: Option<String>,
}

impl Target {
    pub fn is_empty(&self) -> bool {
        self.app.is_none()
            && self.window.is_none()
            && self.id.is_none()
            && self.node_ref.is_none()
            && self.role.is_none()
            && self.name.is_none()
    }
}

fn is_true(b: &bool) -> bool {
    *b
}

/// Compact control reference returned by searches.
/// Output convention: `enabled`/`visible` are omitted when true (the normal
/// state); their presence with `false` is the signal. `name` is omitted when
/// empty. Semantic fields (ref, id, role, actions) are never elided.
#[derive(Debug, Clone, Serialize)]
pub struct ControlRef {
    #[serde(rename = "ref")]
    pub node_ref: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub id: Option<String>,
    pub role: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub name: String,
    #[serde(skip_serializing_if = "is_true")]
    pub enabled: bool,
    #[serde(skip_serializing_if = "is_true")]
    pub visible: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub app: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub window: Option<String>,
    #[serde(skip_serializing_if = "Vec::is_empty", default)]
    pub actions: Vec<String>,
}

#[derive(Debug)]
pub enum UiError {
    /// No control matched the target.
    NotFound(String),
    /// More than one control matched; candidates included so the caller can disambiguate.
    Ambiguous(String, Vec<ControlRef>),
    /// A previously returned ref no longer resolves to a live object.
    StaleTarget(String),
    /// The control exists but exposes no usable accessibility interface for this operation.
    ControlNotAccessible(String),
    /// The control resolved but is not in a state to act on (hidden/disabled).
    NotActionable(String),
    /// Target/value malformed.
    InvalidArgument(String),
    /// wait_for timed out.
    Timeout(String),
    /// Underlying AT-SPI / D-Bus failure.
    Atspi(String),
}

impl UiError {
    pub fn code(&self) -> &'static str {
        match self {
            UiError::NotFound(_) => "not_found",
            UiError::Ambiguous(_, _) => "ambiguous",
            UiError::StaleTarget(_) => "stale_target",
            UiError::ControlNotAccessible(_) => "control_not_accessible",
            UiError::NotActionable(_) => "not_actionable",
            UiError::InvalidArgument(_) => "invalid_argument",
            UiError::Timeout(_) => "timeout",
            UiError::Atspi(_) => "atspi_error",
        }
    }

    pub fn to_json(&self) -> serde_json::Value {
        let msg = match self {
            UiError::NotFound(m)
            | UiError::StaleTarget(m)
            | UiError::ControlNotAccessible(m)
            | UiError::NotActionable(m)
            | UiError::InvalidArgument(m)
            | UiError::Timeout(m)
            | UiError::Atspi(m) => m.clone(),
            UiError::Ambiguous(m, _) => m.clone(),
        };
        let mut v = serde_json::json!({ "error": self.code(), "message": msg });
        if let UiError::Ambiguous(_, cands) = self {
            v["candidates"] = serde_json::to_value(cands).unwrap_or_default();
        }
        v
    }
}

impl std::fmt::Display for UiError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}: {}", self.code(), self.to_json())
    }
}

impl std::error::Error for UiError {}

impl From<zbus::Error> for UiError {
    fn from(e: zbus::Error) -> Self {
        UiError::Atspi(e.to_string())
    }
}

pub type UiResult<T> = Result<T, UiError>;
