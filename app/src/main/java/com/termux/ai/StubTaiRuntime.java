package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public final class StubTaiRuntime implements TaiRuntime {
    private String loadedModelId;

    @NonNull
    @Override
    public synchronized TaiRuntimeState getState() {
        return new TaiRuntimeState(
            loadedModelId != null,
            loadedModelId,
            "stub",
            "LiteRT-LM is not integrated yet; API, settings, safety planner, and shell bridge are active."
        );
    }

    @NonNull
    @Override
    public synchronized JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        loadedModelId = modelSpec.id;
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("loadedModelId", loadedModelId);
        data.put("runtime", "stub");
        data.put("options", options.toJson());
        data.put("message", "Stub runtime marked the model as loaded. No model file was downloaded or mapped.");
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject unload() throws JSONException {
        String previous = loadedModelId;
        loadedModelId = null;
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("unloadedModelId", previous == null ? JSONObject.NULL : previous);
        data.put("runtime", "stub");
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject chat(
        @NonNull String modelId,
        @NonNull String systemPrompt,
        @NonNull String userPrompt,
        @NonNull TaiRuntimeOptions options
    ) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("model", modelId);
        data.put("runtime", "stub");
        data.put("loaded", modelId.equals(loadedModelId));
        data.put("response", "TAI stub response: local model inference is not wired yet. Prompt received: " + userPrompt);
        data.put("options", options.toJson());
        data.put("todo", "Replace StubTaiRuntime with a LiteRT-LM implementation behind TaiRuntime.");
        return data;
    }
}
