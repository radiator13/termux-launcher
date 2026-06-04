package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashSet;

public final class TaiModelDownloader {
    private final TaiModelStore store;

    public TaiModelDownloader(@NonNull TaiModelStore store) {
        this.store = store;
    }

    @NonNull
    public JSONObject startDownload(
        @NonNull String modelId,
        @NonNull String url,
        @NonNull String displayName,
        @NonNull String license,
        @NonNull LinkedHashSet<String> capabilities
    ) throws JSONException {
        String safeModelId = sanitize(modelId);
        if (safeModelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        if (!url.startsWith("https://")) return error(400, "bad_request", "Model downloads require an https URL");

        File modelDir = new File(store.getModelsDirectory(), safeModelId);
        File output = new File(modelDir, fileNameFromUrl(url));
        String transferId = "download-" + safeModelId;
        JSONObject transfer = transfer(transferId, safeModelId, url, output.getAbsolutePath(), "queued", 0L, 0L, "");
        store.upsertDownload(transfer);

        Thread thread = new Thread(() -> runDownload(transferId, safeModelId, url, output, displayName, license, capabilities), "tai-model-download");
        thread.setDaemon(true);
        thread.start();

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("started", true);
        data.put("transfer", transfer);
        data.put("message", "Download started in the Android app process. Check tai downloads for progress.");
        return data;
    }

    private void runDownload(
        String transferId,
        String modelId,
        String url,
        File output,
        String displayName,
        String license,
        LinkedHashSet<String> capabilities
    ) {
        long bytesRead = 0L;
        long contentLength = 0L;
        try {
            File parent = output.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IllegalStateException("Failed to create model directory");
            }

            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Download failed with HTTP " + status);
            }
            contentLength = connection.getHeaderFieldLong("Content-Length", -1L);
            store.upsertDownload(transfer(transferId, modelId, url, output.getAbsolutePath(), "running", 0L, contentLength, ""));

            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream outputStream = new FileOutputStream(output, false)) {
                byte[] buffer = new byte[1024 * 64];
                int read;
                long lastPersisted = 0L;
                while ((read = input.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                    bytesRead += read;
                    if (bytesRead - lastPersisted >= 1024L * 1024L) {
                        store.upsertDownload(transfer(transferId, modelId, url, output.getAbsolutePath(), "running", bytesRead, contentLength, ""));
                        lastPersisted = bytesRead;
                    }
                }
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
                false
            );
            store.upsertUserModel(spec);
            store.upsertDownload(transfer(transferId, modelId, url, output.getAbsolutePath(), "complete", output.length(), output.length(), ""));
        } catch (Exception e) {
            try {
                store.upsertDownload(transfer(transferId, modelId, url, output.getAbsolutePath(), "failed", bytesRead, contentLength, e.getMessage()));
            } catch (JSONException ignored) {
            }
        }
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
}
