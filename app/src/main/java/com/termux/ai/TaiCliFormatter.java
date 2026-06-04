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
                case "chat":
                    return formatChat(data);
                case "plan":
                case "build":
                    return formatPlan(data, "build".equals(command) ? "TAI build plan" : "TAI plan");
                case "notifications":
                    return formatNotifications(data);
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
            appendValue(out, "State", runtime.optString("status", ""));
        }

        JSONObject settings = data.optJSONObject("settings");
        if (settings != null) {
            JSONObject roles = settings.optJSONObject("roles");
            if (roles != null) {
                out.append("\nRoles\n");
                appendValue(out, "Default assistant", roles.optString(TaiModelRegistry.ROLE_DEFAULT_ASSISTANT, ""));
                appendValue(out, "Coding/build", roles.optString(TaiModelRegistry.ROLE_CODING_BUILD, ""));
                appendValue(out, "Mobile actions", roles.optString(TaiModelRegistry.ROLE_MOBILE_ACTIONS, ""));
            }
            out.append("\nSettings\n");
            appendValue(out, "Unattended mode", settings.optBoolean("unattendedMode", false) ? "on" : "off");
            appendValue(out, "Idle unload", settings.optInt("idleUnloadMinutes", 0) + " min");
            appendValue(out, "Hugging Face token", settings.optBoolean("huggingFaceTokenConfigured", false) ? "configured" : "not configured");
            JSONObject options = settings.optJSONObject("runtimeOptions");
            if (options != null) {
                appendValue(out, "Accelerator", nullable(options, "accelerator", "Auto / model default"));
                appendValue(out, "Max tokens", nullable(options, "maxTokens", "Auto / model default"));
                appendValue(out, "Temperature", nullable(options, "temperature", "Auto / model default"));
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
    private static String formatModels(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI models\n");
        appendValue(out, "Storage", data.optString("storageDirectory", ""));
        appendValue(out, "Bundled model files", data.optBoolean("bundledModelFiles", false) ? "yes" : "no");

        JSONObject roles = data.optJSONObject("roles");
        if (roles != null) {
            out.append("\nRoles\n");
            appendValue(out, "Default assistant", roles.optString(TaiModelRegistry.ROLE_DEFAULT_ASSISTANT, ""));
            appendValue(out, "Coding/build", roles.optString(TaiModelRegistry.ROLE_CODING_BUILD, ""));
            appendValue(out, "Mobile actions", roles.optString(TaiModelRegistry.ROLE_MOBILE_ACTIONS, ""));
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
    private static String formatChat(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI");
        String profile = data.optString("profile", "");
        if (!profile.isEmpty()) out.append(" [").append(profile).append("]");
        out.append('\n');
        appendValue(out, "Model", data.optString("model", ""));
        appendValue(out, "Backend", data.optString("backend", ""));
        appendValue(out, "Elapsed", formatDuration(data.optLong("elapsedMs", 0L)));
        appendValue(out, "Fallback", nullable(data, "backendFallbackReason", ""));
        out.append('\n');
        String response = data.optString("response", "");
        out.append(response.isEmpty() ? "(empty response)" : response);
        if (!response.endsWith("\n")) out.append('\n');
        return out.toString();
    }

    @NonNull
    private static String formatPlan(@NonNull JSONObject data, @NonNull String title) {
        StringBuilder out = new StringBuilder();
        out.append(title).append('\n');
        appendValue(out, "Task", data.optString("task", data.optString("workingDirectory", "")));
        appendValue(out, "Summary", data.optString("summary", ""));
        appendValue(out, "Mode", data.optString("mode", ""));
        appendValue(out, "Auto execute", data.optBoolean("autoExecute", false) ? "yes" : "no");

        JSONArray systems = data.optJSONArray("detectedBuildSystems");
        if (systems != null && systems.length() > 0) {
            appendValue(out, "Detected", join(systems));
        }

        JSONObject safety = data.optJSONObject("safety");
        if (safety != null) {
            appendValue(out, "Safety", safety.optString("level", "") + " - " + safety.optString("note", ""));
        }

        JSONArray commands = data.optJSONArray("commands");
        if (commands != null && commands.length() > 0) {
            out.append("\nCommands\n");
            appendCommands(out, commands);
        }

        appendValue(out, "Memory", data.optString("memoryPressureMode", ""));
        appendValue(out, "TODO", data.optString("todo", ""));
        return out.toString();
    }

    @NonNull
    private static String formatNotifications(@NonNull JSONObject data) {
        StringBuilder out = new StringBuilder();
        out.append("TAI notifications\n");
        appendValue(out, "Range", data.optString("range", ""));
        appendValue(out, "Available", data.optBoolean("available", false) ? "yes" : "no");
        appendValue(out, "Count", data.has("notificationCount") ? String.valueOf(data.optInt("notificationCount")) : "");
        appendValue(out, "Summary", data.optString("summary", ""));
        appendValue(out, "Hint", data.optString("hint", ""));
        appendValue(out, "Privacy", data.optString("privacy", ""));
        appendValue(out, "TODO", data.optString("todo", ""));
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

    private static void appendTransfer(@NonNull StringBuilder out, @NonNull JSONObject transfer) {
        appendValue(out, "Model", transfer.optString("modelId", ""));
        appendValue(out, "Status", transfer.optString("status", ""));
        appendValue(out, "Progress", progress(transfer.optLong("bytesRead", 0L), transfer.optLong("totalBytes", 0L)));
        appendValue(out, "Path", transfer.optString("path", ""));
        appendValue(out, "Error", transfer.optString("error", ""));
    }

    private static void appendCommands(@NonNull StringBuilder out, @NonNull JSONArray commands) {
        for (int i = 0; i < commands.length(); i++) {
            JSONObject command = commands.optJSONObject(i);
            if (command == null) continue;
            out.append("  ").append(i + 1).append(". ").append(command.optString("title", "Command")).append('\n');
            out.append("     ").append(command.optString("command", "")).append('\n');
            appendValue(out, "     Confirmation", command.optBoolean("confirmationRequired", false) ? "required" : "not required");
            appendValue(out, "     Destructive", command.optBoolean("destructive", false) ? "yes" : "no");
        }
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
    private static String formatDuration(long elapsedMs) {
        if (elapsedMs <= 0L) return "";
        if (elapsedMs < 1000L) return elapsedMs + " ms";
        return String.format(Locale.US, "%.2f s", elapsedMs / 1000d);
    }

    @NonNull
    private static String clean(@NonNull String value) {
        String trimmed = value.trim();
        if ("null".equals(trimmed)) return "";
        return trimmed;
    }
}
