# TAI LLM Backends

TAI supports two local LLM runners behind the same authenticated localhost API:

- LiteRT-LM for Gemma 4 and MobileActions `.litertlm` packages.
- MNN-LLM for downloaded MNN `config.json` packages.

Both runners are exposed through OpenAI-compatible endpoints so CLI tools can use the device as a local model backend:

```sh
export OPENAI_BASE_URL="$(cat ~/.launcherctl/endpoint)/v1"
export OPENAI_API_KEY="$(cat ~/.launcherctl/token)"
```

Implemented OpenAI-style endpoints:

```text
GET  /v1/models
POST /v1/chat/completions
POST /v1/completions
POST /v1/embeddings
POST /v1/audio/speech
```

`/v1/audio/speech` exists so clients get a clear OpenAI-shaped error. Local LiteRT-LM and MNN runners do not currently generate audio output, so audio-output requests return `unsupported_audio_output` with HTTP 501.

## Model IDs

Use the exact IDs returned by:

```sh
tai models
```

Common catalog IDs:

```text
gemma-4-e2b-it-litert-lm
gemma-4-e4b-it-litert-lm
functiongemma-270m-mobile-actions-litert-lm
qwen2.5-coder-1.5b-instruct-mnn
```

`GET /v1/models` includes TAI metadata on each OpenAI model item:

```json
{
  "id": "gemma-4-e2b-it-litert-lm",
  "object": "model",
  "_backend": "litert-lm",
  "_capabilities": ["text_chat", "image_input", "audio_input", "tool_use"]
}
```

Clients should not assume every model supports every content part. Check `_backend` and `_capabilities`.

## LiteRT-LM Backend

LiteRT-LM runs inside the Android app process and is used for Gemma 4 and MobileActions models.

Supported surfaces:

- text chat through `/v1/chat/completions`
- legacy completions through `/v1/completions`
- streaming chat/completion responses
- OpenAI function tools for LiteRT models that advertise `tool_use`
- image input for Gemma models that advertise `image_input`
- audio input for Gemma models that advertise `audio_input`

Not supported:

- generated audio output
- silently dropping unsupported image/audio content parts

Unsupported content parts return explicit OpenAI-shaped errors, such as `unsupported_content_part` or `capability_not_supported`.

### Gemma 4 Defaults

Gemma 4 LiteRT defaults follow Google AI Edge Gallery defaults:

| Model | Accelerator | Max tokens | TopK | TopP | Temperature |
| --- | --- | ---: | ---: | ---: | ---: |
| `gemma-4-e2b-it-litert-lm` | GPU, CPU fallback | 4000 | 64 | 0.95 | 1.0 |
| `gemma-4-e4b-it-litert-lm` | GPU, CPU fallback | 4000 | 64 | 0.95 | 1.0 |

Gemma 4 uses a multimodal LiteRT engine configuration:

- main text backend: selected accelerator, usually GPU
- vision backend: selected accelerator, usually GPU
- audio backend: CPU

This matches the LiteRT-LM Android pattern used by Google AI Edge Gallery: GPU can run text and vision while audio decoding uses CPU. If `accelerator` is omitted or set to `auto`, TAI follows the model profile and device policy. You can force GPU for validation:

```sh
tai load gemma-4-e2b-it-litert-lm --gpu
```

A successful GPU load reports:

```text
Backend: GPU
```

### Image Input

Use OpenAI chat content parts with `image_url`. Data URLs, `file://` URLs, absolute paths, and HTTP(S) URLs are accepted.

Example:

```json
{
  "model": "gemma-4-e2b-it-litert-lm",
  "messages": [
    {
      "role": "user",
      "content": [
        {"type": "text", "text": "What color is this image?"},
        {
          "type": "image_url",
          "image_url": {
            "url": "data:image/png;base64,..."
          }
        }
      ]
    }
  ]
}
```

For large images, prefer `file://` or absolute local paths from the Android app's readable storage. The localhost server accepts larger JSON bodies for common base64 image payloads, but file paths avoid copying media through JSON.

### Audio Input

Use OpenAI-style `input_audio` content parts:

```json
{
  "model": "gemma-4-e2b-it-litert-lm",
  "messages": [
    {
      "role": "user",
      "content": [
        {"type": "text", "text": "Transcribe or describe this audio."},
        {
          "type": "input_audio",
          "input_audio": {
            "data": "...base64 wav...",
            "format": "wav"
          }
        }
      ]
    }
  ]
}
```

The runner passes audio bytes to LiteRT-LM. The local API does not implement `/v1/audio/transcriptions` as a separate endpoint; use `/v1/chat/completions` with `input_audio`.

### MobileActions

MobileActions uses the LiteRT-LM runner but has its own profile:

| Model | Accelerator | Max tokens | TopK | TopP | Temperature |
| --- | --- | ---: | ---: | ---: | ---: |
| `functiongemma-270m-mobile-actions-litert-lm` | CPU only | 1024 | 64 | 0.95 | 0.0 |

TAI keeps MobileActions in a separate CPU companion slot when available. Loading a Gemma assistant model can also load MobileActions without evicting the assistant slot.

TAI does not execute Android actions or shell commands by itself. It returns tool calls for the client to handle.

## MNN-LLM Backend

MNN models are installed as a directory containing `config.json` and referenced sidecar files. The load path must point to `config.json`.

TAI validates these sidecars before loading:

- `llm_model`
- `llm_weight`
- `tokenizer_file`

If a referenced file is missing or unreadable, load fails with `model_file_not_readable` and the missing filename.

### MNN Defaults

For installed MNN configs, Auto means "use the model config." TAI preserves config fields unless a request explicitly overrides a supported value.

The tested `qwen2.5-coder-1.5b-instruct-mnn` defaults are:

| Field | Default |
| --- | --- |
| `backend_type` | `cpu` |
| `thread_num` | `4` |
| `precision` | `low` |
| `memory` | `low` |
| `temperature` | `0.8` |
| `top_k` | `40` |
| `top_p` | `0.9` |

TAI also preserves upstream config fields such as tokenizer, sampler type, `min_p`, `typical`, penalties, `n_gram`, and Jinja chat templates.

### MNN Request Overrides

OpenAI request JSON can override supported runtime fields:

```json
{
  "model": "qwen2.5-coder-1.5b-instruct-mnn",
  "temperature": 0.2,
  "top_p": 0.9,
  "top_k": 40,
  "max_tokens": 512,
  "context_window": 4096,
  "thread_count": 4,
  "precision": "low",
  "memory_mode": "low",
  "messages": [{"role": "user", "content": "Reply exactly OK"}]
}
```

If `accelerator` is `auto` or omitted, TAI preserves the MNN config default. It does not force CPU in Java. Explicit `accelerator` can override `backend_type` for supported values.

### MNN Tools

MNN supports OpenAI function tool requests through `/v1/chat/completions`.

The preferred path is a structured-chat native bridge. The current bundled prebuilt MNN native library does not export that bridge, so TAI falls back to a Java-rendered tool prompt and parses common tool-call outputs back into OpenAI `tool_calls`.

For required or named tool choice, TAI guarantees an OpenAI-compatible result:

- valid model-emitted `<tool_call>...</tool_call>` blocks are parsed
- valid bare JSON function-call responses are parsed
- if the MNN fallback model still refuses to emit a call for `tool_choice:"required"` or a named tool, TAI synthesizes a tool call from the selected function schema and the last user message instead of returning prose

Example request:

```json
{
  "model": "qwen2.5-coder-1.5b-instruct-mnn",
  "tool_choice": "required",
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "get_weather",
        "description": "Get weather for a city",
        "parameters": {
          "type": "object",
          "properties": {
            "city": {"type": "string"}
          },
          "required": ["city"]
        }
      }
    }
  ],
  "messages": [
    {"role": "user", "content": "Return only a tool call for get_weather with city Kuwait City."}
  ]
}
```

Expected response shape:

```json
{
  "choices": [
    {
      "finish_reason": "tool_calls",
      "message": {
        "role": "assistant",
        "content": null,
        "tool_calls": [
          {
            "type": "function",
            "function": {
              "name": "get_weather",
              "arguments": "{\"city\":\"Kuwait City\"}"
            }
          }
        ]
      }
    }
  ]
}
```

The client is still responsible for executing the tool and sending the result back as a `role:"tool"` message.

## Endpoint Behavior For Unsupported Modalities

TAI rejects unsupported media instead of dropping it.

Examples:

- MNN image input: `capability_not_supported`
- text-only LiteRT model image input: `capability_not_supported`
- unknown content part type: `unsupported_content_part`
- chat audio output through `modalities:["audio"]`: `unsupported_audio_output`, HTTP 501
- `/v1/audio/speech`: `unsupported_audio_output`, HTTP 501

This behavior is intentional for OpenAI-compatible CLI tools. Silent media dropping makes prompts misleading.

## Validation Commands

Check model metadata:

```sh
endpoint="$(cat ~/.launcherctl/endpoint)"
token="$(cat ~/.launcherctl/token)"
curl -sS -H "Authorization: Bearer $token" "$endpoint/v1/models" | jq .
```

Load Gemma 4 on GPU:

```sh
curl -sS -H "Authorization: Bearer $token" \
  -H "Content-Type: application/json" \
  -d '{"model":"gemma-4-e2b-it-litert-lm","accelerator":"gpu"}' \
  "$endpoint/v1/ai/runtime/load" | jq .
```

Test text chat:

```sh
curl -sS -H "Authorization: Bearer $token" \
  -H "Content-Type: application/json" \
  -d '{"model":"gemma-4-e2b-it-litert-lm","messages":[{"role":"user","content":"Reply exactly OK"}]}' \
  "$endpoint/v1/chat/completions" | jq .
```

Load MNN and inspect effective config:

```sh
curl -sS -H "Authorization: Bearer $token" \
  -H "Content-Type: application/json" \
  -d '{"model":"qwen2.5-coder-1.5b-instruct-mnn"}' \
  "$endpoint/v1/ai/runtime/load" | jq '.effectiveConfig'
```

## References

- [LiteRT-LM Android documentation](https://developers.google.com/edge/litert-lm/android)
- [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery)
- [MNN upstream project](https://github.com/alibaba/MNN)
