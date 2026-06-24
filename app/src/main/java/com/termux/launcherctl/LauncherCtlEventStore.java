package com.termux.launcherctl;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Append-only JSONL event store for agent audit and system events.
 *
 * <p>Events are written to both {@code events.jsonl} and {@code agent-runs.jsonl}
 * as required by the launcherctl agent platform plan. The store is intentionally
 * simple: it appends JSON objects one per line and supports tailing recent lines.
 */
public final class LauncherCtlEventStore {
    private static final String LOG_TAG = "LauncherCtlEventStore";
    private static final int DEFAULT_WRITE_TIMEOUT_MS = 5_000;
    private static final int MAX_TAIL_LINES = 10_000;

    private static LauncherCtlEventStore sInstance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "launcherctl-event-store");
        t.setDaemon(true);
        return t;
    });

    private LauncherCtlEventStore() {
    }

    public static synchronized LauncherCtlEventStore getInstance() {
        if (sInstance == null) {
            sInstance = new LauncherCtlEventStore();
        }
        return sInstance;
    }

    /** Resets the singleton for unit tests. */
    static synchronized void resetForTesting() {
        if (sInstance != null) {
            sInstance.close();
            sInstance = null;
        }
    }

    /**
     * Appends a structured event to {@code events.jsonl}. The event type is stored
     * under {@code type} and the timestamp under {@code timestampMs}.
     */
    public void appendEvent(String type, JSONObject payload) {
        if (type == null || type.isEmpty()) {
            return;
        }
        executor.submit(() -> {
            try {
                appendEventSync(type, payload, LauncherCtlStorage.getEventsJsonlFile());
            } catch (Exception e) {
                Log.w(LOG_TAG, "Failed to append event: " + e.getMessage());
            }
        });
    }

    /**
     * Appends an agent run audit record to {@code agent-runs.jsonl}. Every route
     * and execute call should write here.
     */
    public void appendAgentRun(String type, JSONObject payload) {
        if (type == null || type.isEmpty()) {
            return;
        }
        executor.submit(() -> {
            try {
                appendEventSync(type, payload, LauncherCtlStorage.getAgentRunsJsonlFile());
            } catch (Exception e) {
                Log.w(LOG_TAG, "Failed to append agent run: " + e.getMessage());
            }
        });
    }

    /**
     * Synchronously appends an event. Exposed for callers that need to wait for
     * persistence and for unit tests.
     */
    public void appendEventSync(String type, JSONObject payload, File file) throws JSONException, IOException {
        JSONObject event = new JSONObject();
        event.put("type", type);
        event.put("timestampMs", System.currentTimeMillis());
        if (payload != null) {
            event.put("payload", new JSONObject(payload.toString()));
        }
        appendJsonl(file, event);
    }

    /**
     * Reads the most recent events from {@code events.jsonl}. Returns up to
     * {@code limit} events optionally filtered to those with timestamps
     * greater than or equal to {@code sinceMs}.
     */
    public List<JSONObject> tailEvents(int limit, Long sinceMs) {
        return tailJsonl(LauncherCtlStorage.getEventsJsonlFile(),
            clampLimit(limit), sinceMs);
    }

    /**
     * Reads the most recent agent run records from {@code agent-runs.jsonl}.
     */
    public List<JSONObject> tailAgentRuns(int limit, Long sinceMs) {
        return tailJsonl(LauncherCtlStorage.getAgentRunsJsonlFile(),
            clampLimit(limit), sinceMs);
    }

    private int clampLimit(int limit) {
        if (limit < 1) return 1;
        if (limit > MAX_TAIL_LINES) return MAX_TAIL_LINES;
        return limit;
    }

    private void appendJsonl(File file, JSONObject event) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create dir for " + file.getAbsolutePath());
        }
        try (FileOutputStream fos = new FileOutputStream(file, true);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            writer.write(event.toString());
            writer.newLine();
        }
    }

    private List<JSONObject> tailJsonl(File file, int limit, Long sinceMs) {
        if (!file.exists() || !file.canRead()) {
            return Collections.emptyList();
        }

        List<JSONObject> recent = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    JSONObject event = new JSONObject(line);
                    if (sinceMs != null && event.optLong("timestampMs", 0) < sinceMs) {
                        continue;
                    }
                    recent.add(event);
                    if (recent.size() > limit) {
                        recent.remove(0);
                    }
                } catch (JSONException e) {
                    Log.w(LOG_TAG, "Skipping malformed JSONL line: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            Log.w(LOG_TAG, "Failed to tail JSONL file: " + e.getMessage());
        }
        return recent;
    }

    /**
     * Waits for queued async writes to finish. Exposed for unit tests.
     */
    public void awaitWritesForTesting(long timeoutMs) throws InterruptedException {
        executor.shutdown();
        executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Closes the executor. The store should not be used after this call outside
     * of tests.
     */
    public synchronized void close() {
        executor.shutdown();
        try {
            executor.awaitTermination(DEFAULT_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
