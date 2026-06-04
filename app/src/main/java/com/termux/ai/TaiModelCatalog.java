package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class TaiModelCatalog {
    private static final Map<String, CatalogEntry> ENTRIES = buildEntries();

    private TaiModelCatalog() {
    }

    @NonNull
    public static Map<String, CatalogEntry> entries() {
        return ENTRIES;
    }

    @Nullable
    public static CatalogEntry get(@Nullable String modelId) {
        if (modelId == null) return null;
        return ENTRIES.get(modelId);
    }

    @NonNull
    private static Map<String, CatalogEntry> buildEntries() {
        LinkedHashMap<String, CatalogEntry> entries = new LinkedHashMap<>();
        entries.put(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, new CatalogEntry(
            TaiModelRegistry.MODEL_GEMMA_4_E2B_IT,
            "Gemma 4 E2B IT",
            "Fast assistant",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm",
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            "Apache-2.0",
            2_588_147_712L,
            false,
            setOf("text_chat", "image_input", "tool_use")
        ));
        entries.put(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT, new CatalogEntry(
            TaiModelRegistry.MODEL_GEMMA_4_E4B_IT,
            "Gemma 4 E4B IT",
            "Coding, build, and reasoning",
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm",
            "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm",
            "Apache-2.0",
            3_660_000_000L,
            false,
            setOf("text_chat", "image_input", "tool_use")
        ));
        entries.put(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M, new CatalogEntry(
            TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M,
            "MobileActions 270M",
            "Mobile action routing",
            "https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions",
            "https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions/resolve/main/mobile_actions_q8_ekv1024.litertlm",
            "Gemma",
            289_000_000L,
            true,
            setOf("text_chat", "tool_use", "mobile_actions")
        ));
        return Collections.unmodifiableMap(entries);
    }

    private static LinkedHashSet<String> setOf(String... values) {
        return new LinkedHashSet<>(Arrays.asList(values));
    }

    public static final class CatalogEntry {
        public final String modelId;
        public final String displayName;
        public final String roleHint;
        public final String providerPageUrl;
        public final String downloadUrl;
        public final String license;
        public final long sizeBytes;
        public final boolean gated;
        public final LinkedHashSet<String> capabilities;

        private CatalogEntry(
            @NonNull String modelId,
            @NonNull String displayName,
            @NonNull String roleHint,
            @NonNull String providerPageUrl,
            @NonNull String downloadUrl,
            @NonNull String license,
            long sizeBytes,
            boolean gated,
            @NonNull LinkedHashSet<String> capabilities
        ) {
            this.modelId = modelId;
            this.displayName = displayName;
            this.roleHint = roleHint;
            this.providerPageUrl = providerPageUrl;
            this.downloadUrl = downloadUrl;
            this.license = license;
            this.sizeBytes = sizeBytes;
            this.gated = gated;
            this.capabilities = capabilities;
        }
    }
}
