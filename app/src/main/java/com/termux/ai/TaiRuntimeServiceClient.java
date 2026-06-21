package com.termux.ai;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class TaiRuntimeServiceClient {
    private static final long CONNECT_TIMEOUT_MS = 5_000L;
    private static final long REQUEST_TIMEOUT_MS = 120_000L;
    private static final int INLINE_BODY_LIMIT_BYTES = 512 * 1024;

    private final Context appContext;
    private final Object connectionLock = new Object();
    private final Messenger incoming = new Messenger(new IncomingHandler());
    private final Map<String, PendingRequest> pending = new ConcurrentHashMap<>();
    @Nullable private Messenger service;
    @Nullable private CountDownLatch connectingLatch;
    private boolean binding;

    public TaiRuntimeServiceClient(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    public JSONObject request(@NonNull String operation, @Nullable String body) throws JSONException {
        return request(operation, body, REQUEST_TIMEOUT_MS);
    }

    @NonNull
    public JSONObject request(@NonNull String operation, @Nullable String body, long timeoutMs) throws JSONException {
        PendingRequest request = send(operation, body, false, null);
        try {
            if (!request.done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                pending.remove(request.requestId);
                return runtimeUnavailable("tai_runtime_timeout", "TAI runtime service timed out.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(request.requestId);
            return runtimeUnavailable("tai_runtime_interrupted", "TAI runtime request interrupted.");
        }
        if (request.result == null) return runtimeUnavailable("tai_runtime_unavailable", "TAI runtime service did not return a result.");
        return request.result;
    }

    public void stream(@NonNull String operation, @Nullable String body, @NonNull TaiManager.OpenAiStreamSink sink)
        throws JSONException, IOException {
        PendingRequest request = send(operation, body, true, sink);
        try {
            request.done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(request.requestId);
            emitRuntimeError(sink, runtimeUnavailable("tai_runtime_interrupted", "TAI runtime stream interrupted."));
        }
        if (request.result != null && !request.result.optBoolean("ok", true)) {
            emitRuntimeError(sink, request.result);
        }
    }

    @NonNull
    private PendingRequest send(
        @NonNull String operation,
        @Nullable String body,
        boolean stream,
        @Nullable TaiManager.OpenAiStreamSink sink
    ) throws JSONException {
        Messenger target = ensureConnected();
        if (target == null) {
            PendingRequest failed = new PendingRequest(UUID.randomUUID().toString(), stream, sink);
            failed.result = runtimeUnavailable("tai_runtime_unavailable", "TAI runtime service is not connected.");
            failed.done.countDown();
            return failed;
        }

        String requestId = UUID.randomUUID().toString();
        PendingRequest pendingRequest = new PendingRequest(requestId, stream, sink);
        pending.put(requestId, pendingRequest);
        TransportBody transportBody;
        try {
            transportBody = transportBody(requestId, body == null ? "" : body);
        } catch (IOException e) {
            pending.remove(requestId);
            pendingRequest.result = runtimeUnavailable("tai_runtime_transport_failed", e.getMessage());
            pendingRequest.done.countDown();
            return pendingRequest;
        }

        Message message = Message.obtain(null, TaiRuntimeIpc.MSG_REQUEST);
        message.replyTo = incoming;
        Bundle data = new Bundle();
        data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, requestId);
        data.putString(TaiRuntimeIpc.KEY_OPERATION, operation);
        if (transportBody.inlineBody != null) data.putString(TaiRuntimeIpc.KEY_BODY, transportBody.inlineBody);
        if (transportBody.bodyFile != null) data.putString(TaiRuntimeIpc.KEY_BODY_FILE, transportBody.bodyFile);
        message.setData(data);
        try {
            target.send(message);
        } catch (RemoteException e) {
            pending.remove(requestId);
            pendingRequest.result = runtimeCrashed("tai_runtime_send_failed", "TAI runtime service disconnected while starting the request.");
            pendingRequest.done.countDown();
        }
        return pendingRequest;
    }

    @Nullable
    private Messenger ensureConnected() {
        synchronized (connectionLock) {
            if (service != null) return service;
            if (!binding) {
                binding = true;
                connectingLatch = new CountDownLatch(1);
                Intent intent = new Intent(appContext, TaiRuntimeService.class);
                boolean bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
                if (!bound) {
                    binding = false;
                    if (connectingLatch != null) connectingLatch.countDown();
                    return null;
                }
            }
        }

        CountDownLatch latch;
        synchronized (connectionLock) {
            latch = connectingLatch;
        }
        if (latch != null) {
            try {
                latch.await(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (connectionLock) {
            return service;
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (connectionLock) {
                service = new Messenger(binder);
                binding = false;
                if (connectingLatch != null) connectingLatch.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onRuntimeBinderDied();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            onRuntimeBinderDied();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            onRuntimeBinderDied();
        }
    };

    private void onRuntimeBinderDied() {
        synchronized (connectionLock) {
            service = null;
            binding = false;
            if (connectingLatch != null) connectingLatch.countDown();
        }
        JSONObject error = runtimeCrashed("tai_runtime_crashed",
            "AI runtime crashed while loading or running a model. Try CPU or a smaller model.");
        for (PendingRequest request : pending.values()) {
            request.result = error;
            if (request.stream && request.sink != null) {
                try {
                    emitRuntimeError(request.sink, error);
                } catch (IOException | JSONException ignored) {
                }
            }
            request.done.countDown();
        }
        pending.clear();
    }

    private final class IncomingHandler extends Handler {
        IncomingHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message message) {
            Bundle data = message.getData();
            String requestId = data.getString(TaiRuntimeIpc.KEY_REQUEST_ID, "");
            PendingRequest request = pending.get(requestId);
            if (request == null) {
                super.handleMessage(message);
                return;
            }
            try {
                if (message.what == TaiRuntimeIpc.MSG_RESPONSE) {
                    String result = data.getString(TaiRuntimeIpc.KEY_RESULT, "{}");
                    request.result = new JSONObject(result);
                    pending.remove(requestId);
                    request.done.countDown();
                    return;
                }
                if (message.what == TaiRuntimeIpc.MSG_STREAM_EVENT) {
                    String event = data.getString(TaiRuntimeIpc.KEY_EVENT, "{}");
                    if (request.sink != null) request.sink.onEvent(new JSONObject(event));
                    return;
                }
                if (message.what == TaiRuntimeIpc.MSG_STREAM_DONE) {
                    if (request.sink != null) request.sink.onDone();
                    pending.remove(requestId);
                    request.done.countDown();
                    return;
                }
            } catch (Exception e) {
                request.result = runtimeUnavailable("tai_runtime_stream_failed",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                pending.remove(requestId);
                request.done.countDown();
                return;
            }
            super.handleMessage(message);
        }
    }

    @NonNull
    private TransportBody transportBody(@NonNull String requestId, @NonNull String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= INLINE_BODY_LIMIT_BYTES) return new TransportBody(body, null);
        File dir = new File(appContext.getCacheDir(), "tai-ipc");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create TAI IPC cache directory.");
        }
        File file = new File(dir, requestId + ".json");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
        return new TransportBody(null, file.getAbsolutePath());
    }

    private void emitRuntimeError(@NonNull TaiManager.OpenAiStreamSink sink, @NonNull JSONObject source)
        throws JSONException, IOException {
        JSONObject error = new JSONObject();
        error.put("message", source.optString("message", "TAI runtime failed"));
        error.put("type", "invalid_request_error");
        error.put("code", source.optString("error", "tai_runtime_error"));
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("tai", source);
        sink.onEvent(response);
        sink.onDone();
    }

    @NonNull
    private JSONObject runtimeUnavailable(@NonNull String code, @Nullable String message) {
        JSONObject error = new JSONObject();
        try {
            error.put("ok", false);
            error.put("error", code);
            error.put("message", message == null || message.trim().isEmpty()
                ? "TAI runtime service is unavailable." : message);
            error.put("runtimeProcess", TaiRuntimeIpc.RUNTIME_PROCESS_SUFFIX);
            error.put("_statusCode", 503);
        } catch (JSONException ignored) {
        }
        return error;
    }

    @NonNull
    private JSONObject runtimeCrashed(@NonNull String code, @NonNull String message) {
        JSONObject error = runtimeUnavailable(code, message);
        try {
            JSONObject marker = TaiRuntimeCrashMarker.read(appContext);
            if (marker != null) error.put("lastRuntimeCrash", marker);
        } catch (JSONException ignored) {
        }
        return error;
    }

    private static final class TransportBody {
        @Nullable final String inlineBody;
        @Nullable final String bodyFile;

        TransportBody(@Nullable String inlineBody, @Nullable String bodyFile) {
            this.inlineBody = inlineBody;
            this.bodyFile = bodyFile;
        }
    }

    private static final class PendingRequest {
        final String requestId;
        final boolean stream;
        @Nullable final TaiManager.OpenAiStreamSink sink;
        final CountDownLatch done = new CountDownLatch(1);
        @Nullable JSONObject result;

        PendingRequest(@NonNull String requestId, boolean stream, @Nullable TaiManager.OpenAiStreamSink sink) {
            this.requestId = requestId;
            this.stream = stream;
            this.sink = sink;
        }
    }
}
