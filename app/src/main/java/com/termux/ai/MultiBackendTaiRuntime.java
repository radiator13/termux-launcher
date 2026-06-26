package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class MultiBackendTaiRuntime implements TaiRuntime {
    private final DualSlotTaiRuntime liteRt;
    private final MnnTaiRuntime mnn;
    private final LiteRtEmbeddingRuntime embeddings;
    private TaiRuntime activeAssistant;

    public MultiBackendTaiRuntime(@NonNull Context context) {
        liteRt = new DualSlotTaiRuntime(context);
        mnn = new MnnTaiRuntime(context);
        embeddings = new LiteRtEmbeddingRuntime();
        activeAssistant = liteRt;
    }

    @NonNull @Override public synchronized TaiRuntimeState getState() {
        TaiRuntimeState assistant = activeAssistant.getState();
        TaiRuntimeState liteState = liteRt.getState();
        TaiRuntimeState mnnState = mnn.getState();
        boolean includeLiteRtCompanion = activeAssistant != liteRt && liteState.loaded && liteState.loadedModelId != null
            && liteState.loadedModelId.contains(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M);
        boolean includeMnn = activeAssistant != mnn && (mnnState.loaded || mnnState.activeGeneration || !"unloaded".equals(mnnState.state));
        if (!includeLiteRtCompanion && !includeMnn) return assistant;
        JSONObject extra = new JSONObject();
        try {
            extra.put("assistant", assistant.toJson());
            if (includeLiteRtCompanion) extra.put("mobileActions", liteState.toJson());
            if (includeMnn) extra.put("mnn", mnnState.toJson());
        } catch (JSONException ignored) {}
        return new TaiRuntimeState(assistant.loaded || liteState.loaded || mnnState.loaded, assistant.loadedModelId,
            "tai-multi-backend", assistant.state, assistant.status, assistant.backend,
            assistant.backendFallbackReason, assistant.loadedModelPath,
            assistant.activeGeneration || liteState.activeGeneration || mnnState.activeGeneration, assistant.activeGenerationId,
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
        embeddings.close();
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

    @NonNull @Override public synchronized JSONObject cancel() throws JSONException {
        JSONObject assistant = activeAssistant.cancel();
        if (activeAssistant != liteRt) liteRt.cancel();
        return assistant;
    }

    @NonNull @Override public synchronized JSONObject chat(@NonNull String id, @NonNull String system, @NonNull String user, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).chat(id, system, user, options); }
    @NonNull @Override public synchronized JSONObject chat(@NonNull String id, @NonNull String system, @NonNull String user, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).chat(id, system, user, options, callback); }
    @NonNull @Override public synchronized JSONObject chat(@NonNull String id, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).chat(id, request, options); }
    @NonNull @Override public synchronized JSONObject chat(@NonNull String id, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).chat(id, request, options, callback); }
    @NonNull @Override public synchronized JSONObject complete(@NonNull String id, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException { return runtimeForId(id).complete(id, prompt, options); }
    @NonNull @Override public synchronized JSONObject complete(@NonNull String id, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return runtimeForId(id).complete(id, prompt, options, callback); }

    @NonNull
    public synchronized JSONObject embed(@NonNull String modelId, @NonNull String input) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", "Embeddings are not available for the active LiteRT/MNN backends.");
        error.put("type", "invalid_request_error");
        error.put("code", "capability_not_supported");
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", 400);
        return response;
    }

    @NonNull
    public synchronized JSONObject embed(@NonNull TaiModelSpec model, @NonNull List<String> inputs, int dimensions) throws JSONException {
        if (isLiteRtEmbeddingFlatbuffer(model)) return embeddings.embed(model, inputs, dimensions);
        if (inputs.size() == 1 && dimensions <= 0) return embed(model.id, inputs.get(0));
        JSONObject error = new JSONObject();
        error.put("message", "Embeddings are not available for model '" + model.id + "'.");
        error.put("type", "invalid_request_error");
        error.put("param", "model");
        error.put("code", "capability_not_supported");
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", 400);
        return response;
    }

    private synchronized TaiRuntime runtimeForId(String id) {
        if (isMobileActions(id)) return liteRt;
        TaiRuntimeState mnnState = mnn.getState();
        if (mnnState.loadedModelId != null && mnnState.loadedModelId.equals(id)) return mnn;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
        if (entry != null && TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)) return mnn;
        return activeAssistant;
    }

    private TaiRuntime runtimeForModel(TaiModelSpec model) {
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)) return mnn;
        return liteRt;
    }

    private boolean isLiteRtEmbeddingFlatbuffer(@NonNull TaiModelSpec model) {
        String path = model.localPath == null ? "" : model.localPath.toLowerCase(java.util.Locale.ROOT);
        return TaiModelSpec.BACKEND_LITERT_LM.equals(model.backend)
            && model.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)
            && path.endsWith(".tflite");
    }

    private boolean isMobileActions(String id) { return TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M.equals(id); }

    private JSONObject error(String code, String message) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("ok", false); result.put("error", code); result.put("message", message); result.put("_statusCode", 409);
        return result;
    }
}
