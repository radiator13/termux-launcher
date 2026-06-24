package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class TaiModelCatalog {
    private static final String UNVERIFIED_ARTIFACT_POLICY = "Import-only: models.yaml provides repository URL and estimates, but no verified artifact path, revision, and checksum policy exists in code.";
    private static final Map<String, CatalogEntry> BUILT_IN_ENTRIES = buildEntries();
    private static volatile Map<String, CatalogEntry> entries = BUILT_IN_ENTRIES;
    private TaiModelCatalog() {}

    @NonNull public static Map<String, CatalogEntry> entries() { return entries; }
    @Nullable public static CatalogEntry get(@Nullable String modelId) { return modelId == null ? null : entries.get(modelId); }

    /** Synthetic catalog entry for an installed model that isn't in the curated catalog (imported or
     *  added by Hugging Face URL), so the catalog screen lists and manages it alongside built-ins. */
    @NonNull
    public static CatalogEntry installedModelEntry(@NonNull TaiModelSpec spec) {
        return new CatalogEntry(
            spec.id, spec.displayName,
            spec.roleHint == null || spec.roleHint.isEmpty() ? "Added model" : spec.roleHint,
            "", "main", null, spec.license, spec.sizeBytes, false,
            spec.backend, spec.format, spec.architecture, spec.quantization,
            spec.endpointContextWindow, spec.sourceContextWindow, spec.defaultMaxOutputTokens,
            spec.recommendedRamGb, spec.sha256,
            new LinkedHashSet<>(spec.sourceCapabilities),
            new LinkedHashSet<>(spec.endpointCapabilities), spec.toolMode,
            "installed", "installed",
            new LinkedHashSet<>(), "", "", false, false, "Already installed");
    }

    static synchronized void applyRemotePayload(@NonNull JSONObject payload, boolean allowEqualVersion) {
        JSONArray remoteEntries = payload.optJSONArray("entries");
        if (remoteEntries == null) return;
        LinkedHashMap<String, CatalogEntry> merged = new LinkedHashMap<>(BUILT_IN_ENTRIES);
        for (int i = 0; i < remoteEntries.length(); i++) {
            JSONObject item = remoteEntries.optJSONObject(i);
            if (item == null) continue;
            try {
                LinkedHashSet<String> capabilities = new LinkedHashSet<>();
                JSONArray values = item.optJSONArray("capabilities");
                if (values != null) for (int j = 0; j < values.length(); j++) capabilities.add(values.getString(j));
                LinkedHashSet<String> sourceCapabilities = new LinkedHashSet<>();
                JSONArray sourceValues = item.optJSONArray("sourceCapabilities");
                if (sourceValues != null) for (int j = 0; j < sourceValues.length(); j++) sourceCapabilities.add(sourceValues.getString(j));
                if (sourceCapabilities.isEmpty()) sourceCapabilities.addAll(capabilities);
                LinkedHashSet<String> endpointCapabilities = new LinkedHashSet<>();
                JSONArray endpointValues = item.optJSONArray("endpointCapabilities");
                if (endpointValues != null) for (int j = 0; j < endpointValues.length(); j++) endpointCapabilities.add(endpointValues.getString(j));
                CatalogEntry entry = new CatalogEntry(item.getString("modelId"), item.getString("displayName"),
                    item.optString("roleHint", "Curated model"), item.getString("repositoryId"),
                    item.getString("revision"), item.isNull("artifactPath") ? null : item.optString("artifactPath"),
                    item.getString("license"), item.getLong("sizeBytes"), item.optBoolean("gated", false),
                    item.getString("backend"), item.getString("format"), item.optString("architecture", ""),
                    item.isNull("quantization") ? null : item.optString("quantization"),
                    item.optInt("endpointContextWindow", item.optInt("contextWindow", 4096)),
                    item.optInt("sourceContextWindow", item.optInt("contextWindow", 4096)),
                    item.optInt("defaultMaxOutputTokens", TaiModelSpec.defaultMaxOutputTokensFor(item.getString("modelId"), item.getString("backend"))),
                    item.optInt("recommendedRamGb", 0), item.isNull("sha256") ? null : item.optString("sha256"),
                    sourceCapabilities, endpointCapabilities.isEmpty() ? null : endpointCapabilities,
                    item.isNull("toolMode") ? null : item.optString("toolMode", null),
                    item.optString("jobGroup", "remote"), item.optString("priority", "remote"),
                    displayTags(item.optJSONArray("displayCapabilityTags")), item.optString("sizeEstimate", ""),
                    item.optString("ramTier", ""), item.optBoolean("recommended", false),
                    item.optBoolean("downloadAvailable", true), item.optString("unavailableReason", ""));
                if (!TaiModelSpec.isSupportedBackendFormat(entry.backend, entry.format)) continue;
                merged.put(entry.modelId, entry);
            } catch (Exception ignored) {}
        }
        entries = Collections.unmodifiableMap(merged);
    }

    @NonNull
    private static Map<String, CatalogEntry> buildEntries() {
        LinkedHashMap<String, CatalogEntry> entries = new LinkedHashMap<>();
        entries.put(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, liteRtAvailable(
            TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, "Gemma 4 E2B IT", "general_multimodal", "recommended_default", true,
            "Fast assistant", "litert-community/gemma-4-E2B-it-litert-lm", "6e5c4f1e395deb959c494953478fa5cec4b8008f",
            "gemma-4-E2B-it.litertlm", "Apache-2.0", 2_588_147_712L, "2.4 GB", "8GB+", false,
            tags("Text", "Vision", "Audio"), setOf("text_chat", "image_input", "audio_input", "tool_use", "llm_thinking", "speculative_decoding")));
        entries.put(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT, liteRtAvailable(
            TaiModelRegistry.MODEL_GEMMA_4_E4B_IT, "Gemma 4 E4B IT", "general_multimodal", "premium_default", false,
            "Coding and reasoning", "litert-community/gemma-4-E4B-it-litert-lm", "28299f30ee4d43294517a4ac93abd6163412f07f",
            "gemma-4-E4B-it.litertlm", "Apache-2.0", 3_659_530_240L, "3.7 GB", "12GB+", false,
            tags("Text", "Vision", "Audio", "Reasoning"), setOf("text_chat", "image_input", "audio_input", "llm_thinking")));
        entries.put("qwen2.5-1.5b-instruct-litert-lm", liteRtImportOnly(
            "qwen2.5-1.5b-instruct-litert-lm", "Qwen2.5 1.5B Instruct", "lightweight_text", "lightweight_alternative", false,
            "Lightweight text, code, and multilingual", "litert-community/Qwen2.5-1.5B-Instruct",
            1_597_931_520L, "1.5 GB", "6GB+", "q8",
            tags("Text", "Code", "Multilingual"), setOf("text_chat", "code", "multilingual")));
        entries.put("deepseek-r1-distill-qwen-1.5b-litert-lm", liteRtAvailable(
            "deepseek-r1-distill-qwen-1.5b-litert-lm", "DeepSeek-R1 Distill 1.5B", "reasoning", "reasoning_small", false,
            "Small reasoning model", "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            "2f8b8ee90d8f93b15305b699e8772b277d074a9a",
            "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm", "MIT",
            1_833_451_520L, "1.7 GB", "6GB+", false, "deepseek-r1-distill-qwen", "q8",
            "69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295",
            tags("Reasoning", "Text"), setOf("text_chat", "reasoning")));
        entries.put(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M, liteRtAvailable(
            TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M, "FunctionGemma 270M", "tool_calling", "experimental_launcher_agent", false,
            "Mobile actions tool-call model", "litert-community/functiongemma-270m-ft-mobile-actions", "38942192c9b723af836d489074823ff33d4a3e7a",
            "mobile_actions_q8_ekv1024.litertlm", "Gemma Terms of Use", 288_964_608L, "0.3 GB", "4GB+", true,
            tags("Tools"), setOf("text_chat", "tool_use", "mobile_actions")));

        entries.put("qwen2.5-coder-1.5b-instruct-mnn", mnnAvailable(
            "qwen2.5-coder-1.5b-instruct-mnn", "Qwen2.5-Coder 1.5B", "coding", "recommended_coder", true,
            "Code and terminal assistant", "taobao-mnn/Qwen2.5-Coder-1.5B-Instruct-MNN", 1_343_595_873L,
            "1.3 GB", "4GB-6GB+", "qwen2.5-coder", "int4", tags("Code", "Text", "Tools"), setOf("text_chat", "code", "tool_use")));
        entries.put("qwen2.5-coder-7b-instruct-mnn", mnnAvailable(
            "qwen2.5-coder-7b-instruct-mnn", "Qwen2.5-Coder 7B", "coding", "advanced_coder", false,
            "Higher quality code model", "taobao-mnn/Qwen2.5-Coder-7B-Instruct-MNN", 5_079_861_358L,
            "5.1 GB", "10GB-12GB+", "qwen2.5-coder", "int4", tags("Code", "Text"), setOf("text_chat", "code")));
        entries.put("qwen2.5-0.5b-instruct-mnn", mnnAvailable(
            "qwen2.5-0.5b-instruct-mnn", "Qwen2.5 0.5B", "lightweight_text", "tiny_general", false,
            "Tiny general chat", "taobao-mnn/Qwen2.5-0.5B-Instruct-MNN", 556_808_791L,
            "557 MB", "3GB+", "qwen2.5", "int4", tags("Text", "Multilingual"), setOf("text_chat", "multilingual")));
        entries.put("qwen2.5-1.5b-instruct-mnn", mnnAvailable(
            "qwen2.5-1.5b-instruct-mnn", "Qwen2.5 1.5B", "general_text", "lightweight_general", false,
            "Lightweight text and multilingual", "taobao-mnn/Qwen2.5-1.5B-Instruct-MNN", 879_484_183L,
            "879 MB", "4GB-6GB+", "qwen2.5", "int4", tags("Text", "Multilingual"), setOf("text_chat", "multilingual")));
        entries.put("qwen2.5-3b-instruct-mnn", mnnAvailable(
            "qwen2.5-3b-instruct-mnn", "Qwen2.5 3B", "general_text", "balanced_general", false,
            "Balanced local assistant", "taobao-mnn/Qwen2.5-3B-Instruct-MNN", 2_369_484_250L,
            "2.4 GB", "6GB-8GB+", "qwen2.5", "int4", tags("Text"), setOf("text_chat")));
        entries.put("deepseek-r1-1.5b-qwen-mnn", mnnAvailable(
            "deepseek-r1-1.5b-qwen-mnn", "DeepSeek-R1 1.5B Qwen", "reasoning", "lightweight_reasoning", false,
            "Small reasoning model", "taobao-mnn/DeepSeek-R1-1.5B-Qwen-MNN", 1_557_822_247L,
            "1.6 GB", "4GB-6GB+", "deepseek-r1-qwen", "int4", tags("Reasoning", "Text"), setOf("text_chat", "reasoning")));

        return Collections.unmodifiableMap(entries);
    }

    private static CatalogEntry liteRtAvailable(String id, String name, String jobGroup, String priority, boolean recommended,
                                                 String role, String repo, String revision, String file, String license, long size,
                                                 String sizeEstimate, String ramTier, boolean gated, LinkedHashSet<String> displayTags,
                                                 LinkedHashSet<String> capabilities) {
        return liteRtAvailable(id, name, jobGroup, priority, recommended, role, repo, revision, file, license, size,
            sizeEstimate, ramTier, gated, "gemma", null, null, displayTags, capabilities);
    }

    private static CatalogEntry liteRtAvailable(String id, String name, String jobGroup, String priority, boolean recommended,
                                                 String role, String repo, String revision, String file, String license, long size,
                                                 String sizeEstimate, String ramTier, boolean gated, String architecture,
                                                 @Nullable String quantization, @Nullable String sha256,
                                                 LinkedHashSet<String> displayTags, LinkedHashSet<String> capabilities) {
        return entry(id, name, role, repo, revision, file, license, size, gated, TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, architecture, quantization,
            TaiModelSpec.defaultEndpointContextWindowFor(id, TaiModelSpec.BACKEND_LITERT_LM),
            ramGb(ramTier), sha256, capabilities,
            jobGroup, priority, displayTags, sizeEstimate, ramTier, recommended, true, "");
    }

    private static CatalogEntry liteRtImportOnly(String id, String name, String jobGroup, String priority, boolean recommended,
                                                  String role, String repo, long size, String sizeEstimate, String ramTier,
                                                  @Nullable String quantization, LinkedHashSet<String> displayTags,
                                                  LinkedHashSet<String> capabilities) {
        return entry(id, name, role, repo, "main", null, "Review upstream license before import", size, false,
            TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "", quantization, 4096, ramGb(ramTier), null,
            capabilities, jobGroup, priority, displayTags, sizeEstimate, ramTier, recommended, false, UNVERIFIED_ARTIFACT_POLICY);
    }

    private static CatalogEntry mnnAvailable(String id, String name, String jobGroup, String priority, boolean recommended,
                                             String role, String repo, long size, String sizeEstimate, String ramTier,
                                             String architecture, String quantization, LinkedHashSet<String> displayTags,
                                             LinkedHashSet<String> capabilities) {
        return entry(id, name, role, repo, "main", "config.json", "Apache-2.0", size, false,
            TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN, architecture, quantization, 8192,
            ramGb(ramTier), null, capabilities, jobGroup, priority, displayTags, sizeEstimate, ramTier,
            recommended, true, "");
    }

    private static CatalogEntry entry(String id, String name, String role, String repo, String revision, @Nullable String file,
                                      String license, long size, boolean gated, String backend, String format, String architecture,
                                      @Nullable String quantization, int contextWindow, int ramGb, @Nullable String sha256,
                                      LinkedHashSet<String> capabilities, String jobGroup, String priority,
                                      LinkedHashSet<String> displayTags, String sizeEstimate, String ramTier,
                                      boolean recommended, boolean downloadAvailable, String unavailableReason) {
        return new CatalogEntry(id, name, role, repo, revision, file, license, size, gated, backend, format, architecture,
            quantization, contextWindow, sourceContextWindowFor(id, backend, contextWindow),
            TaiModelSpec.defaultMaxOutputTokensFor(id, backend), ramGb, sha256,
            capabilities, null, null, jobGroup, priority, displayTags, sizeEstimate,
            ramTier, recommended, downloadAvailable, unavailableReason);
    }

    private static int sourceContextWindowFor(String id, String backend, int endpointContextWindow) {
        if (TaiModelRegistry.MODEL_GEMMA_4_E2B_IT.equals(id) || TaiModelRegistry.MODEL_GEMMA_4_E4B_IT.equals(id)) return 32_768;
        if (TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M.equals(id)) return 1024;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend) && id.startsWith("qwen2.5-")) return 32_768;
        return endpointContextWindow;
    }

    private static LinkedHashSet<String> setOf(String... values) { return new LinkedHashSet<>(Arrays.asList(values)); }
    private static LinkedHashSet<String> tags(String... values) { return setOf(values); }
    private static LinkedHashSet<String> displayTags(@Nullable JSONArray values) throws Exception {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (values != null) for (int i = 0; i < values.length(); i++) tags.add(values.getString(i));
        return tags;
    }
    private static int ramGb(String ramTier) {
        if (ramTier == null || ramTier.isEmpty()) return 0;
        StringBuilder digits = new StringBuilder();
        for (int i = ramTier.length() - 1; i >= 0; i--) {
            char c = ramTier.charAt(i);
            if (Character.isDigit(c)) digits.insert(0, c);
            else if (digits.length() > 0) break;
        }
        if (digits.length() == 0) return 0;
        try { return Integer.parseInt(digits.toString()); } catch (NumberFormatException e) { return 0; }
    }

    public static final class CatalogEntry {
        public final String modelId, displayName, roleHint, repositoryId, revision, artifactPath, license;
        public final long sizeBytes;
        public final boolean gated;
        public final String backend, format, architecture, quantization;
        public final String jobGroup, priority, sizeEstimate, ramTier, unavailableReason;
        public final int contextWindow, endpointContextWindow, sourceContextWindow, defaultMaxOutputTokens, recommendedRamGb;
        public final boolean recommended, downloadAvailable;
        @Nullable public final String sha256;
        @Nullable public final String toolMode;
        public final LinkedHashSet<String> capabilities, endpointCapabilities, sourceCapabilities, displayCapabilityTags;
        public final String providerPageUrl, downloadUrl;

        private CatalogEntry(String modelId, String displayName, String roleHint, String repositoryId,
                             String revision, @Nullable String artifactPath, String license, long sizeBytes,
                             boolean gated, String backend, String format, String architecture,
                             @Nullable String quantization, int endpointContextWindow, int sourceContextWindow,
                             int defaultMaxOutputTokens, int recommendedRamGb, @Nullable String sha256,
                             LinkedHashSet<String> sourceCapabilities,
                             @Nullable LinkedHashSet<String> endpointCapabilities, @Nullable String toolMode,
                             String jobGroup, String priority,
                             LinkedHashSet<String> displayCapabilityTags, String sizeEstimate, String ramTier,
                             boolean recommended, boolean downloadAvailable, String unavailableReason) {
            this.modelId = modelId; this.displayName = displayName; this.roleHint = roleHint;
            this.repositoryId = repositoryId; this.revision = revision; this.artifactPath = artifactPath;
            this.license = license; this.sizeBytes = sizeBytes; this.gated = gated; this.backend = backend;
            this.format = format; this.architecture = architecture; this.quantization = quantization;
            this.endpointContextWindow = endpointContextWindow; this.sourceContextWindow = sourceContextWindow;
            this.contextWindow = endpointContextWindow; this.defaultMaxOutputTokens = defaultMaxOutputTokens;
            this.recommendedRamGb = recommendedRamGb; this.sha256 = sha256;
            this.sourceCapabilities = sourceCapabilities;
            this.endpointCapabilities = endpointCapabilities == null
                ? TaiModelSpec.endpointCapabilitiesFor(modelId, backend, format, sourceCapabilities, null)
                : endpointCapabilities;
            this.capabilities = this.endpointCapabilities;
            this.toolMode = toolMode == null ? TaiModelSpec.toolModeFor(backend, this.endpointCapabilities) : toolMode;
            this.jobGroup = jobGroup; this.priority = priority;
            this.displayCapabilityTags = displayCapabilityTags; this.sizeEstimate = sizeEstimate; this.ramTier = ramTier;
            this.recommended = recommended; this.downloadAvailable = downloadAvailable; this.unavailableReason = unavailableReason;
            this.providerPageUrl = "https://huggingface.co/" + repositoryId;
            this.downloadUrl = !downloadAvailable || artifactPath == null ? null : providerPageUrl + "/resolve/" + revision + "/" + artifactPath + "?download=true";
        }
    }
}
