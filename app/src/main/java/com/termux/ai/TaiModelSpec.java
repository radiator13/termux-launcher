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
    public static final String BACKEND_LITERT_LM = "litert-lm";
    public static final String BACKEND_LLAMA_CPP = "llama-cpp";
    public static final String BACKEND_MLC = "mlc";
    public static final String FORMAT_LITERTLM = "litertlm";
    public static final String FORMAT_GGUF = "gguf";
    public static final String FORMAT_MLC_PACKAGE = "mlc-package";

    public final String id;
    public final String displayName;
    public final String roleHint;
    public final String source;
    @Nullable public final String localPath;
    public final String license;
    public final long sizeBytes;
    public final Set<String> capabilities;
    public final boolean builtInCatalogEntry;
    @Nullable public final TaiModelProfile runtimeProfile;
    @NonNull public final String backend;
    @NonNull public final String format;
    @Nullable public final String architecture;
    @Nullable public final String quantization;
    public final int contextWindow;
    public final int recommendedRamGb;
    @Nullable public final String sha256;
    @Nullable public final String runtimeLibrary;

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
        this(id, displayName, roleHint, source, localPath, license, sizeBytes, capabilities, true);
    }

    public TaiModelSpec(
        @NonNull String id,
        @NonNull String displayName,
        @NonNull String roleHint,
        @NonNull String source,
        @Nullable String localPath,
        @NonNull String license,
        long sizeBytes,
        @NonNull Set<String> capabilities,
        boolean builtInCatalogEntry
    ) {
        this(id, displayName, roleHint, source, localPath, license, sizeBytes, capabilities,
            builtInCatalogEntry, null);
    }

    public TaiModelSpec(
        @NonNull String id,
        @NonNull String displayName,
        @NonNull String roleHint,
        @NonNull String source,
        @Nullable String localPath,
        @NonNull String license,
        long sizeBytes,
        @NonNull Set<String> capabilities,
        boolean builtInCatalogEntry,
        @Nullable TaiModelProfile runtimeProfile
    ) {
        this(id, displayName, roleHint, source, localPath, license, sizeBytes, capabilities,
            builtInCatalogEntry, runtimeProfile, inferBackend(localPath), inferFormat(localPath),
            null, null, 4096, 0, null, null);
    }

    public TaiModelSpec(
        @NonNull String id,
        @NonNull String displayName,
        @NonNull String roleHint,
        @NonNull String source,
        @Nullable String localPath,
        @NonNull String license,
        long sizeBytes,
        @NonNull Set<String> capabilities,
        boolean builtInCatalogEntry,
        @Nullable TaiModelProfile runtimeProfile,
        @NonNull String backend,
        @NonNull String format,
        @Nullable String architecture,
        @Nullable String quantization,
        int contextWindow,
        int recommendedRamGb,
        @Nullable String sha256,
        @Nullable String runtimeLibrary
    ) {
        this.id = id;
        this.displayName = displayName;
        this.roleHint = roleHint;
        this.source = source;
        this.localPath = localPath;
        this.license = license;
        this.sizeBytes = sizeBytes;
        this.capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
        this.builtInCatalogEntry = builtInCatalogEntry;
        this.runtimeProfile = runtimeProfile;
        this.backend = backend;
        this.format = format;
        this.architecture = architecture;
        this.quantization = quantization;
        this.contextWindow = contextWindow > 0 ? contextWindow : 4096;
        this.recommendedRamGb = Math.max(0, recommendedRamGb);
        this.sha256 = sha256;
        this.runtimeLibrary = runtimeLibrary;
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
        json.put("builtInCatalogEntry", builtInCatalogEntry);
        json.put("runtimeProfile", TaiModelProfile.forModel(this).toJson());
        json.put("backend", backend);
        json.put("format", format);
        json.put("architecture", architecture == null ? JSONObject.NULL : architecture);
        json.put("quantization", quantization == null ? JSONObject.NULL : quantization);
        json.put("contextWindow", contextWindow);
        json.put("recommendedRamGb", recommendedRamGb);
        json.put("sha256", sha256 == null ? JSONObject.NULL : sha256);
        json.put("runtimeLibrary", runtimeLibrary == null ? JSONObject.NULL : runtimeLibrary);
        JSONArray capabilityArray = new JSONArray();
        for (String capability : capabilities) {
            capabilityArray.put(capability);
        }
        json.put("capabilities", capabilityArray);
        return json;
    }

    @NonNull
    public static TaiModelSpec fromJson(@NonNull JSONObject json) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        JSONArray capabilityArray = json.optJSONArray("capabilities");
        if (capabilityArray != null) {
            for (int i = 0; i < capabilityArray.length(); i++) {
                String capability = capabilityArray.optString(i, "");
                if (!capability.isEmpty()) capabilities.add(capability);
            }
        }
        return new TaiModelSpec(
            json.optString("id", ""),
            json.optString("displayName", json.optString("id", "")),
            json.optString("roleHint", "Imported model"),
            json.optString("source", "imported"),
            json.isNull("localPath") ? null : json.optString("localPath", null),
            json.optString("license", "User-provided model; license accepted externally"),
            json.optLong("sizeBytes", 0L),
            capabilities,
            json.optBoolean("builtInCatalogEntry", false),
            json.optJSONObject("runtimeProfile") == null ? null : TaiModelProfile.fromJson(json.optJSONObject("runtimeProfile")),
            json.optString("backend", inferBackend(json.optString("localPath", null))),
            json.optString("format", inferFormat(json.optString("localPath", null))),
            json.isNull("architecture") ? null : json.optString("architecture", null),
            json.isNull("quantization") ? null : json.optString("quantization", null),
            json.optInt("contextWindow", 4096),
            json.optInt("recommendedRamGb", 0),
            json.isNull("sha256") ? null : json.optString("sha256", null),
            json.isNull("runtimeLibrary") ? null : json.optString("runtimeLibrary", null)
        );
    }

    @NonNull
    public static String inferBackend(@Nullable String path) {
        return FORMAT_GGUF.equals(inferFormat(path)) ? BACKEND_LLAMA_CPP : BACKEND_LITERT_LM;
    }

    @NonNull
    public static String inferFormat(@Nullable String path) {
        String value = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        if (value.endsWith(".gguf")) return FORMAT_GGUF;
        return FORMAT_LITERTLM;
    }
}
