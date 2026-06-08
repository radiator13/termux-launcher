package com.termux.ai;

import androidx.annotation.NonNull;

import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class MlcTaiRuntime implements TaiRuntime {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private Object engine;
    private Method reloadMethod;
    private Method unloadMethod;
    private Method resetMethod;
    private Method chatCompletionMethod;
    private TaiModelSpec loadedModel;
    private String state = "unloaded";
    private String status = "MLC runtime is unloaded.";
    private boolean generating;
    private String generationId;
    private long generationStartedAtMs;
    private long loadedAtMs;
    private long lastUsedAtMs;
    private long keepWarmUntilMs;
    private long idleUnloadAtMs;
    private ScheduledFuture<?> idleFuture;
    private volatile StreamRequest activeRequest;

    public static boolean isPackaged() {
        try {
            Class.forName("ai.mlc.mlcllm.JSONFFIEngine");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NonNull @Override public synchronized TaiRuntimeState getState() {
        JSONObject extra = new JSONObject();
        try {
            extra.put("format", TaiModelSpec.FORMAT_MLC_PACKAGE);
            extra.put("gpuApi", "OpenCL");
            extra.put("nativeAvailable", isPackaged());
        } catch (JSONException ignored) {}
        return new TaiRuntimeState(engine != null && loadedModel != null,
            loadedModel == null ? null : loadedModel.id, TaiModelSpec.BACKEND_MLC, state, status,
            loadedModel == null ? "none" : "GPU (OpenCL)", null,
            loadedModel == null ? null : loadedModel.localPath, generating, generationId,
            generationStartedAtMs, keepWarmUntilMs, idleUnloadAtMs, loadedAtMs, lastUsedAtMs, extra);
    }

    @Override public synchronized boolean isModelLoaded(@NonNull String modelId) {
        return engine != null && loadedModel != null && modelId.equals(loadedModel.id);
    }

    @NonNull @Override public JSONObject load(@NonNull TaiModelSpec model, @NonNull TaiRuntimeOptions options) throws JSONException { return loadInternal(model, options, 0); }

    @NonNull @Override public synchronized JSONObject unload() throws JSONException {
        if (generating) return error(409, "generation_active", "Cancel active MLC generation before unloading.");
        String previous = loadedModel == null ? null : loadedModel.id;
        closeLocked("MLC runtime is unloaded.");
        JSONObject result = envelope(true);
        result.put("unloadedModelId", previous == null ? JSONObject.NULL : previous);
        return result;
    }

    @NonNull @Override public JSONObject keepWarm(@NonNull TaiModelSpec model, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException { return loadInternal(model, options, Math.max(1, minutes)); }

    @NonNull @Override public synchronized JSONObject cancel() throws JSONException {
        if (!generating || engine == null) {
            JSONObject result = envelope(true); result.put("cancelled", false); return result;
        }
        try {
            Field field = engine.getClass().getDeclaredField("abortFunc");
            field.setAccessible(true);
            Object function = field.get(engine);
            Method invoke = findVarargInvoke(function.getClass());
            Object emptyArgs = Array.newInstance(invoke.getParameterTypes()[0].getComponentType(), 0);
            invoke.invoke(function, emptyArgs);
            state = "stopping";
            status = "Cancelling MLC generation.";
            JSONObject result = envelope(true); result.put("cancelled", true); return result;
        } catch (Exception e) {
            return error(500, "mlc_cancel_failed", e.getMessage());
        }
    }

    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull String system, @NonNull String user, @NonNull TaiRuntimeOptions options) throws JSONException { return chat(id, TaiChatRequest.simple(system, user), options); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull String system, @NonNull String user, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return chat(id, TaiChatRequest.simple(system, user), options, callback); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException { return generate(id, messages(request), options, null); }
    @NonNull @Override public JSONObject chat(@NonNull String id, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return generate(id, messages(request), options, callback); }
    @NonNull @Override public JSONObject complete(@NonNull String id, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException { return generate(id, completionMessages(prompt), options, null); }
    @NonNull @Override public JSONObject complete(@NonNull String id, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException { return generate(id, completionMessages(prompt), options, callback); }

    private JSONObject loadInternal(TaiModelSpec model, TaiRuntimeOptions options, int warmMinutes) throws JSONException {
        synchronized (this) {
            if (!isPackaged()) return error(501, "mlc_unavailable", "MLC is available only in the arm64 AI build.");
            if (!TaiModelSpec.FORMAT_MLC_PACKAGE.equals(model.format)) return error(400, "unsupported_model_format", "MLC requires a compiled MLC model package.");
            if (model.localPath == null || !new File(model.localPath).isDirectory()) return error(404, "model_files_not_readable", "MLC model package is missing.");
            if (model.runtimeLibrary == null || model.runtimeLibrary.isEmpty()) return error(400, "mlc_library_missing", "Model metadata does not name its compiled MLC library.");
            if (generating) return error(409, "generation_active", "Cancel generation before loading another model.");
            if (isModelLoaded(model.id)) { applyWarmLocked(warmMinutes, options); return envelope(true); }
            closeLocked("Switching MLC model.");
            state = "loading"; status = "Loading MLC model with OpenCL.";
        }
        try {
            ensureEngine();
            JSONObject config = new JSONObject();
            config.put("model", model.localPath);
            config.put("model_lib", "system://" + model.runtimeLibrary);
            config.put("mode", "interactive");
            reloadMethod.invoke(engine, config.toString());
            synchronized (this) {
                loadedModel = model; state = warmMinutes > 0 ? "idle-warm" : "loaded";
                status = "MLC model loaded with OpenCL."; loadedAtMs = lastUsedAtMs = System.currentTimeMillis();
                applyWarmLocked(warmMinutes, options); return envelope(true);
            }
        } catch (Throwable e) {
            synchronized (this) { closeLocked("MLC load failed: " + rootMessage(e)); state = "failed"; }
            return error(500, "mlc_load_failed", status);
        }
    }

    private void ensureEngine() throws Exception {
        synchronized (this) { if (engine != null) return; }
        Class<?> engineClass = Class.forName("ai.mlc.mlcllm.JSONFFIEngine");
        Object candidate = engineClass.getConstructor().newInstance();
        Class<?> callbackClass = Class.forName("ai.mlc.mlcllm.JSONFFIEngine$KotlinFunction");
        Object callback = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass},
            (proxy, method, args) -> { if (args != null && args.length > 0) onStream(String.valueOf(args[0])); return null; });
        engineClass.getMethod("initBackgroundEngine", callbackClass).invoke(candidate, callback);
        Method runLoop = engineClass.getMethod("runBackgroundLoop");
        Method streamLoop = engineClass.getMethod("runBackgroundStreamBackLoop");
        Thread worker = new Thread(() -> invokeQuietly(runLoop, candidate), "tai-mlc-engine");
        Thread streamWorker = new Thread(() -> invokeQuietly(streamLoop, candidate), "tai-mlc-stream");
        worker.setDaemon(true); streamWorker.setDaemon(true); worker.start(); streamWorker.start();
        synchronized (this) {
            engine = candidate;
            reloadMethod = engineClass.getMethod("reload", String.class);
            unloadMethod = engineClass.getMethod("unload");
            resetMethod = engineClass.getMethod("reset");
            chatCompletionMethod = engineClass.getMethod("chatCompletion", String.class, String.class);
        }
    }

    private JSONObject generate(String id, JSONArray messages, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException {
        StreamRequest request;
        synchronized (this) {
            if (!isModelLoaded(id)) return error(409, "model_not_loaded", "Load the requested MLC model first.");
            if (generating) return error(409, "generation_active", "An MLC generation is already running.");
            generating = true; state = "generating"; generationId = UUID.randomUUID().toString();
            generationStartedAtMs = System.currentTimeMillis(); cancelIdleLocked();
            request = new StreamRequest(generationId, callback); activeRequest = request;
        }
        JSONObject body = new JSONObject();
        body.put("messages", messages); body.put("model", id); body.put("stream", true);
        body.put("stream_options", new JSONObject().put("include_usage", true));
        if (options.maxTokens != null) body.put("max_tokens", options.maxTokens);
        if (options.temperature != null) body.put("temperature", options.temperature);
        if (options.topP != null) body.put("top_p", options.topP);
        try {
            resetMethod.invoke(engine);
            chatCompletionMethod.invoke(engine, body.toString(), request.id);
            if (!request.done.await(5, TimeUnit.MINUTES)) {
                cancel(); request.error = "MLC generation timed out.";
            }
        } catch (Throwable e) {
            request.error = rootMessage(e);
        }
        synchronized (this) {
            activeRequest = null; generating = false; generationId = null; generationStartedAtMs = 0L;
            lastUsedAtMs = System.currentTimeMillis(); scheduleIdleLocked(options.idleUnloadMinutes == null ? 10 : options.idleUnloadMinutes);
            state = keepWarmUntilMs > System.currentTimeMillis() ? "idle-warm" : "loaded";
            if (request.error != null) { status = request.error; return error(500, "mlc_generation_failed", request.error); }
            status = "MLC generation complete.";
            if (callback != null) callback.onComplete(request.text.toString());
            JSONObject result = envelope(true); result.put("model", id); result.put("response", request.text.toString());
            result.put("toolCalls", new JSONArray()); return result;
        }
    }

    private void onStream(String raw) {
        StreamRequest request = activeRequest;
        if (request == null) return;
        try {
            JSONArray responses = new JSONArray(raw);
            for (int i = 0; i < responses.length(); i++) {
                JSONObject response = responses.optJSONObject(i); if (response == null) continue;
                if (response.has("usage") && !response.isNull("usage")) { request.done.countDown(); continue; }
                JSONArray choices = response.optJSONArray("choices"); if (choices == null || choices.length() == 0) continue;
                JSONObject delta = choices.optJSONObject(0).optJSONObject("delta"); if (delta == null) continue;
                Object content = delta.opt("content"); String token = content instanceof String ? (String) content : "";
                if (!token.isEmpty()) { request.text.append(token); if (request.callback != null) request.callback.onToken(token); }
                if (!choices.optJSONObject(0).isNull("finish_reason")) request.done.countDown();
            }
        } catch (Exception e) { request.error = e.getMessage(); request.done.countDown(); }
    }

    private JSONArray messages(TaiChatRequest request) throws JSONException {
        JSONArray result = new JSONArray();
        if (!request.systemPrompt.isEmpty()) result.put(message("system", request.systemPrompt));
        for (Message message : request.initialMessages) result.put(message(role(message), text(message)));
        result.put(message(role(request.message), text(request.message))); return result;
    }

    private JSONArray completionMessages(String prompt) throws JSONException { return new JSONArray().put(message("user", prompt)); }
    private JSONObject message(String role, String content) throws JSONException { return new JSONObject().put("role", role).put("content", content); }
    private String role(Message message) { String role = message.getRole().toString().toLowerCase(); return "model".equals(role) ? "assistant" : role; }
    private String text(Message message) { StringBuilder value = new StringBuilder(); if (message.getContents() != null) for (Content content : message.getContents().getContents()) value.append(content instanceof Content.Text ? ((Content.Text) content).getText() : String.valueOf(content)); return value.toString(); }

    private synchronized void applyWarmLocked(int minutes, TaiRuntimeOptions options) { keepWarmUntilMs = minutes > 0 ? System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes) : 0L; scheduleIdleLocked(options.idleUnloadMinutes == null ? 10 : options.idleUnloadMinutes); }
    private synchronized void scheduleIdleLocked(int minutes) { cancelIdleLocked(); if (engine == null || loadedModel == null || minutes <= 0) return; idleUnloadAtMs = Math.max(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes), keepWarmUntilMs); idleFuture = scheduler.schedule(() -> { synchronized (MlcTaiRuntime.this) { if (!generating && System.currentTimeMillis() >= idleUnloadAtMs) closeLocked("MLC model unloaded after idle timeout."); } }, Math.max(1000, idleUnloadAtMs - System.currentTimeMillis()), TimeUnit.MILLISECONDS); }
    private synchronized void cancelIdleLocked() { if (idleFuture != null) idleFuture.cancel(false); idleFuture = null; idleUnloadAtMs = 0L; }
    private synchronized void closeLocked(String message) { cancelIdleLocked(); if (engine != null && unloadMethod != null) invokeQuietly(unloadMethod, engine); engine = null; loadedModel = null; generating = false; keepWarmUntilMs = 0L; state = "unloaded"; status = message; }

    private JSONObject envelope(boolean ok) throws JSONException { return new JSONObject().put("ok", ok).put("runtime", TaiModelSpec.BACKEND_MLC).put("state", getState().toJson()); }
    private JSONObject error(int code, String error, String message) throws JSONException { return envelope(false).put("error", error).put("message", message == null ? error : message).put("_statusCode", code); }
    private static void invokeQuietly(Method method, Object target) { try { method.invoke(target); } catch (Throwable ignored) {} }
    private static String rootMessage(Throwable error) { while (error.getCause() != null) error = error.getCause(); return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
    private static Method findVarargInvoke(Class<?> type) { for (Method method : type.getMethods()) if ("invoke".equals(method.getName()) && method.isVarArgs()) return method; throw new IllegalStateException("TVM Function.invoke unavailable"); }

    private static final class StreamRequest {
        final String id; final TaiGenerationCallback callback; final StringBuilder text = new StringBuilder(); final CountDownLatch done = new CountDownLatch(1); volatile String error;
        StreamRequest(String id, TaiGenerationCallback callback) { this.id = id; this.callback = callback; }
    }
}
