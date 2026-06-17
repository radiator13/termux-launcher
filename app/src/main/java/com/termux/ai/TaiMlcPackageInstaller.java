package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Handles the full MLC package install flow: validates manifest schema,
 * verifies downloaded files, and copies them to app-private model storage.
 */
public final class TaiMlcPackageInstaller {

    public static final String SCHEMA_VERSION = "1.0";

    public static final String ERROR_INVALID_MANIFEST = "mlc_invalid_manifest";
    public static final String ERROR_UNSUPPORTED_SCHEMA = "mlc_unsupported_schema";
    public static final String ERROR_UNKNOWN_MODEL_LIBRARY = "mlc_unknown_model_library";
    public static final String ERROR_NATIVE_ARTIFACT_FORBIDDEN = "mlc_native_artifact_forbidden";
    public static final String ERROR_RAW_WEIGHTS_FORBIDDEN = "mlc_raw_weights_forbidden";
    public static final String ERROR_PATH_TRAVERSAL = "mlc_path_traversal";
    public static final String ERROR_HASH_MISMATCH = "mlc_hash_mismatch";
    public static final String ERROR_DUPLICATE_MODEL = "mlc_duplicate_model";
    public static final String ERROR_FILE_MISSING = "mlc_file_missing";
    public static final String ERROR_INSECURE_URL = "insecure_url";

    private static final Set<String> RAW_WEIGHT_EXTENSIONS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        ".safetensors", ".gguf", ".bin", ".pt", ".onnx"
    )));

    private static final Set<String> KNOWN_CAPABILITIES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        TaiModelSpec.CAPABILITY_TEXT_CHAT,
        TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS
    )));

    public static final class InstallResult {
        public final boolean success;
        public final String errorCode;
        public final String message;
        public final TaiModelSpec installedSpec;

        InstallResult(boolean success, @Nullable String errorCode, @Nullable String message, @Nullable TaiModelSpec installedSpec) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.installedSpec = installedSpec;
        }

        @NonNull
        public static InstallResult success(@NonNull TaiModelSpec spec) {
            return new InstallResult(true, null, "Installation successful", spec);
        }

        @NonNull
        public static InstallResult failure(@NonNull String errorCode, @NonNull String message) {
            return new InstallResult(false, errorCode, message, null);
        }
    }

    /**
     * Validates the manifest JSON string, verifies downloaded files in {@code downloadDir},
     * copies them to app-private model storage, and persists a {@link TaiModelSpec}.
     *
     * @param manifestJson the manifest as a JSON string
     * @param downloadDir  the directory containing downloaded files referenced by the manifest
     * @param store        the model store for persistence
     * @return an {@link InstallResult} describing the outcome
     */
    @NonNull
    public InstallResult installFromManifest(
        @Nullable String manifestJson,
        @NonNull File downloadDir,
        @NonNull TaiModelStore store
    ) {
        if (manifestJson == null || manifestJson.trim().isEmpty()) {
            return InstallResult.failure(ERROR_INVALID_MANIFEST, "Manifest is empty");
        }

        String trimmed = manifestJson.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html")) {
            return InstallResult.failure(ERROR_INVALID_MANIFEST, "Manifest appears to be an HTML page");
        }

        JSONObject manifest;
        try {
            manifest = new JSONObject(manifestJson);
        } catch (JSONException e) {
            return InstallResult.failure(ERROR_INVALID_MANIFEST, "Manifest is not valid JSON: " + e.getMessage());
        }

        InstallResult validation = validateManifestInternal(manifest, store);
        if (!validation.success) {
            return validation;
        }

        String modelId = manifest.optString("modelId", "");
        String displayName = manifest.optString("displayName", modelId);
        String license = manifest.optString("license", "User accepted provider terms externally");

        JSONArray capabilitiesArray = manifest.optJSONArray("capabilities");
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (capabilitiesArray != null) {
            for (int i = 0; i < capabilitiesArray.length(); i++) {
                String cap = capabilitiesArray.optString(i, "");
                if (!cap.isEmpty()) capabilities.add(cap);
            }
        }

        JSONArray filesArray = manifest.optJSONArray("files");
        long totalSize = 0;

        try {
            for (int i = 0; i < filesArray.length(); i++) {
                JSONObject fileEntry = filesArray.getJSONObject(i);
                String path = fileEntry.getString("path");
                long expectedSize = fileEntry.getLong("size");
                String expectedHash = fileEntry.optString("sha256", null);

                File file = new File(downloadDir, path);
                if (!file.isFile()) {
                    return InstallResult.failure(ERROR_FILE_MISSING, "Missing file: " + path);
                }
                if (file.length() != expectedSize) {
                    return InstallResult.failure(ERROR_HASH_MISMATCH,
                        "Size mismatch for " + path + ": expected " + expectedSize + ", got " + file.length());
                }
                if (expectedHash != null && !expectedHash.isEmpty()) {
                    String actualHash = sha256(file);
                    if (!actualHash.equalsIgnoreCase(expectedHash)) {
                        return InstallResult.failure(ERROR_HASH_MISMATCH, "SHA-256 mismatch for " + path);
                    }
                }
                totalSize += file.length();
            }

            File modelDir = new File(store.getModelsDirectory(), modelId);
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                return InstallResult.failure(ERROR_FILE_MISSING,
                    "Failed to create model directory: " + modelDir.getAbsolutePath());
            }

            for (int i = 0; i < filesArray.length(); i++) {
                JSONObject fileEntry = filesArray.getJSONObject(i);
                String path = fileEntry.getString("path");
                File src = new File(downloadDir, path);
                File dst = new File(modelDir, path);
                File parent = dst.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    return InstallResult.failure(ERROR_FILE_MISSING,
                        "Failed to create directory for: " + path);
                }
                copyFile(src, dst);
            }

            TaiModelSpec spec = new TaiModelSpec(
                modelId,
                displayName.isEmpty() ? modelId : displayName,
                "Downloaded model",
                "downloaded",
                modelDir.getAbsolutePath(),
                license,
                totalSize,
                capabilities,
                false,
                null,
                TaiModelSpec.BACKEND_MLC_LLM,
                TaiModelSpec.FORMAT_MLC,
                emptyToNull(manifest.optString("architecture", null)),
                emptyToNull(manifest.optString("quantization", null)),
                manifest.optInt("contextWindow", 4096),
                manifest.optInt("recommendedRamGb", 0),
                emptyToNull(manifest.optString("manifestSha256", null))
            );
            store.upsertUserModel(spec);

            return InstallResult.success(spec);

        } catch (Exception e) {
            return InstallResult.failure(ERROR_INVALID_MANIFEST,
                "Installation failed: " + e.getMessage());
        }
    }

    /**
     * Validates manifest structure without touching the filesystem.
     * Package-visible so {@link TaiModelDownloader} can fail fast before downloading files.
     */
    @NonNull
    InstallResult validateManifestInternal(@NonNull JSONObject manifest, @NonNull TaiModelStore store) {
        String schemaVersion = manifest.optString("schemaVersion", "");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            return InstallResult.failure(ERROR_UNSUPPORTED_SCHEMA,
                "Unsupported schema version: " + schemaVersion + ", expected: " + SCHEMA_VERSION);
        }

        String modelId = manifest.optString("modelId", "");
        if (modelId.isEmpty()) {
            return InstallResult.failure(ERROR_INVALID_MANIFEST, "Missing modelId in manifest");
        }
        if (store.getUserModel(modelId) != null) {
            return InstallResult.failure(ERROR_DUPLICATE_MODEL, "Model already exists: " + modelId);
        }

        String backend = manifest.optString("backend", "");
        if (!TaiModelSpec.BACKEND_MLC_LLM.equals(backend)) {
            return InstallResult.failure(ERROR_INVALID_MANIFEST,
                "Invalid backend: " + backend + ", expected: " + TaiModelSpec.BACKEND_MLC_LLM);
        }

        String format = manifest.optString("format", "");
        if (!TaiModelSpec.FORMAT_MLC.equals(format)) {
            return InstallResult.failure(ERROR_INVALID_MANIFEST,
                "Invalid format: " + format + ", expected: " + TaiModelSpec.FORMAT_MLC);
        }

        String modelLibraryId = manifest.optString("modelLibraryId", "");
        if (modelLibraryId.isEmpty()) {
            return InstallResult.failure(ERROR_INVALID_MANIFEST, "Missing modelLibraryId in manifest");
        }
        if (!MlcBundledLibraryRegistry.contains(modelLibraryId)) {
            return InstallResult.failure(ERROR_UNKNOWN_MODEL_LIBRARY,
                "Unknown modelLibraryId: " + modelLibraryId);
        }

        JSONArray capabilitiesArray = manifest.optJSONArray("capabilities");
        if (capabilitiesArray == null || capabilitiesArray.length() == 0) {
            return InstallResult.failure(ERROR_INVALID_MANIFEST,
                "Missing or empty capabilities in manifest");
        }
        for (int i = 0; i < capabilitiesArray.length(); i++) {
            String cap = capabilitiesArray.optString(i, "");
            if (!KNOWN_CAPABILITIES.contains(cap)) {
                return InstallResult.failure(ERROR_INVALID_MANIFEST, "Unknown capability: " + cap);
            }
        }

        JSONArray filesArray = manifest.optJSONArray("files");
        if (filesArray == null || filesArray.length() == 0) {
            return InstallResult.failure(ERROR_INVALID_MANIFEST,
                "Missing or empty files list in manifest");
        }

        for (int i = 0; i < filesArray.length(); i++) {
            JSONObject fileEntry = filesArray.optJSONObject(i);
            if (fileEntry == null) {
                return InstallResult.failure(ERROR_INVALID_MANIFEST,
                    "Malformed file entry at index " + i);
            }

            String path = fileEntry.optString("path", "");
            if (path.isEmpty()) {
                return InstallResult.failure(ERROR_INVALID_MANIFEST,
                    "Missing path in file entry at index " + i);
            }

            if (path.contains("../") || path.contains(".." + File.separator)) {
                return InstallResult.failure(ERROR_PATH_TRAVERSAL,
                    "Path traversal detected: " + path);
            }

            String lowerPath = path.toLowerCase(Locale.ROOT);
            if (lowerPath.endsWith(".so")) {
                return InstallResult.failure(ERROR_NATIVE_ARTIFACT_FORBIDDEN,
                    "Native artifact (.so) not allowed: " + path);
            }

            for (String ext : RAW_WEIGHT_EXTENSIONS) {
                if (lowerPath.endsWith(ext)) {
                    return InstallResult.failure(ERROR_RAW_WEIGHTS_FORBIDDEN,
                        "Raw weight file not allowed: " + path);
                }
            }

            String sha256 = fileEntry.optString("sha256", "");
            if (sha256.isEmpty()) {
                return InstallResult.failure(ERROR_HASH_MISMATCH,
                    "Missing SHA-256 for file: " + path);
            }

            if (!fileEntry.has("size")) {
                return InstallResult.failure(ERROR_INVALID_MANIFEST,
                    "Missing size for file: " + path);
            }
        }

        String sourceUrl = manifest.optString("sourceUrl", "");
        if (!sourceUrl.isEmpty() && !sourceUrl.startsWith("https://")) {
            return InstallResult.failure(ERROR_INSECURE_URL,
                "Manifest sourceUrl must use HTTPS: " + sourceUrl);
        }

        return InstallResult.success(null);
    }

    @Nullable
    private static String emptyToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    @NonNull
    private static String sha256(@NonNull File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (byte value : digest.digest()) {
            builder.append(String.format(Locale.US, "%02x", value));
        }
        return builder.toString();
    }

    private static void copyFile(@NonNull File src, @NonNull File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }
}
