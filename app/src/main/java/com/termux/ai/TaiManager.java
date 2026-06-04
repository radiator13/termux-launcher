package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.launcherctl.LauncherCtlNotificationListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.LinkedHashSet;

public final class TaiManager {
    private static TaiManager instance;

    private final Context appContext;
    private final TaiSettings settings;
    private final TaiModelRegistry registry;
    private final TaiModelStore modelStore;
    private final TaiModelDownloader modelDownloader;
    private final TaiRuntime runtime;
    private final TaiShellPlanner shellPlanner;
    private final TaiNotificationSummarizer notificationSummarizer;
    private final TaiActionRouter actionRouter;
    private final TaiBuildAgent buildAgent;

    private TaiManager(@NonNull Context context) {
        appContext = context.getApplicationContext();
        settings = new TaiSettings(appContext);
        registry = new TaiModelRegistry();
        modelStore = new TaiModelStore(appContext);
        modelDownloader = new TaiModelDownloader(modelStore);
        runtime = new StubTaiRuntime();
        shellPlanner = new TaiShellPlanner();
        notificationSummarizer = new TaiNotificationSummarizer();
        actionRouter = new TaiActionRouter();
        buildAgent = new TaiBuildAgent();
    }

    @NonNull
    public static synchronized TaiManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new TaiManager(context);
        }
        return instance;
    }

    @NonNull
    public JSONObject status() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("name", "TAI");
        data.put("displayName", "Termux AI");
        data.put("runtime", runtime.getState().toJson());
        data.put("settings", settings.toJson());
        data.put("promptProfiles", TaiPromptProfile.builtInsJson());
        data.put("appProcessRuntime", true);
        data.put("modelsBundledInApk", false);
        data.put("downloadsRequireExplicitUserAction", true);
        data.put("limitations", currentLimitations());
        return data;
    }

    @NonNull
    public JSONObject models() throws JSONException {
        JSONObject data = registry.toJson(settings, modelStore.getUserModels());
        data.put("storageDirectory", modelStore.getModelsDirectory().getAbsolutePath());
        data.put("downloads", modelStore.getDownloads());
        return data;
    }

    @NonNull
    public JSONObject importModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String path = request.optString("path", "").trim();
        if (path.isEmpty()) return error(400, "bad_request", "Missing model path");
        File modelFile = new File(path);
        if (!modelFile.isFile() || !modelFile.canRead()) {
            JSONObject error = error(404, "model_file_not_readable", "Model file does not exist or is not readable by the app process");
            error.put("path", path);
            return error;
        }

        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", modelFile.getName())));
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        TaiModelSpec spec = new TaiModelSpec(
            modelId,
            request.optString("displayName", modelId),
            request.optString("roleHint", "Imported local model"),
            "imported",
            modelFile.getAbsolutePath(),
            request.optString("license", "User-provided model; license accepted externally"),
            modelFile.length(),
            capabilitiesFromRequest(request),
            false
        );
        modelStore.upsertUserModel(spec);

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("imported", true);
        data.put("model", spec.toJson());
        data.put("requiresUserApprovedPath", true);
        data.put("copiedIntoAppPrivateStorage", false);
        data.put("message", "Model path registered. The runtime is still stubbed until LiteRT-LM integration is added.");
        return data;
    }

    @NonNull
    public JSONObject downloadModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        boolean acceptedTerms = request.optBoolean("acceptedTerms", false);
        if (!acceptedTerms) {
            JSONObject error = error(403, "terms_not_accepted", "Model downloads require acceptedTerms=true after reviewing provider license/terms");
            error.put("downloadsRequireExplicitUserAction", true);
            error.put("huggingFaceTokenBundled", false);
            return error;
        }
        String modelId = sanitizeModelId(request.optString("modelId", request.optString("model", "")));
        String url = request.optString("url", "").trim();
        if (modelId.isEmpty()) return error(400, "bad_request", "Missing model id");
        if (url.isEmpty()) return error(400, "bad_request", "Missing download URL");
        JSONObject data = modelDownloader.startDownload(
            modelId,
            url,
            request.optString("displayName", modelId),
            request.optString("license", "User accepted provider terms externally"),
            capabilitiesFromRequest(request)
        );
        data.put("downloadsRequireExplicitUserAction", true);
        data.put("huggingFaceTokenBundled", false);
        return data;
    }

    @NonNull
    public JSONObject downloads() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("downloads", modelStore.getDownloads());
        return data;
    }

    @NonNull
    public JSONObject loadModel(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String modelId = request.optString("model", request.optString("modelId", settings.getDefaultAssistantModel()));
        TaiModelSpec spec = registry.getModel(modelId);
        if (spec == null) spec = modelStore.getUserModel(modelId);
        if (spec == null) return error(404, "model_not_found", "Unknown TAI model: " + modelId);
        return runtime.load(spec, settings.getRuntimeOptions());
    }

    @NonNull
    public JSONObject unloadModel() throws JSONException {
        return runtime.unload();
    }

    @NonNull
    public JSONObject chat(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String prompt = request.optString("message", request.optString("prompt", ""));
        if (prompt.trim().isEmpty()) return error(400, "bad_request", "Missing message");
        String modelId = request.optString("model", settings.getDefaultAssistantModel());
        String systemPrompt = request.optString("systemPrompt", settings.getGeneralSystemPrompt());
        return runtime.chat(modelId, systemPrompt, prompt, settings.getRuntimeOptions());
    }

    @NonNull
    public JSONObject terminalPlan(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String task = request.optString("task", request.optString("prompt", ""));
        if (task.trim().isEmpty()) return error(400, "bad_request", "Missing terminal task");
        return shellPlanner.plan(task, settings.isUnattendedModeEnabled());
    }

    @NonNull
    public JSONObject terminalExecute(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String command = request.optString("command", "");
        JSONObject data = notImplemented("terminal_execute_requires_confirmation",
            "TAI does not execute shell commands in this foundation build. Review the plan and run commands yourself.");
        data.put("command", command);
        data.put("destructive", TaiSafetyPolicy.isDestructiveCommand(command));
        data.put("confirmationRequired", TaiSafetyPolicy.requiresConfirmation(command));
        return data;
    }

    @NonNull
    public JSONObject summarizeNotifications(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String range = request.optString("range", "today");
        JSONObject snapshot = LauncherCtlNotificationListener.getNotificationsSnapshot();
        return notificationSummarizer.summarize(snapshot, range);
    }

    @NonNull
    public JSONObject routeAction(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String prompt = request.optString("request", request.optString("prompt", ""));
        if (prompt.trim().isEmpty()) return error(400, "bad_request", "Missing action request");
        return actionRouter.route(prompt);
    }

    @NonNull
    public JSONObject executeAction(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        JSONObject data = notImplemented("action_execute_stub",
            "Device action execution is not enabled in this foundation build.");
        data.put("request", request);
        data.put("confirmationRequired", true);
        return data;
    }

    @NonNull
    public JSONObject buildPlan(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        String cwd = request.optString("cwd", request.optString("workingDirectory", ""));
        if (cwd.trim().isEmpty()) return error(400, "bad_request", "Missing working directory");
        return buildAgent.plan(cwd, request.optString("mode", "print_command"));
    }

    @NonNull
    public JSONObject buildRun(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        JSONObject data = notImplemented("build_run_stub",
            "Monitored build execution is TODO. Use build/plan and run the printed command after review.");
        data.put("request", request);
        data.put("autoInstallDependencies", false);
        return data;
    }

    @NonNull
    public JSONObject promptLabRun(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        JSONObject data = chat(body);
        data.put("promptLab", true);
        data.put("rawRequest", request);
        data.put("runtimeState", runtime.getState().toJson());
        return data;
    }

    @NonNull
    public JSONObject openAiChatCompletions(@NonNull String body) throws JSONException {
        JSONObject request = parseBody(body);
        JSONArray messages = request.optJSONArray("messages");
        if (messages == null || messages.length() == 0) return error(400, "bad_request", "Missing messages");

        StringBuilder prompt = new StringBuilder();
        String systemPrompt = settings.getGeneralSystemPrompt();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            String role = message.optString("role", "user");
            String content = message.optString("content", "");
            if ("system".equals(role)) {
                systemPrompt = content;
            } else {
                prompt.append(role).append(": ").append(content).append('\n');
            }
        }

        JSONObject chat = runtime.chat(request.optString("model", settings.getDefaultAssistantModel()),
            systemPrompt, prompt.toString().trim(), settings.getRuntimeOptions());
        JSONObject response = new JSONObject();
        response.put("id", "tai-stub");
        response.put("object", "chat.completion");
        response.put("model", chat.optString("model", settings.getDefaultAssistantModel()));
        JSONArray choices = new JSONArray();
        JSONObject choice = new JSONObject();
        choice.put("index", 0);
        choice.put("finish_reason", "stop");
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("content", chat.optString("response", ""));
        choice.put("message", message);
        choices.put(choice);
        response.put("choices", choices);
        response.put("tai", chat);
        return response;
    }

    @NonNull
    private JSONObject parseBody(@NonNull String body) throws JSONException {
        if (body.trim().isEmpty()) return new JSONObject();
        return new JSONObject(body);
    }

    @NonNull
    private JSONObject notImplemented(String code, String message) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", false);
        data.put("error", code);
        data.put("message", message);
        data.put("_statusCode", 501);
        return data;
    }

    @NonNull
    private JSONObject error(int statusCode, String code, String message) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", false);
        data.put("error", code);
        data.put("message", message);
        data.put("_statusCode", statusCode);
        return data;
    }

    @NonNull
    private JSONArray currentLimitations() {
        JSONArray limitations = new JSONArray();
        limitations.put("LiteRT-LM inference is not integrated yet.");
        limitations.put("Model import/download registry persistence exists, but runtime loading is still stubbed.");
        limitations.put("Image input, audio scribe, streaming, and monitored build execution are TODO.");
        limitations.put("TAI command execution is plan-only unless a future confirmed execution mode is added.");
        return limitations;
    }

    @NonNull
    private LinkedHashSet<String> capabilitiesFromRequest(@NonNull JSONObject request) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        JSONArray array = request.optJSONArray("capabilities");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String capability = array.optString(i, "");
                if (!capability.isEmpty()) capabilities.add(capability);
            }
        }
        if (capabilities.isEmpty()) capabilities.add("text_chat");
        return capabilities;
    }

    @NonNull
    private String sanitizeModelId(@NonNull String value) {
        return value.trim().replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
