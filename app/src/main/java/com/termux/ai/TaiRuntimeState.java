package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class TaiRuntimeState {
    public final boolean loaded;
    @Nullable public final String loadedModelId;
    @NonNull public final String runtimeName;
    @NonNull public final String state;
    @NonNull public final String status;
    @NonNull public final String backend;
    @Nullable public final String backendFallbackReason;
    @Nullable public final String loadedModelPath;
    public final boolean activeGeneration;
    @Nullable public final String activeGenerationId;
    public final long activeGenerationStartedAtMs;
    public final long keepWarmUntilMs;
    public final long idleUnloadAtMs;
    public final long loadedAtMs;
    public final long lastUsedAtMs;

    public TaiRuntimeState(boolean loaded, @Nullable String loadedModelId, @NonNull String runtimeName, @NonNull String status) {
        this(
            loaded,
            loadedModelId,
            runtimeName,
            loaded ? "loaded" : "unloaded",
            status,
            "none",
            null,
            null,
            false,
            null,
            0L,
            0L,
            0L,
            0L,
            0L
        );
    }

    public TaiRuntimeState(
        boolean loaded,
        @Nullable String loadedModelId,
        @NonNull String runtimeName,
        @NonNull String state,
        @NonNull String status,
        @NonNull String backend,
        @Nullable String backendFallbackReason,
        @Nullable String loadedModelPath,
        boolean activeGeneration,
        @Nullable String activeGenerationId,
        long activeGenerationStartedAtMs,
        long keepWarmUntilMs,
        long idleUnloadAtMs,
        long loadedAtMs,
        long lastUsedAtMs
    ) {
        this.loaded = loaded;
        this.loadedModelId = loadedModelId;
        this.runtimeName = runtimeName;
        this.state = state;
        this.status = status;
        this.backend = backend;
        this.backendFallbackReason = backendFallbackReason;
        this.loadedModelPath = loadedModelPath;
        this.activeGeneration = activeGeneration;
        this.activeGenerationId = activeGenerationId;
        this.activeGenerationStartedAtMs = activeGenerationStartedAtMs;
        this.keepWarmUntilMs = keepWarmUntilMs;
        this.idleUnloadAtMs = idleUnloadAtMs;
        this.loadedAtMs = loadedAtMs;
        this.lastUsedAtMs = lastUsedAtMs;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        long now = System.currentTimeMillis();
        JSONObject json = new JSONObject();
        json.put("loaded", loaded);
        json.put("loadedModelId", loadedModelId == null ? JSONObject.NULL : loadedModelId);
        json.put("runtimeName", runtimeName);
        json.put("state", state);
        json.put("status", status);
        json.put("backend", backend);
        json.put("backendFallbackReason", backendFallbackReason == null || backendFallbackReason.isEmpty() ? JSONObject.NULL : backendFallbackReason);
        json.put("loadedModelPath", loadedModelPath == null ? JSONObject.NULL : loadedModelPath);
        json.put("activeGeneration", activeGeneration);
        json.put("activeGenerationId", activeGenerationId == null ? JSONObject.NULL : activeGenerationId);
        json.put("activeGenerationStartedAtMs", activeGenerationStartedAtMs);
        json.put("keepWarmUntilMs", keepWarmUntilMs);
        json.put("keepWarmRemainingMs", keepWarmUntilMs > now ? keepWarmUntilMs - now : 0L);
        json.put("idleUnloadAtMs", idleUnloadAtMs);
        json.put("idleUnloadRemainingMs", idleUnloadAtMs > now ? idleUnloadAtMs - now : 0L);
        json.put("loadedAtMs", loadedAtMs);
        json.put("lastUsedAtMs", lastUsedAtMs);
        return json;
    }
}
