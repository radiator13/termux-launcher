package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public interface TaiRuntime {
    @NonNull TaiRuntimeState getState();
    @NonNull JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException;
    @NonNull JSONObject unload() throws JSONException;
    @NonNull JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options) throws JSONException;
}
