# TAI / Termux AI

TAI is the native local AI assistant foundation for Termux Launcher. It is designed to run in the Android app process and expose capabilities to Termux shells through the existing authenticated `launcherctl` local bridge.

This implementation is phased. It adds settings, model registry, authenticated API routes, shell helpers, safety planners, foreground model downloads, and a LiteRT-LM runtime adapter behind `TaiRuntime` so the shell/API surface stays stable as runtime features expand.

## Model Roles

TAI starts with these default role assignments:

- `Gemma-4-E2B-it`: default fast assistant model
- `Gemma-4-E4B-it`: coding/build/reasoning model
- `MobileActions-270M`: mobile action/router model

These are defaults only. Open Settings -> TAI / Termux AI to change role assignments.

TAI does not bundle model files in the APK. Downloads and imports must be explicit user actions, with license/terms awareness for gated or restricted models. Hugging Face tokens are never bundled; an optional user token can be saved in app-private settings for Hugging Face downloads.

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
tai --json status
tai status
tai models
tai import ~/models/gemma.task Gemma-4-E2B-it-local
tai download Gemma-4-E2B-it https://example.invalid/path/to/model.task --accept-terms
tai downloads
tai load Gemma-4-E2B-it
tai load Gemma-4-E2B-it --cpu
tai unload
tai ask "hello"
tai plan "update packages"
@tai where is the config files for neovim?
tai notifications today
tai build --print-command
tai doctor
```

The `tai` CLI prints human-readable text by default. Use `tai --json <command>` when scripts or debugging need the raw authenticated API JSON response.

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

Model import and download registry persistence is implemented. Downloaded or imported `.litertlm` models can be loaded through the Android-side LiteRT-LM adapter on supported 64-bit devices.

The runtime currently supports non-streaming text prompts. It keeps sampling settings in the Auto/model-default state unless the user explicitly configures overrides. The accelerator setting is preserved in settings, but direct LiteRT-LM GPU loading is disabled in this build because native GPU engine creation crashed the Android app process on this device. Auto selects CPU. Explicit `tai load <model> --gpu` returns a visible error instead of calling the unsafe native GPU path. GPU support should be re-enabled only after probing/loading can run in an isolated runtime process.

## Safety Policy

TAI does not silently run destructive shell commands.

The terminal planner is plan-only in this foundation build. It returns structured plan JSON with commands, safety status, and confirmation flags. It flags package updates, installs, and destructive patterns for review. It avoids unattended package flags unless a future explicit mode supports them.

Examples:

- `tai plan "update packages"` detects `pkg`/`apt` versus `pacman` and prints interactive update commands.
- `@tai where is the config files for neovim?` prints read-only `find` commands for common Neovim locations.
- `@tai find me the file fish.config` searches for both `fish.config` and the common `config.fish` name.

`tai code` uses the coding/build model role. If the request looks like a terminal helper task covered by the built-in planner, it is routed to the structured terminal planner instead of asking the model for free-form prose.

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

## Importing and Downloading Models

TAI supports two explicit model registration paths:

```sh
tai import /absolute/path/to/model.task MyLocalModel
tai download MyDownloadedModel https://provider.example/model.task --accept-terms
tai downloads
```

Import registers a readable local file path. It does not copy the file into app-private storage yet.

Download starts a foreground app-process transfer into app-private storage under `files/tai/models/`. Settings shows progress while the page is open, and Android shows a progress notification while the transfer runs. Downloads require:

- an explicit HTTPS URL
- an explicit model id
- `--accept-terms`, meaning you reviewed the provider license/terms yourself

For gated Hugging Face models, first accept the provider terms on Hugging Face, create a read token, and save it in Settings -> TAI / Termux AI -> Hugging Face token. TAI sends that token only as a Bearer token to Hugging Face download URLs. Do not put Hugging Face or other private tokens in shell history.

## Current Limitations / TODO

- Expand the LiteRT-LM runtime adapter with streaming, benchmark counters, multimodal prompts, and tool-calling integration.
- Move LiteRT-LM GPU probing/loading into an isolated runtime process before enabling GPU acceleration again.
- Add copy-into-private-storage import mode and UI file picker.
- Add pause/cancel/retry controls for foreground downloads.
- Add streaming/SSE responses.
- Add image input and audio scribe support for capable models.
- Implement safe Android-side flashlight/device action execution.
- Add confirmed terminal/build agent execution modes.
- Add prompt-lab UI for raw request/response inspection.
