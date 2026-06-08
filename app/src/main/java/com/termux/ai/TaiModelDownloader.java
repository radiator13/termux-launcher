package com.termux.ai;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
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
            "", "", authToken);
    }

    @NonNull
    public JSONObject startCatalogDownload(@NonNull TaiModelCatalog.CatalogEntry entry,
                                           @Nullable String authToken) throws JSONException {
        if (TaiModelSpec.FORMAT_MLC_PACKAGE.equals(entry.format)) {
            String safeModelId = sanitize(entry.modelId);
            File output = new File(store.getModelsDirectory(), safeModelId);
            String transferId = "download-" + safeModelId;
            JSONObject transfer = transfer(transferId, safeModelId, entry.providerPageUrl,
                output.getAbsolutePath(), "queued", 0L, entry.sizeBytes, "");
            store.upsertDownload(transfer);
            Intent intent = baseIntent(transferId, entry, output, authToken);
            intent.putExtra(TaiModelDownloadService.EXTRA_REPOSITORY_ID, entry.repositoryId);
            intent.putExtra(TaiModelDownloadService.EXTRA_REVISION, entry.revision);
            startService(intent);
            return started(transfer);
        }
        return startDownload(entry.modelId, entry.downloadUrl, entry.displayName, entry.license,
            entry.capabilities, entry.backend, entry.format, entry.architecture, entry.quantization,
            entry.contextWindow, entry.recommendedRamGb, entry.sha256, entry.runtimeLibrary, authToken);
    }

    @NonNull
    private JSONObject startDownload(
        @NonNull String modelId, @NonNull String url, @NonNull String displayName,
        @NonNull String license, @NonNull LinkedHashSet<String> capabilities,
        @NonNull String backend, @NonNull String format, @Nullable String architecture,
        @Nullable String quantization, int contextWindow, int recommendedRamGb,
        @Nullable String expectedSha256, @Nullable String runtimeLibrary, @Nullable String authToken
    ) throws JSONException {
        String safeModelId = sanitize(modelId);
        if (safeModelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        if (!url.startsWith("https://")) return error(400, "bad_request", "Model downloads require an https URL");

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
            recommendedRamGb, expectedSha256, runtimeLibrary);
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
        String runtimeLibrary,
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
                recommendedRamGb, emptyToNull(expectedSha256), emptyToNull(runtimeLibrary)
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

    public void runCatalogDownload(String transferId, String modelId, String repositoryId,
        String revision, File outputDirectory, String displayName, String license,
        LinkedHashSet<String> capabilities, String backend, String format, String architecture,
        String quantization, int contextWindow, int recommendedRamGb, String runtimeLibrary,
        @Nullable String authToken, @Nullable ProgressCallback callback) {
        File temporary = new File(outputDirectory.getAbsolutePath() + ".installing");
        long completed = 0L;
        long total = 0L;
        try {
            if (temporary.exists()) deleteTree(temporary);
            if (!temporary.mkdirs()) throw new IllegalStateException("Could not create temporary model directory.");
            String api = "https://huggingface.co/api/models/" + repositoryId + "/tree/" + revision + "?recursive=true&expand=true";
            String treeJson = readText(open(api, authToken, 0L));
            JSONArray tree = new JSONArray(treeJson);
            List<RepoFile> files = new ArrayList<>();
            for (int i = 0; i < tree.length(); i++) {
                JSONObject item = tree.optJSONObject(i);
                if (item == null || !"file".equals(item.optString("type"))) continue;
                String path = item.optString("path", "");
                if (!requiredMlcFile(path)) continue;
                JSONObject lfs = item.optJSONObject("lfs");
                String hash = lfs == null ? "" : lfs.optString("oid", "");
                long size = item.optLong("size", 0L);
                files.add(new RepoFile(path, size, hash)); total += size;
            }
            if (files.isEmpty()) throw new IllegalStateException("Pinned MLC repository contains no runtime model files.");
            if (temporary.getUsableSpace() < total) throw new IllegalStateException("Not enough free storage for this model.");
            for (RepoFile file : files) {
                if (TaiModelDownloadService.isCancelled(modelId)) throw new InterruptedException("Download cancelled.");
                File destination = new File(temporary, file.path);
                File parent = destination.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("Could not create model subdirectory.");
                String url = "https://huggingface.co/" + repositoryId + "/resolve/" + revision + "/" + file.path + "?download=true";
                downloadFile(modelId, url, destination, file.sha256, authToken);
                completed += file.size;
                persist(transfer(transferId, modelId, url, outputDirectory.getAbsolutePath(), "running", completed, total, ""), callback);
            }
            if (outputDirectory.exists()) deleteTree(outputDirectory);
            if (!temporary.renameTo(outputDirectory)) throw new IllegalStateException("Could not finalize MLC model package.");
            TaiModelSpec spec = new TaiModelSpec(modelId, displayName, "Downloaded model", "downloaded",
                outputDirectory.getAbsolutePath(), license, directorySize(outputDirectory), capabilities,
                false, null, backend, format, emptyToNull(architecture), emptyToNull(quantization),
                contextWindow, recommendedRamGb, null, emptyToNull(runtimeLibrary));
            store.upsertUserModel(spec);
            persist(transfer(transferId, modelId, repositoryId, outputDirectory.getAbsolutePath(), "complete", total, total, ""), callback);
        } catch (Exception e) {
            deleteTree(temporary);
            try { persist(transfer(transferId, modelId, repositoryId, outputDirectory.getAbsolutePath(), "failed", completed, total, e.getMessage()), callback); } catch (JSONException ignored) {}
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
        if (!lowerName.endsWith(".litertlm") && !lowerName.endsWith(".task") && !lowerName.endsWith(".gguf")) {
            return false;
        }
        if (file.length() < 1024L * 1024L) {
            return false;
        }
        try (InputStream input = new BufferedInputStream(new java.io.FileInputStream(file))) {
            byte[] buffer = new byte[256];
            int read = input.read(buffer);
            if (read <= 0) return false;
            if (lowerName.endsWith(".gguf")) return read >= 4 && buffer[0] == 'G' && buffer[1] == 'G' && buffer[2] == 'U' && buffer[3] == 'F';
            String prefix = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8).trim().toLowerCase();
            return !(prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.contains("<head"));
        } catch (Exception e) {
            return false;
        }
    }

    private Intent baseIntent(String transferId, TaiModelCatalog.CatalogEntry entry, File output, @Nullable String authToken) {
        Intent intent = new Intent(appContext, TaiModelDownloadService.class);
        intent.setAction(TaiModelDownloadService.ACTION_DOWNLOAD);
        intent.putExtra(TaiModelDownloadService.EXTRA_TRANSFER_ID, transferId);
        intent.putExtra(TaiModelDownloadService.EXTRA_MODEL_ID, entry.modelId);
        intent.putExtra(TaiModelDownloadService.EXTRA_URL, entry.providerPageUrl);
        intent.putExtra(TaiModelDownloadService.EXTRA_OUTPUT_PATH, output.getAbsolutePath());
        intent.putExtra(TaiModelDownloadService.EXTRA_DISPLAY_NAME, entry.displayName);
        intent.putExtra(TaiModelDownloadService.EXTRA_LICENSE, entry.license);
        intent.putExtra(TaiModelDownloadService.EXTRA_CAPABILITIES, entry.capabilities.toArray(new String[0]));
        intent.putExtra(TaiModelDownloadService.EXTRA_AUTH_TOKEN, authToken == null ? "" : authToken);
        putRuntimeMetadata(intent, entry.backend, entry.format, entry.architecture, entry.quantization,
            entry.contextWindow, entry.recommendedRamGb, entry.sha256, entry.runtimeLibrary);
        return intent;
    }

    private void putRuntimeMetadata(Intent intent, String backend, String format, @Nullable String architecture,
        @Nullable String quantization, int contextWindow, int recommendedRamGb, @Nullable String sha256,
        @Nullable String runtimeLibrary) {
        intent.putExtra(TaiModelDownloadService.EXTRA_BACKEND, backend == null ? "" : backend);
        intent.putExtra(TaiModelDownloadService.EXTRA_FORMAT, format == null ? "" : format);
        intent.putExtra(TaiModelDownloadService.EXTRA_ARCHITECTURE, architecture == null ? "" : architecture);
        intent.putExtra(TaiModelDownloadService.EXTRA_QUANTIZATION, quantization == null ? "" : quantization);
        intent.putExtra(TaiModelDownloadService.EXTRA_CONTEXT_WINDOW, contextWindow);
        intent.putExtra(TaiModelDownloadService.EXTRA_RECOMMENDED_RAM_GB, recommendedRamGb);
        intent.putExtra(TaiModelDownloadService.EXTRA_SHA256, sha256 == null ? "" : sha256);
        intent.putExtra(TaiModelDownloadService.EXTRA_RUNTIME_LIBRARY, runtimeLibrary == null ? "" : runtimeLibrary);
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

    private String readText(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new IllegalStateException("Request failed with HTTP " + status);
        try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void downloadFile(String modelId, String url, File destination, String expectedSha256, @Nullable String authToken) throws Exception {
        File partial = new File(destination.getAbsolutePath() + ".part");
        long existing = partial.isFile() ? partial.length() : 0L;
        HttpURLConnection connection = open(url, authToken, existing);
        int status = connection.getResponseCode();
        if (status == 416) { existing = 0L; partial.delete(); connection = open(url, authToken, 0L); status = connection.getResponseCode(); }
        if (status < 200 || status >= 300) throw new IllegalStateException("Download failed with HTTP " + status);
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(partial, existing > 0L && status == 206)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (TaiModelDownloadService.isCancelled(modelId)) throw new InterruptedException("Download cancelled.");
                output.write(buffer, 0, read);
            }
        }
        if (expectedSha256 != null && !expectedSha256.isEmpty() && !expectedSha256.equalsIgnoreCase(sha256(partial))) {
            throw new IllegalStateException("Downloaded shard failed SHA-256 verification: " + destination.getName());
        }
        if (destination.exists() && !destination.delete()) throw new IllegalStateException("Could not replace file: " + destination.getName());
        if (!partial.renameTo(destination)) throw new IllegalStateException("Could not finalize file: " + destination.getName());
    }

    private boolean requiredMlcFile(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.equals("mlc-chat-config.json")
            || lower.equals("ndarray-cache.json")
            || lower.endsWith(".json")
            || lower.endsWith(".txt")
            || lower.endsWith(".model")
            || lower.endsWith(".bin");
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

    private long directorySize(File file) {
        if (file == null || !file.exists()) return 0L;
        if (file.isFile()) return file.length();
        long total = 0L;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) total += directorySize(child);
        return total;
    }

    private void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    @Nullable
    private String emptyToNull(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static final class RepoFile {
        final String path; final long size; final String sha256;
        RepoFile(String path, long size, String sha256) { this.path = path; this.size = size; this.sha256 = sha256 == null ? "" : sha256; }
    }
}
