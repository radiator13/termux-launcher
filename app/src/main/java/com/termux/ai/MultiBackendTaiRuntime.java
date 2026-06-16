package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public final class MultiBackendTaiRuntime implements TaiRuntime {
    private final DualSlotTaiRuntime liteRt;
    private final MlcTaiRuntime mlc;
    private TaiRuntime activeAssistant;

    public MultiBackendTaiRuntime(@NonNull Context context) {
        liteRt = new DualSlotTaiRuntime(context);
        mlc = new MlcTaiRuntime();
        activeAssistant = liteRt;
    }

    @NonNull @Override public synchronized TaiRuntimeState getState() {
        TaiRuntimeState assistant = activeAssistant.getState();
        TaiRuntimeState liteState = liteRt.getState();
        TaiRuntimeState mlcState = mlc.getState();
        boolean includeLiteRtCompanion = activeAssistant != liteRt && liteState.loaded && liteState.loadedModelId != null
            && liteState.loadedModelId.contains(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M);
        boolean includeMlc = activeAssistant != mlc && (mlcState.loaded || mlcState.activeGeneration || !"unloaded".equals(mlcState.state));
        if (!includeLiteRtCompanion && !includeMlc) return assistant;
        JSONObject extra = new JSONObject();
        try {
            extra.put("assistant", assistant.toJson());
            if (includeLiteRtCompanion) extra.put("mobileActions", liteState.toJson());
            if (includeMlc) extra.put("mlc", mlcState.toJson());
        } catch (JSONException ignored) {}
        return new TaiRuntimeState(assistant.loaded || liteState.loaded || mlcState.loaded, assistant.loadedModelId,
            "tai-multi-backend", assistant.state, assistant.status, assistant.backend,
            assistant.backendFallbackReason, assistant.loadedModelPath,
            assistant.activeGeneration || liteState.activeGeneration || mlcState.activeGeneration, assistant.activeGenerationId,
            assistant.activeGenerationStartedAtMs, assistant.keepWarmUntilMs, assistant.idleUnloadAtMs,
            assistant.loadedAtMs, assistant.lastUsedAtMs, extra);
    }

    @Override public synchronized boolean isModelLoaded(@NonNull String modelId) {
        return runtimeForId(modelId).isModelLoaded(modelId);
    }

    @NonNull @Override public synchronized JSONObject load(@NonNull TaiModelSpec model, @NonNull TaiRuntimeOptions options) throws JSONException {
        TaiRuntime target = runtimeForModel(model);
        if (isMobileActions(model.id)) return liteRt.load(model, options);
        if (target != activeAssistant) {
            TaiRuntimeState current = activeAssistant.getState();
            if (current.activeGeneration) return error("generation_active", "Cancel active generation before switching AI backends.");
            activeAssistant.unload();
            activeAssistant = target;
        }
        return target.load(model, options);
    }

    @NonNull @Override public synchronized JSONObject unload() throws JSONException {
        JSONObject assistant = activeAssistant.unload();
        JSONObject companion = activeAssistant == liteRt ? new JSONObject() : liteRt.unload();
        JSONObject result = new JSONObject();
        result.put("ok", assistant.optBoolean("ok", false) && (companion.length() == 0 || companion.optBoolean("ok", false)));
        result.put("runtime", "tai-multi-backend");
        result.put("assistant", assistant);
        if (companion.length() > 0) result.put("mobileActions", companion);
        return result;
    }

    @NonNull @Override public synchronized JSONObject keepWarm(@NonNull TaiModelSpec model, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        TaiRuntime target = runtimeForModel(model);
        if (target != activeAssistant && !isMobileActions(model.id)) {
            activeAssistant.unload();
            activeAssistant = target;
        }
        return target.keepWarm(model, options, minutes);
    }

    @NonNull @Override public JSONObject cancel() throws JSONException {
        JSONObject assistant = activeAssistant.cancel();
        if (activeAssistant != liteRt) liteRt.cancel();
        return assistant;
    }

    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull String system, @NonNull String user, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).chat(id, system, user, options); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull String system, @NonNull String user, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).chat(id, system, user, options, callback); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).chat(id, request, options); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).chat(id, request, options, callback); }
    @NonNull @Override public JSONObject complete(@NonNull String id, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).complete(id, prompt, options); }
    @NonNull @Override public JSONObject complete(@NonNull String id, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).complete(id, prompt, options, callback); }

    private synchronized TaiRuntime runtimeForId(String id) {
        if (isMobileActions(id)) return liteRt;
        TaiRuntimeState mlcState = mlc.getState();
        if (mlcState.loadedModelId != null && mlcState.loadedModelId.equals(id)) return mlc;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
        if (entry != null && TaiModelSpec.BACKEND_MLC_LLM.equals(entry.backend)) return mlc;
        return activeAssistant;
    }

    private TaiRuntime runtimeForModel(TaiModelSpec model) {
        if (TaiModelSpec.BACKEND_MLC_LLM.equals(model.backend)) return mlc;
        return liteRt;
    }

    private boolean isMobileActions(String id) { return TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M.equals(id); }

    private JSONObject error(String code, String message) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("ok", false); result.put("error", code); result.put("message", message); result.put("_statusCode", 409);
        return result;
    }
}
