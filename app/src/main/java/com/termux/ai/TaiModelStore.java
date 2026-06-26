package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class TaiModelStore {
    public static final String STATE_QUEUED = "queued";
    public static final String STATE_DOWNLOADING = "downloading";
    public static final String STATE_CANCELLED = "cancelled";
    public static final String STATE_FAILED = "failed";
    public static final String STATE_VERIFYING = "verifying";
    public static final String STATE_INSTALLED = "installed";
    public static final String STATE_UNAVAILABLE = "unavailable";

    public static final String ERROR_ACTIVE_MODEL_LOADED = "active_model_loaded";
    public static final String ERROR_DELETE_REQUIRES_CONFIRMATION = "delete_requires_confirmation";

    private static final String KEY_USER_MODELS = "tai_user_models_json";
    private static final String KEY_DOWNLOADS = "tai_downloads_json";
    private static final String KEY_CAPS_PREFIX = "tai_model_caps.";
    private static final String KEY_EXPOSURE_PREFIX = "tai_model_exposure.";
    public static final String EXPOSURE_SPLIT = "split";
    public static final String EXPOSURE_COMBINED = "combined";
    public static final String EXPOSURE_BOTH = "both";

    private final Context appContext;
    private final SharedPreferences preferences;

    public TaiModelStore(@NonNull Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public File getModelsDirectory() {
        return new File(appContext.getFilesDir(), "tai/models");
    }

    @NonNull
    public synchronized Map<String, TaiModelSpec> getUserModels() {
        LinkedHashMap<String, TaiModelSpec> models = new LinkedHashMap<>();
        JSONArray array = parseArray(preferences.getString(KEY_USER_MODELS, "[]"));
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            TaiModelSpec spec;
            try {
                spec = TaiModelSpec.fromJson(json);
            } catch (IllegalArgumentException e) {
                continue;
            }
            spec = applyCapabilityOverride(normalizeLegacyModelSpec(spec));
            if (!spec.id.isEmpty()
                && TaiModelSpec.isSupportedBackendFormat(spec.backend, spec.format)) {
                models.put(spec.id, spec);
            }
        }
        return models;
    }

    @Nullable
    public synchronized TaiModelSpec getUserModel(@Nullable String modelId) {
        if (modelId == null) return null;
        return getUserModels().get(modelId);
    }

    /** User-declared capability set for a model (e.g. enable vision/audio/tools on an import). */
    public synchronized void setCapabilityOverride(@NonNull String modelId, @NonNull java.util.Set<String> capabilities) {
        LinkedHashSet<String> caps = new LinkedHashSet<>(capabilities);
        caps.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        preferences.edit().putString(KEY_CAPS_PREFIX + modelId, android.text.TextUtils.join(",", caps)).apply();
    }

    /** How a multimodal model is advertised: split / combined / both. Defaults to split. */
    @NonNull
    public synchronized String getExposure(@NonNull String modelId) {
        return preferences.getString(KEY_EXPOSURE_PREFIX + modelId, EXPOSURE_SPLIT);
    }

    public synchronized void setExposure(@NonNull String modelId, @NonNull String exposure) {
        preferences.edit().putString(KEY_EXPOSURE_PREFIX + modelId, exposure).apply();
    }

    /** Re-applies any user capability override to a spec resolved outside the user-model store
     *  (on-disk/registry built-ins), so generation honours the same modalities /v1/models advertises. */
    @NonNull
    public synchronized TaiModelSpec withCapabilityOverride(@NonNull TaiModelSpec spec) {
        return applyCapabilityOverride(spec);
    }

    @NonNull
    private TaiModelSpec applyCapabilityOverride(@NonNull TaiModelSpec spec) {
        String raw = preferences.getString(KEY_CAPS_PREFIX + spec.id, null);
        if (raw == null || raw.trim().isEmpty()) return spec;
        LinkedHashSet<String> source = new LinkedHashSet<>();
        source.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        for (String capability : raw.split(",")) {
            String trimmed = capability.trim();
            if (!trimmed.isEmpty()) source.add(trimmed);
        }
        LinkedHashSet<String> endpoint = TaiModelSpec.endpointCapabilitiesFor(
            spec.id, spec.backend, spec.format, source, spec.localPath);
        return new TaiModelSpec(spec.id, spec.displayName, spec.roleHint, spec.source, spec.localPath,
            spec.license, spec.sizeBytes, source, spec.builtInCatalogEntry, spec.runtimeProfile,
            spec.backend, spec.format, spec.architecture, spec.quantization, spec.endpointContextWindow,
            spec.sourceContextWindow, spec.defaultMaxOutputTokens, spec.recommendedRamGb, spec.sha256,
            endpoint, TaiModelSpec.toolModeFor(spec.backend, endpoint));
    }

    @NonNull
    public synchronized Map<String, TaiModelSpec> getInstalledUserModels() {
        LinkedHashMap<String, TaiModelSpec> installed = new LinkedHashMap<>();
        for (TaiModelSpec spec : getUserModels().values()) {
            if (isModelReadable(spec)) installed.put(spec.id, spec);
        }
        return installed;
    }

    /**
     * Builds a spec for a catalog model whose package is already present on disk under
     * {@code tai/models/&lt;id&gt;/}, even when it was never registered in user-models (e.g. a
     * download whose files completed but whose final registration step was interrupted). Returns
     * {@code null} unless the package is a known catalog entry and is readable. Self-heals the
     * "Download or import this model before loading it" state for models that are physically there.
     */
    @Nullable
    public synchronized TaiModelSpec onDiskModelSpec(@Nullable String modelId) {
        if (modelId == null || modelId.trim().isEmpty()) return null;
        String id = TaiSettings.migrateBuiltInModelId(modelId);
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
        if (entry == null) return null;
        File dir = new File(getModelsDirectory(), id);
        if (!dir.isDirectory()) return null;
        String path;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)) {
            File config = new File(dir, "config.json");
            if (!config.isFile()) return null;
            path = config.getAbsolutePath();
        } else {
            File pkg = firstLiteRtPackage(dir);
            if (pkg == null) return null;
            path = pkg.getAbsolutePath();
        }
        LinkedHashSet<String> endpointCapabilities = new LinkedHashSet<>(TaiModelSpec.endpointCapabilitiesFor(
            entry.modelId, entry.backend, entry.format, entry.sourceCapabilities, path));
        try {
            TaiModelSpec spec = new TaiModelSpec(
                entry.modelId, entry.displayName, entry.roleHint, "downloaded", path, entry.license,
                localModelSize(dir), new LinkedHashSet<>(entry.sourceCapabilities), false, null,
                entry.backend, entry.format, entry.architecture, entry.quantization,
                entry.endpointContextWindow, entry.sourceContextWindow, entry.defaultMaxOutputTokens,
                entry.recommendedRamGb, entry.sha256, endpointCapabilities,
                TaiModelSpec.toolModeFor(entry.backend, endpointCapabilities));
            return isModelReadable(spec) ? spec : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    private File firstLiteRtPackage(@NonNull File dir) {
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            String name = child.getName().toLowerCase(java.util.Locale.ROOT);
            if (child.isFile() && (name.endsWith(".litertlm") || name.endsWith(".task") || name.endsWith(".tflite"))) return child;
        }
        return null;
    }

    @NonNull
    public synchronized Map<String, TaiModelSpec> getDownloadedReadableModels() {
        LinkedHashMap<String, TaiModelSpec> models = new LinkedHashMap<>();
        JSONArray downloads = getDownloads();
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject item = downloads.optJSONObject(i);
            if (item == null || !isCompletedDownload(item)) continue;
            TaiModelSpec spec = specFromDownload(item);
            if (spec != null && isModelReadable(spec)) models.put(spec.id, spec);
        }
        return models;
    }

    public synchronized int pruneMissingUserModels() {
        Map<String, TaiModelSpec> models = getUserModels();
        JSONArray array = new JSONArray();
        int removed = 0;
        for (TaiModelSpec model : models.values()) {
            if (!isModelReadable(model)) {
                removed++;
                continue;
            }
            try {
                array.put(model.toJson());
            } catch (JSONException ignored) {
            }
        }
        if (removed > 0) preferences.edit().putString(KEY_USER_MODELS, array.toString()).apply();
        return removed;
    }

    public synchronized void upsertUserModel(@NonNull TaiModelSpec spec) throws JSONException {
        Map<String, TaiModelSpec> models = getUserModels();
        models.put(spec.id, spec);
        JSONArray array = new JSONArray();
        for (TaiModelSpec model : models.values()) {
            array.put(model.toJson());
        }
        preferences.edit().putString(KEY_USER_MODELS, array.toString()).apply();
    }

    public synchronized boolean deleteUserModel(@NonNull String modelId) {
        return deleteUserModel(modelId, false, true).deleted;
    }

    @NonNull
    public synchronized DeleteResult deleteUserModel(@NonNull String modelId,
                                                     boolean activeModelLoaded,
                                                     boolean confirmed) {
        if (activeModelLoaded) {
            return DeleteResult.blocked(ERROR_ACTIVE_MODEL_LOADED,
                "Unload the active model before deleting it.");
        }
        if (!confirmed) {
            return DeleteResult.blocked(ERROR_DELETE_REQUIRES_CONFIRMATION,
                "Deleting a model requires explicit confirmation.");
        }

        Map<String, TaiModelSpec> models = getUserModels();
        TaiModelSpec removed = models.remove(modelId);
        JSONArray array = new JSONArray();
        for (TaiModelSpec model : models.values()) {
            try {
                array.put(model.toJson());
            } catch (JSONException ignored) {
            }
        }

        JSONArray downloads = getDownloads();
        JSONArray keptDownloads = new JSONArray();
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject item = downloads.optJSONObject(i);
            if (item == null) continue;
            if (!modelId.equals(item.optString("modelId", ""))) {
                keptDownloads.put(item);
            }
        }

        preferences.edit()
            .putString(KEY_USER_MODELS, array.toString())
            .putString(KEY_DOWNLOADS, keptDownloads.toString())
            .apply();

        File modelDir = new File(getModelsDirectory(), modelId);
        deleteRecursively(modelDir);
        return DeleteResult.deleted(removed != null);
    }

    @NonNull
    public synchronized JSONArray getDownloads() {
        return parseArray(preferences.getString(KEY_DOWNLOADS, "[]"));
    }

    public synchronized void upsertDownload(@NonNull JSONObject transfer) {
        String id = transfer.optString("id", "");
        JSONArray current = getDownloads();
        JSONArray next = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) continue;
            if (id.equals(item.optString("id", ""))) {
                next.put(transfer);
                replaced = true;
            } else {
                next.put(item);
            }
        }
        if (!replaced) next.put(transfer);
        preferences.edit().putString(KEY_DOWNLOADS, next.toString()).apply();
    }

    public synchronized void updateDownloadStatus(@NonNull String transferId, @NonNull String status, @NonNull String error) {
        JSONArray current = getDownloads();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null || !transferId.equals(item.optString("id", ""))) continue;
            try {
                item.put("status", status);
                item.put("error", error);
                item.put("updatedAtMs", System.currentTimeMillis());
            } catch (JSONException ignored) {
            }
            break;
        }
        preferences.edit().putString(KEY_DOWNLOADS, current.toString()).apply();
    }

    @NonNull
    private JSONArray parseArray(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return new JSONArray();
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private boolean isModelReadable(@NonNull TaiModelSpec spec) {
        if (spec.localPath == null || spec.localPath.trim().isEmpty()) return false;
        File modelFile = new File(spec.localPath);
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(spec.backend)) {
            return mnnPackageReadable(modelFile);
        }
        String lowerName = modelFile.getName().toLowerCase(java.util.Locale.ROOT);
        return modelFile.isFile()
            && modelFile.canRead()
            && (lowerName.endsWith(".litertlm") || lowerName.endsWith(".task") || lowerName.endsWith(".tflite"));
    }

    private boolean mnnPackageReadable(@NonNull File modelFile) {
        File config = modelFile.isDirectory() ? new File(modelFile, "config.json") : modelFile;
        if (!config.isFile() || !config.canRead()) return false;
        File modelDir = config.getParentFile();
        if (modelDir == null) return false;
        try {
            JSONObject json = new JSONObject(readUtf8(config));
            return sidecarReadable(modelDir, json.optString("llm_model", "llm.mnn"))
                && sidecarReadable(modelDir, json.optString("llm_weight", "llm.mnn.weight"))
                && sidecarReadable(modelDir, mnnTokenizerFile(modelDir, json));
        } catch (Exception e) {
            return false;
        }
    }

    /** Qwen3-VL/eagle MNN packages omit tokenizer_file and ship the conventional tokenizer.txt;
     *  fall back to it (or tokenizer.mtok) so those packages are still recognised. */
    @NonNull
    static String mnnTokenizerFile(@NonNull File modelDir, @NonNull JSONObject config) {
        String declared = config.optString("tokenizer_file", "");
        if (declared != null && !declared.trim().isEmpty()) return declared;
        for (String name : new String[] {"tokenizer.txt", "tokenizer.mtok"}) {
            if (new File(modelDir, name).isFile()) return name;
        }
        return "tokenizer.txt";
    }

    private boolean sidecarReadable(@NonNull File modelDir, @Nullable String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return false;
        File file = new File(modelDir, fileName);
        return file.isFile() && file.canRead();
    }

    private boolean isCompletedDownload(@NonNull JSONObject item) {
        String status = item.optString("status", "");
        return STATE_INSTALLED.equals(status) || "complete".equals(status);
    }

    @Nullable
    private TaiModelSpec specFromDownload(@NonNull JSONObject item) {
        String rawId = item.optString("modelId", item.optString("id", ""));
        String modelId = TaiSettings.migrateBuiltInModelId(rawId);
        if (modelId.trim().isEmpty()) return null;
        String path = item.optString("path", "");
        if (path.trim().isEmpty()) return null;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        String displayName = modelId;
        String roleHint = "Downloaded model";
        String license = "User accepted provider terms externally";
        long sizeBytes = localModelSize(new File(path));
        String backend = TaiModelSpec.inferBackend(path);
        String format = TaiModelSpec.inferFormat(path);
        String architecture = null;
        String quantization = null;
        int endpointContextWindow = TaiModelSpec.defaultEndpointContextWindowFor(modelId, backend);
        int sourceContextWindow = endpointContextWindow;
        int defaultMaxOutputTokens = TaiModelSpec.defaultMaxOutputTokensFor(modelId, backend);
        int recommendedRamGb = 0;
        String sha256 = null;
        LinkedHashSet<String> endpointCapabilities = null;
        String toolMode = null;
        LinkedHashSet<String> declaredCapabilities = capabilitiesFromArray(item.optJSONArray("capabilities"));
        if (entry != null) {
            displayName = entry.displayName;
            roleHint = entry.roleHint;
            license = entry.license;
            capabilities.addAll(entry.sourceCapabilities);
            backend = entry.backend;
            format = entry.format;
            architecture = entry.architecture;
            quantization = entry.quantization;
            endpointContextWindow = entry.endpointContextWindow;
            sourceContextWindow = entry.sourceContextWindow;
            defaultMaxOutputTokens = entry.defaultMaxOutputTokens;
            recommendedRamGb = entry.recommendedRamGb;
            sha256 = entry.sha256;
        }
        if (!declaredCapabilities.isEmpty()) {
            capabilities.clear();
            capabilities.addAll(declaredCapabilities);
        }
        if (sizeBytes <= 0L) sizeBytes = item.optLong("bytesRead", item.optLong("totalBytes", entry == null ? 0L : entry.sizeBytes));
        if (capabilities.isEmpty()) capabilities.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        endpointCapabilities = new LinkedHashSet<>(TaiModelSpec.endpointCapabilitiesFor(
            modelId, backend, format, capabilities, path));
        toolMode = TaiModelSpec.toolModeFor(backend, endpointCapabilities);
        try {
            return new TaiModelSpec(
                modelId,
                displayName,
                roleHint,
                "downloaded",
                path,
                license,
                sizeBytes,
                capabilities,
                false,
                null,
                backend,
                format,
                architecture,
                quantization,
                endpointContextWindow,
                sourceContextWindow,
                defaultMaxOutputTokens,
                recommendedRamGb,
                sha256,
                endpointCapabilities,
                toolMode
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @NonNull
    private LinkedHashSet<String> capabilitiesFromArray(@Nullable JSONArray capabilityArray) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (capabilityArray == null) return capabilities;
        for (int i = 0; i < capabilityArray.length(); i++) {
            String capability = capabilityArray.optString(i, "");
            if (!capability.isEmpty()) capabilities.add(capability);
        }
        return capabilities;
    }

    @NonNull
    private String readUtf8(@NonNull File file) throws java.io.IOException {
        try (java.io.FileInputStream input = new java.io.FileInputStream(file);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        }
    }

    @NonNull
    private TaiModelSpec normalizeLegacyModelSpec(@NonNull TaiModelSpec spec) {
        String migratedId = TaiSettings.migrateBuiltInModelId(spec.id);
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(migratedId);
        if (entry == null && migratedId.equals(spec.id)) return spec;
        if (entry == null) {
            return new TaiModelSpec(
                migratedId,
                spec.displayName,
                spec.roleHint,
                spec.source,
                spec.localPath,
                spec.license,
                spec.sizeBytes,
                spec.sourceCapabilities,
                spec.builtInCatalogEntry,
                spec.runtimeProfile,
                spec.backend,
                spec.format,
                spec.architecture,
                spec.quantization,
                spec.endpointContextWindow,
                spec.sourceContextWindow,
                spec.defaultMaxOutputTokens,
                spec.recommendedRamGb,
                spec.sha256,
                spec.endpointCapabilities,
                spec.toolMode
            );
        }
        long localSize = spec.localPath == null ? 0L : localModelSize(new File(spec.localPath));
        LinkedHashSet<String> endpointCapabilities = TaiModelSpec.endpointCapabilitiesFor(
            entry.modelId, entry.backend, entry.format, entry.sourceCapabilities, spec.localPath);
        return new TaiModelSpec(
            migratedId,
            entry.displayName,
            entry.roleHint,
            spec.source,
            spec.localPath,
            entry.license,
            localSize > 0L ? localSize : spec.sizeBytes,
            entry.sourceCapabilities,
            spec.builtInCatalogEntry,
            spec.runtimeProfile,
            entry.backend,
            entry.format,
            entry.architecture,
            entry.quantization,
            entry.endpointContextWindow,
            entry.sourceContextWindow,
            entry.defaultMaxOutputTokens,
            entry.recommendedRamGb,
            entry.sha256,
            endpointCapabilities,
            TaiModelSpec.toolModeFor(entry.backend, endpointCapabilities)
        );
    }

    private long localModelSize(@NonNull File file) {
        if (file.isFile()) return file.length();
        if (!file.isDirectory()) return 0L;
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) total += localModelSize(child);
        }
        return total;
    }

    private void deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    public static final class DeleteResult {
        public final boolean ok;
        public final boolean deleted;
        @NonNull public final String errorCode;
        @NonNull public final String message;

        private DeleteResult(boolean ok, boolean deleted, @NonNull String errorCode, @NonNull String message) {
            this.ok = ok;
            this.deleted = deleted;
            this.errorCode = errorCode;
            this.message = message;
        }

        @NonNull
        static DeleteResult deleted(boolean deleted) {
            return new DeleteResult(true, deleted, "", deleted ? "Model deleted." : "Model was not installed.");
        }

        @NonNull
        static DeleteResult blocked(@NonNull String errorCode, @NonNull String message) {
            return new DeleteResult(false, false, errorCode, message);
        }
    }
}
