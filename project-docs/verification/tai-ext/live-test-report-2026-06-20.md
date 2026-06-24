# Live Verification Report - 2026-06-20

Environment:

- Device: Nothing A065, Android API 36.
- Installed branch tested first: `tai-ext` at `9489f697`.
- Follow-up fix commit: `c887bda3`.
- Runtime state directory: `~/.launcherctl`.

## Passed On Installed APK

- `launcherctl status` returned `ok: true`, Shizuku `READY`, privileged access available, and notification listener connected.
- `launcherctl capabilities` returned hardware gating, FunctionGemma availability, TAI runtime state, and 14 tools.
- `launcherctl tools` returned 14 internal tools and 14 OpenAI-compatible function schemas.
- `tai status`, `tai runtime`, and `GET /v1/models` worked.
- `launcherctl apps` listed 114 launchable activities.
- `launcherctl resources` returned CPU, memory, storage, battery, and privileged backend fields.
- Notification history:
  - temporary notification posted with `termux-notification`.
  - `notifications.search` found it.
  - `notifications.since` showed both posted and removed events after removal.
  - `notifications.jsonl` and `launcher.db` were created under `~/.launcherctl`.
- Agent route:
  - `open termux` -> `apps.launch`
  - `now playing` -> `media.now_playing`
  - `system resources` -> `system.resources`
  - `show recent notifications` -> `notifications.recent`
- Agent execute:
  - `capabilities.get`, `apps.search`, `system.resources`, `media.now_playing`, `memory.write`, `memory.search`, and `events.tail` worked.
  - `apps.launch` and `intent.open` returned `confirmation_required` without confirmation.
  - confirmed `apps.launch` for `termux` succeeded.
  - confirmed invalid `intent.open` returned structured `activity_not_found`.
- Events:
  - `launcherctl events tail`, `GET /v1/events`, and `GET /v1/events/stream` worked.
- MCP:
  - `launcherctl mcp` handled `tools/list`.
  - `tools/call` worked for `capabilities_get`.
  - `_confirm: true` worked for confirmed `memory_search`.
  - newline-delimited and `Content-Length` framing both worked.
- FunctionGemma:
  - `tai load functiongemma-270m-mobile-actions-litert-lm --cpu` succeeded.
  - capabilities reflected `functionGemma.modelLoaded: true`.

## Issues Found

1. `what is playing` routed to `capabilities.get`; `now playing` routed correctly.
2. FunctionGemma route fallback sent all 14 full OpenAI tool schemas and exceeded MobileActions 270M context:
   `Input token ids are too long. Exceeding the maximum number of tokens allowed: 1167 >= 1024`.
3. Epoch-millisecond tool schemas used `2147483647` as `maximum`, which is too small for current timestamps.
4. Successful `agent.execute` audit events did not include the tool name in event payloads.

## Fixes Applied In `c887bda3`

- Added natural media phrases such as `what is playing`.
- Switched FunctionGemma routing to compact tool schemas and a shorter system prompt.
- Raised epoch-millisecond schema maximums to JavaScript safe integer range.
- Filled audit tool names from the execute request body when the result payload does not contain `tool`.
- Added unit tests for media routing, epoch schema range, and successful audit tool names.

## Rebuild Status

- CI run for `c887bda3`: passed.
- Run: https://github.com/PickleHik3/termux-launcher/actions/runs/27848857824
- APK artifacts produced:
  - `termux-app_v0.2.22-c887bda-apt-android-7-github-debug_universal`
  - `termux-app_v0.2.22-c887bda-apt-android-7-github-debug_arm64-v8a`
  - `termux-app_v0.2.22-c887bda-apt-android-7-github-debug_armeabi-v7a`
  - `termux-app_v0.2.22-c887bda-apt-android-7-github-debug_x86_64`
  - `termux-app_v0.2.22-c887bda-apt-android-7-github-debug_x86`
  - `termux-app_v0.2.22-c887bda-apt-android-7-github-debug_sha256sums`

The current installed APK was verified before these fixes were installed locally. Install one of the `c887bda3` artifacts above to live-test the FunctionGemma compact routing and the corrected `what is playing` route on-device.
