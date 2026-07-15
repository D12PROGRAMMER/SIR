#!/usr/bin/env python3
"""Generate the SIR vault's mechanical symbol reference from src/*.rs.

Output: docs/SIR-Vault/_generated/symbols/<module>.<symbol>.md (one file per
symbol, YAML frontmatter, wiki-links) plus a Symbol Index. Everything here is
derived from the source text: signatures, line numbers, doc comments, callers
(textual call sites), callees, project types in signatures, and UiError
variants constructed in the body. Curated flow/test links come from the maps
at the bottom, which name only symbols that exist.
"""
import os
import re
import json
from collections import defaultdict

ROOT = "/root/ui-mcp"
SRC = os.path.join(ROOT, "src")
OUT = os.path.join(ROOT, "docs/SIR-Vault/_generated/symbols")
os.makedirs(OUT, exist_ok=True)

modules = {}
for fn in sorted(os.listdir(SRC)):
    if fn.endswith(".rs"):
        modules[fn[:-3]] = open(os.path.join(SRC, fn)).read().splitlines()

ITEM_RE = re.compile(
    r"^(?P<indent>\s*)(?P<vis>pub(?:\([^)]*\))?\s+)?"
    r"(?P<async>async\s+)?"
    r"(?P<kind>fn|struct|enum|trait|const|static|macro_rules!)\s+"
    r"(?P<name>\w+)"
)
IMPL_RE = re.compile(r"^impl(?:<[^>]*>)?\s+(?:\w+\s+for\s+)?(?P<type>\w+)")

symbols = []          # dicts
by_name = defaultdict(list)

for mod, lines in modules.items():
    impl_ctx = None
    impl_end_depth = 0
    depth = 0
    doc_buf = []
    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("///"):
            doc_buf.append(stripped[3:].strip())
        m_impl = IMPL_RE.match(line)
        if m_impl and depth == 0:
            impl_ctx = m_impl.group("type")
            impl_end_depth = depth
        m = ITEM_RE.match(line)
        if m and (depth == 0 or (impl_ctx and depth == impl_end_depth + 1)):
            kind = m.group("kind").rstrip("!")
            name = m.group("name")
            parent = impl_ctx if (depth > 0 and kind == "fn") else None
            qual = f"{parent}.{name}" if parent else name
            # capture signature: join lines until the opening brace or ';'
            sig_lines = []
            j = i
            while j < len(lines):
                sig_lines.append(lines[j].rstrip())
                if "{" in lines[j] or lines[j].rstrip().endswith(";"):
                    break
                j += 1
            sig = "\n".join(sig_lines)
            sig_display = re.sub(r"\s*\{\s*$", "", sig)
            # body span for fns (brace matching from the sig-opening line)
            body = ""
            if kind == "fn" and "{" in sig:
                d = 0
                k = i
                start = None
                while k < len(lines):
                    for ch in lines[k]:
                        if ch == "{":
                            d += 1
                            start = start or k
                        elif ch == "}":
                            d -= 1
                    if start is not None and d == 0:
                        break
                    k += 1
                body = "\n".join(lines[i:k + 1])
            symbols.append({
                "module": mod, "kind": kind, "name": name, "qual": qual,
                "parent_type": parent, "line": i + 1,
                "vis": "public" if (m.group("vis") or "").startswith("pub") else "private",
                "async": bool(m.group("async")),
                "doc": " ".join(doc_buf), "sig": sig_display, "body": body,
            })
            by_name[name].append(symbols[-1])
            doc_buf = []
        elif not stripped.startswith("///"):
            if stripped and not stripped.startswith("//"):
                doc_buf = []
        depth += line.count("{") - line.count("}")
        if impl_ctx and depth <= impl_end_depth and "{" in "".join(lines[:i + 1]):
            if depth == impl_end_depth and "}" in line:
                impl_ctx = None

project_types = {s["name"] for s in symbols if s["kind"] in ("struct", "enum", "trait")}
fn_names = {s["name"] for s in symbols if s["kind"] == "fn"}

# textual callers: occurrences of `name(` outside the def module line
callers = defaultdict(list)
for mod, lines in modules.items():
    for i, line in enumerate(lines):
        for name in fn_names:
            if re.search(rf"[\.\s\(:]{re.escape(name)}\s*\(", line):
                defs = [s for s in by_name[name] if s["kind"] == "fn"]
                for d in defs:
                    if not (d["module"] == mod and d["line"] == i + 1):
                        callers[d["qual"] + "@" + d["module"]].append(f"{mod}.rs:{i + 1}")

ERR_VARIANTS = {
    "NotFound": "not_found", "Ambiguous": "ambiguous", "StaleTarget": "stale_target",
    "ControlNotAccessible": "control_not_accessible", "NotActionable": "not_actionable",
    "InvalidArgument": "invalid_argument", "Timeout": "timeout", "Atspi": "atspi_error",
}

FLOWS = {
    ("actions", "Service.new"): ["Flow - Startup and Initial Enumeration"],
    ("actions", "supervisor"): ["Flow - Startup and Initial Enumeration",
                                 "Flow - Bus Restart Recovery",
                                 "Flow - Dual Connection Architecture"],
    ("actions", "liveness_ping"): ["Flow - Bus Restart Recovery"],
    ("actions", "handle_event"): ["Flow - Event Processing", "Flow - Cache Invalidation"],
    ("actions", "Service.press"): ["Flow - Press Action"],
    ("actions", "Service.wait_for"): ["Flow - Event Processing"],
    ("actions", "snapshot"): ["Flow - Press Action"],
    ("actions", "action_names"): ["Flow - Press Action"],
    ("cache", "Cache.sync_apps"): ["Flow - Startup and Initial Enumeration",
                                    "Flow - App Restart Recovery"],
    ("cache", "Cache.walk_app"): ["Flow - Startup and Initial Enumeration",
                                   "Flow - Cache Invalidation"],
    ("cache", "Cache.walk_from"): ["Flow - Startup and Initial Enumeration"],
    ("cache", "Cache.ensure_walked"): ["Flow - Target Resolution"],
    ("cache", "Cache.mark_app_dirty"): ["Flow - Cache Invalidation",
                                         "Flow - Event Processing"],
    ("cache", "Cache.remove_subtree"): ["Flow - Cache Invalidation",
                                         "Flow - Event Processing"],
    ("cache", "Cache.remove_app"): ["Flow - App Restart Recovery"],
    ("resolver", "resolve"): ["Flow - Target Resolution"],
    ("resolver", "verify_live"): ["Flow - Target Resolution",
                                   "Flow - App Restart Recovery"],
    ("mcp", "serve"): ["Flow - MCP Request Handling"],
    ("mcp", "call_tool"): ["Flow - MCP Request Handling"],
}
TESTS = {
    ("actions", "Service.press"): ["press Save by id (all toolkits)",
                                    "disabled control -> not_actionable",
                                    "disambiguation by ref succeeds"],
    ("actions", "Service.set_value"): ["set_value on text field (GTK, Qt)"],
    ("actions", "Service.find"): ["find Save by id (all toolkits)"],
    ("actions", "Service.wait_for"): ["dynamically added widget found via events",
                                       "web button waits (Firefox/Electron)"],
    ("actions", "Service.list_apps"): ["app enumerated automatically"],
    ("actions", "supervisor"): ["bus_restart.py: supervisor reconnected & rebuilt cache"],
    ("resolver", "resolve"): ["duplicate controls -> ambiguous",
                               "unknown id -> not_found",
                               "removed widget ref -> stale_target"],
    ("cache", "Cache.sync_apps"): ["restart: relaunched app resolvable"],
}


def link_types(sig):
    found = []
    for t in sorted(project_types):
        if re.search(rf"\b{t}\b", sig):
            defs = by_name[t]
            if defs:
                found.append(f"[[{defs[0]['module']}.{t}]]")
    return found


index_rows = defaultdict(list)
count = 0
for s in symbols:
    fname = f"{s['module']}.{s['qual']}.md"
    src_link = f"../../../../src/{s['module']}.rs#L{s['line']}"
    fm = [
        "---",
        f"kind: {s['kind']}",
        f"module: {s['module']}",
        f"symbol: {s['qual']}",
        f"source: src/{s['module']}.rs",
        f"line: {s['line']}",
        f"visibility: {s['vis']}",
        f"async: {str(s['async']).lower()}",
        "generated: true",
        "---",
    ]
    body_parts = [f"# `{s['qual']}`", ""]
    if s["doc"]:
        body_parts += [s["doc"], ""]
    body_parts += ["```rust", s["sig"], "```", "",
                   f"[source]({src_link}) · parent module: [[Module - {s['module']}]]", ""]
    if s["kind"] == "fn":
        calls = sorted({n for n in fn_names
                        if n != s["name"] and re.search(rf"[\.\s\(=]{n}\s*\(", s["body"])})
        if calls:
            links = []
            for n in calls:
                d = by_name[n][0]
                links.append(f"[[{d['module']}.{d['qual']}]]")
            body_parts += ["**Calls:** " + ", ".join(sorted(set(links))), ""]
        got_callers = callers.get(s["qual"] + "@" + s["module"], [])
        if got_callers:
            body_parts += ["**Called from:** " + ", ".join(sorted(set(got_callers))[:12]), ""]
        tys = link_types(s["sig"])
        if tys:
            body_parts += ["**Types in signature:** " + ", ".join(tys), ""]
        if "UiResult" in s["sig"] or "UiError" in s["sig"]:
            errs = sorted({ERR_VARIANTS[v] for v in ERR_VARIANTS
                           if f"UiError::{v}" in s["body"]})
            if errs:
                # plain text, not a wiki-link: 30+ identical edges to one hub
                # note made the graph unreadable
                body_parts += ["**Errors produced:** " + ", ".join(f"`{e}`" for e in errs)
                               + " (see Error Model)", ""]
    flows = FLOWS.get((s["module"], s["qual"]))
    if flows:
        body_parts += ["**Execution flows:** " + ", ".join(f"[[{f}]]" for f in flows), ""]
    tests = TESTS.get((s["module"], s["qual"]))
    if tests:
        body_parts += ["**Exercised by:** " + "; ".join(tests)
                       + " ([[Acceptance Suite]])", ""]
    elif s["kind"] == "fn" and s["vis"] == "public":
        # plain text for the default case — a link here from ~70 pages
        # turns Acceptance Suite into a graph-dominating hub
        body_parts += ["**Exercised by:** acceptance suite (indirect)", ""]
    open(os.path.join(OUT, fname), "w").write("\n".join(fm + [""] + body_parts))
    count += 1
    index_rows[s["module"]].append(
        f"| [[{s['module']}.{s['qual']}\\|{s['qual']}]] | {s['kind']} | {s['vis']} | "
        f"{'async' if s['async'] else ''} | L{s['line']} |")

idx = ["---", "generated: true", "---", "", "# Symbol Index", "",
       f"{count} symbols across {len(modules)} modules. "
       "Every entry is generated from source; regenerate with `test/gen_symbols.py`.", ""]
for mod in sorted(index_rows):
    idx += [f"## [[Module - {mod}|{mod}]]", "",
            "| Symbol | Kind | Visibility | Async | Line |",
            "|---|---|---|---|---|"] + sorted(index_rows[mod]) + [""]
open(os.path.join(OUT, "Symbol Index.md"), "w").write("\n".join(idx))
print(f"GENERATED {count} symbol pages + index")
