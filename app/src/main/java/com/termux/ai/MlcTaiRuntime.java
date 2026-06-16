package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public final class MlcTaiRuntime implements TaiRuntime {
    private String runtimeState = "unloaded";
    private String statusMessage = "MLC runtime is unloaded.";
    private long keepWarmUntilMs;
    private long lastUsedAtMs;

    @NonNull
    @Override
    public synchronized TaiRuntimeState getState() {
        return new TaiRuntimeState(
            false,
            null,
            TaiModelSpec.BACKEND_MLC_LLM,
            runtimeState,
            statusMessage,
            TaiModelSpec.BACKEND_MLC_LLM,
            null,
            null,
            false,
            null,
            0L,
            keepWarmUntilMs,
            0L,
            0L,
            lastUsedAtMs
        );
    }

    @Override
    public synchronized boolean isModelLoaded(@NonNull String modelId) {
        return false;
    }

    @NonNull
    @Override
    public synchronized JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        runtimeState = "failed";
        statusMessage = "MLC runtime is not available in this build.";
        lastUsedAtMs = System.currentTimeMillis();
        JSONObject data = envelope(false);
        data.put("error", "mlc_runtime_unavailable");
        data.put("message", statusMessage);
        data.put("modelId", modelSpec.id);
        data.put("_statusCode", 501);
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject unload() throws JSONException {
        runtimeState = "unloaded";
        statusMessage = "MLC runtime is unloaded.";
        keepWarmUntilMs = 0L;
        JSONObject data = envelope(true);
        data.put("unloadedModelId", JSONObject.NULL);
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        int keepWarmMinutes = minutes > 0 ? minutes : 30;
        runtimeState = "idle-warm";
        statusMessage = "MLC runtime stub is warm but no model is loaded.";
        keepWarmUntilMs = System.currentTimeMillis() + java.util.concurrent.TimeUnit.MINUTES.toMillis(keepWarmMinutes);
        lastUsedAtMs = System.currentTimeMillis();
        JSONObject data = envelope(true);
        data.put("keepWarm", true);
        data.put("keepWarmMinutes", keepWarmMinutes);
        data.put("keepWarmUntilMs", keepWarmUntilMs);
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject cancel() throws JSONException {
        JSONObject data = envelope(true);
        data.put("cancelled", false);
        data.put("message", "No active MLC generation.");
        return data;
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return unsupported(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return unsupported(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException {
        return unsupported(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return unsupported(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return unsupported(modelId, "completion");
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return unsupported(modelId, "completion");
    }

    @NonNull
    private synchronized JSONObject envelope(boolean ok) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", ok);
        data.put("runtime", TaiModelSpec.BACKEND_MLC_LLM);
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    private JSONObject unsupported(@NonNull String modelId, @NonNull String operation) throws JSONException {
        JSONObject data = envelope(false);
        data.put("error", "unsupported_operation");
        data.put("message", "MLC " + operation + " is unavailable until the MLC runtime is implemented.");
        data.put("modelId", modelId);
        data.put("_statusCode", 501);
        return data;
    }
}
