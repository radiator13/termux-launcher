# Termux AI

Termux AI, also called TAI, is a local on-device model host built into Termux Launcher. It runs inside the Android app process and exposes an authenticated localhost API through LauncherCtl.

TAI is not a shell agent. It does not plan commands or control Android by itself. Use tools such as `aichat`, coding assistants, or tmux helpers for the chat UI. TAI provides the local model runtime those tools can call.

## What It Does

TAI can:

- download catalog models from the app settings page
- import local `.litertlm` or `.task` model files
- load, unload, keep warm, and cancel model runs
- expose OpenAI-compatible endpoints for chat and completions
- stream responses with Server-Sent Events when the client asks for streaming

TAI does not currently:

- execute shell commands
- summarize notifications
- perform Android actions
- accept image or audio prompts
- provide a benchmark UI

Advanced runtime and API details are in [Developer docs](Developer_Docs.md).

## Open the Settings Page

Open:

```text
Long press Terminal -> More -> TAI / Termux AI
```

From there you can:

- download supported catalog models
- import a local model with Android's file picker
- choose the default assistant model
- choose Auto, GPU, or CPU when supported
- change generation defaults such as max tokens and temperature
- configure the local API port
- view, edit, or recreate the API token
- save an optional Hugging Face token for gated Hugging Face downloads

The APK does not include model files. Downloads and imports are explicit user actions so you can review model licenses and provider terms.

## Supported Models

Use these catalog model IDs exactly:

```text
Gemma-4-E2B-it
Gemma-4-E4B-it
MobileActions-270M
```

`Gemma-4-E2B-it` is the default fast assistant model. `Gemma-4-E4B-it` is larger. `MobileActions-270M` is smaller and CPU-only.

Device memory matters. Low-memory devices may show a warning or fail to load larger models. If GPU loading fails, try CPU mode:

```sh
tai load Gemma-4-E2B-it --cpu
```

## Shell Helper

The app installs:

```sh
tai
```

Useful commands:

```sh
tai status
tai runtime
tai models
tai downloads
tai load Gemma-4-E2B-it
tai keep-warm Gemma-4-E2B-it --minutes 30
tai cancel
tai unload
tai doctor
```

Use JSON output for scripts:

```sh
tai --json status
tai --json runtime
```

## Download or Import Models

The easiest path is the Settings page:

```text
TAI / Termux AI -> Models
```

Settings downloads catalog models into app-private storage. Settings imports copy selected `.litertlm` or `.task` files into app-private storage.

The CLI can also register a readable local model path:

```sh
tai import /absolute/path/to/model.litertlm MyLocalModel
```

CLI import registers the path. It does not copy the file into app-private storage.

For direct downloads:

```sh
tai download MyDownloadedModel https://provider.example/model.litertlm --accept-terms
tai downloads
```

Downloads require:

- an HTTPS URL
- a model ID
- `--accept-terms`, meaning you reviewed the provider license or terms yourself

For gated Hugging Face models, accept the model terms on Hugging Face, create a read token, then save it in:

```text
Settings -> TAI / Termux AI -> Hugging Face token
```

Do not paste private tokens into shell commands if you want to avoid shell history.

## Use With OpenAI-Compatible Clients

TAI shares the LauncherCtl endpoint and token:

```sh
~/.launcherctl/endpoint
~/.launcherctl/token
```

Most OpenAI-compatible tools need a base URL ending in `/v1`:

```sh
export OPENAI_BASE_URL="$(cat ~/.launcherctl/endpoint)/v1"
export OPENAI_API_KEY="$(cat ~/.launcherctl/token)"
```

Supported OpenAI-style endpoints:

```text
GET  /v1/models
POST /v1/chat/completions
POST /v1/completions
```

When a client sends a request, TAI checks the model name:

- if the model is already loaded, it uses it
- if the model is installed but not loaded, it loads it
- if another installed assistant model is requested, it switches to that model
- if the model is unknown or missing, the request fails with an error

## Runtime Basics

The default accelerator is Auto. Auto follows the model profile and the device capabilities. You can request GPU or CPU explicitly:

```sh
tai load Gemma-4-E2B-it --gpu
tai load Gemma-4-E2B-it --cpu
```

The model stays loaded while it is active, then unloads after the configured idle timeout. You can keep it warm for a while:

```sh
tai keep-warm Gemma-4-E2B-it --minutes 30
```

Only one generation runs at a time. Stop an active generation with:

```sh
tai cancel
```

## Security Notes

The endpoint is local to the device:

```text
127.0.0.1
```

Requests must include the bearer token from:

```sh
~/.launcherctl/token
```

Treat this token like an API key. If it is exposed, recreate it from Settings or run:

```sh
launcherctl token rotate
```

After rotating the token, update any AI clients that saved the old key.

## Troubleshooting

Check that the bridge files exist:

```sh
cat ~/.launcherctl/endpoint
cat ~/.launcherctl/token
```

If they are missing, open Termux Launcher again.

Check installed models:

```sh
tai models
```

Check runtime state:

```sh
tai runtime
```

If a model does not load, try CPU mode and check memory warnings:

```sh
tai load Gemma-4-E2B-it --cpu
tai doctor
```
