# Termux Launcher AI

Termux Launcher AI is a local AI service built into Termux Launcher. It lets apps and command-line tools talk to on-device AI models through a localhost API.

The short version:

- The AI runs on your device, inside the Termux Launcher app process.
- It exposes an OpenAI-compatible API for tools such as `aichat`.
- It supports LiteRT-LM and MNN model backends.
- It does not bundle model files inside the APK. You download or import models yourself.
- The API is protected by a bearer token stored on your device.

## What It Is For

Termux Launcher AI is mainly a model host. It gives other tools a local AI backend.

Good uses:

- chatting with a local model from a CLI tool
- connecting OpenAI-compatible tools to an on-device endpoint
- keeping a selected model warm in the background
- switching between supported local models by changing the request model name
- letting Termux Launcher manage local model downloads, imports, loading, and unloading

It is not meant to replace every AI shell tool. Tools like `aichat`, coding assistants, or tmux helpers can provide the user interface. Termux Launcher AI provides the local model runtime they can call.

## Main Pieces

### TAI

TAI means Termux AI. It is the name used for the local AI runtime and helper command.

The app installs a command called:

```sh
tai
```

Use it to check status, list models, load models, unload models, and inspect the runtime.

Useful commands:

```sh
tai status
tai runtime
tai models
tai load gemma-4-e2b-it-litert-lm
tai unload
tai keep-warm gemma-4-e2b-it-litert-lm --minutes 30
```

Use raw JSON output when scripting:

```sh
tai --json runtime
```

### LauncherCtl

LauncherCtl is the local bridge used by Termux Launcher. It exposes localhost API endpoints and writes the connection details into your home directory.

Important files:

```sh
~/.launcherctl/endpoint
~/.launcherctl/token
```

`endpoint` contains the local base URL.

`token` contains the bearer token used as the API key.

## OpenAI-Compatible Endpoint

Termux Launcher AI provides an OpenAI-compatible API under:

```text
http://127.0.0.1:41237/v1
```

The port can be changed in Settings. If you change it, the current endpoint is still written to:

```sh
~/.launcherctl/endpoint
```

For CLI tools, use:

```sh
export OPENAI_BASE_URL="$(cat ~/.launcherctl/endpoint)/v1"
export OPENAI_API_KEY="$(cat ~/.launcherctl/token)"
```

Supported OpenAI-style endpoints:

```text
GET  /v1/models
POST /v1/chat/completions
POST /v1/completions
POST /v1/embeddings
POST /v1/audio/speech
```

`/v1/audio/speech` returns a clear `unsupported_audio_output` error because the local runners do not currently generate audio output.

Most OpenAI-compatible CLI tools expect a base URL ending in `/v1`, so use:

```text
http://127.0.0.1:41237/v1
```

not:

```text
http://127.0.0.1:41237
```

## Supported Model Names

Use these model IDs exactly:

```text
gemma-4-e2b-it-litert-lm
gemma-4-e4b-it-litert-lm
functiongemma-270m-mobile-actions-litert-lm
qwen2.5-coder-1.5b-instruct-mnn
```

`gemma-4-e2b-it-litert-lm` is the fast default assistant model.

`gemma-4-e4b-it-litert-lm` is the larger assistant model.

`functiongemma-270m-mobile-actions-litert-lm` is a smaller model intended for mobile action-style tasks. It is CPU-only.

`qwen2.5-coder-1.5b-instruct-mnn` is the default installed MNN code model.

## How Model Loading Works

You do not always need to manually load a model first.

When an OpenAI-compatible client sends a generation request, Termux Launcher AI checks the requested model:

- If the model is already loaded, it uses it.
- If the model is installed but not loaded, it loads it automatically.
- If the request uses another assistant model, it switches the assistant slot to that model.
- If the model is unknown or not installed, the request fails with an error.

The loaded model is kept warm for the configured timeout, then unloaded when idle.

You can also load manually:

```sh
tai load gemma-4-e2b-it-litert-lm
```

and unload manually:

```sh
tai unload
```

## Two Model Slots

Termux Launcher AI supports two runtime slots:

- one LiteRT-LM assistant model slot
- one MobileActions model slot

The assistant slot is for:

```text
gemma-4-e2b-it-litert-lm
gemma-4-e4b-it-litert-lm
```

The MobileActions slot is for:

```text
functiongemma-270m-mobile-actions-litert-lm
```

This means the launcher can keep one main assistant model available while also keeping MobileActions available separately on CPU.

The assistant model prefers GPU when supported. MobileActions is always CPU.

## Settings

Open:

```text
Settings -> TAI / Termux AI
```

From there you can:

- choose the default assistant model
- download catalog models
- import a local `.litertlm` or `.task` model with Android's file picker; the selected file is copied into app-private model storage
- change generation defaults such as max tokens and temperature
- choose Auto, GPU, or CPU acceleration where supported
- configure the API port
- randomize the API port
- view or edit the API bearer token
- recreate the API bearer token
- view the OpenAI-compatible endpoint

The API port and bearer token persist. They do not change every time the app restarts unless you change them.

## Using With aichat

Install and configure `aichat` as an OpenAI-compatible client.

Use:

```sh
export OPENAI_BASE_URL="$(cat ~/.launcherctl/endpoint)/v1"
export OPENAI_API_KEY="$(cat ~/.launcherctl/token)"
```

Then choose one of the supported model IDs, for example:

```text
gemma-4-e2b-it-litert-lm
```

When the first request is sent, Termux Launcher AI will load the model if it is installed and not already loaded.

## Downloading or Importing Models

The APK does not include model files. This keeps the APK smaller and avoids bundling third-party model licenses.

You can download supported catalog models from the settings page, or use the CLI:

```sh
tai download gemma-4-e2b-it-litert-lm <model-url> --accept-terms
```

You can import an existing local LiteRT-LM file:

```sh
tai import /absolute/path/to/model.litertlm MyModelName
```

For catalog models, use the official model ID so OpenAI-compatible tools can request it directly.

## tmux Status Indicator

If you use the Termux Launcher tmux package, the status bar can show whether an AI model is loaded.

Loaded:

```text
󱜙
```

Unloaded:

```text
󱚡
```

When a model is loaded, the widget can also show the remaining keep-warm or idle timer. After the model is unloaded, the unloaded icon disappears after a short timeout.

## Security Notes

The AI endpoint is bound to localhost:

```text
127.0.0.1
```

It is meant for local apps and tools on the same device.

Requests must include the bearer token from:

```sh
~/.launcherctl/token
```

Treat this token like an API key. If it is exposed, recreate it from:

```text
Settings -> TAI / Termux AI -> Recreate API token
```

After recreating the token, update any CLI tools that stored the old key.

## Troubleshooting

### The CLI tool cannot connect

Check that Termux Launcher has been opened at least once after install:

```sh
cat ~/.launcherctl/endpoint
cat ~/.launcherctl/token
```

If the files are missing, open the app again.

### The model is not found

Check installed models:

```sh
tai models
```

Make sure the request uses the exact model ID.

### The model does not load

Check runtime state:

```sh
tai runtime
```

Try CPU mode if GPU loading fails:

```sh
tai load gemma-4-e2b-it-litert-lm --cpu
```

### The API key does not work

Read the current token:

```sh
cat ~/.launcherctl/token
```

If needed, recreate it in Settings and update your CLI tool.

## More Details

For the technical reference, see:

- [TAI / Termux AI](Termux_AI)
- [TAI LLM backends](Termux_AI_Backends)
- [LauncherCtl API](LauncherCtl_API)
