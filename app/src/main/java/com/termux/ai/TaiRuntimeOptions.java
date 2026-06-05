package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class TaiRuntimeOptions {
    @Nullable public final Integer maxTokens;
    @Nullable public final Integer topK;
    @Nullable public final Double topP;
    @Nullable public final Double temperature;
    @Nullable public final String accelerator;
    @Nullable public final Boolean thinkingEnabled;
    @Nullable public final Boolean speculativeDecodingEnabled;
    @Nullable public final Integer idleUnloadMinutes;

    public TaiRuntimeOptions(
        @Nullable Integer maxTokens,
        @Nullable Integer topK,
        @Nullable Double topP,
        @Nullable Double temperature,
        @Nullable String accelerator,
        @Nullable Boolean thinkingEnabled,
        @Nullable Boolean speculativeDecodingEnabled,
        @Nullable Integer idleUnloadMinutes
    ) {
        this.maxTokens = maxTokens;
        this.topK = topK;
        this.topP = topP;
        this.temperature = temperature;
        this.accelerator = accelerator;
        this.thinkingEnabled = thinkingEnabled;
        this.speculativeDecodingEnabled = speculativeDecodingEnabled;
        this.idleUnloadMinutes = idleUnloadMinutes;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        putNullable(json, "maxTokens", maxTokens);
        putNullable(json, "topK", topK);
        putNullable(json, "topP", topP);
        putNullable(json, "temperature", temperature);
        putNullable(json, "accelerator", accelerator);
        putNullable(json, "thinkingEnabled", thinkingEnabled);
        putNullable(json, "speculativeDecodingEnabled", speculativeDecodingEnabled);
        putNullable(json, "idleUnloadMinutes", idleUnloadMinutes);
        json.put("usesGalleryGenerationDefaultsForNulls", true);
        return json;
    }

    @NonNull
    public TaiRuntimeOptions withAccelerator(@Nullable String overrideAccelerator) {
        return new TaiRuntimeOptions(
            maxTokens,
            topK,
            topP,
            temperature,
            overrideAccelerator,
            thinkingEnabled,
            speculativeDecodingEnabled,
            idleUnloadMinutes
        );
    }

    @NonNull
    public TaiRuntimeOptions withGenerationOverrides(
        @Nullable Integer overrideMaxTokens,
        @Nullable Integer overrideTopK,
        @Nullable Double overrideTopP,
        @Nullable Double overrideTemperature,
        @Nullable String overrideAccelerator,
        @Nullable Boolean overrideThinkingEnabled,
        @Nullable Boolean overrideSpeculativeDecodingEnabled
    ) {
        return new TaiRuntimeOptions(
            overrideMaxTokens != null ? overrideMaxTokens : maxTokens,
            overrideTopK != null ? overrideTopK : topK,
            overrideTopP != null ? overrideTopP : topP,
            overrideTemperature != null ? overrideTemperature : temperature,
            overrideAccelerator != null ? overrideAccelerator : accelerator,
            overrideThinkingEnabled != null ? overrideThinkingEnabled : thinkingEnabled,
            overrideSpeculativeDecodingEnabled != null ? overrideSpeculativeDecodingEnabled : speculativeDecodingEnabled,
            idleUnloadMinutes
        );
    }

    private static void putNullable(JSONObject json, String key, Object value) throws JSONException {
        json.put(key, value == null ? JSONObject.NULL : value);
    }
}
