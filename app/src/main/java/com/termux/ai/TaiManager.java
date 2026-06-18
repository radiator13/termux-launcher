package com.termux.ai;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.OpenApiTool;
import com.google.ai.edge.litertlm.ToolCall;
import com.google.ai.edge.litertlm.ToolKt;
import com.google.ai.edge.litertlm.ToolProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class TaiManager {
    private static TaiManager instance;
    private static final int MAX_MEDIA_BYTES = 25 * 1024 * 1024;

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
        TaiRemoteCatalog.loadCached(appContext);
        Thread catalogRefresh = new Thread(() -> TaiRemoteCatalog.refresh(appContext), "tai-catalog-refresh");
        catalogRefresh.setDaemon(true);
        catalogRefresh.start();
        settings = new TaiSettings(appContext);
        registry = new TaiModelRegistry();
        modelStore = new TaiModelStore(appContext);
        modelDownloader = new TaiModelDownloader(appContext, modelStore);
        runtime = new MultiBackendTaiRuntime(appContext);
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
        endpoints.put("/v1/embeddings");
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
        data.put("backendPolicy", "LiteRT-LM uses the selected accelerator policy with runtime fallback where supported.");
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
    public JSONObject importModelDocument(@NonNull Uri uri, @Nullable String modelId) throws JSONException {
        return new TaiModelImporter(appContext, modelStore).importDocument(uri, modelId);
    }

    @NonNull
    public TaiModelImporter.DocumentMetadata modelDocumentMetadata(@NonNull Uri uri) {
        return new TaiModelImporter(appContext, modelStore).readMetadata(uri);
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
        if (!entry.downloadAvailable) {
            JSONObject error = error(409, "catalog_download_unavailable", entry.unavailableReason);
            error.put("providerPageUrl", entry.providerPageUrl);
            error.put("downloadAvailable", false);
            error.put("unavailableReason", entry.unavailableReason);
            return error;
        }
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
        return modelDownloader.startCatalogDownload(entry, settings.getHuggingFaceToken());
    }

    @NonNull
    public JSONObject deleteModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        TaiRuntimeState state = runtime.getState();
        boolean activeModelLoaded = state.loadedModelId != null && state.loadedModelId.equals(modelId);
        TaiModelStore.DeleteResult deleteResult = modelStore.deleteUserModel(modelId,
            activeModelLoaded, request.optBoolean("confirm", false));
        if (!deleteResult.ok) return error(409, deleteResult.errorCode, deleteResult.message);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("deleted", deleteResult.deleted);
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
    public JSONObject cancelDownload(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        TaiModelDownloadService.requestCancel(modelId);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("modelId", modelId);
        data.put("cancellationRequested", true);
        return data;
    }

    @NonNull
    public JSONObject loadModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown TAI model: " + modelId);
        String requestedBackend = request.optString("backend", "").trim();
        if (!requestedBackend.isEmpty() && !requestedBackend.equalsIgnoreCase(spec.backend)) {
            return error(409, "backend_mismatch", "Model " + modelId + " requires backend " + spec.backend + ".");
        }
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
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
        return runtime.keepWarm(spec, runtimeOptionsFromRequest(request, spec), minutes);
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

        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        TaiModelSpec spec = resolveModel(modelId);
        if (spec == null) return openAiError(error(404, "model_not_found", "Unknown TAI model: " + modelId));
        JSONObject audioOutputError = unsupportedAudioOutputRequest(request);
        if (audioOutputError != null) return openAiError(audioOutputError);
        OpenAiChatRequest chatRequest;
        try {
            chatRequest = openAiChatRequest(request, messages, spec);
        } catch (JSONException e) {
            return openAiError(chatRequestError(e));
        }
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
        JSONObject loadError = ensureModelLoadedForGeneration(modelId, options);
        if (loadError != null) return openAiError(loadError);

        JSONObject chat = runtime.chat(modelId, chatRequest.request, options);
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
        JSONArray toolCalls = chat.optJSONArray("toolCalls");
        boolean hasToolCalls = toolCalls != null && toolCalls.length() > 0;
        choice.put("finish_reason", hasToolCalls ? "tool_calls" : "stop");
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("content", hasToolCalls && chat.optString("response", "").isEmpty()
            ? JSONObject.NULL : chat.optString("response", ""));
        if (hasToolCalls) message.put("tool_calls", toolCalls);
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
        TaiModelSpec spec = resolveModel(modelId);
        if (spec == null) return openAiError(error(404, "model_not_found", "Unknown TAI model: " + modelId));
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
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

    @NonNull
    public JSONObject openAiAudioSpeech(@NonNull String body) throws JSONException {
        parseBody(body);
        return openAiError(error(501, "unsupported_audio_output",
            "Audio output is not available from the local LiteRT-LM or MNN runners. "
                + "Use text responses or a separate text-to-speech backend."));
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
        TaiModelSpec spec = resolveModel(modelId);
        if (spec == null) {
            emitOpenAiError(sink, error(404, "model_not_found", "Unknown TAI model: " + modelId));
            return;
        }
        JSONObject audioOutputError = unsupportedAudioOutputRequest(request);
        if (audioOutputError != null) {
            emitOpenAiError(sink, audioOutputError);
            return;
        }
        OpenAiChatRequest chatRequest;
        try {
            chatRequest = openAiChatRequest(request, messages, spec);
        } catch (JSONException e) {
            emitOpenAiError(sink, chatRequestError(e));
            return;
        }
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
        JSONObject loadError = ensureModelLoadedForGeneration(modelId, options);
        if (loadError != null) {
            emitOpenAiError(sink, loadError);
            return;
        }
        String id = "tai-chatcmpl-" + System.currentTimeMillis();
        long created = System.currentTimeMillis() / 1000L;
        emitChatChunk(sink, id, created, modelId, "", "assistant", null);

        AtomicReference<IOException> ioError = new AtomicReference<>();
        AtomicReference<Boolean> emittedToolCalls = new AtomicReference<>(false);
        JSONObject chat = runtime.chat(modelId, chatRequest.request, options, new TaiGenerationCallback() {
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
            public void onToolCalls(@NonNull JSONArray toolCalls) {
                if (toolCalls.length() == 0 || ioError.get() != null) return;
                try {
                    emitToolCallChunk(sink, id, created, modelId, toolCalls);
                    emittedToolCalls.set(true);
                } catch (IOException | JSONException e) {
                    ioError.set(e instanceof IOException ? (IOException) e : new IOException(e));
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
        JSONArray toolCalls = chat.optJSONArray("toolCalls");
        boolean hasToolCalls = toolCalls != null && toolCalls.length() > 0;
        if (hasToolCalls && !emittedToolCalls.get()) {
            emitToolCallChunk(sink, id, created, modelId, toolCalls);
        }
        emitChatChunk(sink, id, created, modelId, "", null, hasToolCalls ? "tool_calls" : "stop");
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
        TaiModelSpec spec = resolveModel(modelId);
        if (spec == null) {
            emitOpenAiError(sink, error(404, "model_not_found", "Unknown TAI model: " + modelId));
            return;
        }
        TaiRuntimeOptions options = runtimeOptionsFromRequest(request, spec);
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
        JSONObject installed = new JSONObject();
        JSONArray models = new JSONArray();
        boolean mnnSupported = TaiDeviceCapabilities.detect(appContext).mnnSupported;
        LinkedHashMap<String, TaiModelSpec> availableModels = new LinkedHashMap<>();
        availableModels.putAll(modelStore.getDownloadedReadableModels());
        availableModels.putAll(modelStore.getInstalledUserModels());
        for (TaiModelSpec spec : availableModels.values()) {
            if (TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend) && !mnnSupported) continue;
            models.put(spec.toJson());
        }
        installed.put("models", models);
        return openAiModelsFromTaiModels(installed);
    }

    @NonNull
    static JSONObject openAiModelsFromTaiModels(@NonNull JSONObject source) throws JSONException {
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
            String backend = model.optString("backend", TaiModelSpec.BACKEND_LITERT_LM);
            item.put("_backend", backend);
            JSONArray capabilities = model.optJSONArray("capabilities");
            if (capabilities == null || capabilities.length() == 0) {
                capabilities = new JSONArray();
                capabilities.put(TaiModelSpec.CAPABILITY_TEXT_CHAT);
            }
            item.put("_capabilities", openAiEndpointCapabilities(capabilities, backend));
            data.put(item);
        }

        JSONObject response = new JSONObject();
        response.put("object", "list");
        response.put("data", data);
        return response;
    }

    @NonNull
    public JSONObject embeddings(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = requestedModelId(request, settings.getDefaultAssistantModel());
        String input = request.optString("input", "");
        TaiModelSpec spec = resolveModel(modelId);
        if (spec == null) {
            JSONObject error = new JSONObject();
            error.put("message", "Unknown TAI model: " + modelId);
            error.put("type", "invalid_request_error");
            error.put("param", "model");
            error.put("code", "model_not_found");
            JSONObject response = new JSONObject();
            response.put("error", error);
            response.put("_statusCode", 404);
            return response;
        }
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(spec.backend)) {
            JSONObject error = new JSONObject();
            error.put("message", "Embeddings are not supported for model '" + modelId + "'.");
            error.put("type", "invalid_request_error");
            error.put("param", "model");
            error.put("code", "capability_not_supported");
            JSONObject response = new JSONObject();
            response.put("error", error);
            response.put("_statusCode", 501);
            return response;
        }
        return ((MultiBackendTaiRuntime) runtime).embed(modelId, input);
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
        TaiRuntimeOptions companionOptions = settings.getRuntimeOptions(mobileActions).withAccelerator("cpu");
        JSONObject companion = runtime.load(mobileActions, companionOptions);
        result.put("companionMobileActions", companion);
    }

    @NonNull
    private TaiRuntimeOptions runtimeOptionsFromRequest(@NonNull JSONObject request, @NonNull TaiModelSpec spec) {
        TaiRuntimeOptions options = settings.getRuntimeOptions(spec);
        Integer maxTokens = integerOverride(request, "max_tokens", integerOverride(request, "max_completion_tokens", null));
        Integer topK = integerOverride(request, "top_k", null);
        Double topP = doubleOverride(request, "top_p", null);
        Double temperature = doubleOverride(request, "temperature", null);
        Integer contextWindow = integerOverride(request, "context_window", null);
        Integer threadCount = integerOverride(request, "thread_count", null);
        String precision = stringOverride(request, "precision");
        String memoryMode = stringOverride(request, "memory_mode");
        String accelerator = null;
        if (request.has("accelerator") && !request.isNull("accelerator")) {
            String value = request.optString("accelerator", "").trim();
            accelerator = value.isEmpty() || "auto".equalsIgnoreCase(value) ? "auto" : value;
        }
        Boolean thinking = booleanOverride(request, "thinking");
        Boolean speculative = booleanOverride(request, "speculative_decoding");
        return options.withGenerationOverrides(maxTokens, topK, topP, temperature, accelerator,
            contextWindow, threadCount, precision, memoryMode, thinking, speculative);
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

    @Nullable
    private String stringOverride(@NonNull JSONObject request, @NonNull String key) {
        if (!request.has(key) || request.isNull(key)) return null;
        String value = request.optString(key, "").trim();
        return value.isEmpty() ? null : value;
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

    @NonNull
    private JSONObject chatRequestError(@NonNull JSONException e) throws JSONException {
        String message = e.getMessage() == null ? "Invalid chat request" : e.getMessage();
        if (message.startsWith("unsupported_content_part:")) {
            String type = message.substring("unsupported_content_part:".length());
            return error(400, "unsupported_content_part", "Unsupported OpenAI content part: " + type);
        }
        if (message.startsWith("capability_not_supported:")) {
            String detail = message.substring("capability_not_supported:".length());
            return error(400, "capability_not_supported", detail);
        }
        if (message.startsWith("media_fetch_failed:")) {
            String detail = message.substring("media_fetch_failed:".length());
            return error(400, "media_fetch_failed", detail);
        }
        return error(400, "invalid_chat_request", message);
    }

    @Nullable
    private JSONObject unsupportedAudioOutputRequest(@NonNull JSONObject request) throws JSONException {
        JSONArray modalities = request.optJSONArray("modalities");
        if (modalities != null) {
            for (int i = 0; i < modalities.length(); i++) {
                if ("audio".equalsIgnoreCase(modalities.optString(i, ""))) {
                    return error(501, "unsupported_audio_output",
                        "Audio output is not available from the local LiteRT/MNN chat runtimes.");
                }
            }
        }
        if (request.has("audio") && !request.isNull("audio")) {
            return error(501, "unsupported_audio_output",
                "Audio output is not available from the local LiteRT/MNN chat runtimes.");
        }
        return null;
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

    private void emitToolCallChunk(
        @NonNull OpenAiStreamSink sink,
        @NonNull String id,
        long created,
        @NonNull String model,
        @NonNull JSONArray toolCalls
    ) throws JSONException, IOException {
        JSONObject response = new JSONObject();
        response.put("id", id);
        response.put("object", "chat.completion.chunk");
        response.put("created", created);
        response.put("model", model);
        JSONObject delta = new JSONObject();
        JSONArray deltaCalls = new JSONArray();
        for (int i = 0; i < toolCalls.length(); i++) {
            JSONObject source = toolCalls.optJSONObject(i);
            if (source == null) continue;
            JSONObject call = new JSONObject(source.toString());
            call.put("index", i);
            deltaCalls.put(call);
        }
        delta.put("tool_calls", deltaCalls);
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", JSONObject.NULL);
        response.put("choices", new JSONArray().put(choice));
        sink.onEvent(response);
    }

    @NonNull
    private JSONArray currentLimitations() {
        JSONArray limitations = new JSONArray();
        limitations.put("LiteRT-LM inference is available on supported device ABI builds.");
        limitations.put("Auto follows Edge Gallery model accelerator allowlists, minimum-memory metadata, and Pixel 10 GPU exclusion; LiteRT-LM initialization remains the final backend check.");
        limitations.put("Streaming text responses, cancellation, and keep-warm lifecycle controls are available through the localhost API.");
        limitations.put("LiteRT-LM image and audio input are accepted for models that declare those capabilities.");
        limitations.put("Audio output is not available from the local LiteRT-LM or MNN runners.");
        limitations.put("OpenAI function tools are returned for client-side execution; TAI does not automatically execute shell commands or device actions.");
        return limitations;
    }

    @NonNull
    private OpenAiChatRequest openAiChatRequest(
        @NonNull JSONObject request,
        @NonNull JSONArray messages,
        @NonNull TaiModelSpec spec
    ) throws JSONException {
        StringBuilder clientSystemPrompt = new StringBuilder();
        List<Message> conversationMessages = new ArrayList<>();
        Map<String, String> toolNamesByCallId = new LinkedHashMap<>();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String role = message.optString("role", "user");
            if ("system".equals(role) || "developer".equals(role)) {
                String content = messageContentToText(message.opt("content"));
                if (!content.isEmpty()) {
                    if (clientSystemPrompt.length() > 0) clientSystemPrompt.append('\n');
                    clientSystemPrompt.append(content);
                }
                continue;
            }
            if ("assistant".equals(role)) {
                String content = messageContentToText(message.opt("content"));
                List<ToolCall> calls = toolCallsFromAssistant(message, toolNamesByCallId);
                conversationMessages.add(Message.Companion.model(
                    Contents.Companion.of(content), calls, Collections.emptyMap()));
            } else if ("tool".equals(role)) {
                String callId = message.optString("tool_call_id", "");
                String toolName = message.optString("name", toolNamesByCallId.get(callId));
                if (toolName == null || toolName.isEmpty()) toolName = "tool";
                Object response = jsonCompatibleValue(message.opt("content"));
                conversationMessages.add(Message.Companion.tool(
                    Contents.Companion.of(new Content.ToolResponse(toolName, response))));
            } else {
                conversationMessages.add(Message.Companion.user(messageContentToContents(message.opt("content"), spec)));
            }
        }
        if (conversationMessages.isEmpty()) {
            throw new JSONException("Chat request has no user, assistant, or tool messages");
        }

        String systemPrompt = clientSystemPrompt.length() > 0
            ? clientSystemPrompt.toString() : settings.getSystemPrompt(spec.id);
        JSONArray toolsJson = request.optJSONArray("tools");
        List<ToolProvider> tools = toolProviders(toolsJson, request.opt("tool_choice"));
        systemPrompt = applyToolChoiceInstruction(systemPrompt, request.opt("tool_choice"), toolsJson);

        Message finalMessage = conversationMessages.remove(conversationMessages.size() - 1);
        if (finalMessage.getRole() != com.google.ai.edge.litertlm.Role.USER
            && finalMessage.getRole() != com.google.ai.edge.litertlm.Role.TOOL) {
            throw new JSONException("The final chat message must have role user or tool");
        }
        return new OpenAiChatRequest(new TaiChatRequest(
            systemPrompt, conversationMessages, finalMessage, tools, false,
            messages, toolsJson, request.opt("tool_choice")));
    }

    @NonNull
    static List<ToolProvider> toolProviders(@Nullable JSONArray tools, @Nullable Object toolChoice) throws JSONException {
        if (tools == null || tools.length() == 0 || "none".equals(String.valueOf(toolChoice))) {
            return Collections.emptyList();
        }
        List<ToolProvider> providers = new ArrayList<>();
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool == null || !"function".equals(tool.optString("type", ""))) {
                throw new JSONException("Only OpenAI function tools are supported");
            }
            JSONObject function = tool.optJSONObject("function");
            if (function == null || function.optString("name", "").isEmpty()) {
                throw new JSONException("Each function tool requires a name");
            }
            JSONObject liteRtDescription = new JSONObject();
            liteRtDescription.put("name", function.getString("name"));
            if (function.has("description")) liteRtDescription.put("description", function.optString("description", ""));
            if (function.has("parameters")) liteRtDescription.put("parameters", function.opt("parameters"));
            String description = liteRtDescription.toString();
            providers.add(ToolKt.tool(new OpenApiTool() {
                @NonNull
                @Override
                public String getToolDescriptionJsonString() {
                    return description;
                }

                @NonNull
                @Override
                public String execute(@NonNull String paramsJsonString) {
                    throw new UnsupportedOperationException("TAI uses client-side tool execution.");
                }
            }));
        }
        if (providers.isEmpty()) throw new JSONException("No valid function tools were provided");
        return providers;
    }

    @NonNull
    static List<ToolCall> toolCallsFromAssistant(
        @NonNull JSONObject message,
        @NonNull Map<String, String> toolNamesByCallId
    ) throws JSONException {
        JSONArray calls = message.optJSONArray("tool_calls");
        if (calls == null) return Collections.emptyList();
        List<ToolCall> output = new ArrayList<>();
        for (int i = 0; i < calls.length(); i++) {
            JSONObject call = calls.optJSONObject(i);
            JSONObject function = call == null ? null : call.optJSONObject("function");
            if (function == null) continue;
            String name = function.optString("name", "");
            if (name.isEmpty()) continue;
            String callId = call.optString("id", "");
            if (!callId.isEmpty()) toolNamesByCallId.put(callId, name);
            Object argumentsValue = function.opt("arguments");
            JSONObject arguments;
            if (argumentsValue instanceof JSONObject) {
                arguments = (JSONObject) argumentsValue;
            } else {
                String argumentsText = argumentsValue == null ? "{}" : String.valueOf(argumentsValue);
                arguments = argumentsText.trim().isEmpty() ? new JSONObject() : new JSONObject(argumentsText);
            }
            output.add(new ToolCall(name, jsonObjectToMap(arguments)));
        }
        return output;
    }

    @NonNull
    static String applyToolChoiceInstruction(
        @NonNull String systemPrompt,
        @Nullable Object toolChoice,
        @Nullable JSONArray tools
    ) {
        if (toolChoice == null || JSONObject.NULL.equals(toolChoice) || tools == null || tools.length() == 0) {
            return systemPrompt;
        }
        if ("required".equals(String.valueOf(toolChoice))) {
            return systemPrompt + "\nYou must call one of the provided tools for this response.";
        }
        if (toolChoice instanceof JSONObject) {
            JSONObject function = ((JSONObject) toolChoice).optJSONObject("function");
            String name = function == null ? "" : function.optString("name", "");
            if (!name.isEmpty()) return systemPrompt + "\nYou must call the provided tool named " + name + ".";
        }
        return systemPrompt;
    }

    @NonNull
    static Map<String, Object> jsonObjectToMap(@NonNull JSONObject object) {
        Map<String, Object> map = new LinkedHashMap<>();
        java.util.Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            map.put(key, jsonCompatibleValue(object.opt(key)));
        }
        return map;
    }

    @Nullable
    static Object jsonCompatibleValue(@Nullable Object value) {
        if (value == null || JSONObject.NULL.equals(value)) return null;
        if (value instanceof JSONObject) return jsonObjectToMap((JSONObject) value);
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) list.add(jsonCompatibleValue(array.opt(i)));
            return list;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
                try {
                    return text.startsWith("{")
                        ? jsonObjectToMap(new JSONObject(text))
                        : jsonCompatibleValue(new JSONArray(text));
                } catch (JSONException ignored) {
                }
            }
        }
        return value;
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
    private String messageContentToText(@Nullable Object content) throws JSONException {
        if (content == null || JSONObject.NULL.equals(content)) return "";
        if (content instanceof JSONArray) {
            JSONArray array = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    String type = object.optString("type", "");
                    if ("text".equals(type)) {
                        builder.append(object.optString("text", ""));
                    } else if ("image_url".equals(type) || "input_image".equals(type)
                        || "audio".equals(type) || "input_audio".equals(type)) {
                        throw new JSONException("unsupported_content_part:" + type);
                    } else if (!type.isEmpty()) {
                        throw new JSONException("unsupported_content_part:" + type);
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
    static Contents messageContentToContents(@Nullable Object content, @NonNull TaiModelSpec spec) throws JSONException {
        if (content == null || JSONObject.NULL.equals(content)) return Contents.Companion.of("");
        if (!(content instanceof JSONArray)) return Contents.Companion.of(String.valueOf(content));
        JSONArray array = (JSONArray) content;
        ArrayList<Content> contents = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof JSONObject) {
                JSONObject object = (JSONObject) item;
                String type = object.optString("type", "");
                if ("text".equals(type)) {
                    contents.add(new Content.Text(object.optString("text", "")));
                } else if ("image_url".equals(type) || "input_image".equals(type)) {
                    if (!TaiModelSpec.BACKEND_LITERT_LM.equals(spec.backend)
                        || !spec.capabilities.contains("image_input")) {
                        throw new JSONException("capability_not_supported:Model " + spec.id + " does not support image input through this endpoint.");
                    }
                    contents.add(imageContent(object));
                } else if ("input_audio".equals(type) || "audio".equals(type)) {
                    if (!TaiModelSpec.BACKEND_LITERT_LM.equals(spec.backend)
                        || !spec.capabilities.contains("audio_input")) {
                        throw new JSONException("capability_not_supported:Model " + spec.id + " does not support audio input through this endpoint.");
                    }
                    contents.add(audioContent(object));
                } else if (!type.isEmpty()) {
                    throw new JSONException("unsupported_content_part:" + type);
                }
            } else if (item != null && !JSONObject.NULL.equals(item)) {
                contents.add(new Content.Text(String.valueOf(item)));
            }
        }
        if (contents.isEmpty()) contents.add(new Content.Text(""));
        return Contents.Companion.of(contents);
    }

    @NonNull
    private static Content imageContent(@NonNull JSONObject part) throws JSONException {
        Object imageUrlValue = part.opt("image_url");
        String url = "";
        if (imageUrlValue instanceof JSONObject) {
            url = ((JSONObject) imageUrlValue).optString("url", "");
        } else if (imageUrlValue != null && !JSONObject.NULL.equals(imageUrlValue)) {
            url = String.valueOf(imageUrlValue);
        }
        if (url.trim().isEmpty()) url = part.optString("image_url", "");
        if (url.trim().isEmpty()) throw new JSONException("unsupported_content_part:image_url");
        return contentFromUrl(url, true);
    }

    @NonNull
    private static Content audioContent(@NonNull JSONObject part) throws JSONException {
        JSONObject inputAudio = part.optJSONObject("input_audio");
        if (inputAudio == null) inputAudio = part.optJSONObject("audio");
        if (inputAudio != null) {
            String data = inputAudio.optString("data", "");
            if (!data.trim().isEmpty()) {
                return new Content.AudioBytes(decodeBase64(data, "input_audio"));
            }
            String url = inputAudio.optString("url", "");
            if (!url.trim().isEmpty()) return contentFromUrl(url, false);
        }
        String url = part.optString("audio_url", "");
        if (!url.trim().isEmpty()) return contentFromUrl(url, false);
        throw new JSONException("unsupported_content_part:input_audio");
    }

    @NonNull
    private static Content contentFromUrl(@NonNull String rawUrl, boolean image) throws JSONException {
        String url = rawUrl.trim();
        if (url.startsWith("data:")) {
            return image
                ? new Content.ImageBytes(decodeDataUrl(url, "image_url"))
                : new Content.AudioBytes(decodeDataUrl(url, "input_audio"));
        }
        if (url.startsWith("file://")) {
            String path = Uri.parse(url).getPath();
            if (path == null || path.trim().isEmpty()) throw new JSONException("media_fetch_failed:Empty file URL");
            return image ? new Content.ImageFile(path) : new Content.AudioFile(path);
        }
        if (url.startsWith("/")) {
            return image ? new Content.ImageFile(url) : new Content.AudioFile(url);
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            byte[] bytes = fetchMedia(url);
            return image ? new Content.ImageBytes(bytes) : new Content.AudioBytes(bytes);
        }
        throw new JSONException("media_fetch_failed:Unsupported media URL scheme");
    }

    @NonNull
    private static byte[] decodeDataUrl(@NonNull String dataUrl, @NonNull String partName) throws JSONException {
        int comma = dataUrl.indexOf(',');
        if (comma < 0) throw new JSONException("media_fetch_failed:Malformed data URL for " + partName);
        String metadata = dataUrl.substring(0, comma).toLowerCase(Locale.ROOT);
        if (!metadata.contains(";base64")) {
            throw new JSONException("media_fetch_failed:Only base64 data URLs are supported for " + partName);
        }
        return decodeBase64(dataUrl.substring(comma + 1), partName);
    }

    @NonNull
    private static byte[] decodeBase64(@NonNull String value, @NonNull String partName) throws JSONException {
        try {
            byte[] bytes = Base64.decode(value, Base64.DEFAULT);
            if (bytes.length > MAX_MEDIA_BYTES) throw new JSONException("media_fetch_failed:" + partName + " exceeds 25 MB");
            return bytes;
        } catch (IllegalArgumentException e) {
            throw new JSONException("media_fetch_failed:Invalid base64 for " + partName);
        }
    }

    @NonNull
    private static byte[] fetchMedia(@NonNull String url) throws JSONException {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new JSONException("media_fetch_failed:HTTP " + status + " while fetching media");
            }
            int length = connection.getContentLength();
            if (length > MAX_MEDIA_BYTES) throw new JSONException("media_fetch_failed:Media exceeds 25 MB");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_MEDIA_BYTES) throw new JSONException("media_fetch_failed:Media exceeds 25 MB");
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } catch (JSONException e) {
            throw e;
        } catch (Exception e) {
            throw new JSONException("media_fetch_failed:" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    @NonNull
    private static JSONArray openAiEndpointCapabilities(@NonNull JSONArray capabilities, @NonNull String backend) {
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < capabilities.length(); i++) {
            String capability = capabilities.optString(i, "");
            if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)
                && ("tool_use".equals(capability) || "image_input".equals(capability) || "audio_input".equals(capability))) {
                continue;
            }
            filtered.put(capability);
        }
        if (filtered.length() == 0) filtered.put(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        return filtered;
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
            json.put("jobGroup", entry.jobGroup);
            json.put("priority", entry.priority);
            json.put("providerPageUrl", entry.providerPageUrl);
            json.put("downloadUrl", entry.downloadUrl == null ? JSONObject.NULL : entry.downloadUrl);
            json.put("downloadAvailable", entry.downloadAvailable);
            json.put("unavailableReason", entry.unavailableReason);
            json.put("license", entry.license);
            json.put("sizeBytes", entry.sizeBytes);
            json.put("sizeEstimate", entry.sizeEstimate);
            json.put("ramTier", entry.ramTier);
            json.put("recommended", entry.recommended);
            json.put("gated", entry.gated);
            json.put("backend", entry.backend);
            json.put("format", entry.format);
            json.put("architecture", entry.architecture);
            json.put("quantization", entry.quantization == null ? JSONObject.NULL : entry.quantization);
            json.put("contextWindow", entry.contextWindow);
            json.put("recommendedRamGb", entry.recommendedRamGb);
            json.put("revision", entry.revision);
            json.put("sha256", entry.sha256 == null ? JSONObject.NULL : entry.sha256);
            TaiModelSpec catalogSpec = registry.getModel(entry.modelId);
            if (catalogSpec != null) json.put("runtimeProfile", TaiModelProfile.forModel(catalogSpec).toJson());
            JSONArray capabilities = new JSONArray();
            for (String capability : entry.capabilities) capabilities.put(capability);
            json.put("capabilities", capabilities);
            JSONArray displayCapabilityTags = new JSONArray();
            for (String tag : entry.displayCapabilityTags) displayCapabilityTags.put(tag);
            json.put("displayCapabilityTags", displayCapabilityTags);
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
        if (model.recommendedRamGb > 0 && device.memoryBytes > 0L
            && device.memoryBytes < model.recommendedRamGb * 1024L * 1024L * 1024L) {
            warnings.put("Device memory is below this model's recommendation of " + model.recommendedRamGb + " GiB.");
        }
        data.put("compatibilityWarnings", warnings);
    }

    private static final class OpenAiChatRequest {
        final TaiChatRequest request;

        OpenAiChatRequest(@NonNull TaiChatRequest request) {
            this.request = request;
        }
    }
}
