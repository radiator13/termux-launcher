# LauncherCtl API (Local Shell Bridge)

## Overview
LauncherCtl is a localhost API bridge for exposing Android/app data to shell tools without high-frequency `adb`/`rish` polling loops.

- Server: in app process, bound to `127.0.0.1` on random port.
- Auth: bearer token from `~/.launcherctl/token`.
- Endpoint URL: `~/.launcherctl/endpoint`.
- CLI: `$PREFIX/bin/launcherctl` (installed by the launcher app when `TermuxActivity` starts).

Important behavior:
- `launcherctl tty-exec` is a local CLI helper that uses `~/.rish/rish` for interactive/TTY-required commands.

## Files and Components

- Server implementation:
  - `app/src/main/java/com/termux/launcherctl/LauncherCtlApiServer.java`
- Notification/media source:
  - `app/src/main/java/com/termux/launcherctl/LauncherCtlNotificationListener.java`
- App startup wiring:
  - `app/src/main/java/com/termux/app/TermuxActivity.java`
- Manifest service entry:
  - `app/src/main/AndroidManifest.xml`

Runtime files under `$HOME/.launcherctl`:

- `token`: API bearer token.
- `endpoint`: local base URL (`http://127.0.0.1:<port>`).

## Endpoints (v1)

### `GET /v1/status`
Returns backend + LauncherCtl runtime status.

### `GET /v1/apps`
Returns the launcher's launchable activity catalog.

Each entry includes:
- `label`
- `packageName`
- `activityName`
- `stableId`
- `systemApp`
- `launchable`

The top-level payload also includes:
- `count`: number of launchable activities
- `packageCount`: number of unique packages represented by those activities

### `GET /v1/system/resources`
Returns a system resource snapshot:
- CPU metrics:
  - `cpuPercent` (from `/proc/stat` delta, with load-average fallback)
  - `cpuCores`
  - `loadAvg1m`, `loadAvg5m`, `loadAvg15m`
- Memory metrics:
  - top-level compatibility fields:
    - `memTotalBytes`, `memAvailableBytes`, `memFreeBytes`, `memUsedBytes`
  - nested `memory` object with additional meminfo-derived fields:
    - `buffersBytes`, `cachedBytes`, `swapCachedBytes`, `activeBytes`, `inactiveBytes`,
      `shmemBytes`, `slabBytes`, `swapTotalBytes`, `swapFreeBytes`
- Runtime/heap metrics:
  - `javaHeapUsedBytes`, `javaHeapMaxBytes`, `javaHeapFreeBytes`, `javaHeapTotalBytes`
  - nested `runtime` object
- Uptime metrics:
  - nested `uptime` object (`systemUptimeSec`, `systemUptimeMs`, `processUptimeMs`, `processUptimeSec`)
- Storage metrics:
  - nested `storage` array with per-path totals/used/free/available bytes
- Battery metrics:
  - nested `battery` object (`levelPercent`, charging state, plug type, temperature, voltage, health)
- Network metrics:
  - nested `network` array with per-interface `rx/tx` bytes, packets, errors, drops
- Thermal metrics:
  - nested `thermal` array from `/sys/class/thermal/thermal_zone*`
- Backend diagnostics:
  - `backendType`, `backendState`, `statusReason`, `statusMessage`, `isPrivilegedAvailable`

### `GET /v1/media/now-playing`
Returns cached now-playing media session data.
Requires notification listener access.

### `GET /v1/media/art`
Returns cached now-playing album art snapshot as base64-encoded JPEG payload.
Requires notification listener access.

### `GET /v1/notifications`
Returns cached notification list.
Requires notification listener access.

### `POST /v1/auth/rotate`
Rotates API token and rewrites `~/.launcherctl/token` and `~/.launcherctl/endpoint`.

## CLI Usage

```sh
launcherctl --help
launcherctl status
launcherctl apps
launcherctl launch whatsapp
launcherctl resources
launcherctl media
launcherctl art
launcherctl notifications
launcherctl tty-doctor
launcherctl tty-exec "id"
launcherctl token rotate
```

Note: `launcherctl-status` is not a command. Use `launcherctl status`.

## Interactive Commands (`tty-exec`)

Use `tty-exec` when a tool requires an interactive terminal (for example `btop`).

Prerequisites in user home:
- `~/.rish/rish` (executable)
- `~/.rish/rish_shizuku.dex` (present; on Android 14+, keep this non-writable)

Diagnostics:
```sh
launcherctl tty-doctor
```

Example:
```sh
launcherctl tty-exec "XDG_CONFIG_HOME=/data/local/tmp/btop-config /data/local/tmp/btop/btop --force-utf"
```

## Security Model

### Attack Surface
- Localhost API reachable from local device processes.
- Token theft enables API calls.
- Notification/media endpoints may expose sensitive user content.

### Mitigations Implemented
- Bearer token auth, startup-generated random token.
- Constant-time token comparison.
- Bounded worker pool (prevents unbounded thread growth).
- HTTP parser limits:
  - request line size,
  - header line size/count,
  - max body size.
- Endpoint rate limiting (`429` on abuse).
- Token rotation endpoint.
- Sensitive files written owner-only.

### Remaining Security Considerations
- Localhost token auth still depends on local process trust.
- If same app UID ecosystem is compromised, token can be read.
- Consider Unix domain sockets for tighter local access boundaries in future.
- Consider endpoint toggles for media/notifications if privacy requirements increase.

## Notification and Media Data

`launcherctl media` and `launcherctl notifications` require notification listener permission for the app.
`launcherctl art` also requires notification listener permission.

If not granted, responses include:
- `listenerConnected: false`
- a `hint` message.

## Troubleshooting

### Interactive command fails with "No tty detected"
- Run `launcherctl tty-doctor` and apply its suggested fixes for `~/.rish`.

### `launcherctl media`/`notifications` empty
- Grant notification access for the app in Android settings.

### Token errors (`401`)
- Run `launcherctl token rotate`.
- Re-run command after the token file is rewritten.

## Performance Notes

- LauncherCtl is event-driven for notifications/media and avoids constant polling loops.
- `/v1/apps` can be heavier than status queries; avoid frequent tight loops.
- `/v1/system/resources` is designed for periodic dashboard polling.
