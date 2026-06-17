package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * MNN runtime adapter placeholder.
 *
 * <p>The model lifecycle is wired now so MNN models can be downloaded,
 * registered, selected, and routed. Native inference requires adding the
 * MNN Android LLM libraries and replacing the unsupported generation path
 * with JNI/API calls into the MNN session.
 */
public final class MnnTaiRuntime implements TaiRuntime {
    private String runtimeState = "unloaded";
    private String statusMessage = "MNN runtime is unloaded.";
    private String loadedModelId;
    private String loadedModelPath;

    public MnnTaiRuntime(@NonNull Context context) {
    }

    @NonNull
    @Override
    public synchronized TaiRuntimeState getState() {
        return new TaiRuntimeState(
            loadedModelId != null,
            loadedModelId,
            TaiModelSpec.BACKEND_MNN_LLM,
            runtimeState,
            statusMessage,
            "mnn",
            null,
            loadedModelPath,
            false,
            null,
            0L,
            0L,
            0L,
            0L,
            0L
        );
    }

    @Override
    public synchronized boolean isModelLoaded(@NonNull String modelId) {
        return loadedModelId != null && loadedModelId.equals(modelId);
    }

    @NonNull
    @Override
    public synchronized JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        if (!TaiModelSpec.BACKEND_MNN_LLM.equals(modelSpec.backend)) {
            return error(409, "backend_mismatch", "Model " + modelSpec.id + " is not an MNN model.");
        }
        if (modelSpec.localPath == null || modelSpec.localPath.trim().isEmpty()) {
            return error(404, "model_file_not_readable", "MNN model config path is missing.");
        }
        File config = new File(modelSpec.localPath);
        if (!config.isFile() || !"config.json".equals(config.getName())) {
            return error(404, "model_file_not_readable", "MNN models must point to a readable config.json file.");
        }
        File modelDir = config.getParentFile();
        if (modelDir == null || !new File(modelDir, "llm.mnn").isFile() || !new File(modelDir, "llm.mnn.weight").isFile()) {
            return error(404, "model_file_not_readable", "MNN model directory is incomplete.");
        }
        loadedModelId = modelSpec.id;
        loadedModelPath = config.getAbsolutePath();
        runtimeState = "loaded";
        statusMessage = "MNN model registered. Native MNN inference is not integrated yet.";
        JSONObject data = stateEnvelope(false);
        data.put("loadedModelId", loadedModelId);
        data.put("message", statusMessage);
        data.put("_statusCode", 501);
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject unload() throws JSONException {
        String previous = loadedModelId;
        loadedModelId = null;
        loadedModelPath = null;
        runtimeState = "unloaded";
        statusMessage = "MNN runtime is unloaded.";
        JSONObject data = stateEnvelope(true);
        data.put("unloadedModelId", previous == null ? JSONObject.NULL : previous);
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        return load(modelSpec, options);
    }

    @NonNull
    @Override
    public synchronized JSONObject cancel() throws JSONException {
        JSONObject data = stateEnvelope(true);
        data.put("cancelled", false);
        data.put("message", "No active MNN generation.");
        return data;
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return unsupported(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return unsupported(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException {
        return unsupported(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return unsupported(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return unsupported(modelId, "completion");
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return unsupported(modelId, "completion");
    }

    @NonNull
    private synchronized JSONObject unsupported(@NonNull String modelId, @NonNull String operation) throws JSONException {
        if (loadedModelId == null || !loadedModelId.equals(modelId)) {
            return error(409, "model_not_loaded", "Load the downloaded MNN model first with tai load " + modelId + " or from the TAI settings UI.");
        }
        JSONObject data = stateEnvelope(false);
        data.put("error", "unsupported_operation");
        data.put("message", "MNN " + operation + " is unavailable until the MNN native runtime is integrated.");
        data.put("modelId", modelId);
        data.put("_statusCode", 501);
        return data;
    }

    @NonNull
    private JSONObject stateEnvelope(boolean ok) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", ok);
        data.put("runtime", TaiModelSpec.BACKEND_MNN_LLM);
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    private JSONObject error(int statusCode, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject data = stateEnvelope(false);
        data.put("error", code);
        data.put("message", message);
        data.put("_statusCode", statusCode);
        return data;
    }
}
