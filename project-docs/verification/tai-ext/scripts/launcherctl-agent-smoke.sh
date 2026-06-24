#!/data/data/com.termux/files/usr/bin/sh
set -eu

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "missing required command: $1" >&2
    return 1
  }
}

section() {
  printf '\n## %s\n' "$1"
}

json_query() {
  if command -v jq >/dev/null 2>&1; then
    jq "$1"
  else
    cat
  fi
}

need launcherctl
need curl
need python3

if [ ! -r "$HOME/.launcherctl/endpoint" ] || [ ! -r "$HOME/.launcherctl/token" ]; then
  echo "LauncherCtl endpoint/token missing. Start Termux Launcher first." >&2
  exit 1
fi

BASE=$(sed -n '1p' "$HOME/.launcherctl/endpoint")
TOKEN=$(sed -n '1p' "$HOME/.launcherctl/token")

post_json() {
  path="$1"
  body="$2"
  curl -sS -X POST \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    --data "$body" \
    "$BASE$path"
}

get_json() {
  path="$1"
  curl -sS -H "Authorization: Bearer $TOKEN" "$BASE$path"
}

section "Status"
launcherctl status | json_query '{ok, backendState, notificationListenerConnected, endpoint: .endpoint.supportedEndpoints}'

section "Capabilities"
launcherctl capabilities | json_query '{ok, device: {model: .device.model, sdkInt: .device.sdkInt, memoryGiB: .device.memoryGiB}, functionGemma, tools: (.availableTools | length), warnings, blockingReasons}'

section "Tools"
launcherctl tools | json_query '{ok, count, openAiNames: [.openAiTools[].function.name]}'

section "TAI"
tai status | sed -n '1,80p'

section "Agent Route"
launcherctl agent --dry-run "open termux" | json_query '{ok, tool, arguments, requiresConfirmation, risk}'
launcherctl agent --dry-run "what is playing" | json_query '{ok, tool, arguments, requiresConfirmation, risk}'
launcherctl agent --dry-run "show recent notifications" | json_query '{ok, tool, arguments, requiresConfirmation, risk}'

section "Agent Execute"
post_json /v1/agent/execute '{"tool":"capabilities.get","arguments":{}}' \
  | json_query '{ok, hasDevice: (.result.device != null), tools: (.result.availableTools | length)}'
post_json /v1/agent/execute '{"tool":"apps.launch","arguments":{"query":"termux"}}' \
  | json_query '{ok, error, message, requiresConfirmation, risk}'
post_json /v1/agent/execute '{"tool":"memory.write","arguments":{"namespace":"verification","key":"smoke","value":"launcherctl smoke"},"confirm":true}' \
  | json_query '{ok, result}'
post_json /v1/agent/execute '{"tool":"memory.search","arguments":{"namespace":"verification","query":"smoke","limit":5},"confirm":true}' \
  | json_query '{ok, count: (.result.count // 0), entries: (.result.entries // [])}'

section "Notification History"
if command -v termux-notification >/dev/null 2>&1 && command -v termux-notification-remove >/dev/null 2>&1; then
  termux-notification --id 902061 --title "LauncherCtl Verification" --content "notification-history-smoke-902061" --priority low
  sleep 2
  launcherctl notifications search notification-history-smoke-902061 \
    | json_query '{ok, count, events: [.events[] | {eventType, packageName: .notification.packageName, title: .notification.title, text: .notification.text}]}'
  termux-notification-remove 902061
  sleep 2
  launcherctl notifications since 1781903530000 10 \
    | json_query '{ok, count, events: [.events[] | select(.notification.text == "notification-history-smoke-902061") | {eventType, packageName: .notification.packageName, title: .notification.title, text: .notification.text}]}'
else
  echo "termux-notification tools not installed; skipping active post/remove check"
  launcherctl notifications stats | json_query '{ok, total, posted, removed, packages}'
fi

section "Events"
launcherctl events tail 10 | json_query '{ok, count, events: [.events[] | {type, tool: .payload.tool, resultOk: .payload.resultOk, error: .payload.error}]}'
get_json /v1/events | json_query '{ok, count}'

section "MCP"
printf '%s\n' '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}' \
  | launcherctl mcp \
  | json_query '{jsonrpc, id, toolCount: (.result.tools | length), first: .result.tools[0].name}'
printf '%s\n' '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"capabilities_get","arguments":{}}}' \
  | launcherctl mcp \
  | json_query '{jsonrpc, id, isError: .result.isError}'

section "OpenAI Compatible"
get_json /v1/models | json_query '{object, count: (.data | length), first: (.data[0] // null)}'
