package com.termux.ai;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.ExperimentalFlags;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.MessageCallback;
import com.google.ai.edge.litertlm.SamplerConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class LiteRtTaiRuntime implements TaiRuntime {
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final int DEFAULT_TOP_K = 64;
    private static final double DEFAULT_TOP_P = 0.95d;
    private static final double DEFAULT_TEMPERATURE = 1.0d;
    private static final int DEFAULT_KEEP_WARM_MINUTES = 30;
    private static final String AUTO_GPU_REASON =
        "Auto selected GPU first, matching Google AI Edge Gallery's fast default path.";

    private final Context appContext;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-runtime-idle");
        thread.setDaemon(true);
        return thread;
    });

    private Engine engine;
    private Conversation conversation;
    private String conversationKey = "";
    private String loadedModelId;
    private String loadedModelPath;
    private String backendName = "none";
    private String backendFallbackReason = "";
    private String runtimeState = "unloaded";
    private String statusMessage = "LiteRT-LM runtime is unloaded.";
    private TaiRuntimeOptions loadedOptions;
    private ScheduledFuture<?> idleUnloadFuture;
    private boolean generating;
    private String activeGenerationId;
    private long activeGenerationStartedAtMs;
    private long loadedAtMs;
    private long lastUsedAtMs;
    private long keepWarmUntilMs;
    private long idleUnloadAtMs;

    public LiteRtTaiRuntime(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public synchronized TaiRuntimeState getState() {
        maybeRefreshStateLocked();
        return new TaiRuntimeState(
            engine != null,
            loadedModelId,
            "litert-lm",
            runtimeState,
            statusMessage,
            backendName,
            backendFallbackReason,
            loadedModelPath,
            generating,
            activeGenerationId,
            activeGenerationStartedAtMs,
            keepWarmUntilMs,
            idleUnloadAtMs,
            loadedAtMs,
            lastUsedAtMs
        );
    }

    @NonNull
    @Override
    public synchronized JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        return loadInternalLocked(modelSpec, options, 0);
    }

    @NonNull
    @Override
    public synchronized JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        int keepWarmMinutes = minutes > 0 ? minutes : DEFAULT_KEEP_WARM_MINUTES;
        if (engine == null || loadedModelId == null || !loadedModelId.equals(modelSpec.id)) {
            JSONObject loaded = loadInternalLocked(modelSpec, options, keepWarmMinutes);
            if (!loaded.optBoolean("ok", false)) return loaded;
        } else {
            keepWarmUntilMs = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(keepWarmMinutes);
            statusMessage = "Model is warm.";
            maybeRefreshStateLocked();
            scheduleIdleUnloadLocked();
        }

        JSONObject data = stateEnvelopeLocked(true);
        data.put("keepWarm", true);
        data.put("keepWarmMinutes", keepWarmMinutes);
        data.put("keepWarmUntilMs", keepWarmUntilMs);
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject unload() throws JSONException {
        if (generating) {
            return error(409, "generation_active", "Cancel the active generation before unloading the LiteRT-LM runtime.");
        }
        String previous = loadedModelId;
        closeEngineLocked("LiteRT-LM runtime is unloaded.", "unloaded");
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("unloadedModelId", previous == null ? JSONObject.NULL : previous);
        data.put("runtime", "litert-lm");
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject cancel() throws JSONException {
        if (!generating || conversation == null) {
            JSONObject data = stateEnvelopeLocked(true);
            data.put("cancelled", false);
            data.put("message", "No active generation.");
            return data;
        }
        runtimeState = "stopping";
        statusMessage = "Cancelling active generation.";
        try {
            conversation.cancelProcess();
        } catch (Exception e) {
            return error(500, "cancel_failed", "Failed to cancel active LiteRT-LM generation: " + e.getMessage());
        }
        JSONObject data = stateEnvelopeLocked(true);
        data.put("cancelled", true);
        data.put("message", "Cancel requested.");
        return data;
    }

    @NonNull
    @Override
    public JSONObject chat(
        @NonNull String modelId,
        @NonNull String systemPrompt,
        @NonNull String userPrompt,
        @NonNull TaiRuntimeOptions options
    ) throws JSONException {
        return generate(modelId, "chat", systemPrompt, userPrompt, options, null);
    }

    @NonNull
    @Override
    public JSONObject chat(
        @NonNull String modelId,
        @NonNull String systemPrompt,
        @NonNull String userPrompt,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiGenerationCallback callback
    ) throws JSONException {
        return generate(modelId, "chat", systemPrompt, userPrompt, options, callback);
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "completion", "", prompt, options, null);
    }

    @NonNull
    @Override
    public JSONObject complete(
        @NonNull String modelId,
        @NonNull String prompt,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiGenerationCallback callback
    ) throws JSONException {
        return generate(modelId, "completion", "", prompt, options, callback);
    }

    @NonNull
    private JSONObject generate(
        @NonNull String modelId,
        @NonNull String mode,
        @NonNull String systemPrompt,
        @NonNull String prompt,
        @NonNull TaiRuntimeOptions options,
        @Nullable TaiGenerationCallback callback
    ) throws JSONException {
        Conversation activeConversation;
        String generationId;
        long startedAt;
        synchronized (this) {
            JSONObject availabilityError = ensureLoadedForGenerationLocked(modelId);
            if (availabilityError != null) return availabilityError;
            if (generating) {
                return error(409, "generation_active", "A LiteRT-LM generation is already running. Cancel it or wait for it to finish.");
            }
            activeConversation = ensureConversationLocked(mode, systemPrompt, options);
            generationId = beginGenerationLocked();
            startedAt = activeGenerationStartedAtMs;
        }

        CountDownLatch done = new CountDownLatch(1);
        StringBuilder responseBuilder = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        try {
            if (callback == null) {
                Message response = activeConversation.sendMessage(prompt, Collections.emptyMap());
                String responseText = textFromMessage(response);
                responseBuilder.append(responseText);
                done.countDown();
            } else {
                activeConversation.sendMessageAsync(prompt, new MessageCallback() {
                    @Override
                    public void onMessage(@NonNull Message message) {
                        String text = textFromMessage(message);
                        synchronized (responseBuilder) {
                            responseBuilder.append(text);
                        }
                        callback.onToken(text);
                    }

                    @Override
                    public void onDone() {
                        String fullText;
                        synchronized (responseBuilder) {
                            fullText = responseBuilder.toString();
                        }
                        callback.onComplete(fullText);
                        done.countDown();
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        errorRef.set(throwable);
                        callback.onError(throwable);
                        done.countDown();
                    }
                }, Collections.emptyMap());
            }
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorRef.set(e);
        } catch (Throwable t) {
            errorRef.set(t);
        } finally {
            synchronized (this) {
                finishGenerationLocked(errorRef.get());
            }
        }

        Throwable throwable = errorRef.get();
        if (throwable != null) {
            if (throwable instanceof CancellationException) {
                return error(499, "generation_cancelled", "LiteRT-LM generation was cancelled.");
            }
            return error(500, "litert_lm_generation_failed", "LiteRT-LM generation failed: " + throwable.getMessage());
        }

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("model", modelId);
        data.put("runtime", "litert-lm");
        data.put("backend", backendName);
        data.put("backendFallbackReason", backendFallbackReason.isEmpty() ? JSONObject.NULL : backendFallbackReason);
        data.put("loaded", true);
        data.put("generationId", generationId);
        data.put("mode", mode);
        data.put("response", responseBuilder.toString());
        data.put("elapsedMs", System.currentTimeMillis() - startedAt);
        data.put("options", options.toJson());
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    private JSONObject loadInternalLocked(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int keepWarmMinutes) throws JSONException {
        if (generating) {
            return error(409, "generation_active", "Cancel the active generation before loading another LiteRT-LM model.");
        }
        if (!isSupportedAbi()) {
            return error(501, "litert_lm_unsupported_abi", "LiteRT-LM 0.12.0 ships native libraries for arm64-v8a and x86_64 only.");
        }
        if (modelSpec.localPath == null || modelSpec.localPath.trim().isEmpty()) {
            return error(404, "model_file_missing", "Download or import this model before loading it.");
        }
        File modelFile = new File(modelSpec.localPath);
        if (!modelFile.isFile() || !modelFile.canRead()) {
            JSONObject error = error(404, "model_file_not_readable", "Model file is missing or not readable by the app process.");
            error.put("path", modelSpec.localPath);
            return error;
        }

        closeEngineLocked("Loading model.", "loading");
        applyExperimentalFlags(options);
        runtimeState = "loading";
        statusMessage = "Loading model.";
        try {
            if (isAutoAccelerator(options)) {
                try {
                    backendFallbackReason = AUTO_GPU_REASON;
                    engine = createAndInitializeEngine(modelFile.getAbsolutePath(), options, new Backend.GPU());
                } catch (Exception gpuException) {
                    closeEngineResourcesLocked();
                    backendFallbackReason = "Auto GPU load failed; fell back to CPU. GPU error: " + gpuException.getMessage();
                    engine = createAndInitializeEngine(modelFile.getAbsolutePath(), options, new Backend.CPU(null));
                }
            } else {
                backendFallbackReason = "";
                engine = createAndInitializeEngine(modelFile.getAbsolutePath(), options, preferredBackend(options));
            }
        } catch (Exception e) {
            closeEngineResourcesLocked();
            runtimeState = "failed";
            statusMessage = "LiteRT-LM load failed: " + e.getMessage();
            return error(500, "litert_lm_load_failed", statusMessage);
        }

        loadedModelId = modelSpec.id;
        loadedModelPath = modelFile.getAbsolutePath();
        loadedOptions = options;
        loadedAtMs = System.currentTimeMillis();
        lastUsedAtMs = loadedAtMs;
        keepWarmUntilMs = keepWarmMinutes > 0 ? loadedAtMs + TimeUnit.MINUTES.toMillis(keepWarmMinutes) : 0L;
        statusMessage = keepWarmUntilMs > 0L ? "Model loaded and warm." : "Model loaded.";
        maybeRefreshStateLocked();
        scheduleIdleUnloadLocked();

        JSONObject data = stateEnvelopeLocked(true);
        data.put("loadedModelId", loadedModelId);
        data.put("backend", backendName);
        data.put("backendFallbackReason", backendFallbackReason.isEmpty() ? JSONObject.NULL : backendFallbackReason);
        data.put("modelPath", loadedModelPath);
        data.put("options", options.toJson());
        if (keepWarmUntilMs > 0L) {
            data.put("keepWarm", true);
            data.put("keepWarmMinutes", keepWarmMinutes);
            data.put("keepWarmUntilMs", keepWarmUntilMs);
        }
        return data;
    }

    @Nullable
    private JSONObject ensureLoadedForGenerationLocked(@NonNull String modelId) throws JSONException {
        if (engine == null || loadedModelId == null || !loadedModelId.equals(modelId)) {
            return error(409, "model_not_loaded", "Load the downloaded model first with tai load " + modelId + " or from the TAI settings UI.");
        }
        return null;
    }

    @NonNull
    private Conversation ensureConversationLocked(@NonNull String mode, @NonNull String systemPrompt, @NonNull TaiRuntimeOptions options) {
        String key = mode + "|" + normalized(systemPrompt) + "|" + optionsKey(options);
        if (conversation != null && conversation.isAlive() && key.equals(conversationKey)) {
            return conversation;
        }
        closeConversationLocked();
        Contents systemContents = contents(systemPrompt);
        ConversationConfig conversationConfig = conversationConfig(systemContents, options);
        conversation = engine.createConversation(conversationConfig);
        conversationKey = key;
        return conversation;
    }

    @NonNull
    private String beginGenerationLocked() {
        long now = System.currentTimeMillis();
        generating = true;
        activeGenerationStartedAtMs = now;
        activeGenerationId = "tai-gen-" + now;
        lastUsedAtMs = now;
        runtimeState = "generating";
        statusMessage = "Generating.";
        cancelIdleUnloadLocked();
        return activeGenerationId;
    }

    private void finishGenerationLocked(@Nullable Throwable throwable) {
        generating = false;
        activeGenerationId = null;
        activeGenerationStartedAtMs = 0L;
        lastUsedAtMs = System.currentTimeMillis();
        if (throwable instanceof CancellationException) {
            statusMessage = "Generation cancelled.";
            runtimeState = "loaded";
        } else if (throwable != null) {
            statusMessage = "Generation failed: " + throwable.getMessage();
            runtimeState = "loaded";
        } else {
            statusMessage = "Model loaded.";
            runtimeState = "loaded";
        }
        maybeRefreshStateLocked();
        scheduleIdleUnloadLocked();
    }

    private Engine createAndInitializeEngine(@NonNull String modelPath, @NonNull TaiRuntimeOptions options, @NonNull Backend backend) {
        backendName = backend.getName();
        File cacheDir = new File(appContext.getCacheDir(), "tai-litertlm");
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs() && !cacheDir.isDirectory()) {
            throw new IllegalStateException("Unable to create LiteRT-LM cache directory: " + cacheDir.getAbsolutePath());
        }
        EngineConfig config = new EngineConfig(
            modelPath,
            backend,
            null,
            null,
            options.maxTokens == null ? DEFAULT_MAX_TOKENS : options.maxTokens,
            null,
            cacheDir.getAbsolutePath()
        );
        Engine loadedEngine = new Engine(config);
        loadedEngine.initialize();
        return loadedEngine;
    }

    @NonNull
    private Backend preferredBackend(@NonNull TaiRuntimeOptions options) {
        if (isGpuAccelerator(options) || isAutoAccelerator(options)) return new Backend.GPU();
        return new Backend.CPU(null);
    }

    private boolean isAutoAccelerator(@NonNull TaiRuntimeOptions options) {
        return options.accelerator == null || "auto".equalsIgnoreCase(String.valueOf(options.accelerator));
    }

    private boolean isGpuAccelerator(@NonNull TaiRuntimeOptions options) {
        return "gpu".equalsIgnoreCase(options.accelerator);
    }

    private void applyExperimentalFlags(@NonNull TaiRuntimeOptions options) {
        ExperimentalFlags.INSTANCE.setEnableSpeculativeDecoding(Boolean.TRUE.equals(options.speculativeDecodingEnabled));
    }

    @NonNull
    private ConversationConfig conversationConfig(@NonNull Contents systemPrompt, @NonNull TaiRuntimeOptions options) {
        SamplerConfig samplerConfig = new SamplerConfig(
            options.topK == null ? DEFAULT_TOP_K : options.topK,
            options.topP == null ? DEFAULT_TOP_P : options.topP,
            options.temperature == null ? DEFAULT_TEMPERATURE : options.temperature,
            0
        );
        return new ConversationConfig(systemPrompt, Collections.emptyList(), Collections.emptyList(), samplerConfig);
    }

    @NonNull
    private Contents contents(@NonNull String text) {
        return Contents.Companion.of(text);
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

    private void scheduleIdleUnloadLocked() {
        cancelIdleUnloadLocked();
        if (engine == null) {
            idleUnloadAtMs = 0L;
            return;
        }
        long target = calculateUnloadAtMsLocked();
        idleUnloadAtMs = target;
        if (target <= 0L) return;
        long delayMs = Math.max(1000L, target - System.currentTimeMillis());
        idleUnloadFuture = scheduler.schedule(this::maybeUnloadAfterIdle, delayMs, TimeUnit.MILLISECONDS);
    }

    private void maybeUnloadAfterIdle() {
        synchronized (this) {
            if (engine == null) return;
            if (generating) {
                scheduleIdleUnloadLocked();
                return;
            }
            long target = calculateUnloadAtMsLocked();
            idleUnloadAtMs = target;
            long now = System.currentTimeMillis();
            if (target > now) {
                idleUnloadFuture = scheduler.schedule(this::maybeUnloadAfterIdle, target - now, TimeUnit.MILLISECONDS);
                return;
            }
            if (target > 0L) {
                closeEngineLocked("Model unloaded after idle or keep-warm timeout.", "unloaded");
            }
        }
    }

    private long calculateUnloadAtMsLocked() {
        long target = 0L;
        long now = System.currentTimeMillis();
        if (keepWarmUntilMs > now) target = keepWarmUntilMs;
        int idleMinutes = loadedOptions != null && loadedOptions.idleUnloadMinutes != null ? loadedOptions.idleUnloadMinutes : 0;
        if (idleMinutes > 0) {
            long idleTarget = lastUsedAtMs + TimeUnit.MINUTES.toMillis(idleMinutes);
            target = Math.max(target, idleTarget);
        }
        return target;
    }

    private void cancelIdleUnloadLocked() {
        if (idleUnloadFuture != null) {
            idleUnloadFuture.cancel(false);
            idleUnloadFuture = null;
        }
    }

    private void maybeRefreshStateLocked() {
        if (engine == null) {
            if (!"failed".equals(runtimeState)) runtimeState = "unloaded";
            return;
        }
        if (generating) {
            if ("stopping".equals(runtimeState)) return;
            runtimeState = "generating";
        } else if (keepWarmUntilMs > System.currentTimeMillis()) {
            runtimeState = "idle-warm";
        } else {
            runtimeState = "loaded";
        }
    }

    private void closeEngineLocked(@NonNull String nextStatus, @NonNull String nextState) {
        cancelIdleUnloadLocked();
        closeEngineResourcesLocked();
        loadedModelId = null;
        loadedModelPath = null;
        loadedOptions = null;
        backendName = "none";
        backendFallbackReason = "";
        runtimeState = nextState;
        statusMessage = nextStatus;
        generating = false;
        activeGenerationId = null;
        activeGenerationStartedAtMs = 0L;
        loadedAtMs = 0L;
        lastUsedAtMs = 0L;
        keepWarmUntilMs = 0L;
        idleUnloadAtMs = 0L;
    }

    private void closeEngineResourcesLocked() {
        closeConversationLocked();
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception ignored) {
            }
        }
        engine = null;
    }

    private void closeConversationLocked() {
        if (conversation != null) {
            try {
                conversation.close();
            } catch (Exception ignored) {
            }
        }
        conversation = null;
        conversationKey = "";
    }

    private boolean isSupportedAbi() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) || "x86_64".equals(abi)) return true;
        }
        return false;
    }

    @NonNull
    private JSONObject stateEnvelopeLocked(boolean ok) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", ok);
        data.put("runtime", "litert-lm");
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    private JSONObject error(int statusCode, String code, String message) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", false);
        data.put("error", code);
        data.put("message", message);
        data.put("runtime", "litert-lm");
        data.put("state", getState().toJson());
        data.put("_statusCode", statusCode);
        return data;
    }

    @NonNull
    private String optionsKey(@NonNull TaiRuntimeOptions options) {
        return String.valueOf(options.maxTokens) + "|"
            + options.topK + "|"
            + options.topP + "|"
            + options.temperature + "|"
            + options.thinkingEnabled + "|"
            + options.speculativeDecodingEnabled;
    }

    @NonNull
    private String normalized(@NonNull String value) {
        return value.trim();
    }
}
