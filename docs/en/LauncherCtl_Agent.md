# LauncherCtl Agent Tools

LauncherCtl gives Termux shell tools and local agents a safe way to read launcher, notification, media, and device state from the Android app. It also exposes a small action layer for opening apps, opening Android intents, and storing local agent memory.

Use this page when you want to run the tools from Termux. Use [LauncherCtl API](LauncherCtl_API) when you need endpoint details for a custom client.

## Requirements

- Open Termux Launcher at least once after installing or updating the APK.
- Keep the LauncherCtl files available under `~/.launcherctl`.
- Install common shell helpers when needed:

```sh
pkg install curl jq python
```

The launcher writes these local files:

```text
~/.launcherctl/endpoint
~/.launcherctl/token
```

Treat `~/.launcherctl/token` like a password. Do not paste it into screenshots, shared logs, or shell history.

## Check Status

Start with:

```sh
launcherctl status
launcherctl capabilities
launcherctl tools
```

`status` shows whether the localhost bridge is reachable. `capabilities` shows device support, notification listener state, TAI runtime state, FunctionGemma availability, warnings, and blocking reasons. `tools` lists the agent tools and their JSON schemas.

If `launcherctl` says the endpoint or token file is missing, open Termux Launcher and then run the command again.

## Common Commands

List and launch apps:

```sh
launcherctl apps
launcherctl launch termux
```

Read current device state:

```sh
launcherctl resources
launcherctl media
launcherctl art
```

Read notifications:

```sh
launcherctl notifications
launcherctl notifications recent 50
launcherctl notifications search invoice
launcherctl notifications stats
```

`media`, `art`, and notification commands require Android notification listener access for Termux Launcher. If permission is missing, LauncherCtl returns a structured hint instead of silently failing.

## Natural-Language Routing

The `agent` command maps a short request to one LauncherCtl tool.

Preview the route without executing it:

```sh
launcherctl agent --dry-run "what is playing"
launcherctl agent --dry-run "show recent notifications"
launcherctl agent --dry-run "open maps"
```

Run the routed tool:

```sh
launcherctl agent "what is playing"
launcherctl agent "show recent notifications"
```

For requests that can change device state, inspect the dry-run result first:

```sh
launcherctl agent --dry-run "open maps"
launcherctl agent "open maps"
```

The API marks medium, high, and critical risk tools as confirmation-gated. The CLI's non-dry-run mode sends the confirmation flag when it executes the routed tool, so dry-run is the review step.

## Available Agent Tools

LauncherCtl currently exposes these tools:

| Tool | What it does | Risk |
| --- | --- | --- |
| `capabilities.get` | Reads device, integration, and tool availability. | Low |
| `apps.search` | Searches installed launcher apps. | Low |
| `apps.launch` | Opens an installed app by label, package, activity, or stable id. | Medium |
| `notifications.recent` | Reads recent notification history events. | Low |
| `notifications.since` | Reads notification history since an epoch-millisecond timestamp. | Low |
| `notifications.search` | Searches notification history text. | Low |
| `notifications.stats` | Counts notification history events. | Low |
| `media.now_playing` | Reads the current media session. | Low |
| `system.resources` | Reads CPU, memory, storage, battery, network, and backend state. | Low |
| `intent.open` | Opens an Android URI/action intent. | Medium |
| `memory.write` | Stores a short key/value note in local agent memory. | High |
| `memory.search` | Searches local agent memory. | Medium |
| `events.tail` | Reads recent LauncherCtl agent/system events. | Low |
| `user.confirm` | Represents an explicit confirmation request for clients. | Critical |

Use `launcherctl tools` for the exact schema accepted by each tool.

## Direct HTTP Use

Scripts can call the authenticated localhost API directly:

```sh
BASE=$(sed -n '1p' ~/.launcherctl/endpoint)
TOKEN=$(cat ~/.launcherctl/token)

curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$BASE/v1/launcher/capabilities"
```

Route a request:

```sh
curl -fsS -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"request":"what is playing"}' \
  "$BASE/v1/agent/route"
```

Execute a low-risk tool:

```sh
curl -fsS -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tool":"media.now_playing","arguments":{}}' \
  "$BASE/v1/agent/execute"
```

Execute a confirmation-gated tool only after your client has asked the user:

```sh
curl -fsS -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tool":"apps.launch","arguments":{"query":"termux"},"confirm":true}' \
  "$BASE/v1/agent/execute"
```

## MCP Clients

MCP-capable clients can use LauncherCtl as a stdio tool server:

```sh
launcherctl mcp
```

The MCP bridge uses the same tools, token files, and confirmation gates as the HTTP API. A client should pass `_confirm: true` only after the user has approved the action.

## Local Model Clients

TAI exposes OpenAI-compatible model endpoints through the same local bridge. Use this for chat/completion clients:

```sh
BASE=$(sed -n '1p' ~/.launcherctl/endpoint)
TOKEN=$(cat ~/.launcherctl/token)
export OPENAI_BASE_URL="$BASE/v1"
export OPENAI_API_KEY="$TOKEN"
```

This is separate from the LauncherCtl agent tools. Model clients call `/v1/chat/completions`; agent clients call `/v1/agent/route`, `/v1/agent/execute`, or `launcherctl mcp`.

## FunctionGemma Routing

Deterministic keyword routing is used first. If FunctionGemma mobile-actions is already loaded and supported, LauncherCtl may use it as a best-effort fallback for routing unclear requests.

LauncherCtl does not auto-download or auto-load FunctionGemma. To check availability:

```sh
launcherctl capabilities
tai status
```

## Event History

LauncherCtl records route, execute, notification, and system events under `~/.launcherctl`.

Read recent events:

```sh
launcherctl events tail 20
```

This is useful when debugging a local agent or confirming which tool was selected.

## Refresh Helper Scripts

After updating the APK, refresh shell helper scripts with:

```sh
launcherctl update-scripts
```

Existing helper files are backed up first. `~/.tmux.conf` is not overwritten.

## Troubleshooting

### `launcherctl` cannot find endpoint or token

Open Termux Launcher once, then retry. The app writes `~/.launcherctl/endpoint` and `~/.launcherctl/token` when the bridge starts.

### Media or notifications are empty

Grant notification listener access to Termux Launcher in Android settings, then retry:

```sh
launcherctl capabilities
launcherctl notifications recent 10
launcherctl media
```

### Non-dry-run agent command needs `jq`

Install `jq`:

```sh
pkg install jq
```

Or use dry-run mode and call the HTTP API yourself.

### Token errors

Rotate the local API token:

```sh
launcherctl token rotate
```

Then rerun the command. Any open client using the old token must reload `~/.launcherctl/token`.

### Shizuku shell setup problems

LauncherCtl does not wrap arbitrary `rish` commands. For Shizuku shell diagnostics, run:

```sh
launcherctl tty-doctor
```

Apply the suggested fixes, then run it again.
