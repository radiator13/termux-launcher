package com.termux.ai;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.ExperimentalFlags;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.SamplerConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Collections;

public final class LiteRtTaiRuntime implements TaiRuntime {
    private static final String AUTO_CPU_REASON =
        "Auto selected CPU. Use --gpu or choose GPU in AI settings to request LiteRT-LM GPU acceleration.";

    private final Context appContext;
    private Engine engine;
    private String loadedModelId;
    private String loadedModelPath;
    private String backendName = "none";
    private String backendFallbackReason = "";
    private String statusMessage = "LiteRT-LM runtime is idle.";

    public LiteRtTaiRuntime(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public synchronized TaiRuntimeState getState() {
        String status = statusMessage + " Backend: " + backendName;
        if (!backendFallbackReason.isEmpty()) status += ". " + backendFallbackReason;
        return new TaiRuntimeState(engine != null, loadedModelId, "litert-lm", status);
    }

    @NonNull
    @Override
    public synchronized JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        if (!isSupportedAbi()) {
            return error(501, "litert_lm_unsupported_abi", "LiteRT-LM 0.12.0 ships native libraries for arm64-v8a and x86_64 only.");
        }
        if (modelSpec.localPath == null || modelSpec.localPath.trim().isEmpty()) {
            return error(404, "model_file_missing", "Download or import this model before loading it.");
        }
        File modelFile = new File(modelSpec.localPath);
        if (!modelFile.isFile() || !modelFile.canRead()) {
            JSONObject error = error(404, "model_file_not_readable", "Model file is missing or not readable by the app process.");
            error.put("path", modelSpec.localPath);
            return error;
        }

        closeEngine();
        applyExperimentalFlags(options);
        try {
            backendFallbackReason = isAutoAccelerator(options) ? AUTO_CPU_REASON : "";
            engine = createAndInitializeEngine(modelFile.getAbsolutePath(), options, preferredBackend(options));
        } catch (Exception e) {
            statusMessage = "LiteRT-LM load failed: " + e.getMessage();
            return error(500, "litert_lm_load_failed", statusMessage);
        }

        loadedModelId = modelSpec.id;
        loadedModelPath = modelFile.getAbsolutePath();
        statusMessage = "Model loaded.";
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("loadedModelId", loadedModelId);
        data.put("runtime", "litert-lm");
        data.put("backend", backendName);
        data.put("backendFallbackReason", backendFallbackReason.isEmpty() ? JSONObject.NULL : backendFallbackReason);
        data.put("modelPath", loadedModelPath);
        data.put("options", options.toJson());
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject unload() throws JSONException {
        String previous = loadedModelId;
        closeEngine();
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("unloadedModelId", previous == null ? JSONObject.NULL : previous);
        data.put("runtime", "litert-lm");
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject chat(
        @NonNull String modelId,
        @NonNull String systemPrompt,
        @NonNull String userPrompt,
        @NonNull TaiRuntimeOptions options
    ) throws JSONException {
        if (engine == null || loadedModelId == null || !loadedModelId.equals(modelId)) {
            return error(409, "model_not_loaded", "Load the downloaded model first with tai load " + modelId + " or from the TAI settings UI.");
        }

        long startedAt = System.currentTimeMillis();
        try {
            Contents systemContents = contents(systemPrompt);
            ConversationConfig conversationConfig = conversationConfig(systemContents, options);
            String responseText;
            try (Conversation conversation = engine.createConversation(conversationConfig)) {
                Message response = conversation.sendMessage(userPrompt, Collections.emptyMap());
                responseText = textFromMessage(response);
            }
            JSONObject data = new JSONObject();
            data.put("ok", true);
            data.put("model", modelId);
            data.put("runtime", "litert-lm");
            data.put("backend", backendName);
            data.put("backendFallbackReason", backendFallbackReason.isEmpty() ? JSONObject.NULL : backendFallbackReason);
            data.put("loaded", true);
            data.put("response", responseText);
            data.put("elapsedMs", System.currentTimeMillis() - startedAt);
            data.put("options", options.toJson());
            return data;
        } catch (Exception e) {
            return error(500, "litert_lm_chat_failed", "LiteRT-LM chat failed: " + e.getMessage());
        }
    }

    private Engine createAndInitializeEngine(@NonNull String modelPath, @NonNull TaiRuntimeOptions options, @NonNull Backend backend) {
        backendName = backend.getName();
        EngineConfig config = new EngineConfig(
            modelPath,
            backend,
            null,
            null,
            options.maxTokens,
            null,
            new File(appContext.getCacheDir(), "tai-litertlm").getAbsolutePath()
        );
        Engine loadedEngine = new Engine(config);
        loadedEngine.initialize();
        return loadedEngine;
    }

    @NonNull
    private Backend preferredBackend(@NonNull TaiRuntimeOptions options) {
        if (isGpuAccelerator(options)) return new Backend.GPU();
        return new Backend.CPU(null);
    }

    private boolean isAutoAccelerator(@NonNull TaiRuntimeOptions options) {
        return options.accelerator == null || "auto".equalsIgnoreCase(String.valueOf(options.accelerator));
    }

    private boolean isGpuAccelerator(@NonNull TaiRuntimeOptions options) {
        return "gpu".equalsIgnoreCase(options.accelerator);
    }

    private void applyExperimentalFlags(@NonNull TaiRuntimeOptions options) {
        ExperimentalFlags.INSTANCE.setEnableSpeculativeDecoding(options.speculativeDecodingEnabled);
    }

    @NonNull
    private ConversationConfig conversationConfig(@NonNull Contents systemPrompt, @NonNull TaiRuntimeOptions options) {
        SamplerConfig samplerConfig = null;
        if (options.topK != null && options.topP != null && options.temperature != null) {
            samplerConfig = new SamplerConfig(options.topK, options.topP, options.temperature, 0);
        }
        if (samplerConfig != null) {
            return new ConversationConfig(systemPrompt, Collections.emptyList(), Collections.emptyList(), samplerConfig);
        }
        return new ConversationConfig(systemPrompt);
    }

    @NonNull
    private Contents contents(@NonNull String text) {
        return Contents.Companion.of(text);
    }

    @NonNull
    private String textFromMessage(@Nullable Message message) {
        if (message == null || message.getContents() == null) return "";
        StringBuilder builder = new StringBuilder();
        for (Content content : message.getContents().getContents()) {
            if (content instanceof Content.Text) {
                builder.append(((Content.Text) content).getText());
            } else if (content != null) {
                builder.append(content.toString());
            }
        }
        return builder.toString();
    }

    private boolean isSupportedAbi() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi) || "x86_64".equals(abi)) return true;
        }
        return false;
    }

    private void closeEngine() {
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception ignored) {
            }
        }
        engine = null;
        loadedModelId = null;
        loadedModelPath = null;
        backendName = "none";
        backendFallbackReason = "";
        statusMessage = "LiteRT-LM runtime is idle.";
    }

    @NonNull
    private JSONObject error(int statusCode, String code, String message) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", false);
        data.put("error", code);
        data.put("message", message);
        data.put("runtime", "litert-lm");
        data.put("_statusCode", statusCode);
        return data;
    }
}
