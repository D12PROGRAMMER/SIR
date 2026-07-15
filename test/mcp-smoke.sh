#!/bin/bash
# MCP protocol smoke test: initialize, list tools, press the Save button.
# Run inside harness.sh (needs the session's a11y bus).
BIN="$(dirname "$0")/../target/debug/ui-mcp"
printf '%s\n' \
  '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke","version":"0"}}}' \
  '{"jsonrpc":"2.0","method":"notifications/initialized"}' \
  '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' \
  '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"ui_find","arguments":{"app":"test-app"}}}' \
  '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"ui_press","arguments":{"target":{"app":"test-app","id":"save-project"}}}}' \
  '{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"ui_press","arguments":{"target":{"app":"test-app","id":"locked"}}}}' \
  '{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"ui_set_value","arguments":{"target":{"app":"test-app","id":"filename"},"value":"demo.txt"}}}' \
  '{"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"ui_press","arguments":{"target":{"app":"test-app","id":"save-project"}}}}' \
  '{"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"ui_wait_for","arguments":{"query":{"app":"test-app","window":"Saved x2","id":"save-project"},"timeout_ms":4000}}}' \
  '{"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"ui_press","arguments":{"target":{"app":"test-app","id":"nonexistent"}}}}' \
  '{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"ui_read","arguments":{"target":{"app":"test-app","id":"filename"}}}}' \
  | "$BIN"
