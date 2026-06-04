# TAI / Termux AI

TAI is the native local AI assistant foundation for Termux Launcher. It is designed to run in the Android app process and expose capabilities to Termux shells through the existing authenticated `launcherctl` local bridge.

This first version is intentionally a foundation build. It adds settings, model registry, authenticated API routes, shell helpers, safety planners, and documentation. Local model inference is still stubbed behind `TaiRuntime` so LiteRT-LM or another Android-side runtime can be added without changing the shell/API surface.

## Model Roles

TAI starts with these default role assignments:

- `Gemma-4-E2B-it`: default fast assistant model
- `Gemma-4-E4B-it`: coding/build/reasoning model
- `MobileActions-270M`: mobile action/router model

These are defaults only. Open Settings -> TAI / Termux AI to change role assignments.

TAI does not bundle model files in the APK. Downloads and imports must be explicit user actions, with license/terms awareness for gated or restricted models. Hugging Face tokens are not bundled.

## Runtime Defaults

TAI stores user overrides separately from model metadata. Runtime tunables default to `Auto / model default`:

- max tokens
- TopK
- TopP
- temperature
- accelerator: Auto / CPU / GPU
- thinking
- speculative decoding

Null/Auto values are not passed as explicit tuned values to the runtime. The model/runtime/import defaults should be used unless the user overrides a setting.

## Shell Commands

The launcher installs:

```sh
tai
@tai
```

Useful commands:

```sh
tai status
tai models
tai load Gemma-4-E2B-it
tai unload
tai ask "hello"
tai plan "update packages"
@tai where is the config files for neovim?
tai notifications today
tai build --print-command
tai doctor
```

`@tai` is the first practical terminal helper. It prints a proposed plan through `tai plan`. True command-line replacement will need shell widgets, readline integration, fish/zsh bindings, or tmux integration.

## Local API

TAI uses the same bearer-token local bridge as `launcherctl`.

Implemented foundation endpoints:

- `GET /v1/ai/status`
- `GET /v1/ai/models`
- `POST /v1/ai/models/import`
- `POST /v1/ai/models/download`
- `POST /v1/ai/models/load`
- `POST /v1/ai/models/unload`
- `POST /v1/ai/chat`
- `POST /v1/ai/terminal/plan`
- `POST /v1/ai/terminal/execute`
- `POST /v1/ai/notifications/summarize`
- `POST /v1/ai/actions/route`
- `POST /v1/ai/actions/execute`
- `POST /v1/ai/build/plan`
- `POST /v1/ai/build/run`
- `POST /v1/ai/prompt-lab/run`
- `POST /v1/chat/completions`

Several endpoints intentionally return clear stub/TODO responses until the real runtime, import/download registry persistence, and execution flows are implemented.

## Safety Policy

TAI does not silently run destructive shell commands.

The terminal planner is plan-only in this foundation build. It flags package updates, installs, and destructive patterns for review. It avoids unattended package flags unless a future explicit mode supports them.

Examples:

- `tai plan "update packages"` detects `pkg`/`apt` versus `pacman` and prints interactive update commands.
- `@tai where is the config files for neovim?` prints read-only `find` commands for common Neovim locations.
- `@tai find me the file fish.config` searches for both `fish.config` and the common `config.fish` name.

Risky actions such as `rm -rf`, `find ... -delete`, `dd`, `mkfs`, mass `chmod -R`, and mass `chown -R` must require explicit confirmation in future execute/agent modes. Destructive `find` tasks should show a dry run first.

## Notification Privacy

Notification content is sensitive. TAI only reads notification data already exposed by the launcher notification listener path, and only through the authenticated local bridge. If notification listener access is not granted, `tai notifications today` returns a setup hint instead of pretending to summarize unavailable data.

Future model summaries should separate actionable notifications from promotional or low-priority items without sending notification text off-device.

## Build Helper

`tai build --print-command` inspects the current directory and detects common build systems such as Gradle, Make, CMake, Meson, Cargo, Go, Node, and Python. It prints candidate commands and does not install dependencies.

Future monitored mode should:

- ask before installing anything
- run and monitor builds
- diagnose errors
- unload the model before long builds when memory pressure matters

## Current Limitations / TODO

- Replace `StubTaiRuntime` with LiteRT-LM or another Android-side local runtime.
- Persist imported models with local path, source, capabilities, license metadata, and size.
- Add explicit model download/import UI with license/terms awareness.
- Add streaming/SSE responses.
- Add image input and audio scribe support for capable models.
- Implement safe Android-side flashlight/device action execution.
- Add confirmed terminal/build agent execution modes.
- Add prompt-lab UI for raw request/response inspection.
