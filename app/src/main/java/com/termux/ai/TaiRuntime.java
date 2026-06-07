package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

public interface TaiRuntime {
    @NonNull TaiRuntimeState getState();
    boolean isModelLoaded(@NonNull String modelId);
    @NonNull JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException;
    @NonNull JSONObject unload() throws JSONException;
    @NonNull JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException;
    @NonNull JSONObject cancel() throws JSONException;
    @NonNull JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options) throws JSONException;
    @NonNull JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException;
    @NonNull JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException;
    @NonNull JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException;
    @NonNull JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException;
    @NonNull JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException;
}
