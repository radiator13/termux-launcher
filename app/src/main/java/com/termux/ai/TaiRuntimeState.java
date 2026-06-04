package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class TaiRuntimeState {
    public final boolean loaded;
    @Nullable public final String loadedModelId;
    @NonNull public final String runtimeName;
    @NonNull public final String status;

    public TaiRuntimeState(boolean loaded, @Nullable String loadedModelId, @NonNull String runtimeName, @NonNull String status) {
        this.loaded = loaded;
        this.loadedModelId = loadedModelId;
        this.runtimeName = runtimeName;
        this.status = status;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("loaded", loaded);
        json.put("loadedModelId", loadedModelId == null ? JSONObject.NULL : loadedModelId);
        json.put("runtimeName", runtimeName);
        json.put("status", status);
        return json;
    }
}
