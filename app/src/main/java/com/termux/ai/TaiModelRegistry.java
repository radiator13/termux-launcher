package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class TaiModelRegistry {
    public static final String MODEL_GEMMA_4_E2B_IT = "Gemma-4-E2B-it";
    public static final String MODEL_GEMMA_4_E4B_IT = "Gemma-4-E4B-it";
    public static final String MODEL_MOBILE_ACTIONS_270M = "MobileActions-270M";

    public static final String ROLE_DEFAULT_ASSISTANT = "defaultAssistant";
    public static final String ROLE_CODING_BUILD = "codingBuild";
    public static final String ROLE_MOBILE_ACTIONS = "mobileActions";

    private final Map<String, TaiModelSpec> builtInModels = new LinkedHashMap<>();

    public TaiModelRegistry() {
        addBuiltIn(new TaiModelSpec(
            MODEL_GEMMA_4_E2B_IT,
            "Gemma 4 E2B IT",
            "Fast assistant",
            "built-in-catalog",
            null,
            "User-provided model; review upstream license before download/import",
            0L,
            setOf("text_chat", "image_input", "tool_use")
        ));
        addBuiltIn(new TaiModelSpec(
            MODEL_GEMMA_4_E4B_IT,
            "Gemma 4 E4B IT",
            "Coding, build, and reasoning",
            "built-in-catalog",
            null,
            "User-provided model; review upstream license before download/import",
            0L,
            setOf("text_chat", "image_input", "tool_use")
        ));
        addBuiltIn(new TaiModelSpec(
            MODEL_MOBILE_ACTIONS_270M,
            "MobileActions 270M",
            "Mobile action routing",
            "built-in-catalog",
            null,
            "User-provided model; review upstream license before download/import",
            0L,
            setOf("text_chat", "tool_use", "mobile_actions")
        ));
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
        JSONObject data = new JSONObject();
        data.put("ok", true);
        JSONArray models = new JSONArray();
        for (TaiModelSpec spec : builtInModels.values()) {
            models.put(spec.toJson());
        }
        data.put("models", models);
        data.put("roles", settings.getRoleAssignmentsJson());
        data.put("downloadsRequireExplicitUserAction", true);
        data.put("bundledModelFiles", false);
        return data;
    }

    private static LinkedHashSet<String> setOf(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }
}
