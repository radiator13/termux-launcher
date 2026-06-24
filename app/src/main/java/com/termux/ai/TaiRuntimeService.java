package com.termux.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TaiRuntimeService extends Service {
    private static final String CHANNEL_ID = "termux_ai_runtime";
    private static final int NOTIFICATION_ID = 24110;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-runtime-service");
        thread.setDaemon(true);
        return thread;
    });
    private final Messenger messenger = new Messenger(new IncomingHandler());
    private volatile boolean foreground;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        ensureChannel();
        return messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private final class IncomingHandler extends Handler {
        IncomingHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message message) {
            if (message.what != TaiRuntimeIpc.MSG_REQUEST || message.replyTo == null) {
                super.handleMessage(message);
                return;
            }
            Bundle data = message.getData();
            String requestId = data.getString(TaiRuntimeIpc.KEY_REQUEST_ID, "");
            String operation = data.getString(TaiRuntimeIpc.KEY_OPERATION, "");
            String body = data.getString(TaiRuntimeIpc.KEY_BODY, null);
            String bodyFile = data.getString(TaiRuntimeIpc.KEY_BODY_FILE, null);
            Messenger replyTo = message.replyTo;
            if (TaiRuntimeIpc.OP_STATUS.equals(operation) || TaiRuntimeIpc.OP_RUNTIME_STATUS.equals(operation)) {
                runRequest(replyTo, requestId, operation, body, bodyFile);
                return;
            }
            executor.execute(() -> runRequest(replyTo, requestId, operation, body, bodyFile));
        }
    }

    private void runRequest(
        @NonNull Messenger replyTo,
        @NonNull String requestId,
        @NonNull String operation,
        @Nullable String body,
        @Nullable String bodyFile
    ) {
        try {
            String payload = body != null ? body : readBodyFile(bodyFile);
            if (isForegroundOperation(operation)) {
                ensureForeground("TAI runtime", "Preparing " + operation);
            }
            if (TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)
                || TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM.equals(operation)) {
                runStreamRequest(replyTo, requestId, operation, payload);
                return;
            }
            JSONObject result = runJsonRequest(operation, payload);
            sendResponse(replyTo, requestId, result);
        } catch (Throwable throwable) {
            sendResponse(replyTo, requestId, error(500, "tai_runtime_service_error", message(throwable)));
        } finally {
            deleteBodyFile(bodyFile);
            updateForegroundAfterOperation();
        }
    }

    @NonNull
    private JSONObject runJsonRequest(@NonNull String operation, @NonNull String body) throws JSONException {
        TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
        switch (operation) {
            case TaiRuntimeIpc.OP_STATUS:
                return manager.status();
            case TaiRuntimeIpc.OP_RUNTIME_STATUS:
                return manager.runtimeStatus();
            case TaiRuntimeIpc.OP_LOAD_MODEL:
                return manager.loadModel(body);
            case TaiRuntimeIpc.OP_UNLOAD_MODEL:
                return manager.unloadModel();
            case TaiRuntimeIpc.OP_KEEP_WARM:
                return manager.keepWarmRuntime(body);
            case TaiRuntimeIpc.OP_CANCEL:
                return manager.cancelRuntime();
            case TaiRuntimeIpc.OP_OPENAI_CHAT:
                return manager.openAiChatCompletions(body);
            case TaiRuntimeIpc.OP_OPENAI_COMPLETION:
                return manager.openAiCompletions(body);
            case TaiRuntimeIpc.OP_EMBEDDINGS:
                return manager.embeddings(body);
            case TaiRuntimeIpc.OP_PREFLIGHT:
                return manager.preflight(body);
            default:
                return error(400, "bad_runtime_operation", "Unknown TAI runtime operation: " + operation);
        }
    }

    private void runStreamRequest(
        @NonNull Messenger replyTo,
        @NonNull String requestId,
        @NonNull String operation,
        @NonNull String body
    ) throws JSONException, IOException {
        TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
        TaiManager.OpenAiStreamSink sink = new TaiManager.OpenAiStreamSink() {
            @Override
            public void onEvent(@NonNull JSONObject event) throws IOException {
                sendStreamEvent(replyTo, requestId, event);
            }

            @Override
            public void onDone() throws IOException {
                sendStreamDone(replyTo, requestId);
            }
        };
        if (TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)) {
            manager.openAiChatCompletionsStream(body, sink);
        } else {
            manager.openAiCompletionsStream(body, sink);
        }
    }

    private boolean isForegroundOperation(@NonNull String operation) {
        return TaiRuntimeIpc.OP_LOAD_MODEL.equals(operation)
            || TaiRuntimeIpc.OP_KEEP_WARM.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_CHAT.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_COMPLETION.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM.equals(operation);
    }

    private void updateForegroundAfterOperation() {
        try {
            TaiRuntimeState state = TaiManager.getRuntimeProcessInstance(this).getRuntimeState();
            if (state.loaded || state.activeGeneration || "loading".equals(state.state) || "idle-warm".equals(state.state)) {
                ensureForeground("TAI runtime", state.status);
            } else if (foreground) {
                stopForeground(true);
                foreground = false;
            }
        } catch (Exception ignored) {
        }
    }

    private void ensureForeground(@NonNull String title, @NonNull String text) {
        ensureChannel();
        try {
            startForeground(NOTIFICATION_ID, buildNotification(title, text));
            foreground = true;
        } catch (RuntimeException e) {
            foreground = false;
        }
    }

    private Notification buildNotification(@NonNull String title, @NonNull String text) {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            settingsIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "TAI runtime", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Termux AI model runtime process");
        manager.createNotificationChannel(channel);
    }

    private void sendResponse(@NonNull Messenger replyTo, @NonNull String requestId, @NonNull JSONObject result) {
        Bundle data = new Bundle();
        data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, requestId);
        data.putString(TaiRuntimeIpc.KEY_RESULT, result.toString());
        send(replyTo, TaiRuntimeIpc.MSG_RESPONSE, data);
    }

    private void sendStreamEvent(@NonNull Messenger replyTo, @NonNull String requestId, @NonNull JSONObject event) throws IOException {
        Bundle data = new Bundle();
        data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, requestId);
        data.putString(TaiRuntimeIpc.KEY_EVENT, event.toString());
        sendOrThrow(replyTo, TaiRuntimeIpc.MSG_STREAM_EVENT, data);
    }

    private void sendStreamDone(@NonNull Messenger replyTo, @NonNull String requestId) throws IOException {
        Bundle data = new Bundle();
        data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, requestId);
        sendOrThrow(replyTo, TaiRuntimeIpc.MSG_STREAM_DONE, data);
    }

    private void send(@NonNull Messenger replyTo, int what, @NonNull Bundle data) {
        try {
            sendOrThrow(replyTo, what, data);
        } catch (IOException ignored) {
        }
    }

    private void sendOrThrow(@NonNull Messenger replyTo, int what, @NonNull Bundle data) throws IOException {
        Message message = Message.obtain(null, what);
        message.setData(data);
        try {
            replyTo.send(message);
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    private String readBodyFile(@Nullable String path) throws IOException {
        if (path == null || path.trim().isEmpty()) return "";
        File file = new File(path);
        try (InputStream stream = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void deleteBodyFile(@Nullable String path) {
        if (path == null || path.trim().isEmpty()) return;
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private JSONObject error(int statusCode, @NonNull String code, @NonNull String message) {
        JSONObject data = new JSONObject();
        try {
            data.put("ok", false);
            data.put("error", code);
            data.put("message", message);
            data.put("_statusCode", statusCode);
        } catch (JSONException ignored) {
        }
        return data;
    }

    @NonNull
    private String message(@NonNull Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}
