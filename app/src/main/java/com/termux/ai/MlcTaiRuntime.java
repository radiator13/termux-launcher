package com.termux.ai;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * MLC runtime adapter backed by APK-bundled native libraries.
 *
 * <p>Implements the same load/keep-warm/unload/cancel state contract as
 * {@link LiteRtTaiRuntime} but does <b>not</b> load native code from custom
 * URLs or downloaded packages. All executable artifacts must be listed in
 * {@link MlcBundledLibraryRegistry} and present in the app's JNI libs dir.
 *
 * <p>Actual native initialization is currently stubbed because no published
 * MLC Android AAR is available. When an AAR is integrated, the stubbed
 * sections in {@link #load(TaiModelSpec, TaiRuntimeOptions)} can be replaced
 * with real JNI calls while the state machine stays unchanged.
 */
public final class MlcTaiRuntime implements TaiRuntime {
    private static final int DEFAULT_KEEP_WARM_MINUTES = 30;

    private final Context appContext;
    @Nullable private File testOverrideNativeLibraryDir;
    @Nullable private String[] testSupportedAbis;

    private String runtimeState = "unloaded";
    private String statusMessage = "MLC runtime is unloaded.";
    @Nullable private String loadedModelId;
    @Nullable private String loadedModelLibraryId;
    private String backendName = "none";
    private boolean generating;
    @Nullable private String activeGenerationId;
    private long activeGenerationStartedAtMs;
    private long loadedAtMs;
    private long lastUsedAtMs;
    private long keepWarmUntilMs;
    private boolean loading;
    private boolean loadCancellationRequested;
    @Nullable private String loadingModelId;

    public MlcTaiRuntime(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * Package-private constructor for tests that need to control the native
     * library directory and/or the set of supported ABIs.
     */
    MlcTaiRuntime(@NonNull Context context, @Nullable File overrideNativeLibraryDir) {
        this.appContext = context.getApplicationContext();
        this.testOverrideNativeLibraryDir = overrideNativeLibraryDir;
    }

    /** Package-private for unit tests. */
    void setSupportedAbisForTest(@Nullable String[] abis) {
        this.testSupportedAbis = abis;
    }

    @NonNull
    @Override
    public synchronized TaiRuntimeState getState() {
        maybeRefreshStateLocked();
        return new TaiRuntimeState(
            "loaded".equals(runtimeState) || "idle-warm".equals(runtimeState) || "generating".equals(runtimeState),
            loadedModelId,
            TaiModelSpec.BACKEND_MLC_LLM,
            runtimeState,
            statusMessage,
            backendName,
            null,
            null,
            generating,
            activeGenerationId,
            activeGenerationStartedAtMs,
            keepWarmUntilMs,
            0L,
            loadedAtMs,
            lastUsedAtMs
        );
    }

    @Override
    public synchronized boolean isModelLoaded(@NonNull String modelId) {
        return loadedModelId != null && loadedModelId.equals(modelId)
            && ("loaded".equals(runtimeState) || "idle-warm".equals(runtimeState));
    }

    @NonNull
    @Override
    public JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        synchronized (this) {
            if (generating) {
                return error(409, "generation_active",
                    "Cancel the active generation before loading another MLC model.");
            }
            if (loading) {
                return error(409, "load_in_progress", "An MLC model is already loading.");
            }
        }

        // 1. Device ABI check.
        String deviceAbi = findSupportedAbi();
        if (deviceAbi == null) {
            return error(501, "mlc_runtime_unavailable",
                "MLC runtime is not available for this device ABI.");
        }

        // 2. Registry lookup.
        MlcBundledLibraryRegistry.Entry entry = MlcBundledLibraryRegistry.get(modelSpec.id);
        if (entry == null) {
            return error(501, "mlc_runtime_unavailable",
                "Model library ID is not in the bundled MLC registry.");
        }
        if (!entry.requiredAbi.equals(deviceAbi)) {
            return error(501, "mlc_runtime_unavailable",
                "Model requires ABI " + entry.requiredAbi + " but device supports " + deviceAbi + ".");
        }

        // 3. Trust-boundary validation (reject custom .so / raw weights / path traversal).
        TaiMlcPackageValidator.ValidationResult pathValidation =
            TaiMlcPackageValidator.validatePackagePath(modelSpec.id,
                modelSpec.localPath != null ? modelSpec.localPath : "");
        if (pathValidation != TaiMlcPackageValidator.ValidationResult.OK) {
            return error(501, "mlc_runtime_unavailable",
                TaiMlcPackageValidator.resultMessage(pathValidation));
        }

        // 4. Verify bundled native libraries exist in the JNI dir.
        File nativeLibDir = getNativeLibraryDir();
        if (nativeLibDir == null) {
            return error(501, "mlc_runtime_unavailable",
                "Native library directory is not available.");
        }
        for (String libName : entry.nativeLibraryNames) {
            File libFile = new File(nativeLibDir, libName);
            if (!libFile.isFile()) {
                return error(501, "mlc_runtime_unavailable",
                    "Bundled native library missing: " + libName);
            }
        }

        // 5. Begin loading state (native init is stubbed).
        synchronized (this) {
            loading = true;
            loadCancellationRequested = false;
            loadingModelId = modelSpec.id;
            runtimeState = "loading";
            statusMessage = "Loading " + modelSpec.id + ".";
        }

        // Stub: in production this would call MLC JNI init.
        // A tiny sleep gives cancellation tests a window to race.
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        synchronized (this) {
            if (loadCancellationRequested) {
                finishLoadingLocked();
                runtimeState = "unloaded";
                statusMessage = "Model load cancelled.";
                return error(499, "model_load_cancelled", statusMessage);
            }
            finishLoadingLocked();
            loadedModelId = modelSpec.id;
            loadedModelLibraryId = modelSpec.id;
            runtimeState = "loaded";
            statusMessage = "Model loaded.";
            loadedAtMs = System.currentTimeMillis();
            lastUsedAtMs = loadedAtMs;
            backendName = "mlc";
            keepWarmUntilMs = 0L;

            JSONObject data = stateEnvelopeLocked(true);
            data.put("loadedModelId", loadedModelId);
            data.put("backend", backendName);
            data.put("modelLibraryId", loadedModelLibraryId);
            return data;
        }
    }

    @NonNull
    @Override
    public synchronized JSONObject unload() throws JSONException {
        if (generating) {
            return error(409, "generation_active",
                "Cancel the active generation before unloading the MLC runtime.");
        }
        if (loading) {
            loadCancellationRequested = true;
            runtimeState = "stopping";
            statusMessage = "Cancelling model load. Native initialization will be discarded when it returns.";
            JSONObject data = stateEnvelopeLocked(true);
            data.put("unloadedModelId", JSONObject.NULL);
            data.put("loadingModelId", loadingModelId == null ? JSONObject.NULL : loadingModelId);
            data.put("loadCancellationRequested", true);
            data.put("message", statusMessage);
            return data;
        }
        String previous = loadedModelId;
        closeEngineLocked("MLC runtime is unloaded.", "unloaded");
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("unloadedModelId", previous == null ? JSONObject.NULL : previous);
        data.put("runtime", TaiModelSpec.BACKEND_MLC_LLM);
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        int keepWarmMinutes = minutes > 0 ? minutes : DEFAULT_KEEP_WARM_MINUTES;
        if (!loading && loadedModelId != null && loadedModelId.equals(modelSpec.id)) {
            keepWarmUntilMs = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(keepWarmMinutes);
            statusMessage = "Model is warm.";
            maybeRefreshStateLocked();
            JSONObject data = stateEnvelopeLocked(true);
            data.put("keepWarm", true);
            data.put("keepWarmMinutes", keepWarmMinutes);
            data.put("keepWarmUntilMs", keepWarmUntilMs);
            return data;
        }
        return load(modelSpec, options);
    }

    @NonNull
    @Override
    public synchronized JSONObject cancel() throws JSONException {
        if (loading) {
            loadCancellationRequested = true;
            runtimeState = "stopping";
            statusMessage = "Cancelling model load. Native initialization will be discarded when it returns.";
            JSONObject data = stateEnvelopeLocked(true);
            data.put("cancelled", true);
            data.put("loadCancellationRequested", true);
            data.put("message", "Model load cancellation requested.");
            return data;
        }
        if (!generating) {
            JSONObject data = stateEnvelopeLocked(true);
            data.put("cancelled", false);
            data.put("message", "No active generation.");
            return data;
        }
        runtimeState = "stopping";
        statusMessage = "Cancelling active generation.";
        generating = false;
        activeGenerationId = null;
        activeGenerationStartedAtMs = 0L;
        JSONObject data = stateEnvelopeLocked(true);
        data.put("cancelled", true);
        data.put("message", "Cancel requested.");
        return data;
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, "chat");
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "completion");
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, "completion");
    }

    @NonNull
    private JSONObject generate(@NonNull String modelId, @NonNull String operation) throws JSONException {
        synchronized (this) {
            if (loadedModelId == null || !loadedModelId.equals(modelId)) {
                return error(409, "model_not_loaded",
                    "Load the downloaded model first with tai load " + modelId + " or from the TAI settings UI.");
            }
        }
        return unsupported(modelId, operation);
    }

    @Nullable
    private String findSupportedAbi() {
        String[] abis = testSupportedAbis != null ? testSupportedAbis : Build.SUPPORTED_ABIS;
        if (abis == null) return null;
        for (String abi : abis) {
            if (MlcBundledLibraryRegistry.isAbiSupported(abi)) {
                return abi;
            }
        }
        return null;
    }

    @Nullable
    private File getNativeLibraryDir() {
        if (testOverrideNativeLibraryDir != null) {
            return testOverrideNativeLibraryDir;
        }
        String nativeLibDir = appContext.getApplicationInfo().nativeLibraryDir;
        if (nativeLibDir == null) return null;
        return new File(nativeLibDir);
    }

    private void maybeRefreshStateLocked() {
        if (loading) {
            runtimeState = loadCancellationRequested ? "stopping" : "loading";
            return;
        }
        if (loadedModelId == null) {
            if (!"failed".equals(runtimeState)) {
                runtimeState = "unloaded";
            }
            return;
        }
        if (generating) {
            runtimeState = "generating";
        } else if (keepWarmUntilMs > System.currentTimeMillis()) {
            runtimeState = "idle-warm";
        } else {
            runtimeState = "loaded";
        }
    }

    private void closeEngineLocked(@NonNull String nextStatus, @NonNull String nextState) {
        loadedModelId = null;
        loadedModelLibraryId = null;
        backendName = "none";
        runtimeState = nextState;
        statusMessage = nextStatus;
        generating = false;
        activeGenerationId = null;
        activeGenerationStartedAtMs = 0L;
        loadedAtMs = 0L;
        lastUsedAtMs = 0L;
        keepWarmUntilMs = 0L;
    }

    private void finishLoadingLocked() {
        loading = false;
        loadCancellationRequested = false;
        loadingModelId = null;
    }

    @NonNull
    private JSONObject stateEnvelopeLocked(boolean ok) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", ok);
        data.put("runtime", TaiModelSpec.BACKEND_MLC_LLM);
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    private JSONObject unsupported(@NonNull String modelId, @NonNull String operation) throws JSONException {
        JSONObject data = stateEnvelopeLocked(false);
        data.put("error", "unsupported_operation");
        data.put("message", "MLC " + operation + " is unavailable until the MLC native runtime is fully integrated.");
        data.put("modelId", modelId);
        data.put("_statusCode", 501);
        return data;
    }

    @NonNull
    private JSONObject error(int statusCode, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", false);
        data.put("error", code);
        data.put("message", message);
        data.put("runtime", TaiModelSpec.BACKEND_MLC_LLM);
        data.put("state", getState().toJson());
        data.put("_statusCode", statusCode);
        return data;
    }
}
