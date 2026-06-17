package com.termux.ai;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Locale;

public final class TaiModelDownloader {
    public interface ProgressCallback {
        void onProgress(@NonNull JSONObject transfer);
    }

    private final Context appContext;
    private final TaiModelStore store;

    public TaiModelDownloader(@NonNull Context context, @NonNull TaiModelStore store) {
        appContext = context.getApplicationContext();
        this.store = store;
    }

    @NonNull
    public JSONObject startDownload(
        @NonNull String modelId,
        @NonNull String url,
        @NonNull String displayName,
        @NonNull String license,
        @NonNull LinkedHashSet<String> capabilities,
        @Nullable String authToken
    ) throws JSONException {
        return startDownload(modelId, url, displayName, license, capabilities,
            TaiModelSpec.inferBackend(url), TaiModelSpec.inferFormat(url), "", "", 4096, 0,
            "", authToken);
    }

    @NonNull
    public JSONObject startCatalogDownload(@NonNull TaiModelCatalog.CatalogEntry entry,
                                           @Nullable String authToken) throws JSONException {
        return startDownload(entry.modelId, entry.downloadUrl, entry.displayName, entry.license,
            entry.capabilities, entry.backend, entry.format, entry.architecture, entry.quantization,
            entry.contextWindow, entry.recommendedRamGb, entry.sha256, authToken);
    }

    @NonNull
    private JSONObject startDownload(
        @NonNull String modelId, @NonNull String url, @NonNull String displayName,
        @NonNull String license, @NonNull LinkedHashSet<String> capabilities,
        @NonNull String backend, @NonNull String format, @Nullable String architecture,
        @Nullable String quantization, int contextWindow, int recommendedRamGb,
        @Nullable String expectedSha256, @Nullable String authToken
    ) throws JSONException {
        String safeModelId = sanitize(modelId);
        if (safeModelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        if (!url.startsWith("https://")) return error(400, "insecure_url", "Model downloads require an https URL");

        File modelDir = new File(store.getModelsDirectory(), safeModelId);
        File output = new File(modelDir, fileNameFromUrl(url));
        String transferId = "download-" + safeModelId;
        JSONObject transfer = transfer(transferId, safeModelId, url, output.getAbsolutePath(), "queued", 0L, 0L, "");
        store.upsertDownload(transfer);

        Intent intent = new Intent(appContext, TaiModelDownloadService.class);
        intent.setAction(TaiModelDownloadService.ACTION_DOWNLOAD);
        intent.putExtra(TaiModelDownloadService.EXTRA_TRANSFER_ID, transferId);
        intent.putExtra(TaiModelDownloadService.EXTRA_MODEL_ID, safeModelId);
        intent.putExtra(TaiModelDownloadService.EXTRA_URL, url);
        intent.putExtra(TaiModelDownloadService.EXTRA_OUTPUT_PATH, output.getAbsolutePath());
        intent.putExtra(TaiModelDownloadService.EXTRA_DISPLAY_NAME, displayName);
        intent.putExtra(TaiModelDownloadService.EXTRA_LICENSE, license);
        intent.putExtra(TaiModelDownloadService.EXTRA_CAPABILITIES, capabilities.toArray(new String[0]));
        intent.putExtra(TaiModelDownloadService.EXTRA_AUTH_TOKEN, authToken == null ? "" : authToken);
        putRuntimeMetadata(intent, backend, format, architecture, quantization, contextWindow,
            recommendedRamGb, expectedSha256);
        startService(intent);

        return started(transfer);
    }

    public void runDownload(
        String transferId,
        String modelId,
        String url,
        File output,
        String displayName,
        String license,
        LinkedHashSet<String> capabilities,
        String backend,
        String format,
        String architecture,
        String quantization,
        int contextWindow,
        int recommendedRamGb,
        String expectedSha256,
        @Nullable String authToken,
        @Nullable ProgressCallback callback
    ) {
        long bytesRead = 0L;
        long contentLength = 0L;
        try {
            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Failed to create model directory");
            }

            File partial = new File(output.getAbsolutePath() + ".part");
            long existing = partial.isFile() ? partial.length() : 0L;
            HttpURLConnection connection = open(url, authToken, existing);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Download failed with HTTP " + status);
            }
            long responseLength = connection.getHeaderFieldLong("Content-Length", -1L);
            contentLength = responseLength > 0 ? existing + responseLength : -1L;
            bytesRead = existing;
            persist(transfer(transferId, modelId, url, output.getAbsolutePath(), "running", bytesRead, contentLength, ""), callback);

            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream outputStream = new FileOutputStream(partial, existing > 0L && connection.getResponseCode() == 206)) {
                byte[] buffer = new byte[1024 * 64];
                int read;
                long lastPersisted = 0L;
                while ((read = input.read(buffer)) != -1) {
                    if (TaiModelDownloadService.isCancelled(modelId)) throw new InterruptedException("Download cancelled.");
                    outputStream.write(buffer, 0, read);
                    bytesRead += read;
                    if (bytesRead - lastPersisted >= 1024L * 1024L) {
                        persist(transfer(transferId, modelId, url, output.getAbsolutePath(), "running", bytesRead, contentLength, ""), callback);
                        lastPersisted = bytesRead;
                    }
                }
            }

            if (!expectedSha256.isEmpty() && !expectedSha256.equalsIgnoreCase(sha256(partial))) {
                throw new IllegalStateException("Downloaded model failed SHA-256 verification.");
            }
            if (output.exists() && !output.delete()) throw new IllegalStateException("Could not replace model file.");
            if (!partial.renameTo(output)) throw new IllegalStateException("Could not finalize model download.");

            // Detect and route MLC packages
            if (isMlcPackage(output, url, backend, format)) {
                String manifestJson = readManifestString(output);
                if (manifestJson == null || manifestJson.trim().isEmpty()) {
                    throw new IllegalStateException(TaiMlcPackageInstaller.ERROR_INVALID_MANIFEST);
                }

                TaiMlcPackageInstaller installer = new TaiMlcPackageInstaller();
                TaiMlcPackageInstaller.InstallResult validation = installer.validateManifestInternal(
                    new JSONObject(manifestJson), store);
                if (!validation.success) {
                    throw new IllegalStateException(validation.errorCode);
                }

                // Download files listed in manifest
                JSONObject manifest = new JSONObject(manifestJson);
                JSONArray files = manifest.getJSONArray("files");
                File downloadDir = output.getParentFile();
                String baseUrl = baseUrlFromUrl(url);
                long manifestSize = output.length();
                long totalMlcBytes = manifestSize;
                for (int i = 0; i < files.length(); i++) {
                    totalMlcBytes += files.getJSONObject(i).getLong("size");
                }
                long currentBytes = manifestSize;

                for (int i = 0; i < files.length(); i++) {
                    JSONObject fileEntry = files.getJSONObject(i);
                    String path = fileEntry.getString("path");
                    String fileUrl = baseUrl + path;
                    if (!fileUrl.startsWith("https://")) {
                        throw new IllegalStateException(TaiMlcPackageInstaller.ERROR_INSECURE_URL);
                    }
                    File fileOutput = new File(downloadDir, path);
                    File fileParent = fileOutput.getParentFile();
                    if (fileParent != null && !fileParent.exists() && !fileParent.mkdirs()) {
                        throw new IllegalStateException(TaiMlcPackageInstaller.ERROR_FILE_MISSING);
                    }

                    File filePartial = new File(fileOutput.getAbsolutePath() + ".part");
                    HttpURLConnection fileConn = open(fileUrl, authToken, 0);
                    int fileStatus = fileConn.getResponseCode();
                    if (fileStatus < 200 || fileStatus >= 300) {
                        throw new IllegalStateException(TaiMlcPackageInstaller.ERROR_FILE_MISSING);
                    }

                    try (InputStream fileInput = new BufferedInputStream(fileConn.getInputStream());
                         FileOutputStream fileOut = new FileOutputStream(filePartial)) {
                        byte[] buffer = new byte[1024 * 64];
                        int read;
                        while ((read = fileInput.read(buffer)) != -1) {
                            if (TaiModelDownloadService.isCancelled(modelId)) throw new InterruptedException("Download cancelled.");
                            fileOut.write(buffer, 0, read);
                            currentBytes += read;
                        }
                    }

                    if (fileOutput.exists() && !fileOutput.delete()) throw new IllegalStateException("Could not replace file.");
                    if (!filePartial.renameTo(fileOutput)) throw new IllegalStateException("Could not finalize file download.");

                    persist(transfer(transferId, modelId, url, output.getAbsolutePath(), "running", currentBytes, totalMlcBytes, ""), callback);
                }

                TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, downloadDir, store);
                if (!result.success) {
                    throw new IllegalStateException(result.errorCode);
                }

                persist(transfer(transferId, modelId, url, output.getAbsolutePath(), "complete", currentBytes, currentBytes, ""), callback);
                return;
            }

            if (!looksLikeModelFile(output)) {
                throw new IllegalStateException("Downloaded file does not look like a LiteRT-LM model. It may be an HTML login or error page.");
            }

            TaiModelSpec spec = new TaiModelSpec(
                modelId,
                displayName.isEmpty() ? modelId : displayName,
                "Downloaded model",
                "downloaded",
                output.getAbsolutePath(),
                license.isEmpty() ? "User accepted provider terms externally" : license,
                output.length(),
                capabilities,
                false,
                null,
                backend.isEmpty() ? TaiModelSpec.inferBackend(output.getAbsolutePath()) : backend,
                format.isEmpty() ? TaiModelSpec.inferFormat(output.getAbsolutePath()) : format,
                emptyToNull(architecture), emptyToNull(quantization), contextWindow,
                recommendedRamGb, emptyToNull(expectedSha256)
            );
            store.upsertUserModel(spec);
            persist(transfer(transferId, modelId, url, output.getAbsolutePath(), "complete", output.length(), output.length(), ""), callback);
        } catch (Exception e) {
            try {
                persist(transfer(transferId, modelId, url, output.getAbsolutePath(), "failed", bytesRead, contentLength, e.getMessage()), callback);
            } catch (JSONException ignored) {
            }
        }
    }

    private void persist(@NonNull JSONObject transfer, @Nullable ProgressCallback callback) {
        store.upsertDownload(transfer);
        if (callback != null) callback.onProgress(transfer);
    }

    private boolean shouldAttachBearerToken(@NonNull String url, @Nullable String authToken) {
        return authToken != null
            && !authToken.trim().isEmpty()
            && (url.startsWith("https://huggingface.co/") || url.startsWith("https://www.huggingface.co/"));
    }

    @NonNull
    private JSONObject transfer(String id, String modelId, String url, String path, String status, long bytesRead, long totalBytes, String error) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("modelId", modelId);
        json.put("url", url);
        json.put("path", path);
        json.put("status", status);
        json.put("bytesRead", bytesRead);
        json.put("totalBytes", totalBytes);
        json.put("error", error == null ? "" : error);
        json.put("updatedAtMs", System.currentTimeMillis());
        return json;
    }

    @NonNull
    private JSONObject error(int statusCode, String code, String message) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("ok", false);
        json.put("error", code);
        json.put("message", message);
        json.put("_statusCode", statusCode);
        return json;
    }

    @NonNull
    private String sanitize(@NonNull String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }

    @NonNull
    private String fileNameFromUrl(@NonNull String url) {
        int slash = url.lastIndexOf('/');
        String name = slash >= 0 ? url.substring(slash + 1) : url;
        int query = name.indexOf('?');
        if (query >= 0) name = name.substring(0, query);
        name = sanitize(name);
        return name.isEmpty() ? "model.bin" : name;
    }

    private boolean looksLikeModelFile(@NonNull File file) {
        String lowerName = file.getName().toLowerCase();
        if (!lowerName.endsWith(".litertlm") && !lowerName.endsWith(".task")) {
            return false;
        }
        if (file.length() < 1024L * 1024L) {
            return false;
        }
        try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] buffer = new byte[256];
            int read = input.read(buffer);
            if (read <= 0) return false;
            String prefix = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase();
            return !(prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.contains("<head"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isMlcPackage(@NonNull File file, @NonNull String url, @NonNull String backend, @NonNull String format) {
        boolean urlHint = hasMlcHint(url);
        boolean paramHint = TaiModelSpec.BACKEND_MLC_LLM.equals(backend) && TaiModelSpec.FORMAT_MLC.equals(format);
        if (!urlHint && !paramHint) return false;

        if (!file.exists() || file.length() == 0 || file.length() > 10 * 1024 * 1024) return false;

        try {
            String content = readManifestString(file);
            if (content == null) return false;
            String trimmed = content.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html")) return false;

            JSONObject manifest = new JSONObject(content);
            return TaiModelSpec.BACKEND_MLC_LLM.equals(manifest.optString("backend", ""))
                && TaiModelSpec.FORMAT_MLC.equals(manifest.optString("format", ""));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasMlcHint(@NonNull String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains(".mlc") || lower.contains("mlc-llm") || lower.contains("mlc-ai");
    }

    @Nullable
    private String readManifestString(@NonNull File file) {
        try {
            if (file.length() > 10 * 1024 * 1024) return null;
            try (InputStream input = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()];
                int read = input.read(bytes);
                return new String(bytes, 0, read, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private String baseUrlFromUrl(@NonNull String url) {
        int query = url.indexOf('?');
        String base = query >= 0 ? url.substring(0, query) : url;
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash < 0) return base.endsWith("/") ? base : base + "/";
        return base.substring(0, lastSlash + 1);
    }

    private void putRuntimeMetadata(Intent intent, String backend, String format, @Nullable String architecture,
        @Nullable String quantization, int contextWindow, int recommendedRamGb, @Nullable String sha256) {
        intent.putExtra(TaiModelDownloadService.EXTRA_BACKEND, backend == null ? "" : backend);
        intent.putExtra(TaiModelDownloadService.EXTRA_FORMAT, format == null ? "" : format);
        intent.putExtra(TaiModelDownloadService.EXTRA_ARCHITECTURE, architecture == null ? "" : architecture);
        intent.putExtra(TaiModelDownloadService.EXTRA_QUANTIZATION, quantization == null ? "" : quantization);
        intent.putExtra(TaiModelDownloadService.EXTRA_CONTEXT_WINDOW, contextWindow);
        intent.putExtra(TaiModelDownloadService.EXTRA_RECOMMENDED_RAM_GB, recommendedRamGb);
        intent.putExtra(TaiModelDownloadService.EXTRA_SHA256, sha256 == null ? "" : sha256);
    }

    private void startService(Intent intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) appContext.startForegroundService(intent);
        else appContext.startService(intent);
    }

    private JSONObject started(JSONObject transfer) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("started", true);
        data.put("transfer", transfer);
        data.put("message", "Download started in the Android app process. Check tai downloads for progress.");
        return data;
    }

    private HttpURLConnection open(String url, @Nullable String authToken, long offset) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        if (offset > 0L) connection.setRequestProperty("Range", "bytes=" + offset + "-");
        if (shouldAttachBearerToken(url, authToken)) connection.setRequestProperty("Authorization", "Bearer " + authToken.trim());
        return connection;
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] buffer = new byte[1024 * 128];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        StringBuilder builder = new StringBuilder();
        for (byte value : digest.digest()) builder.append(String.format(Locale.US, "%02x", value));
        return builder.toString();
    }

    @Nullable
    private String emptyToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
