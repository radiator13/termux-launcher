package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TaiModelRegistry {
    public static final String MODEL_GEMMA_4_E2B_IT = "Gemma-4-E2B-it";
    public static final String MODEL_GEMMA_4_E4B_IT = "Gemma-4-E4B-it";
    public static final String MODEL_MOBILE_ACTIONS_270M = "MobileActions-270M";
    public static final String MODEL_QWEN_CODER_1_5B_GGUF = "Qwen2.5-Coder-1.5B-GGUF";
    public static final String MODEL_QWEN_CODER_1_5B_MLC = "Qwen2.5-Coder-1.5B-MLC";

    public static final String ROLE_DEFAULT_ASSISTANT = "defaultAssistant";

    private final Map<String, TaiModelSpec> builtInModels = new LinkedHashMap<>();

    public TaiModelRegistry() {
        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            addBuiltIn(new TaiModelSpec(
                entry.modelId,
                entry.displayName,
                entry.roleHint,
                "built-in-catalog",
                null,
                "User-provided model; review upstream license before download/import",
                0L,
                entry.capabilities,
                true,
                null,
                entry.backend,
                entry.format,
                entry.architecture,
                entry.quantization,
                entry.contextWindow,
                entry.recommendedRamGb,
                entry.sha256,
                entry.runtimeLibrary
            ));
        }
    }

    private void addBuiltIn(TaiModelSpec spec) {
        builtInModels.put(spec.id, spec);
    }

    @NonNull
    public Map<String, TaiModelSpec> getBuiltInModels() {
        return new LinkedHashMap<>(builtInModels);
    }

    @Nullable
    public TaiModelSpec getModel(@Nullable String modelId) {
        if (modelId == null) return null;
        return builtInModels.get(modelId);
    }

    @NonNull
    public JSONObject toJson(@NonNull TaiSettings settings) throws JSONException {
        return toJson(settings, new LinkedHashMap<>());
    }

    @NonNull
    public JSONObject toJson(@NonNull TaiSettings settings, @NonNull Map<String, TaiModelSpec> userModels) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        JSONArray models = new JSONArray();
        for (TaiModelSpec spec : builtInModels.values()) {
            models.put(spec.toJson());
        }
        for (TaiModelSpec spec : userModels.values()) {
            models.put(spec.toJson());
        }
        data.put("models", models);
        data.put("builtInCount", builtInModels.size());
        data.put("userModelCount", userModels.size());
        data.put("roles", settings.getRoleAssignmentsJson());
        data.put("downloadsRequireExplicitUserAction", true);
        data.put("bundledModelFiles", false);
        return data;
    }

}
