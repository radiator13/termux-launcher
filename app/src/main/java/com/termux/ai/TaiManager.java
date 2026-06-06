package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class TaiManager {
    private static TaiManager instance;

    private final Context appContext;
    private final TaiSettings settings;
    private final TaiModelRegistry registry;
    private final TaiModelStore modelStore;
    private final TaiModelDownloader modelDownloader;
    private final TaiRuntime runtime;

    public interface OpenAiStreamSink {
        void onEvent(@NonNull JSONObject event) throws IOException;
        void onDone() throws IOException;
    }

    private TaiManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        settings = new TaiSettings(appContext);
        registry = new TaiModelRegistry();
        modelStore = new TaiModelStore(appContext);
        modelDownloader = new TaiModelDownloader(appContext, modelStore);
        runtime = new DualSlotTaiRuntime(appContext);
    }

    @NonNull
    public static synchronized TaiManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new TaiManager(context);
        }
        return instance;
    }

    @NonNull
    public JSONObject status() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("name", "TAI");
        data.put("displayName", "Termux AI");
        data.put("runtime", runtime.getState().toJson());
        data.put("settings", settings.toJson());
        data.put("appProcessRuntime", true);
        data.put("modelsBundledInApk", false);
        data.put("downloadsRequireExplicitUserAction", true);
        data.put("limitations", currentLimitations());
        appendDeviceCompatibility(data, runtime.getState());
        JSONArray endpoints = new JSONArray();
        endpoints.put("/v1/models");
        endpoints.put("/v1/chat/completions");
        endpoints.put("/v1/completions");
        data.put("openAiCompatibleEndpoints", endpoints);
        return data;
    }

    @NonNull
    public JSONObject runtimeStatus() throws JSONException {
        TaiRuntimeState state = runtime.getState();
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("runtime", state.toJson());
        data.put("settings", settings.toJson());
        data.put("appProcessRuntime", true);
        data.put("backendPolicy", "Auto follows the model's ordered Edge Gallery accelerator allowlist, applies device exclusions, and uses LiteRT-LM initialization as the final backend check.");
        appendDeviceCompatibility(data, state);
        return data;
    }

    @NonNull
    public JSONObject models() throws JSONException {
        JSONObject data = registry.toJson(settings, modelStore.getUserModels());
        data.put("storageDirectory", modelStore.getModelsDirectory().getAbsolutePath());
        data.put("downloads", modelStore.getDownloads());
        data.put("catalog", catalogJson());
        TaiRuntimeState state = runtime.getState();
        data.put("runtime", state.toJson());
        appendDeviceCompatibility(data, state);
        return data;
    }

    @NonNull
    public JSONObject importModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String path = request.optString("path", "").trim();
        if (path.isEmpty()) return error(400, "bad_request", "Missing model path");
        File modelFile = new File(path);
        if (!modelFile.isFile() || !modelFile.canRead()) {
            JSONObject error = error(404, "model_file_not_readable", "Model file does not exist or is not readable by the app process");
            error.put("path", path);
            return error;
        }

        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", modelFile.getName())));
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        TaiModelSpec baseSpec = new TaiModelSpec(
            modelId,
            request.optString("displayName", modelId),
            request.optString("roleHint", "Imported local model"),
            "imported",
            modelFile.getAbsolutePath(),
            request.optString("license", "User-provided model; license accepted externally"),
            modelFile.length(),
            capabilitiesFromRequest(request),
            false
        );
        TaiModelProfile runtimeProfile = TaiModelProfile.fromRequest(request, TaiModelProfile.forModel(baseSpec));
        TaiModelSpec spec = new TaiModelSpec(
            baseSpec.id,
            baseSpec.displayName,
            baseSpec.roleHint,
            baseSpec.source,
            baseSpec.localPath,
            baseSpec.license,
            baseSpec.sizeBytes,
            baseSpec.capabilities,
            false,
            runtimeProfile
        );
        modelStore.upsertUserModel(spec);

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("imported", true);
        data.put("model", spec.toJson());
        data.put("requiresUserApprovedPath", true);
        data.put("copiedIntoAppPrivateStorage", false);
        data.put("message", "Model path registered. Load it with TAI to run through the Android-side LiteRT-LM runtime when the device ABI and model format are supported.");
        return data;
    }

    @NonNull
    public JSONObject downloadModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        boolean acceptedTerms = request.optBoolean("acceptedTerms", false);
        if (!acceptedTerms) {
            JSONObject error = error(403, "terms_not_accepted", "Model downloads require acceptedTerms=true after reviewing provider license/terms");
            error.put("downloadsRequireExplicitUserAction", true);
            error.put("huggingFaceTokenBundled", false);
            return error;
        }
        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        String url = request.optString("url", "").trim();
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        if (url.isEmpty()) return error(400, "bad_request", "Missing download URL");
        JSONObject data = modelDownloader.startDownload(
            modelId,
            url,
            request.optString("displayName", modelId),
            request.optString("license", "User accepted provider terms externally"),
            capabilitiesFromRequest(request),
            request.optString("huggingFaceToken", settings.getHuggingFaceToken())
        );
        data.put("downloadsRequireExplicitUserAction", true);
        data.put("huggingFaceTokenBundled", false);
        return data;
    }

    @NonNull
    public JSONObject downloadCatalogModel(@NonNull String modelId) throws JSONException {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        if (entry == null) return error(404, "model_not_found", "Unknown catalog model: " + modelId);
        if (entry.gated) {
            String token = settings.getHuggingFaceToken();
            if (token.trim().isEmpty()) {
                JSONObject error = error(403, "gated_model_requires_auth", "This model is gated on Hugging Face. Save a Hugging Face token after accepting the model terms.");
                error.put("providerPageUrl", entry.providerPageUrl);
                error.put("downloadUrl", entry.downloadUrl);
                error.put("huggingFaceTokenBundled", false);
                return error;
            }
        }
        return modelDownloader.startDownload(entry.modelId, entry.downloadUrl, entry.displayName, entry.license, entry.capabilities, settings.getHuggingFaceToken());
    }

    @NonNull
    public JSONObject deleteModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        boolean deleted = modelStore.deleteUserModel(modelId);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("deleted", deleted);
        data.put("modelId", modelId);
        return data;
    }

    @NonNull
    public JSONObject downloads() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("downloads", modelStore.getDownloads());
        return data;
    }

    @NonNull
    public JSONObject loadModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown TAI model: " + modelId);
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request);
        JSONObject result = runtime.load(spec, options);
        appendCompanionMobileActions(result, spec.id);
        return result;
    }

    @NonNull
    public JSONObject unloadModel() throws JSONException {
        return runtime.unload();
    }

    @NonNull
    public JSONObject keepWarmRuntime(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        TaiRuntimeState state = runtime.getState();
        String fallbackModel = state.loadedModelId != null ? state.loadedModelId : settings.getDefaultAssistantModel();
        String modelId = requestedModelId(request, fallbackModel);
        TaiModelSpec spec = resolveModel(modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown TAI model: " + modelId);
        int minutes = request.optInt("minutes", request.optInt("keepWarmMinutes", 0));
        if (minutes <= 0) minutes = settings.getIdleUnloadMinutes() > 0 ? settings.getIdleUnloadMinutes() : 30;
        return runtime.keepWarm(spec, runtimeOptionsFromRequest(request), minutes);
    }

    @NonNull
    public JSONObject cancelRuntime() throws JSONException {
        return runtime.cancel();
    }

    @NonNull
    public JSONObject openAiChatCompletions(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        JSONArray messages = request.optJSONArray("messages");
        if (messages == null || messages.length() == 0) return error(400, "bad_request", "Missing messages");

        PromptParts promptParts = promptPartsFromMessages(messages);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request);
        JSONObject loadError = ensureModelLoadedForGeneration(modelId, options);
        if (loadError != null) return openAiError(loadError);

        JSONObject chat = runtime.chat(modelId,
            promptParts.systemPrompt, promptParts.prompt, options);
        if (!chat.optBoolean("ok", false)) {
            return openAiError(chat);
        }

        JSONObject response = new JSONObject();
        response.put("id", "tai-" + System.currentTimeMillis());
        response.put("object", "chat.completion");
        response.put("model", chat.optString("model", settings.getDefaultAssistantModel()));
        response.put("created", System.currentTimeMillis() / 1000L);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        choice.put("finish_reason", "stop");
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("content", chat.optString("response", ""));
        choice.put("message", message);
        choices.put(choice);
        response.put("choices", choices);
        response.put("tai", chat);
        return response;
    }

    @NonNull
    public JSONObject openAiCompletions(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String prompt = promptFromCompletionRequest(request);
        if (prompt.trim().isEmpty()) return openAiError(error(400, "bad_request", "Missing prompt"));

        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request);
        JSONObject loadError = ensureModelLoadedForGeneration(modelId, options);
        if (loadError != null) return openAiError(loadError);

        JSONObject completion = runtime.complete(modelId, prompt, options);
        if (!completion.optBoolean("ok", false)) {
            return openAiError(completion);
        }

        JSONObject response = new JSONObject();
        response.put("id", "tai-cmpl-" + System.currentTimeMillis());
        response.put("object", "text_completion");
        response.put("model", completion.optString("model", modelId));
        response.put("created", System.currentTimeMillis() / 1000L);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("text", completion.optString("response", ""));
        choice.put("index", 0);
        choice.put("finish_reason", "stop");
        choices.put(choice);
        response.put("choices", choices);
        response.put("tai", completion);
        return response;
    }

    public boolean isStreamRequest(@NonNull String body) {
        try {
            return parseBody(body).optBoolean("stream", false);
        } catch (JSONException e) {
            return false;
        }
    }

    public void openAiChatCompletionsStream(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        JSONObject request = parseBody(body);
        JSONArray messages = request.optJSONArray("messages");
        if (messages == null || messages.length() == 0) {
            emitOpenAiError(sink, error(400, "bad_request", "Missing messages"));
            return;
        }

        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request);
        JSONObject loadError = ensureModelLoadedForGeneration(modelId, options);
        if (loadError != null) {
            emitOpenAiError(sink, loadError);
            return;
        }

        PromptParts promptParts = promptPartsFromMessages(messages);
        String id = "tai-chatcmpl-" + System.currentTimeMillis();
        long created = System.currentTimeMillis() / 1000L;
        emitChatChunk(sink, id, created, modelId, "", "assistant", null);

        AtomicReference<IOException> ioError = new AtomicReference<>();
        JSONObject chat = runtime.chat(modelId, promptParts.systemPrompt, promptParts.prompt, options, new TaiGenerationCallback() {
            @Override
            public void onToken(@NonNull String text) {
                if (text.isEmpty() || ioError.get() != null) return;
                try {
                    emitChatChunk(sink, id, created, modelId, text, null, null);
                } catch (IOException e) {
                    ioError.set(e);
                    try {
                        runtime.cancel();
                    } catch (JSONException ignored) {
                    }
                } catch (JSONException e) {
                    ioError.set(new IOException(e));
                    try {
                        runtime.cancel();
                    } catch (JSONException ignored) {
                    }
                }
            }

            @Override
            public void onComplete(@NonNull String fullText) {
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
            }
        });
        if (ioError.get() != null) throw ioError.get();
        if (!chat.optBoolean("ok", false)) {
            emitOpenAiError(sink, chat);
            return;
        }
        emitChatChunk(sink, id, created, modelId, "", null, "stop");
        sink.onDone();
    }

    public void openAiCompletionsStream(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        JSONObject request = parseBody(body);
        String prompt = promptFromCompletionRequest(request);
        if (prompt.trim().isEmpty()) {
            emitOpenAiError(sink, error(400, "bad_request", "Missing prompt"));
            return;
        }

        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request);
        JSONObject loadError = ensureModelLoadedForGeneration(modelId, options);
        if (loadError != null) {
            emitOpenAiError(sink, loadError);
            return;
        }

        String id = "tai-cmpl-" + System.currentTimeMillis();
        long created = System.currentTimeMillis() / 1000L;
        AtomicReference<IOException> ioError = new AtomicReference<>();
        JSONObject completion = runtime.complete(modelId, prompt, options, new TaiGenerationCallback() {
            @Override
            public void onToken(@NonNull String text) {
                if (text.isEmpty() || ioError.get() != null) return;
                try {
                    emitCompletionChunk(sink, id, created, modelId, text, null);
                } catch (IOException e) {
                    ioError.set(e);
                    try {
                        runtime.cancel();
                    } catch (JSONException ignored) {
                    }
                } catch (JSONException e) {
                    ioError.set(new IOException(e));
                    try {
                        runtime.cancel();
                    } catch (JSONException ignored) {
                    }
                }
            }

            @Override
            public void onComplete(@NonNull String fullText) {
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
            }
        });
        if (ioError.get() != null) throw ioError.get();
        if (!completion.optBoolean("ok", false)) {
            emitOpenAiError(sink, completion);
            return;
        }
        emitCompletionChunk(sink, id, created, modelId, "", "stop");
        sink.onDone();
    }

    @NonNull
    public JSONObject openAiModels() throws JSONException {
        JSONObject source = models();
        JSONArray models = source.optJSONArray("models");
        Map<String, JSONObject> dedupedModels = new LinkedHashMap<>();
        if (models != null) {
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.optJSONObject(i);
                if (model == null) continue;
                String id = model.optString("id", "");
                if (id.isEmpty()) continue;
                dedupedModels.put(id, model);
            }
        }
        JSONArray data = new JSONArray();
        for (JSONObject model : dedupedModels.values()) {
            JSONObject item = new JSONObject();
            item.put("id", model.optString("id", ""));
            item.put("object", "model");
            item.put("created", 0);
            item.put("owned_by", "termux-launcher");
            item.put("tai", model);
            data.put(item);
        }

        JSONObject response = new JSONObject();
        response.put("object", "list");
        response.put("data", data);
        response.put("tai", source);
        return response;
    }

    @NonNull
    private JSONObject parseBody(@NonNull String body) throws JSONException {
        if (body.trim().isEmpty()) return new JSONObject();
        return new JSONObject(body);
    }

    @Nullable
    private JSONObject ensureModelLoadedForGeneration(@NonNull String modelId, @NonNull TaiRuntimeOptions options) throws JSONException {
        if (runtime.isModelLoaded(modelId)) return null;
        TaiModelSpec spec = resolveModel(modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown TAI model: " + modelId);
        JSONObject load = runtime.load(spec, options);
        appendCompanionMobileActions(load, spec.id);
        if (!load.optBoolean("ok", false)) return load;
        return null;
    }

    private void appendCompanionMobileActions(@NonNull JSONObject result, @NonNull String loadedModelId) throws JSONException {
        if (!result.optBoolean("ok", false)) return;
        if (TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M.equals(loadedModelId)) return;
        if (runtime.isModelLoaded(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M)) return;
        TaiModelSpec mobileActions = resolveModel(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M);
        if (mobileActions == null || mobileActions.localPath == null || mobileActions.localPath.trim().isEmpty()) return;
        TaiRuntimeOptions companionOptions = settings.getRuntimeOptions().withAccelerator("cpu");
        JSONObject companion = runtime.load(mobileActions, companionOptions);
        result.put("companionMobileActions", companion);
    }

    @NonNull
    private TaiRuntimeOptions runtimeOptionsFromRequest(@NonNull JSONObject request) {
        TaiRuntimeOptions options = settings.getRuntimeOptions();
        Integer maxTokens = integerOverride(request, "max_tokens", integerOverride(request, "max_completion_tokens", null));
        Integer topK = integerOverride(request, "top_k", null);
        Double topP = doubleOverride(request, "top_p", null);
        Double temperature = doubleOverride(request, "temperature", null);
        String accelerator = null;
        if (request.has("accelerator") && !request.isNull("accelerator")) {
            String value = request.optString("accelerator", "").trim();
            accelerator = value.isEmpty() || "auto".equalsIgnoreCase(value) ? "auto" : value;
        }
        Boolean thinking = booleanOverride(request, "thinking");
        Boolean speculative = booleanOverride(request, "speculative_decoding");
        return options.withGenerationOverrides(maxTokens, topK, topP, temperature, accelerator, thinking, speculative);
    }

    @Nullable
    private Integer integerOverride(@NonNull JSONObject request, @NonNull String key, @Nullable Integer fallback) {
        if (!request.has(key) || request.isNull(key)) return fallback;
        try {
            return request.getInt(key);
        } catch (JSONException e) {
            return fallback;
        }
    }

    @Nullable
    private Double doubleOverride(@NonNull JSONObject request, @NonNull String key, @Nullable Double fallback) {
        if (!request.has(key) || request.isNull(key)) return fallback;
        try {
            return request.getDouble(key);
        } catch (JSONException e) {
            return fallback;
        }
    }

    @Nullable
    private Boolean booleanOverride(@NonNull JSONObject request, @NonNull String key) {
        if (!request.has(key) || request.isNull(key)) return null;
        return request.optBoolean(key);
    }

    @NonNull
    private String requestedModelId(@NonNull JSONObject request, @NonNull String fallback) {
        String model = request.optString("model", request.optString("modelId", fallback));
        return model == null || model.trim().isEmpty() ? fallback : model.trim();
    }

    @Nullable
    private TaiModelSpec resolveModel(@Nullable String modelId) {
        TaiModelSpec spec = modelStore.getUserModel(modelId);
        if (spec == null) spec = registry.getModel(modelId);
        return spec;
    }

    @NonNull
    private JSONObject error(int statusCode, String code, String message) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", false);
        data.put("error", code);
        data.put("message", message);
        data.put("_statusCode", statusCode);
        return data;
    }

    @NonNull
    private JSONObject openAiError(@NonNull JSONObject source) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", source.optString("message", "TAI request failed"));
        error.put("type", "invalid_request_error");
        error.put("code", source.optString("error", "tai_error"));

        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("tai", source);
        response.put("_statusCode", source.optInt("_statusCode", 500));
        return response;
    }

    private void emitOpenAiError(@NonNull OpenAiStreamSink sink, @NonNull JSONObject source) throws JSONException, IOException {
        sink.onEvent(openAiError(source));
        sink.onDone();
    }

    private void emitChatChunk(
        @NonNull OpenAiStreamSink sink,
        @NonNull String id,
        long created,
        @NonNull String model,
        @NonNull String content,
        @Nullable String role,
        @Nullable String finishReason
    ) throws JSONException, IOException {
        JSONObject response = new JSONObject();
        response.put("id", id);
        response.put("object", "chat.completion.chunk");
        response.put("created", created);
        response.put("model", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        JSONObject delta = new JSONObject();
        if (role != null) delta.put("role", role);
        if (!content.isEmpty()) delta.put("content", content);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason == null ? JSONObject.NULL : finishReason);
        choices.put(choice);
        response.put("choices", choices);
        sink.onEvent(response);
    }

    private void emitCompletionChunk(
        @NonNull OpenAiStreamSink sink,
        @NonNull String id,
        long created,
        @NonNull String model,
        @NonNull String text,
        @Nullable String finishReason
    ) throws JSONException, IOException {
        JSONObject response = new JSONObject();
        response.put("id", id);
        response.put("object", "text_completion");
        response.put("created", created);
        response.put("model", model);
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("text", text);
        choice.put("index", 0);
        choice.put("finish_reason", finishReason == null ? JSONObject.NULL : finishReason);
        choices.put(choice);
        response.put("choices", choices);
        sink.onEvent(response);
    }

    @NonNull
    private JSONArray currentLimitations() {
        JSONArray limitations = new JSONArray();
        limitations.put("LiteRT-LM text inference is integrated for downloaded/imported .litertlm models on supported 64-bit ABIs.");
        limitations.put("Auto follows Edge Gallery model accelerator allowlists, minimum-memory metadata, and Pixel 10 GPU exclusion; LiteRT-LM initialization remains the final backend check.");
        limitations.put("Streaming text responses, cancellation, and keep-warm lifecycle controls are available through the localhost API.");
        limitations.put("Benchmark counters and multimodal input are TODO for a later phase.");
        limitations.put("TAI does not execute shell commands or device actions; use dedicated shell tools and future explicit Android capability APIs.");
        return limitations;
    }

    @NonNull
    private PromptParts promptPartsFromMessages(@NonNull JSONArray messages) {
        StringBuilder prompt = new StringBuilder();
        String systemPrompt = settings.getGeneralSystemPrompt();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String role = message.optString("role", "user");
            String content = messageContentToText(message.opt("content"));
            if ("system".equals(role)) {
                systemPrompt = content;
            } else {
                prompt.append(role).append(": ").append(content).append('\n');
            }
        }
        return new PromptParts(systemPrompt, prompt.toString().trim());
    }

    @NonNull
    private String promptFromCompletionRequest(@NonNull JSONObject request) {
        Object prompt = request.opt("prompt");
        if (prompt == null || JSONObject.NULL.equals(prompt)) return "";
        if (prompt instanceof JSONArray) {
            JSONArray array = (JSONArray) prompt;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(String.valueOf(array.opt(i)));
            }
            return builder.toString();
        }
        return String.valueOf(prompt);
    }

    @NonNull
    private String messageContentToText(@Nullable Object content) {
        if (content == null || JSONObject.NULL.equals(content)) return "";
        if (content instanceof JSONArray) {
            JSONArray array = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    if ("text".equals(object.optString("type", ""))) {
                        builder.append(object.optString("text", ""));
                    }
                } else if (item != null && !JSONObject.NULL.equals(item)) {
                    builder.append(String.valueOf(item));
                }
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }

    @NonNull
    private LinkedHashSet<String> capabilitiesFromRequest(@NonNull JSONObject request) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        JSONArray array = request.optJSONArray("capabilities");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String capability = array.optString(i, "");
                if (!capability.isEmpty()) capabilities.add(capability);
            }
        }
        if (capabilities.isEmpty()) capabilities.add("text_chat");
        return capabilities;
    }

    @NonNull
    private String sanitizeModelId(@NonNull String value) {
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "-");
    }

    @NonNull
    private JSONArray catalogJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            JSONObject json = new JSONObject();
            json.put("modelId", entry.modelId);
            json.put("displayName", entry.displayName);
            json.put("roleHint", entry.roleHint);
            json.put("providerPageUrl", entry.providerPageUrl);
            json.put("downloadUrl", entry.downloadUrl);
            json.put("license", entry.license);
            json.put("sizeBytes", entry.sizeBytes);
            json.put("gated", entry.gated);
            TaiModelSpec catalogSpec = registry.getModel(entry.modelId);
            if (catalogSpec != null) json.put("runtimeProfile", TaiModelProfile.forModel(catalogSpec).toJson());
            JSONArray capabilities = new JSONArray();
            for (String capability : entry.capabilities) capabilities.put(capability);
            json.put("capabilities", capabilities);
            array.put(json);
        }
        return array;
    }

    private void appendDeviceCompatibility(@NonNull JSONObject data, @NonNull TaiRuntimeState state) throws JSONException {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(appContext);
        data.put("device", device.toJson());
        if (state.loadedModelId == null) return;
        TaiModelSpec model = resolveModel(state.loadedModelId);
        if (model == null) return;
        TaiModelProfile profile = TaiModelProfile.forModel(model);
        data.put("modelProfile", profile.toJson());
        JSONArray warnings = new JSONArray();
        String memoryWarning = device.memoryWarning(profile);
        if (memoryWarning != null) warnings.put(memoryWarning);
        data.put("compatibilityWarnings", warnings);
    }

    private static final class PromptParts {
        final String systemPrompt;
        final String prompt;

        PromptParts(@NonNull String systemPrompt, @NonNull String prompt) {
            this.systemPrompt = systemPrompt;
            this.prompt = prompt;
        }
    }
}
