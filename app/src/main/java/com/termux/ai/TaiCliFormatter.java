package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public final class TaiCliFormatter {
    private TaiCliFormatter() {
    }

    @NonNull
    public static String format(@NonNull String command, @NonNull JSONObject data) {
        try {
            if (!data.optBoolean("ok", true) || data.has("error")) {
                return formatError(data);
            }
            switch (command) {
                case "status":
                    return formatStatus(data);
                case "runtime":
                    return formatRuntime(data);
                case "models":
                    return formatModels(data);
                case "downloads":
                    return formatDownloads(data);
                case "import":
                    return formatImport(data);
                case "download":
                    return formatDownloadStarted(data);
                case "delete":
                    return formatDelete(data);
                case "load":
                    return formatLoad(data);
                case "unload":
                    return formatUnload(data);
                case "keep-warm":
                    return formatKeepWarm(data);
                case "cancel":
                    return formatCancel(data);
                case "launcher-status":
                    return formatLauncherStatus(data);
                default:
                    return formatGeneric(data);
            }
        } catch (Exception e) {
            return data.toString() + "\n";
        }
    }

    @NonNull
    private static String formatError(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI error");
        String message = clean(data.optString("message", ""));
        if (!message.isEmpty()) out.append(": ").append(message);
        out.append('\n');
        appendValue(out, "Code", clean(data.optString("error", "")));
        appendValue(out, "Runtime", clean(data.optString("runtime", "")));
        appendValue(out, "Provider page", clean(data.optString("providerPageUrl", "")));
        appendValue(out, "Download URL", clean(data.optString("downloadUrl", "")));
        return out.toString();
    }

    @NonNull
    private static String formatStatus(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI status\n");
        JSONObject runtime = data.optJSONObject("runtime");
        if (runtime != null) {
            appendValue(out, "Runtime", runtime.optString("runtimeName", ""));
            appendValue(out, "Loaded model", nullable(runtime, "loadedModelId", "none"));
            appendValue(out, "Lifecycle", runtime.optString("state", ""));
            appendValue(out, "Backend", runtime.optString("backend", ""));
            appendValue(out, "Fallback", nullable(runtime, "backendFallbackReason", ""));
            appendValue(out, "Active generation", runtime.optBoolean("activeGeneration", false) ? "yes" : "no");
            appendValue(out, "Keep warm", runtime.optLong("keepWarmRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("keepWarmRemainingMs", 0L)) : "off");
            appendValue(out, "Idle unload", runtime.optLong("idleUnloadRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("idleUnloadRemainingMs", 0L)) : "off");
            appendValue(out, "State", runtime.optString("status", ""));
        }

        JSONObject settings = data.optJSONObject("settings");
        if (settings != null) {
            JSONObject roles = settings.optJSONObject("roles");
            if (roles != null) {
                out.append("\nRoles\n");
                appendValue(out, "Default assistant", roles.optString(TaiModelRegistry.ROLE_DEFAULT_ASSISTANT, ""));
            }
            out.append("\nSettings\n");
            appendValue(out, "Idle unload", settings.optInt("idleUnloadMinutes", 0) + " min");
            appendValue(out, "Hugging Face token", settings.optBoolean("huggingFaceTokenConfigured", false) ? "configured" : "not configured");
            JSONObject options = settings.optJSONObject("runtimeOptions");
            if (options != null) {
                appendValue(out, "Accelerator", nullable(options, "accelerator", "Auto / GPU preferred"));
                appendValue(out, "Max tokens", nullable(options, "maxTokens", "Auto / Gallery default"));
                appendValue(out, "Temperature", nullable(options, "temperature", "Auto / Gallery default"));
            }
        }

        JSONArray limitations = data.optJSONArray("limitations");
        if (limitations != null && limitations.length() > 0) {
            out.append("\nLimitations\n");
            appendBullets(out, limitations);
        }
        return out.toString();
    }

    @NonNull
    private static String formatRuntime(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI runtime\n");
        JSONObject runtime = data.optJSONObject("runtime");
        if (runtime != null) appendRuntimeState(out, runtime);
        JSONObject settings = data.optJSONObject("settings");
        if (settings != null) {
            JSONObject options = settings.optJSONObject("runtimeOptions");
            if (options != null) {
                out.append("\nDefaults\n");
                appendValue(out, "Accelerator", nullable(options, "accelerator", "Auto / GPU preferred"));
                appendValue(out, "Max tokens", nullable(options, "maxTokens", "Auto / Gallery default"));
                appendValue(out, "TopK", nullable(options, "topK", "Auto / Gallery default"));
                appendValue(out, "TopP", nullable(options, "topP", "Auto / Gallery default"));
                appendValue(out, "Temperature", nullable(options, "temperature", "Auto / Gallery default"));
                appendValue(out, "Speculative decoding", nullable(options, "speculativeDecodingEnabled", "Auto / Gallery default"));
            }
        }
        return out.toString();
    }

    @NonNull
    private static String formatModels(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI models\n");
        appendValue(out, "Storage", data.optString("storageDirectory", ""));
        appendValue(out, "Bundled model files", data.optBoolean("bundledModelFiles", false) ? "yes" : "no");

        JSONObject roles = data.optJSONObject("roles");
        if (roles != null) {
            out.append("\nRoles\n");
            appendValue(out, "Default assistant", roles.optString(TaiModelRegistry.ROLE_DEFAULT_ASSISTANT, ""));
        }

        JSONArray models = data.optJSONArray("models");
        out.append("\nAvailable models\n");
        if (models == null || models.length() == 0) {
            out.append("  none\n");
        } else {
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.optJSONObject(i);
                if (model == null) continue;
                out.append("  ").append(model.optString("id", "unknown"));
                if (!model.isNull("localPath")) {
                    out.append(" [downloaded/imported]");
                } else {
                    out.append(" [catalog]");
                }
                out.append('\n');
                appendValue(out, "    Role", model.optString("roleHint", ""));
                appendValue(out, "    Source", model.optString("source", ""));
                appendValue(out, "    Size", model.optLong("sizeBytes", 0L) > 0 ? formatBytes(model.optLong("sizeBytes")) : "not downloaded");
                appendValue(out, "    Capabilities", join(model.optJSONArray("capabilities")));
            }
        }

        JSONArray downloads = data.optJSONArray("downloads");
        if (downloads != null && downloads.length() > 0) {
            out.append("\nDownloads\n");
            appendDownloads(out, downloads);
        }
        return out.toString();
    }

    @NonNull
    private static String formatDownloads(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI downloads\n");
        JSONArray downloads = data.optJSONArray("downloads");
        if (downloads == null || downloads.length() == 0) {
            out.append("  none\n");
        } else {
            appendDownloads(out, downloads);
        }
        return out.toString();
    }

    @NonNull
    private static String formatImport(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("Model imported\n");
        JSONObject model = data.optJSONObject("model");
        if (model != null) {
            appendValue(out, "Model", model.optString("id", ""));
            appendValue(out, "Path", nullable(model, "localPath", ""));
            appendValue(out, "Capabilities", join(model.optJSONArray("capabilities")));
        }
        appendValue(out, "Note", data.optString("message", ""));
        return out.toString();
    }

    @NonNull
    private static String formatDownloadStarted(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("started", false) ? "Download started\n" : "Download request\n");
        JSONObject transfer = data.optJSONObject("transfer");
        if (transfer != null) appendTransfer(out, transfer);
        appendValue(out, "Note", data.optString("message", ""));
        return out.toString();
    }

    @NonNull
    private static String formatDelete(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("deleted", false) ? "Model deleted\n" : "No matching model was deleted\n");
        appendValue(out, "Model", data.optString("modelId", ""));
        return out.toString();
    }

    @NonNull
    private static String formatLoad(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("Model loaded\n");
        appendValue(out, "Model", data.optString("loadedModelId", ""));
        appendValue(out, "Runtime", data.optString("runtime", ""));
        appendValue(out, "Backend", data.optString("backend", ""));
        appendValue(out, "Fallback", nullable(data, "backendFallbackReason", ""));
        appendValue(out, "Path", data.optString("modelPath", ""));
        JSONObject state = data.optJSONObject("state");
        if (state != null) {
            appendValue(out, "Lifecycle", state.optString("state", ""));
            appendValue(out, "Keep warm", state.optLong("keepWarmRemainingMs", 0L) > 0L ? formatDuration(state.optLong("keepWarmRemainingMs", 0L)) : "off");
            appendValue(out, "Idle unload", state.optLong("idleUnloadRemainingMs", 0L) > 0L ? formatDuration(state.optLong("idleUnloadRemainingMs", 0L)) : "off");
        }
        return out.toString();
    }

    @NonNull
    private static String formatUnload(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("Model unloaded\n");
        appendValue(out, "Previous model", nullable(data, "unloadedModelId", "none"));
        appendValue(out, "Runtime", data.optString("runtime", ""));
        return out.toString();
    }

    @NonNull
    private static String formatKeepWarm(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("Runtime keep-warm enabled\n");
        appendValue(out, "Minutes", data.optInt("keepWarmMinutes", 0) + "");
        JSONObject state = data.optJSONObject("state");
        if (state != null) appendRuntimeState(out, state);
        return out.toString();
    }

    @NonNull
    private static String formatCancel(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append(data.optBoolean("cancelled", false) ? "Generation cancel requested\n" : "No active generation\n");
        appendValue(out, "Message", data.optString("message", ""));
        JSONObject state = data.optJSONObject("state");
        if (state != null) appendRuntimeState(out, state);
        return out.toString();
    }

    @NonNull
    private static String formatLauncherStatus(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("LauncherCtl status\n");
        appendValue(out, "API", data.optString("apiVersion", ""));
        appendValue(out, "Backend", data.optString("backendType", "") + " / " + data.optString("backendState", ""));
        appendValue(out, "Status", data.optString("statusMessage", ""));
        appendValue(out, "Privileged available", data.optBoolean("isPrivilegedAvailable", false) ? "yes" : "no");
        appendValue(out, "Notification listener", data.optBoolean("notificationListenerConnected", false) ? "connected" : "not connected");
        return out.toString();
    }

    @NonNull
    private static String formatGeneric(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        JSONArray names = data.names();
        if (names == null || names.length() == 0) return "{}\n";
        for (int i = 0; i < names.length(); i++) {
            String key = names.optString(i, "");
            Object value = data.opt(key);
            appendValue(out, key, value == null || JSONObject.NULL.equals(value) ? "" : String.valueOf(value));
        }
        return out.toString();
    }

    private static void appendDownloads(@NonNull StringBuilder out, @NonNull JSONArray downloads) {
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject transfer = downloads.optJSONObject(i);
            if (transfer == null) continue;
            out.append("  ").append(i + 1).append(". ");
            out.append(transfer.optString("modelId", transfer.optString("id", "unknown")));
            out.append(" - ").append(transfer.optString("status", "unknown"));
            String progress = progress(transfer.optLong("bytesRead", 0L), transfer.optLong("totalBytes", 0L));
            if (!progress.isEmpty()) out.append(" (").append(progress).append(")");
            out.append('\n');
            appendValue(out, "     Path", transfer.optString("path", ""));
            appendValue(out, "     Error", transfer.optString("error", ""));
        }
    }

    private static void appendRuntimeState(@NonNull StringBuilder out, @NonNull JSONObject runtime) {
        appendValue(out, "Runtime", runtime.optString("runtimeName", ""));
        appendValue(out, "Lifecycle", runtime.optString("state", ""));
        appendValue(out, "Loaded model", nullable(runtime, "loadedModelId", "none"));
        appendValue(out, "Backend", runtime.optString("backend", ""));
        appendValue(out, "Fallback", nullable(runtime, "backendFallbackReason", ""));
        appendValue(out, "Active generation", runtime.optBoolean("activeGeneration", false) ? "yes" : "no");
        appendValue(out, "Keep warm", runtime.optLong("keepWarmRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("keepWarmRemainingMs", 0L)) : "off");
        appendValue(out, "Idle unload", runtime.optLong("idleUnloadRemainingMs", 0L) > 0L ? formatDuration(runtime.optLong("idleUnloadRemainingMs", 0L)) : "off");
        appendValue(out, "Status", runtime.optString("status", ""));
    }

    private static void appendTransfer(@NonNull StringBuilder out, @NonNull JSONObject transfer) {
        appendValue(out, "Model", transfer.optString("modelId", ""));
        appendValue(out, "Status", transfer.optString("status", ""));
        appendValue(out, "Progress", progress(transfer.optLong("bytesRead", 0L), transfer.optLong("totalBytes", 0L)));
        appendValue(out, "Path", transfer.optString("path", ""));
        appendValue(out, "Error", transfer.optString("error", ""));
    }

    private static void appendBullets(@NonNull StringBuilder out, @NonNull JSONArray values) {
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (!value.isEmpty()) out.append("  - ").append(value).append('\n');
        }
    }

    private static void appendValue(@NonNull StringBuilder out, @NonNull String label, @NonNull String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return;
        out.append(label).append(": ").append(clean).append('\n');
    }

    @NonNull
    private static String nullable(@NonNull JSONObject object, @NonNull String key, @NonNull String fallback) {
        if (!object.has(key) || object.isNull(key)) return fallback;
        return clean(object.optString(key, fallback));
    }

    @NonNull
    private static String join(JSONArray values) {
        if (values == null || values.length() == 0) return "";
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (value.isEmpty()) continue;
            if (joined.length() > 0) joined.append(", ");
            joined.append(value);
        }
        return joined.toString();
    }

    @NonNull
    private static String progress(long bytesRead, long totalBytes) {
        if (bytesRead <= 0L && totalBytes <= 0L) return "";
        if (totalBytes <= 0L) return formatBytes(bytesRead);
        double percent = Math.max(0d, Math.min(100d, (bytesRead * 100d) / totalBytes));
        return formatBytes(bytesRead) + " / " + formatBytes(totalBytes) + " (" + String.format(Locale.US, "%.1f", percent) + "%)";
    }

    @NonNull
    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024d && unit < units.length - 1) {
            value /= 1024d;
            unit++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    @NonNull
    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes > 0L) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    @NonNull
    private static String clean(@NonNull String value) {
        String trimmed = value.trim();
        if ("null".equals(trimmed)) return "";
        return trimmed;
    }
}
