package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TaiModelSpec {
    public final String id;
    public final String displayName;
    public final String roleHint;
    public final String source;
    @Nullable public final String localPath;
    public final String license;
    public final long sizeBytes;
    public final Set<String> capabilities;

    public TaiModelSpec(
        @NonNull String id,
        @NonNull String displayName,
        @NonNull String roleHint,
        @NonNull String source,
        @Nullable String localPath,
        @NonNull String license,
        long sizeBytes,
        @NonNull Set<String> capabilities
    ) {
        this.id = id;
        this.displayName = displayName;
        this.roleHint = roleHint;
        this.source = source;
        this.localPath = localPath;
        this.license = license;
        this.sizeBytes = sizeBytes;
        this.capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("displayName", displayName);
        json.put("roleHint", roleHint);
        json.put("source", source);
        json.put("localPath", localPath == null ? JSONObject.NULL : localPath);
        json.put("license", license);
        json.put("sizeBytes", sizeBytes);
        JSONArray capabilityArray = new JSONArray();
        for (String capability : capabilities) {
            capabilityArray.put(capability);
        }
        json.put("capabilities", capabilityArray);
        return json;
    }
}
