package com.termux.ai;

import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.mnnllm.android.llm.LlmSession;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class MnnTaiRuntime implements TaiRuntime {
    private final Context appContext;

    private LlmSession session;
    private String runtimeState = "unloaded";
    private String statusMessage = "MNN runtime is unloaded.";
    private String loadedModelId;
    private String loadedModelPath;
    private TaiRuntimeOptions loadedOptions;
    private long loadedAtMs;
    private long lastUsedAtMs;
    private long keepWarmUntilMs;
    private boolean generating;
    private String activeGenerationId;
    private long activeGenerationStartedAtMs;
    private volatile boolean cancelRequested;

    public MnnTaiRuntime(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    public static boolean isNativeRuntimeAvailable() {
        try {
            Class.forName("com.alibaba.mnnllm.android.llm.LlmSession");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NonNull
    @Override
    public synchronized TaiRuntimeState getState() {
        maybeRefreshStateLocked();
        return new TaiRuntimeState(
            loadedModelId != null,
            loadedModelId,
            TaiModelSpec.BACKEND_MNN_LLM,
            runtimeState,
            statusMessage,
            loadedModelId == null ? "none" : backendName(loadedOptions),
            null,
            loadedModelPath,
            generating,
            activeGenerationId,
            activeGenerationStartedAtMs,
            keepWarmUntilMs,
            keepWarmUntilMs > 0L ? keepWarmUntilMs : 0L,
            loadedAtMs,
            lastUsedAtMs
        );
    }

    @Override
    public synchronized boolean isModelLoaded(@NonNull String modelId) {
        return loadedModelId != null && loadedModelId.equals(modelId) && session != null && session.isLoaded();
    }

    @NonNull
    @Override
    public JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        return loadInternal(modelSpec, options, 0);
    }

    @NonNull
    @Override
    public synchronized JSONObject unload() throws JSONException {
        if (generating) return errorLocked(409, "generation_active", "Cancel active MNN generation before unloading.");
        String previous = loadedModelId;
        releaseSessionLocked();
        runtimeState = "unloaded";
        statusMessage = "MNN runtime is unloaded.";
        JSONObject data = stateEnvelopeLocked(true);
        data.put("unloadedModelId", previous == null ? JSONObject.NULL : previous);
        return data;
    }

    @NonNull
    @Override
    public JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        return loadInternal(modelSpec, options, minutes);
    }

    @NonNull
    @Override
    public synchronized JSONObject cancel() throws JSONException {
        if (!generating) {
            JSONObject data = stateEnvelopeLocked(true);
            data.put("cancelled", false);
            data.put("message", "No active MNN generation.");
            return data;
        }
        cancelRequested = true;
        runtimeState = "stopping";
        statusMessage = "Cancelling active MNN generation.";
        JSONObject data = stateEnvelopeLocked(true);
        data.put("cancelled", true);
        data.put("message", "Cancel requested.");
        return data;
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "chat", TaiChatRequest.oneShot(systemPrompt, userPrompt), options, null);
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, "chat", TaiChatRequest.oneShot(systemPrompt, userPrompt), options, callback);
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "chat", request, options, null);
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, "chat", request, options, callback);
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "completion", TaiChatRequest.oneShot("", prompt), options, null);
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, "completion", TaiChatRequest.oneShot("", prompt), options, callback);
    }

    @NonNull
    private JSONObject loadInternal(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int keepWarmMinutes) throws JSONException {
        if (!TaiModelSpec.BACKEND_MNN_LLM.equals(modelSpec.backend)) {
            return error(409, "backend_mismatch", "Model " + modelSpec.id + " is not an MNN model.");
        }
        if (modelSpec.localPath == null || modelSpec.localPath.trim().isEmpty()) {
            return error(404, "model_file_not_readable", "MNN model config path is missing.");
        }
        File config = new File(modelSpec.localPath);
        if (!config.isFile() || !"config.json".equals(config.getName())) {
            return error(404, "model_file_not_readable", "MNN models must point to a readable config.json file.");
        }
        File modelDir = config.getParentFile();
        if (modelDir == null || !new File(modelDir, "llm.mnn").isFile() || !new File(modelDir, "llm.mnn.weight").isFile()) {
            return error(404, "model_file_not_readable", "MNN model directory is incomplete.");
        }
        if (!isNativeRuntimeAvailable()) {
            return error(501, "mnn_native_unavailable", "Native MNN runtime libraries are not available for this APK/ABI.");
        }

        synchronized (this) {
            if (generating) return errorLocked(409, "generation_active", "Cancel the active generation before loading another MNN model.");
            runtimeState = "loading";
            statusMessage = "Loading " + modelSpec.id + ".";
            releaseSessionLocked();
        }

        LlmSession initialized = new LlmSession();
        String mergedConfig = mergedConfigJson(config, modelSpec, options);
        String extraConfig = extraConfigJson(modelSpec);
        try {
            initialized.load(config.getAbsolutePath(), null, mergedConfig, extraConfig);
        } catch (Throwable t) {
            synchronized (this) {
                runtimeState = "failed";
                statusMessage = "MNN load failed: " + message(t);
                return errorLocked(500, "mnn_load_failed", statusMessage);
            }
        }

        synchronized (this) {
            session = initialized;
            loadedModelId = modelSpec.id;
            loadedModelPath = config.getAbsolutePath();
            loadedOptions = options;
            loadedAtMs = System.currentTimeMillis();
            lastUsedAtMs = loadedAtMs;
            keepWarmUntilMs = keepWarmMinutes > 0 ? loadedAtMs + TimeUnit.MINUTES.toMillis(keepWarmMinutes) : 0L;
            runtimeState = "loaded";
            statusMessage = keepWarmUntilMs > 0L ? "MNN model loaded and warm." : "MNN model loaded.";
            JSONObject data = stateEnvelopeLocked(true);
            data.put("loadedModelId", loadedModelId);
            data.put("backend", backendName(options));
            data.put("modelPath", loadedModelPath);
            data.put("options", options.toJson());
            data.put("effectiveConfig", safeJson(initialized.dumpConfig()));
            if (keepWarmUntilMs > 0L) {
                data.put("keepWarm", true);
                data.put("keepWarmMinutes", keepWarmMinutes);
                data.put("keepWarmUntilMs", keepWarmUntilMs);
            }
            return data;
        }
    }

    @NonNull
    private JSONObject generate(
        @NonNull String modelId,
        @NonNull String mode,
        @NonNull TaiChatRequest request,
        @NonNull TaiRuntimeOptions options,
        @Nullable TaiGenerationCallback callback
    ) throws JSONException {
        LlmSession activeSession;
        String generationId;
        long startedAt;
        synchronized (this) {
            JSONObject availabilityError = ensureLoadedForGenerationLocked(modelId);
            if (availabilityError != null) return availabilityError;
            if (generating) return errorLocked(409, "generation_active", "A MNN generation is already running. Cancel it or wait for it to finish.");
            activeSession = session;
            if (activeSession == null) return errorLocked(409, "model_not_loaded", "Load the downloaded MNN model first.");
            applyRuntimeOptionsLocked(activeSession, request.systemPrompt, options);
            generationId = beginGenerationLocked();
            startedAt = activeGenerationStartedAtMs;
        }

        StringBuilder responseBuilder = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        HashMap<String, Object> metrics = new HashMap<>();
        try {
            List<Pair<String, String>> history = mnnHistory(request);
            HashMap<String, Object> nativeResult = activeSession.generateHistory(history, progress -> {
                if (cancelRequested) return true;
                if (progress == null) return false;
                synchronized (responseBuilder) {
                    responseBuilder.append(progress);
                }
                if (callback != null) callback.onToken(progress);
                return false;
            });
            if (nativeResult != null) metrics.putAll(nativeResult);
        } catch (Throwable t) {
            errorRef.set(t);
        } finally {
            synchronized (this) {
                finishGenerationLocked(errorRef.get());
            }
        }

        Throwable throwable = errorRef.get();
        String response;
        synchronized (responseBuilder) {
            response = responseBuilder.toString();
        }
        if (throwable != null) {
            if (callback != null) callback.onError(throwable);
            if (cancelRequested) return error(499, "generation_cancelled", "MNN generation was cancelled.");
            return error(500, "mnn_generation_failed", "MNN generation failed: " + message(throwable));
        }
        if (callback != null) callback.onComplete(response);

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("model", modelId);
        data.put("runtime", TaiModelSpec.BACKEND_MNN_LLM);
        data.put("backend", backendName(options));
        data.put("loaded", true);
        data.put("generationId", generationId);
        data.put("mode", mode);
        data.put("response", response);
        data.put("toolCalls", new JSONArray());
        data.put("elapsedMs", System.currentTimeMillis() - startedAt);
        data.put("options", options.toJson());
        data.put("metrics", metricsJson(metrics));
        data.put("state", getState().toJson());
        return data;
    }

    @Nullable
    private JSONObject ensureLoadedForGenerationLocked(@NonNull String modelId) throws JSONException {
        if (session == null || loadedModelId == null || !loadedModelId.equals(modelId)) {
            return errorLocked(409, "model_not_loaded", "Load the downloaded MNN model first with tai load " + modelId + " or from the TAI settings UI.");
        }
        return null;
    }

    private void applyRuntimeOptionsLocked(@NonNull LlmSession activeSession, @NonNull String systemPrompt, @NonNull TaiRuntimeOptions options) {
        if (options.maxTokens != null) activeSession.updateMaxNewTokens(options.maxTokens);
        if (!systemPrompt.trim().isEmpty()) activeSession.updateSystemPrompt(systemPrompt);
        try {
            activeSession.updateConfig(overridesJson(systemPrompt, options).toString());
        } catch (JSONException ignored) {
        }
    }

    @NonNull
    private String beginGenerationLocked() {
        long now = System.currentTimeMillis();
        generating = true;
        cancelRequested = false;
        activeGenerationStartedAtMs = now;
        activeGenerationId = "tai-mnn-gen-" + now;
        lastUsedAtMs = now;
        runtimeState = "generating";
        statusMessage = "Generating.";
        return activeGenerationId;
    }

    private void finishGenerationLocked(@Nullable Throwable throwable) {
        generating = false;
        activeGenerationId = null;
        activeGenerationStartedAtMs = 0L;
        lastUsedAtMs = System.currentTimeMillis();
        if (cancelRequested) {
            statusMessage = "Generation cancelled.";
        } else if (throwable != null) {
            statusMessage = "Generation failed: " + message(throwable);
        } else {
            statusMessage = "MNN model loaded.";
        }
        runtimeState = "loaded";
        cancelRequested = false;
        maybeRefreshStateLocked();
    }

    private void maybeRefreshStateLocked() {
        if (loadedModelId == null || generating || keepWarmUntilMs <= 0L) return;
        if (System.currentTimeMillis() > keepWarmUntilMs) keepWarmUntilMs = 0L;
    }

    private void releaseSessionLocked() {
        if (session != null) {
            try {
                session.release();
            } catch (Throwable ignored) {
            }
        }
        session = null;
        loadedModelId = null;
        loadedModelPath = null;
        loadedOptions = null;
        loadedAtMs = 0L;
        lastUsedAtMs = 0L;
        keepWarmUntilMs = 0L;
    }

    @NonNull
    private List<Pair<String, String>> mnnHistory(@NonNull TaiChatRequest request) {
        ArrayList<Pair<String, String>> history = new ArrayList<>();
        if (!request.systemPrompt.trim().isEmpty()) history.add(new Pair<>("system", request.systemPrompt));
        for (Message message : request.initialMessages) history.add(new Pair<>(mnnRole(message), textFromMessage(message)));
        history.add(new Pair<>(mnnRole(request.message), textFromMessage(request.message)));
        return history;
    }

    @NonNull
    private String mnnRole(@NonNull Message message) {
        String role = String.valueOf(message.getRole()).toLowerCase(Locale.ROOT);
        if (role.contains("model") || role.contains("assistant")) return "assistant";
        if (role.contains("tool")) return "tool";
        return "user";
    }

    @NonNull
    private String textFromMessage(@Nullable Message message) {
        if (message == null || message.getContents() == null) return "";
        StringBuilder builder = new StringBuilder();
        for (Content content : message.getContents().getContents()) {
            if (content instanceof Content.Text) {
                builder.append(((Content.Text) content).getText());
            } else if (content != null) {
                builder.append(content.toString());
            }
        }
        return builder.toString();
    }

    @NonNull
    private String mergedConfigJson(@NonNull File config, @NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        JSONObject json = readJsonFile(config);
        if (!json.has("backend_type")) json.put("backend_type", backendName(options));
        json.put("backend_type", backendName(options));
        json.put("thread_num", Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
        json.put("precision", "low");
        json.put("memory", "low");
        if (options.maxTokens != null) json.put("max_new_tokens", options.maxTokens);
        if (options.temperature != null) json.put("temperature", options.temperature);
        if (options.topP != null) json.put("top_p", options.topP);
        if (options.topK != null) json.put("top_k", options.topK);
        json.put("system_prompt", new TaiSettings(appContext).getSystemPrompt(modelSpec.id));
        return json.toString();
    }

    @NonNull
    private JSONObject overridesJson(@NonNull String systemPrompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("backend_type", backendName(options));
        if (options.maxTokens != null) json.put("max_new_tokens", options.maxTokens);
        if (options.temperature != null) json.put("temperature", options.temperature);
        if (options.topP != null) json.put("top_p", options.topP);
        if (options.topK != null) json.put("top_k", options.topK);
        if (!systemPrompt.trim().isEmpty()) json.put("system_prompt", systemPrompt);
        return json;
    }

    @NonNull
    private String extraConfigJson(@NonNull TaiModelSpec modelSpec) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("is_r1", modelSpec.id.toLowerCase(Locale.ROOT).contains("r1")
            || (modelSpec.architecture != null && modelSpec.architecture.toLowerCase(Locale.ROOT).contains("r1")));
        json.put("mmap_dir", "");
        json.put("keep_history", false);
        return json.toString();
    }

    @NonNull
    private String backendName(@Nullable TaiRuntimeOptions options) {
        String accelerator = options == null ? null : options.accelerator;
        if (accelerator == null || accelerator.trim().isEmpty() || "auto".equalsIgnoreCase(accelerator)) return "cpu";
        if ("opencl".equalsIgnoreCase(accelerator) || "gpu".equalsIgnoreCase(accelerator)) return "opencl";
        return "cpu";
    }

    @NonNull
    private JSONObject metricsJson(@NonNull HashMap<String, Object> metrics) throws JSONException {
        JSONObject json = new JSONObject();
        for (String key : metrics.keySet()) {
            Object value = metrics.get(key);
            json.put(key, value == null ? JSONObject.NULL : value);
        }
        return json;
    }

    @NonNull
    private JSONObject safeJson(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return new JSONObject();
        try {
            return new JSONObject(value);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    @NonNull
    private JSONObject readJsonFile(@NonNull File file) throws JSONException {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            throw new JSONException("Could not read MNN config: " + message(e));
        }
    }

    @NonNull
    private JSONObject stateEnvelopeLocked(boolean ok) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", ok);
        data.put("runtime", TaiModelSpec.BACKEND_MNN_LLM);
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    private JSONObject error(int statusCode, @NonNull String code, @NonNull String message) throws JSONException {
        synchronized (this) {
            return errorLocked(statusCode, code, message);
        }
    }

    @NonNull
    private JSONObject errorLocked(int statusCode, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject data = stateEnvelopeLocked(false);
        data.put("error", code);
        data.put("message", message);
        data.put("_statusCode", statusCode);
        return data;
    }

    @NonNull
    private String message(@NonNull Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}
