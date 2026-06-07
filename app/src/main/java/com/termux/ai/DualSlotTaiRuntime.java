package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public final class DualSlotTaiRuntime implements TaiRuntime {
    private final LiteRtTaiRuntime assistantRuntime;
    private final LiteRtTaiRuntime mobileActionsRuntime;

    public DualSlotTaiRuntime(@NonNull Context context) {
        assistantRuntime = new LiteRtTaiRuntime(context);
        mobileActionsRuntime = new LiteRtTaiRuntime(context);
    }

    @NonNull
    @Override
    public TaiRuntimeState getState() {
        TaiRuntimeState assistant = assistantRuntime.getState();
        TaiRuntimeState mobileActions = mobileActionsRuntime.getState();
        boolean loaded = assistant.loaded || mobileActions.loaded;
        boolean activeGeneration = assistant.activeGeneration || mobileActions.activeGeneration;

        String loadedModelId = combinedLoadedModelId(assistant, mobileActions);
        String backend = combinedBackend(assistant, mobileActions);
        String state = combinedState(assistant, mobileActions);
        String status = "Assistant slot: " + assistant.status + " MobileActions slot: " + mobileActions.status;
        JSONObject extra = new JSONObject();
        try {
            JSONObject slots = new JSONObject();
            slots.put("assistant", assistant.toJson());
            slots.put("mobileActions", mobileActions.toJson());
            extra.put("slots", slots);
            extra.put("slotPolicy", "Assistant models use the assistant slot; MobileActions-270M uses a separate CPU slot.");
        } catch (JSONException ignored) {
        }

        return new TaiRuntimeState(
            loaded,
            loadedModelId,
            "litert-lm-dual-slot",
            state,
            status,
            backend,
            combinedFallbackReason(assistant, mobileActions),
            assistant.loadedModelPath != null ? assistant.loadedModelPath : mobileActions.loadedModelPath,
            activeGeneration,
            assistant.activeGenerationId != null ? assistant.activeGenerationId : mobileActions.activeGenerationId,
            Math.max(assistant.activeGenerationStartedAtMs, mobileActions.activeGenerationStartedAtMs),
            Math.max(assistant.keepWarmUntilMs, mobileActions.keepWarmUntilMs),
            Math.max(assistant.idleUnloadAtMs, mobileActions.idleUnloadAtMs),
            Math.max(assistant.loadedAtMs, mobileActions.loadedAtMs),
            Math.max(assistant.lastUsedAtMs, mobileActions.lastUsedAtMs),
            extra
        );
    }

    @Override
    public boolean isModelLoaded(@NonNull String modelId) {
        return runtimeForModel(modelId).isModelLoaded(modelId);
    }

    @NonNull
    @Override
    public JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        return runtimeForModel(modelSpec.id).load(modelSpec, optionsForModel(modelSpec.id, options));
    }

    @NonNull
    @Override
    public JSONObject unload() throws JSONException {
        TaiRuntimeState assistant = assistantRuntime.getState();
        TaiRuntimeState mobileActions = mobileActionsRuntime.getState();
        if (assistant.activeGeneration || mobileActions.activeGeneration) {
            return error(409, "generation_active", "Cancel active generation before unloading TAI runtimes.");
        }
        JSONObject assistantResult = assistantRuntime.unload();
        JSONObject mobileActionsResult = mobileActionsRuntime.unload();
        JSONObject data = envelope(true);
        data.put("assistant", assistantResult);
        data.put("mobileActions", mobileActionsResult);
        return data;
    }

    @NonNull
    @Override
    public JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        return runtimeForModel(modelSpec.id).keepWarm(modelSpec, optionsForModel(modelSpec.id, options), minutes);
    }

    @NonNull
    @Override
    public JSONObject cancel() throws JSONException {
        JSONObject assistantResult = assistantRuntime.cancel();
        JSONObject mobileActionsResult = mobileActionsRuntime.cancel();
        JSONObject data = envelope(true);
        data.put("cancelled", assistantResult.optBoolean("cancelled", false) || mobileActionsResult.optBoolean("cancelled", false));
        data.put("assistant", assistantResult);
        data.put("mobileActions", mobileActionsResult);
        data.put("message", data.optBoolean("cancelled", false) ? "Cancel requested." : "No active generation.");
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
        return runtimeForModel(modelId).chat(modelId, systemPrompt, userPrompt, optionsForModel(modelId, options));
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
        return runtimeForModel(modelId).chat(modelId, systemPrompt, userPrompt, optionsForModel(modelId, options), callback);
    }

    @NonNull
    @Override
    public JSONObject chat(
        @NonNull String modelId,
        @NonNull TaiChatRequest request,
        @NonNull TaiRuntimeOptions options
    ) throws JSONException {
        return runtimeForModel(modelId).chat(modelId, request, optionsForModel(modelId, options));
    }

    @NonNull
    @Override
    public JSONObject chat(
        @NonNull String modelId,
        @NonNull TaiChatRequest request,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiGenerationCallback callback
    ) throws JSONException {
        return runtimeForModel(modelId).chat(modelId, request, optionsForModel(modelId, options), callback);
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return runtimeForModel(modelId).complete(modelId, prompt, optionsForModel(modelId, options));
    }

    @NonNull
    @Override
    public JSONObject complete(
        @NonNull String modelId,
        @NonNull String prompt,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiGenerationCallback callback
    ) throws JSONException {
        return runtimeForModel(modelId).complete(modelId, prompt, optionsForModel(modelId, options), callback);
    }

    private LiteRtTaiRuntime runtimeForModel(@NonNull String modelId) {
        return isMobileActions(modelId) ? mobileActionsRuntime : assistantRuntime;
    }

    @NonNull
    private TaiRuntimeOptions optionsForModel(@NonNull String modelId, @NonNull TaiRuntimeOptions options) {
        return isMobileActions(modelId) ? options.withAccelerator("cpu") : options;
    }

    private boolean isMobileActions(@NonNull String modelId) {
        return TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M.equals(modelId);
    }

    private String combinedLoadedModelId(TaiRuntimeState assistant, TaiRuntimeState mobileActions) {
        if (assistant.loadedModelId != null && mobileActions.loadedModelId != null) {
            return assistant.loadedModelId + " + " + mobileActions.loadedModelId;
        }
        return assistant.loadedModelId != null ? assistant.loadedModelId : mobileActions.loadedModelId;
    }

    private String combinedBackend(TaiRuntimeState assistant, TaiRuntimeState mobileActions) {
        if (!"none".equalsIgnoreCase(assistant.backend) && !"none".equalsIgnoreCase(mobileActions.backend)) {
            return assistant.backend + "+" + mobileActions.backend;
        }
        return !"none".equalsIgnoreCase(assistant.backend) ? assistant.backend : mobileActions.backend;
    }

    private String combinedState(TaiRuntimeState assistant, TaiRuntimeState mobileActions) {
        if ("generating".equals(assistant.state) || "generating".equals(mobileActions.state)) return "generating";
        if ("loading".equals(assistant.state) || "loading".equals(mobileActions.state)) return "loading";
        if ("stopping".equals(assistant.state) || "stopping".equals(mobileActions.state)) return "stopping";
        if ("idle-warm".equals(assistant.state) || "idle-warm".equals(mobileActions.state)) return "idle-warm";
        if (assistant.loaded || mobileActions.loaded) return "loaded";
        if ("failed".equals(assistant.state) || "failed".equals(mobileActions.state)) return "failed";
        return "unloaded";
    }

    private String combinedFallbackReason(TaiRuntimeState assistant, TaiRuntimeState mobileActions) {
        StringBuilder builder = new StringBuilder();
        if (assistant.backendFallbackReason != null && !assistant.backendFallbackReason.isEmpty()) {
            builder.append("Assistant: ").append(assistant.backendFallbackReason);
        }
        if (mobileActions.backendFallbackReason != null && !mobileActions.backendFallbackReason.isEmpty()) {
            if (builder.length() > 0) builder.append(" ");
            builder.append("MobileActions: ").append(mobileActions.backendFallbackReason);
        }
        return builder.toString();
    }

    @NonNull
    private JSONObject envelope(boolean ok) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", ok);
        data.put("runtime", "litert-lm-dual-slot");
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    private JSONObject error(int statusCode, String code, String message) throws JSONException {
        JSONObject data = envelope(false);
        data.put("error", code);
        data.put("message", message);
        data.put("_statusCode", statusCode);
        return data;
    }
}
