package com.termux.ai;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

public final class TaiModelImporter {
    private static final long MIN_MODEL_BYTES = 1024L * 1024L;
    public static final String ERROR_UNSUPPORTED_MODEL_FILE = "unsupported_model_file";
    public static final String ERROR_RAW_WEIGHTS_FORBIDDEN = "raw_weights_forbidden";
    public static final String ERROR_NATIVE_LIBRARY_FORBIDDEN = "native_library_forbidden";
    public static final String ERROR_INSECURE_URL = "insecure_url";
    public static final String ERROR_UNSUPPORTED_BACKEND = "unsupported_backend";

    private final Context appContext;
    private final TaiModelStore store;

    public TaiModelImporter(@NonNull Context context, @NonNull TaiModelStore store) {
        appContext = context.getApplicationContext();
        this.store = store;
    }

    @NonNull
    public JSONObject importDocument(@NonNull Uri uri, @Nullable String requestedModelId) throws JSONException {
        return importDocument(uri, requestedModelId, null);
    }

    @NonNull
    public JSONObject importDocument(@NonNull Uri uri, @Nullable String requestedModelId,
                                     @Nullable String backend) throws JSONException {
        DocumentMetadata metadata = readMetadata(uri);
        String fileName = sanitizeFileName(metadata.displayName);
        ValidationResult fileValidation = backend == null || backend.trim().isEmpty()
            ? validateSupportedImportFileName(fileName)
            : validateImportFileNameForBackend(backend, fileName);
        if (!fileValidation.supported) {
            return error(400, fileValidation.errorCode, fileValidation.message);
        }

        String inferredId = stripModelExtension(fileName);
        String modelId = sanitizeModelId(requestedModelId == null || requestedModelId.trim().isEmpty()
            ? inferredId : requestedModelId);
        if (modelId.isEmpty()) return error(400, "bad_request", "Enter a valid model id.");
        if (store.getUserModel(modelId) != null) {
            return error(409, "model_exists", "A model with this id already exists. Choose another model id or delete it first.");
        }

        File modelDir = new File(store.getModelsDirectory(), modelId);
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            return error(500, "model_directory_failed", "Could not create the app-private model directory.");
        }
        if (metadata.sizeBytes > 0L && modelDir.getUsableSpace() < metadata.sizeBytes) {
            deleteEmptyDirectory(modelDir);
            return error(507, "insufficient_storage", "Not enough free storage to import this model.");
        }

        File output = new File(modelDir, fileName);
        File temporary = new File(modelDir, fileName + ".importing");
        try {
            copyDocument(uri, temporary);
            if (fileValidation.mlcManifest) {
                String manifestJson = readSmallTextFile(temporary);
                TaiMlcPackageInstaller.InstallResult result =
                    new TaiMlcPackageInstaller().installFromManifest(manifestJson, modelDir, store);
                if (!result.success) {
                    temporary.delete();
                    deleteEmptyDirectory(modelDir);
                    return error(400, result.errorCode, result.message);
                }
                temporary.delete();
                JSONObject json = new JSONObject();
                json.put("ok", true);
                json.put("imported", true);
                json.put("copiedIntoAppPrivateStorage", true);
                json.put("sourceUri", uri.toString());
                json.put("model", result.installedSpec == null ? JSONObject.NULL : result.installedSpec.toJson());
                json.put("message", "MLC package verified, copied into app-private storage, and registered.");
                return json;
            }
            if (!looksLikeModelFile(temporary, fileName)) {
                throw new IllegalStateException("The selected file does not look like a readable model package.");
            }
            if (output.exists() && !output.delete()) {
                throw new IllegalStateException("Could not replace the existing destination file.");
            }
            if (!temporary.renameTo(output)) {
                throw new IllegalStateException("Could not finalize the imported model file.");
            }

            String displayName = stripModelExtension(metadata.displayName);
            TaiModelSpec baseSpec = new TaiModelSpec(
                modelId,
                displayName.isEmpty() ? modelId : displayName,
                "Imported local model",
                "imported",
                output.getAbsolutePath(),
                "User-provided model; license accepted externally",
                output.length(),
                Collections.singleton("text_chat"),
                false
            );
            String format = TaiModelSpec.inferFormat(output.getAbsolutePath());
            TaiModelSpec spec = new TaiModelSpec(
                baseSpec.id,
                baseSpec.displayName,
                baseSpec.roleHint,
                baseSpec.source,
                baseSpec.localPath,
                baseSpec.license,
                baseSpec.sizeBytes,
                baseSpec.capabilities,
                false,
                TaiModelProfile.forModel(baseSpec),
                TaiModelSpec.inferBackend(output.getAbsolutePath()),
                format,
                null,
                null,
                4096,
                0,
                null
            );
            store.upsertUserModel(spec);

            JSONObject result = new JSONObject();
            result.put("ok", true);
            result.put("imported", true);
            result.put("copiedIntoAppPrivateStorage", true);
            result.put("sourceUri", uri.toString());
            result.put("model", spec.toJson());
            result.put("message", "Model copied into app-private storage and registered.");
            return result;
        } catch (Exception e) {
            temporary.delete();
            if (!output.exists()) deleteEmptyDirectory(modelDir);
            return error(500, "model_import_failed", messageOrFallback(e, "Model import failed."));
        }
    }

    @NonNull
    public DocumentMetadata readMetadata(@NonNull Uri uri) {
        String displayName = "";
        long sizeBytes = -1L;
        ContentResolver resolver = appContext.getContentResolver();
        try (Cursor cursor = resolver.query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
            null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameColumn >= 0) {
                    String value = cursor.getString(nameColumn);
                    if (value != null && !value.trim().isEmpty()) displayName = value.trim();
                }
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) sizeBytes = cursor.getLong(sizeColumn);
            }
        } catch (Exception ignored) {
        }
        if (displayName.isEmpty()) {
            String lastSegment = uri.getLastPathSegment();
            if (lastSegment != null) {
                int colon = lastSegment.lastIndexOf(':');
                displayName = colon >= 0 ? lastSegment.substring(colon + 1) : lastSegment;
            }
        }
        return new DocumentMetadata(displayName, sizeBytes);
    }

    private void copyDocument(@NonNull Uri uri, @NonNull File output) throws Exception {
        ContentResolver resolver = appContext.getContentResolver();
        try (InputStream rawInput = resolver.openInputStream(uri)) {
            if (rawInput == null) throw new IllegalStateException("The selected document could not be opened.");
            try (BufferedInputStream input = new BufferedInputStream(rawInput);
                 BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(output, false))) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new InterruptedException("Model import cancelled.");
                    }
                    stream.write(buffer, 0, read);
                }
            }
        }
    }

    private boolean looksLikeModelFile(@NonNull File file, @NonNull String originalName) {
        if (!file.isFile() || file.length() < MIN_MODEL_BYTES || !isSupportedFileName(originalName)) return false;
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[256];
            int read = input.read(buffer);
            if (read <= 0) return false;
            String prefix = new String(buffer, 0, read, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
            return !(prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.contains("<head"));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSupportedFileName(@NonNull String fileName) {
        return validateSupportedImportFileName(fileName).supported;
    }

    @NonNull
    public static ValidationResult validateImportFileNameForBackend(@NonNull String backend, @NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        ValidationResult base = validateSupportedImportFileName(fileName);
        if (!base.supported) return base;
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(backend)) {
            if (lower.endsWith(".litertlm") || lower.endsWith(".task")) return ValidationResult.accepted(false);
            return ValidationResult.rejected(ERROR_UNSUPPORTED_MODEL_FILE,
                "LiteRT imports must be .litertlm or .task packages.");
        }
        if (TaiModelSpec.BACKEND_MLC_LLM.equals(backend)) {
            if (isMlcManifestFileName(lower)) return ValidationResult.accepted(true);
            return ValidationResult.rejected(ERROR_UNSUPPORTED_MODEL_FILE,
                "MLC imports must use a supported MLC manifest/package form; raw weights and native libraries are not allowed.");
        }
        return ValidationResult.rejected(ERROR_UNSUPPORTED_BACKEND, "Choose LiteRT or MLC before importing.");
    }

    @NonNull
    public static ValidationResult validateHuggingFaceImportUrl(@NonNull String backend, @NonNull String url) {
        String trimmed = url.trim();
        if (!trimmed.startsWith("https://")) {
            return ValidationResult.rejected(ERROR_INSECURE_URL, "Hugging Face imports require an https:// URL.");
        }
        String name = fileNameFromUrl(trimmed);
        return validateImportFileNameForBackend(backend, name);
    }

    @NonNull
    public static ValidationResult validateSupportedImportFileName(@NonNull String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".so") || lower.endsWith(".dylib") || lower.endsWith(".dll")) {
            return ValidationResult.rejected(ERROR_NATIVE_LIBRARY_FORBIDDEN,
                "Native libraries cannot be imported as models.");
        }
        if (isRawWeightFileName(lower)) {
            return ValidationResult.rejected(ERROR_RAW_WEIGHTS_FORBIDDEN,
                "Raw weight files are not supported. Select a LiteRT .litertlm/.task package or an MLC manifest.");
        }
        if (lower.endsWith(".litertlm") || lower.endsWith(".task")) {
            return ValidationResult.accepted(false);
        }
        if (isMlcManifestFileName(lower)) {
            return ValidationResult.accepted(true);
        }
        return ValidationResult.rejected(ERROR_UNSUPPORTED_MODEL_FILE,
            "Select a LiteRT .litertlm/.task package or an MLC manifest JSON file.");
    }

    @NonNull
    public static String stripModelExtension(@NonNull String fileName) {
        String value = fileName.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".litertlm")) return value.substring(0, value.length() - ".litertlm".length());
        if (lower.endsWith(".task")) return value.substring(0, value.length() - ".task".length());
        if (lower.endsWith(".mlc.json")) return value.substring(0, value.length() - ".mlc.json".length());
        if (lower.endsWith("-mlc.json")) return value.substring(0, value.length() - "-mlc.json".length());
        return value;
    }

    private static boolean isMlcManifestFileName(@NonNull String lowerFileName) {
        return lowerFileName.endsWith(".mlc.json") || lowerFileName.endsWith("-mlc.json");
    }

    private static boolean isRawWeightFileName(@NonNull String lowerFileName) {
        return lowerFileName.endsWith(".safetensors")
            || lowerFileName.endsWith(".gguf")
            || lowerFileName.endsWith(".bin")
            || lowerFileName.endsWith(".pt")
            || lowerFileName.endsWith(".onnx");
    }

    @NonNull
    private static String fileNameFromUrl(@NonNull String url) {
        int slash = url.lastIndexOf('/');
        String name = slash >= 0 ? url.substring(slash + 1) : url;
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        int fragment = name.indexOf('#');
        if (fragment >= 0) name = name.substring(0, fragment);
        return name;
    }

    @NonNull
    private TaiMlcPackageInstaller.InstallResult validateMlcManifestFile(@NonNull File file) {
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] bytes = new byte[(int) Math.min(file.length(), 10L * 1024L * 1024L + 1L)];
            int read = input.read(bytes);
            if (read <= 0 || read > 10L * 1024L * 1024L) {
                return TaiMlcPackageInstaller.InstallResult.failure(TaiMlcPackageInstaller.ERROR_INVALID_MANIFEST,
                    "MLC manifest is empty or too large.");
            }
            JSONObject manifest = new JSONObject(new String(bytes, 0, read, StandardCharsets.UTF_8));
            return new TaiMlcPackageInstaller().validateManifestInternal(manifest, store);
        } catch (Exception e) {
            return TaiMlcPackageInstaller.InstallResult.failure(TaiMlcPackageInstaller.ERROR_INVALID_MANIFEST,
                "MLC manifest is not valid JSON.");
        }
    }

    @NonNull
    private String readSmallTextFile(@NonNull File file) throws Exception {
        if (file.length() <= 0L || file.length() > 10L * 1024L * 1024L) {
            throw new IllegalStateException("MLC manifest is empty or too large.");
        }
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] bytes = new byte[(int) file.length()];
            int read = input.read(bytes);
            if (read <= 0) throw new IllegalStateException("MLC manifest is empty.");
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        }
    }

    @NonNull
    public static String sanitizeModelId(@NonNull String value) {
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
    }

    @NonNull
    private String sanitizeFileName(@NonNull String value) {
        String name = value.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[^A-Za-z0-9._-]", "-");
        return name.isEmpty() ? "model.litertlm" : name;
    }

    private void deleteEmptyDirectory(@NonNull File directory) {
        File[] files = directory.listFiles();
        if (files == null || files.length == 0) directory.delete();
    }

    @NonNull
    private String messageOrFallback(@NonNull Exception exception, @NonNull String fallback) {
        String message = exception.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message;
    }

    @NonNull
    private JSONObject error(int statusCode, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject result = new JSONObject();
        result.put("ok", false);
        result.put("error", code);
        result.put("message", message);
        result.put("_statusCode", statusCode);
        return result;
    }

    public static final class DocumentMetadata {
        public final String displayName;
        public final long sizeBytes;

        DocumentMetadata(@NonNull String displayName, long sizeBytes) {
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
        }
    }

    public static final class ValidationResult {
        public final boolean supported;
        public final boolean mlcManifest;
        @NonNull public final String errorCode;
        @NonNull public final String message;

        private ValidationResult(boolean supported, boolean mlcManifest,
                                 @NonNull String errorCode, @NonNull String message) {
            this.supported = supported;
            this.mlcManifest = mlcManifest;
            this.errorCode = errorCode;
            this.message = message;
        }

        @NonNull
        static ValidationResult accepted(boolean mlcManifest) {
            return new ValidationResult(true, mlcManifest, "", "");
        }

        @NonNull
        static ValidationResult rejected(@NonNull String errorCode, @NonNull String message) {
            return new ValidationResult(false, false, errorCode, message);
        }
    }
}
