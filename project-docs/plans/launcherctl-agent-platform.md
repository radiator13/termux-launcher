# Termux Launcher TAI Extension Plan

## Summary

- Correct repo: `/data/data/com.termux/files/home/myfiles/termux-launcher`.
- Working branch: `tai-ext`, created from `experimental`.
- Local Android build is unavailable; verification uses GitHub Actions.
- Preserve existing TAI OpenAI-compatible endpoints and add device/context/tool surfaces around them.
- Keep all runtime state under existing `~/.launcherctl`.

## Implementation Status

- Implemented notification history storage/query APIs and CLI commands.
- Implemented hardware, runtime, and integration capabilities endpoint; model availability remains in the TAI model APIs.
- Implemented shared tool registry and OpenAI-compatible function schemas.
- Implemented agent route/execute APIs with deterministic fallback and confirmation gating.
- Implemented append-only event/audit storage plus event tail APIs.
- Implemented `launcherctl mcp` as a Python stdio MCP bridge over `/v1/agent/tools` and `/v1/agent/execute`.
- Added docs for LauncherCtl API, TAI integration boundaries, and MCP usage.
- GitHub Actions remains the required Android build/unit-test gate because local Android SDK/NDK is unavailable in this Termux session.

## Branch, CI, And Worker Flow

- Push implementation work to `tai-ext`.
- Use existing GitHub Actions workflow `Build nightly`.
- The workflow runs `testDebugUnitTest`, `assembleDebug`, validates all ABI APKs, and uploads artifacts.
- Retrieve APK artifacts from the successful run, especially universal and arm64-v8a.

## Implementation Changes

- Keep all runtime files under `~/.launcherctl`:
  - `endpoint`, `token`
  - `launcher.db`
  - `notifications.jsonl`
  - `events.jsonl`
  - `agent-runs.jsonl`
  - generated/debug snapshots: `tools.json`, `capabilities.json`, `config.json`
- Add app-side notification history:
  - Keep current `/v1/notifications` active snapshot backward compatible.
  - Persist posted/removed notification events to SQLite and JSONL from `LauncherCtlNotificationListener`.
  - Add APIs/CLI for recent, since, search, and stats.
- Add hardware/model gating:
  - `GET /v1/launcher/capabilities`.
  - Include ABI, SDK, RAM, accelerator support, TAI status, notification access, warnings, and blocking reasons.
  - Fail early with structured errors for unsupported backend/model loads.
- Add shared tool registry:
  - Tool metadata: name, description, JSON schema, risk level, confirmation requirement, executor.
  - Initial tools: `capabilities.get`, `apps.search`, `apps.launch`, `notifications.recent`, `notifications.since`, `notifications.search`, `notifications.stats`, `media.now_playing`, `system.resources`, `intent.open`, `memory.write`, `memory.search`, `events.tail`, `user.confirm`.
- Add Agent APIs:
  - `GET /v1/agent/tools`
  - `POST /v1/agent/route`
  - `POST /v1/agent/execute`
  - `GET /v1/events`
  - `POST /v1/events/tail`
  - `GET /v1/events/stream`
- Keep agent routing deterministic. FunctionGemma is exposed as a normal catalog model rather than a hidden routing companion.
- Extend `launcherctl`:
  - `launcherctl capabilities`
  - `launcherctl tools`
  - `launcherctl agent --dry-run "open maps"`
  - `launcherctl agent "open maps"`
  - `launcherctl notifications recent`
  - `launcherctl notifications since <epoch-ms>`
  - `launcherctl notifications search <text>`
  - `launcherctl notifications stats`
  - `launcherctl events tail`
- Add MCP adapter after HTTP/CLI pass:
  - `launcherctl mcp`
  - Implement as stdio bridge over shared HTTP tool registry.
  - If Python is unavailable, print `pkg install python`.

Notes:
- `GET /v1/events/stream` is implemented as a snapshot SSE stream ending with `data: [DONE]`; it does not keep a long-lived poll loop open in this first pass.
- FunctionGemma companion routing was removed; model clients can select it explicitly, while Android tools are exposed through LauncherCtl and MCP.

## Opencode Worker Plan

- Codex orchestrates, reviews diffs, commits, pushes, and monitors CI.
- Use opencode from repo root:

```sh
opencode run -m opencode-go/kimi-k2.7-code "<focused prompt>"
```

- Model assignment:
  - `opencode-go/kimi-k2.7-code`: main Java/API/CLI implementation.
  - `opencode-go/qwen3.7-max`: tests and schema consistency.
  - `opencode-go/deepseek-v4-pro`: security/API review.
  - `opencode-go/glm-5.2`: docs and CLI help.
- Worker sequence:
  1. Storage helpers and notification persistence.
  2. Notification query APIs and CLI.
  3. Capabilities endpoint and hardware gating.
  4. Shared tool registry and `/v1/agent/tools`.
  5. Agent route/execute with deterministic fallback.
  6. FunctionGemma catalog integration without hidden companion routing.
  7. Events APIs and CLI.
  8. MCP adapter.
  9. Docs and final review.

## Verification

- Local lightweight checks:

```sh
sh -n resources/bin/launcherctl
git diff --check
```

- GitHub Actions required:
  - `Build nightly` on branch `tai-ext`.
  - Must pass unit tests and debug APK build.
  - Must upload universal, arm64-v8a, armeabi-v7a, x86_64, x86 APK artifacts and sha256sums.
- Add or maintain tests for:
  - notification event persistence
  - recent/since/search/stats behavior
  - active notification snapshot compatibility
  - capabilities JSON and unsupported hardware errors
  - tool schema generation
  - route vs execute separation
  - confirmation gating
  - CLI endpoint mappings
  - MCP list/call smoke behavior

## Assumptions

- `~/.launcherctl` remains the single state directory.
- Existing TAI OpenAI-compatible endpoints remain unchanged.
- SQLite is authoritative; JSONL files are compatibility/debug streams.
- Filtered query APIs use POST bodies where needed because the current server does not parse URL query strings.
- MCP is an adapter over the shared tool registry, not separate logic.
