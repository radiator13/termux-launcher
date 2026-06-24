# LauncherCtl

LauncherCtl is the shell bridge for Termux Launcher. It lets terminal commands launch Android apps, read launcher data, and use a small authenticated localhost API.

Start with:

```sh
launcherctl status
launcherctl apps
launcherctl launch whatsapp
```

If the command is missing, open or restart Termux Launcher. The app installs the script when the launcher activity starts.

## Common Commands

```sh
launcherctl --help
launcherctl status
launcherctl apps
launcherctl launch whatsapp
launcherctl resources
launcherctl media
launcherctl art
launcherctl notifications
launcherctl restart
launcherctl update-scripts
launcherctl tty-doctor
launcherctl token rotate
```

What they do:

- `status` checks whether the local bridge is running.
- `apps` prints the launchable app catalog.
- `launch <query>` launches an app by label or package-style query.
- `resources` prints CPU, RAM, battery, storage, network, and thermal information.
- `media`, `art`, and `notifications` read notification listener data if Android permission is granted.
- `restart` asks the launcher app to restart.
- `update-scripts` refreshes repo-owned helper scripts without replacing `~/.tmux.conf`.
- `tty-doctor` checks optional local `rish` files for Shizuku shell helpers.
- `token rotate` creates a new local API token.

## App Launch Examples

Launch WhatsApp:

```sh
launcherctl launch whatsapp
```

Search the same app catalog used by the launcher UI:

```sh
launcherctl apps | less
```

Use a tmux key binding:

```tmux
bind -n M-w run-shell 'tmux display-message "Opening WhatsApp"; launcherctl launch whatsapp >/dev/null 2>&1 || tmux display-message "Launch failed: WhatsApp"'
```

## Endpoint Files

LauncherCtl writes its connection details to:

```sh
~/.launcherctl/endpoint
~/.launcherctl/token
```

`endpoint` contains a base URL like:

```text
http://127.0.0.1:41237
```

`token` contains the bearer token. Treat it like an API key.

For OpenAI-compatible AI clients, add `/v1` to the endpoint yourself:

```sh
export OPENAI_BASE_URL="$(cat ~/.launcherctl/endpoint)/v1"
export OPENAI_API_KEY="$(cat ~/.launcherctl/token)"
```

Termux AI uses the same local bridge and token. See [Termux AI](Termux_AI.md).

## Local API for Scripts

For most users, the `launcherctl` command is enough. If you want to call the API directly:

```sh
BASE="$(cat ~/.launcherctl/endpoint)"
TOKEN="$(cat ~/.launcherctl/token)"
curl -H "Authorization: Bearer $TOKEN" "$BASE/v1/status"
```

Useful routes:

```text
GET  /v1/status
GET  /v1/apps
POST /v1/apps/launch
GET  /v1/system/resources
GET  /v1/media/now-playing
GET  /v1/media/art
GET  /v1/notifications
POST /v1/auth/rotate
```

Termux AI routes share the same server:

```text
GET  /v1/models
POST /v1/chat/completions
POST /v1/completions
GET  /v1/ai/status
GET  /v1/ai/runtime
GET  /v1/ai/models
```

More route and implementation detail is in [Developer docs](Developer_Docs.md).

## Permissions and Privacy

LauncherCtl binds to localhost:

```text
127.0.0.1
```

Local requests must include the bearer token from `~/.launcherctl/token`.

`media`, `art`, and `notifications` require Android notification listener access. Without that permission, those commands return limited or empty data. These commands may expose sensitive notification or media content, so grant the permission only if you want those shell features.

If the token is exposed, rotate it:

```sh
launcherctl token rotate
```

Then update any tools that stored the old token.

## Troubleshooting

If LauncherCtl is not responding:

```sh
launcherctl status
launcherctl restart
```

If `media`, `art`, or `notifications` are empty, grant notification listener access in Android settings.

If an optional Shizuku shell helper fails:

```sh
launcherctl tty-doctor
```

For custom Shizuku shell commands, call `rish` directly:

```sh
rish -c "id"
```
