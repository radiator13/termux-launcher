# TAI / Termux AI

Termux-Launcher now has native AI backends powered by [Google LiteRT](https://developers.google.com/edge/litert)/[MediaPipe](https://developers.google.com/edge/mediapipe/solutions/guide) and [TaoBao/alibaba MNN](https://github.com/alibaba/mnn). 

I wanted fast interference abovfe everything else and therefore, the LiteRT and MNN backends were chosen. I tried MLC backend as well, but it requires the model weights to be bundled along with the APK, and did not present any meaningful benefit for this tradeoff. 

It does not support GGUF models, but you can always just use termux native llamacpp to run those. 

Provides you with a openai compatible API endpoint and key (bearer token), which you may plug into any cli AI apps/tools. 
The app loads the requested models automatically upon first request and will be automatically unloaded after 10 minutes of no interaction. 

TAI is the local on-device model endpoint for Termux Launcher. The localhost API stays in the launcher process, while native LiteRT-LM/MNN model loading and generation run in an isolated Android `:tai_runtime` process. This APK is a LiteRT-LM/MNN host; it is not a GGUF or llama.cpp runner.

TAI is intentionally not a shell agent. Use established shell/coding clients such as `aichat` or `tmuxai` for terminal workflows, pane context, command review, and coding UX. TAI provides the local model runtime those tools can call.

## Scope

TAI currently handles:

- model catalog metadata
- explicit model import/download/delete
- model load/unload/keep-warm/cancel lifecycle control
- OpenAI-compatible `GET /v1/models`
- OpenAI-compatible `POST /v1/chat/completions`
- OpenAI-compatible `POST /v1/completions`
- streaming SSE responses for chat and completion requests
- LiteRT-LM image and audio input for models that advertise those capabilities
- MNN chat and tool-call compatibility through the local OpenAI endpoint
- compatibility with the LauncherCtl agent/tool layer through the same localhost bridge

TAI does not currently:

- plan or execute shell commands
- summarize notifications
- generate audio output
- run GGUF/raw weight files
- expose embeddings unless a local model advertises `text_embeddings` in `/v1/models`
- expose Gallery skills or benchmark UI

Android/device actions are exposed separately through LauncherCtl capability, agent, and MCP APIs instead of being hidden inside the `tai` shell helper or the OpenAI-compatible model endpoints. FunctionGemma can return mobile-action tool calls, but TAI does not automatically execute Android actions.

## Model Roles

TAI keeps one default model assignment for requests that omit `model`:

- `gemma-4-e2b-it-litert-lm`: default fast assistant model

Open Settings -> TAI / Termux AI to change the default model and runtime overrides.

Multimodal models are listed in `/v1/models` as modality-scoped ids that share one downloaded file: the canonical id (e.g. `gemma-4-e2b-it-litert-lm`) loads text-only chat, `<id>-vision` adds image input, and `<id>-audio` adds audio input. Pick the id matching what you want to send; only one modality loads at a time (matching Google AI Edge Gallery), which keeps the GPU load small enough to fit. See [Termux_AI_Backends.md](Termux_AI_Backends.md#per-modality-model-ids).

TAI does not bundle model files in the APK. Downloads and imports must be explicit user actions, with license/terms awareness for gated or restricted models. Hugging Face tokens are never bundled; an optional user token can be saved in app-private settings for Hugging Face downloads.

## Runtime Defaults

TAI stores user overrides separately from model metadata. Runtime tunables default to `Auto / Gallery default`:

- max output tokens
- TopK
- TopP
- temperature
- accelerator: Auto / GPU / CPU
- thinking
- speculative decoding
- idle unload / keep-warm policy

Before every load, TAI runs a hard preflight for ABI, Android API level, bundled native libraries, model-file readability/format, memory pressure, accelerator policy, and known per-device failures. Auto accelerator defaults to CPU on unknown devices. GPU is used automatically only after the same model/device/backend has loaded successfully on GPU before; explicit `--gpu` remains available for manual testing when the model profile and device support it. `functiongemma-270m-mobile-actions-litert-lm` is CPU-only with temperature 0.0, while Gemma 4 E2B/E4B retain their Gallery defaults.

Device memory detection matches Gallery for advertised/total RAM and also checks current available memory. Low available memory is a hard guard before native runtime initialization; below-recommendation RAM is reported as a warning and prevents surprise OpenAI auto-loads.

Known profiles are synchronized with Edge Gallery 1.0.15:

- `gemma-4-e2b-it-litert-lm`: GPU, CPU; 8 GiB minimum; 4000 max output tokens; TopK 64; TopP 0.95; temperature 1.0.
- `gemma-4-e4b-it-litert-lm`: GPU, CPU; 12 GiB minimum; 4000 max output tokens; TopK 64; TopP 0.95; temperature 1.0.
- `functiongemma-270m-mobile-actions-litert-lm`: CPU only; 6 GiB minimum; 1024 max output tokens; TopK 64; TopP 0.95; temperature 0.0.

Unknown imported models default to CPU, matching Gallery's import dialog. The import API accepts `runtimeProfile.compatibleAccelerators`, `defaultMaxTokens`, `defaultTopK`, `defaultTopP`, `defaultTemperature`, and `minDeviceMemoryInGb` when the user knows the package's requirements.

The LiteRT-LM `Engine` remains loaded after `tai load`, and TAI reuses a `Conversation` while the model, prompt mode, system prompt, and sampling options remain compatible. One generation runs at a time. Use `tai cancel` or `POST /v1/ai/runtime/cancel` to stop an active generation.

## Shell Command

The launcher installs:

```sh
tai
```

Useful commands:

```sh
tai --json status
tai status
tai runtime
tai models
tai import ~/models/gemma.litertlm gemma-4-e2b-it-local
tai download gemma-4-e2b-it-litert-lm https://example.invalid/path/to/model.litertlm --accept-terms
tai downloads
tai preflight gemma-4-e2b-it-litert-lm
tai load gemma-4-e2b-it-litert-lm
tai load gemma-4-e2b-it-litert-lm --gpu
tai load gemma-4-e2b-it-litert-lm --cpu
tai keep-warm gemma-4-e2b-it-litert-lm --minutes 30
tai cancel
tai unload
tai doctor
```

The `tai` CLI is a model-management helper, not the chat frontend. Use `tai --json <command>` when scripts or debugging need the raw authenticated API JSON response.

## Local API

TAI uses the same bearer-token local bridge as `launcherctl`.

Implemented endpoints:

- `GET /v1/ai/status`
- `GET /v1/ai/runtime`
- `POST /v1/ai/runtime/preflight`
- `POST /v1/ai/runtime/load`
- `POST /v1/ai/runtime/unload`
- `POST /v1/ai/runtime/keep-warm`
- `POST /v1/ai/runtime/cancel`
- `GET /v1/ai/models`
- `POST /v1/ai/models/import`
- `POST /v1/ai/models/download`
- `POST /v1/ai/models/download-catalog`
- `GET /v1/ai/models/downloads`
- `POST /v1/ai/models/delete`
- `POST /v1/ai/models/load`
- `POST /v1/ai/models/unload`
- `GET /v1/models`
- `POST /v1/chat/completions`
- `POST /v1/completions`
- `POST /v1/embeddings`
- `POST /v1/audio/speech`

Model import and download registry persistence is implemented. Downloaded or imported `.litertlm`/`.task` models can be loaded through the isolated Android LiteRT-LM adapter when preflight passes. Downloaded MNN catalog models load from their repository `config.json` package. GGUF, safetensors, PyTorch, ONNX, and other raw weight formats are rejected because this APK does not include a GGUF/llama.cpp backend.

The runtime supports non-streaming JSON responses and streaming `text/event-stream` responses. Streaming emits OpenAI-style chunks and finishes with `data: [DONE]`. The app manifest declares the same optional native libraries as Edge Gallery: `libvndksupport.so`, `libOpenCL.so`, `libcdsprpc.so`, and `libedgetpu_litert.so`. LiteRT-LM's `Capabilities` API is checked inside `:tai_runtime` before speculative decoding is enabled. If native initialization crashes the runtime process, the launcher survives and reports the last attempted model/backend plus CPU/smaller-model fallback guidance.

Reference implementation and metadata:

- [Google AI Edge Gallery model allowlist 1.0.15](https://github.com/google-ai-edge/gallery/blob/main/model_allowlists/1_0_15.json)
- [Gallery model allowlist/device policy](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/data/ModelAllowlist.kt)
- [Gallery memory detection](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/MemoryWarning.kt)
- [Gallery LiteRT-LM runtime](https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt)

## External Clients

For `aichat`, `tmuxai`, or other OpenAI-compatible clients, point the base URL at the authenticated LauncherCtl endpoint and use:

```text
/v1/chat/completions
/v1/completions
```

The endpoint URL and bearer token are stored at:

```sh
~/.launcherctl/endpoint
~/.launcherctl/token
```

MCP-capable clients can use `launcherctl mcp` for the Android tool surface. That bridge is separate from the OpenAI-compatible TAI chat/completions endpoints and uses the same `~/.launcherctl` endpoint/token files.

## Importing and Downloading Models

TAI supports two explicit model registration paths:

```sh
tai import /absolute/path/to/model.litertlm MyLocalModel
tai download MyDownloadedModel https://provider.example/model.litertlm --accept-terms
tai downloads
```

Import registers a readable local LiteRT-LM `.litertlm` or `.task` file path. It does not copy the file into app-private storage yet. MNN models should be installed from catalog/download URLs so TAI can fetch the `config.json` package and sidecar files.

Download starts a foreground app-process transfer into app-private storage under `files/tai/models/`. Settings shows progress while the page is open, and Android shows a progress notification while the transfer runs. Downloads require:

- an explicit HTTPS URL
- an explicit model id
- `--accept-terms`, meaning you reviewed the provider license/terms yourself

For gated Hugging Face models, first accept the provider terms on Hugging Face, create a read token, and save it in Settings -> TAI / Termux AI -> Hugging Face token. TAI sends that token only as a Bearer token to Hugging Face download URLs. Do not put Hugging Face or other private tokens in shell history.

## LLM Backends

For backend-specific details, including LiteRT-LM GPU multimodal behavior, MNN config defaults, MNN tool-call handling, and OpenAI-compatible media errors, see:

- [TAI LLM backends](Termux_AI_Backends)

## Current Limitations / TODO

- Expand benchmark counters and deeper runtime diagnostics.
- Add copy-into-private-storage import mode and UI file picker.
- Add pause/cancel/retry controls for foreground downloads.
