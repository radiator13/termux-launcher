package com.termux.ai;

import androidx.annotation.NonNull;

import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class LlamaCppTaiRuntime implements TaiRuntime {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private long handle;
    private TaiModelSpec loadedModel;
    private String state = "unloaded";
    private String status = "llama.cpp runtime is unloaded.";
    private String backend = "none";
    private String fallbackReason;
    private boolean generating;
    private String generationId;
    private long generationStartedAtMs;
    private long loadedAtMs;
    private long lastUsedAtMs;
    private long keepWarmUntilMs;
    private long idleUnloadAtMs;
    private ScheduledFuture<?> idleFuture;

    public static boolean isNativeAvailable() {
        return LlamaCppBridge.isAvailable();
    }

    @NonNull
    @Override
    public synchronized TaiRuntimeState getState() {
        JSONObject extra = new JSONObject();
        try {
            extra.put("format", TaiModelSpec.FORMAT_GGUF);
            extra.put("gpuApi", "Vulkan");
            extra.put("nativeAvailable", isNativeAvailable());
        } catch (JSONException ignored) {}
        return new TaiRuntimeState(handle != 0, loadedModel == null ? null : loadedModel.id,
            TaiModelSpec.BACKEND_LLAMA_CPP, state, status, backend, fallbackReason,
            loadedModel == null ? null : loadedModel.localPath, generating, generationId,
            generationStartedAtMs, keepWarmUntilMs, idleUnloadAtMs, loadedAtMs, lastUsedAtMs, extra);
    }

    @Override
    public synchronized boolean isModelLoaded(@NonNull String modelId) {
        return handle != 0 && loadedModel != null && modelId.equals(loadedModel.id);
    }

    @NonNull
    @Override
    public JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        return loadInternal(modelSpec, options, 0);
    }

    @NonNull
    @Override
    public synchronized JSONObject unload() throws JSONException {
        if (generating) return error(409, "generation_active", "Cancel active generation before unloading llama.cpp.");
        String previous = loadedModel == null ? null : loadedModel.id;
        closeLocked("llama.cpp runtime is unloaded.");
        JSONObject result = envelope(true);
        result.put("unloadedModelId", previous == null ? JSONObject.NULL : previous);
        return result;
    }

    @NonNull
    @Override
    public JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        return loadInternal(modelSpec, options, Math.max(1, minutes));
    }

    @NonNull
    @Override
    public synchronized JSONObject cancel() throws JSONException {
        if (!generating || handle == 0) {
            JSONObject result = envelope(true);
            result.put("cancelled", false);
            result.put("message", "No active llama.cpp generation.");
            return result;
        }
        LlamaCppBridge.nativeCancel(handle);
        state = "stopping";
        status = "Cancelling llama.cpp generation.";
        JSONObject result = envelope(true);
        result.put("cancelled", true);
        return result;
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt,
                           @NonNull TaiRuntimeOptions options) throws JSONException {
        return chat(modelId, TaiChatRequest.simple(systemPrompt, userPrompt), options);
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt,
                           @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return chat(modelId, TaiChatRequest.simple(systemPrompt, userPrompt), options, callback);
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request,
                           @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, renderChat(request), true, options, null);
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request,
                           @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, renderChat(request), true, options, callback);
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt,
                               @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, prompt, false, options, null);
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt,
                               @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, prompt, false, options, callback);
    }

    private JSONObject loadInternal(TaiModelSpec modelSpec, TaiRuntimeOptions options, int warmMinutes) throws JSONException {
        synchronized (this) {
            if (!isNativeAvailable()) return error(501, "llama_cpp_unavailable", "llama.cpp is available only in the arm64 build.");
            if (!TaiModelSpec.FORMAT_GGUF.equals(modelSpec.format)) return error(400, "unsupported_model_format", "llama.cpp requires a GGUF model.");
            if (modelSpec.localPath == null || !new File(modelSpec.localPath).isFile()) return error(404, "model_file_not_readable", "GGUF model file is missing.");
            if (generating) return error(409, "generation_active", "Cancel generation before loading another model.");
            if (isModelLoaded(modelSpec.id)) {
                applyWarmLocked(warmMinutes, options);
                return envelope(true);
            }
            closeLocked("Switching llama.cpp model.");
            state = "loading";
            status = "Loading GGUF model with llama.cpp.";
        }

        String requested = options.accelerator == null ? "auto" : options.accelerator.toLowerCase();
        boolean tryGpu = !"cpu".equals(requested);
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        int context = Math.min(Math.max(512, modelSpec.contextWindow), 8192);
        long newHandle = LlamaCppBridge.nativeLoad(modelSpec.localPath, context, tryGpu ? 999 : 0, threads);
        String fallback = null;
        String selectedBackend = tryGpu ? "GPU (Vulkan)" : "CPU";
        if (newHandle == 0 && tryGpu && !"gpu".equals(requested)) {
            String gpuError = LlamaCppBridge.nativeLastError();
            newHandle = LlamaCppBridge.nativeLoad(modelSpec.localPath, context, 0, threads);
            fallback = "Vulkan load failed; using CPU: " + gpuError;
            selectedBackend = "CPU";
        }
        synchronized (this) {
            if (newHandle == 0) {
                state = "failed";
                status = LlamaCppBridge.nativeLastError();
                return error(500, "llama_cpp_load_failed", status);
            }
            handle = newHandle;
            loadedModel = modelSpec;
            state = warmMinutes > 0 ? "idle-warm" : "loaded";
            status = "GGUF model loaded with llama.cpp.";
            backend = selectedBackend;
            fallbackReason = fallback;
            loadedAtMs = lastUsedAtMs = System.currentTimeMillis();
            applyWarmLocked(warmMinutes, options);
            return envelope(true);
        }
    }

    private JSONObject generate(String modelId, String prompt, boolean chat, TaiRuntimeOptions options,
                                TaiGenerationCallback callback) throws JSONException {
        final long activeHandle;
        synchronized (this) {
            if (!isModelLoaded(modelId)) return error(409, "model_not_loaded", "Load the requested GGUF model first.");
            if (generating) return error(409, "generation_active", "A llama.cpp generation is already running.");
            generating = true;
            state = "generating";
            generationId = "llama-" + System.currentTimeMillis();
            generationStartedAtMs = System.currentTimeMillis();
            activeHandle = handle;
            cancelIdleLocked();
        }
        int maxTokens = options.maxTokens == null ? 512 : Math.max(1, options.maxTokens);
        int topK = options.topK == null ? 40 : Math.max(0, options.topK);
        double topP = options.topP == null ? 0.95 : options.topP;
        double temperature = options.temperature == null ? 0.7 : options.temperature;
        String response = LlamaCppBridge.nativeGenerate(activeHandle, prompt, chat, maxTokens, topK, topP,
            temperature, callback == null ? null : callback::onToken);
        synchronized (this) {
            generating = false;
            generationId = null;
            generationStartedAtMs = 0L;
            lastUsedAtMs = System.currentTimeMillis();
            scheduleIdleLocked(options.idleUnloadMinutes == null ? 10 : options.idleUnloadMinutes);
            if (response == null) {
                String error = LlamaCppBridge.nativeLastError();
                state = handle == 0 ? "unloaded" : "loaded";
                status = error;
                if (error.toLowerCase().contains("cancel")) return error(499, "generation_cancelled", error);
                return error(500, "llama_cpp_generation_failed", error);
            }
            state = keepWarmUntilMs > System.currentTimeMillis() ? "idle-warm" : "loaded";
            status = "llama.cpp generation complete.";
            if (callback != null) callback.onComplete(response);
            JSONObject result = envelope(true);
            result.put("model", modelId);
            result.put("response", response);
            result.put("toolCalls", new JSONArray());
            return result;
        }
    }

    private String renderChat(TaiChatRequest request) {
        StringBuilder prompt = new StringBuilder();
        if (!request.systemPrompt.isEmpty()) prompt.append("System:\n").append(request.systemPrompt).append("\n\n");
        for (Message message : request.initialMessages) appendMessage(prompt, message);
        appendMessage(prompt, request.message);
        return prompt.toString();
    }

    private void appendMessage(StringBuilder prompt, Message message) {
        prompt.append(message.getRole().toString()).append(":\n");
        if (message.getContents() != null) {
            for (Content content : message.getContents().getContents()) {
                if (content instanceof Content.Text) prompt.append(((Content.Text) content).getText());
                else prompt.append(String.valueOf(content));
            }
        }
        prompt.append("\n\n");
    }

    private synchronized void applyWarmLocked(int minutes, TaiRuntimeOptions options) {
        keepWarmUntilMs = minutes > 0 ? System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes) : 0L;
        scheduleIdleLocked(options.idleUnloadMinutes == null ? 10 : options.idleUnloadMinutes);
    }

    private synchronized void scheduleIdleLocked(int minutes) {
        cancelIdleLocked();
        if (handle == 0 || minutes <= 0) { idleUnloadAtMs = 0L; return; }
        idleUnloadAtMs = Math.max(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes), keepWarmUntilMs);
        idleFuture = scheduler.schedule(() -> {
            synchronized (LlamaCppTaiRuntime.this) {
                if (!generating && handle != 0 && System.currentTimeMillis() >= idleUnloadAtMs) closeLocked("GGUF model unloaded after idle timeout.");
            }
        }, Math.max(1000L, idleUnloadAtMs - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
    }

    private synchronized void cancelIdleLocked() {
        if (idleFuture != null) idleFuture.cancel(false);
        idleFuture = null;
        idleUnloadAtMs = 0L;
    }

    private synchronized void closeLocked(String message) {
        cancelIdleLocked();
        if (handle != 0) LlamaCppBridge.nativeUnload(handle);
        handle = 0L;
        loadedModel = null;
        generating = false;
        state = "unloaded";
        status = message;
        backend = "none";
        fallbackReason = null;
        keepWarmUntilMs = 0L;
    }

    private JSONObject envelope(boolean ok) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("ok", ok);
        result.put("runtime", TaiModelSpec.BACKEND_LLAMA_CPP);
        result.put("state", getState().toJson());
        return result;
    }

    private JSONObject error(int statusCode, String code, String message) throws JSONException {
        JSONObject result = envelope(false);
        result.put("error", code);
        result.put("message", message);
        result.put("_statusCode", statusCode);
        return result;
    }
}
