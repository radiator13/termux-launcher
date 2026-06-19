package com.termux.launcherctl;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Key/value memory storage for agent tools, backed by SQLite under
 * {@code ~/.launcherctl/launcher.db}.
 *
 * <p>Memory entries live in a namespace so multiple callers or agents can share
 * the same database without collisions. The default namespace is {@code agent}.
 */
public final class LauncherCtlMemoryStore {
    private static final String LOG_TAG = "LauncherCtlMemoryStore";
    private static final String TABLE_NAME = "memory_entries";

    private static LauncherCtlMemoryStore sInstance;

    private SQLiteDatabase db;

    private LauncherCtlMemoryStore() {
    }

    public static synchronized LauncherCtlMemoryStore getInstance() {
        if (sInstance == null) {
            sInstance = new LauncherCtlMemoryStore();
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
     * Writes a memory entry, overwriting an existing entry with the same
     * namespace and key.
     */
    public synchronized void write(String namespace, String key, String value) {
        if (namespace == null || namespace.isEmpty() || key == null || key.isEmpty()) {
            return;
        }
        SQLiteDatabase database = getDb();
        if (database == null) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            LauncherCtlMemoryStore.MemoryEntry existing = get(namespace, key);
            if (existing != null) {
                database.execSQL(
                    "UPDATE " + TABLE_NAME + " SET value = ?, updated_at = ? WHERE namespace = ? AND key = ?",
                    new Object[]{value, now, namespace, key}
                );
            } else {
                database.execSQL(
                    "INSERT INTO " + TABLE_NAME + " (namespace, key, value, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?)",
                    new Object[]{namespace, key, value, now, now}
                );
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to write memory entry: " + e.getMessage());
        }
    }

    /**
     * Searches memory entries whose key or value contains the query text.
     */
    public synchronized List<MemoryEntry> search(String namespace, String query, int limit) {
        List<MemoryEntry> results = new ArrayList<>();
        if (namespace == null || namespace.isEmpty() || query == null || query.isEmpty()) {
            return results;
        }
        SQLiteDatabase database = getDb();
        if (database == null) {
            return results;
        }
        String pattern = "%" + query + "%";
        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT namespace, key, value, created_at, updated_at FROM " + TABLE_NAME + " " +
                    "WHERE namespace = ? AND (key LIKE ? OR value LIKE ?) " +
                    "ORDER BY updated_at DESC LIMIT ?",
                new String[]{namespace, pattern, pattern, String.valueOf(limit)});
            while (cursor != null && cursor.moveToNext()) {
                results.add(cursorToEntry(cursor));
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to search memory entries: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return results;
    }

    /**
     * Returns a single memory entry or null if absent.
     */
    public synchronized MemoryEntry get(String namespace, String key) {
        if (namespace == null || namespace.isEmpty() || key == null || key.isEmpty()) {
            return null;
        }
        SQLiteDatabase database = getDb();
        if (database == null) {
            return null;
        }
        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                "SELECT namespace, key, value, created_at, updated_at FROM " + TABLE_NAME + " " +
                    "WHERE namespace = ? AND key = ? LIMIT 1",
                new String[]{namespace, key});
            if (cursor != null && cursor.moveToFirst()) {
                return cursorToEntry(cursor);
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to get memory entry: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private MemoryEntry cursorToEntry(Cursor cursor) {
        return new MemoryEntry(
            cursor.getString(cursor.getColumnIndexOrThrow("namespace")),
            cursor.getString(cursor.getColumnIndexOrThrow("key")),
            cursor.getString(cursor.getColumnIndexOrThrow("value")),
            cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
            cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"))
        );
    }

    private synchronized SQLiteDatabase getDb() {
        try {
            if (db == null || !db.isOpen()) {
                db = SQLiteDatabase.openOrCreateDatabase(LauncherCtlStorage.getDatabaseFile().getAbsolutePath(), null);
                createTables(db);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to open memory database: " + e.getMessage());
        }
        return db;
    }

    private void createTables(SQLiteDatabase database) {
        database.execSQL(
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "namespace TEXT NOT NULL, " +
                "key TEXT NOT NULL, " +
                "value TEXT, " +
                "created_at INTEGER NOT NULL, " +
                "updated_at INTEGER NOT NULL, " +
                "PRIMARY KEY (namespace, key))"
        );
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_memory_search ON " + TABLE_NAME + "(namespace, key, value)"
        );
    }

    /**
     * Closes the database. The store should not be used after this call outside
     * of tests.
     */
    public synchronized void close() {
        if (db != null && db.isOpen()) {
            db.close();
            db = null;
        }
    }

    /** A single memory entry. */
    public static final class MemoryEntry {
        public final String namespace;
        public final String key;
        public final String value;
        public final long createdAt;
        public final long updatedAt;

        public MemoryEntry(String namespace, String key, String value, long createdAt, long updatedAt) {
            this.namespace = namespace;
            this.key = key;
            this.value = value;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public JSONObject toJson() throws JSONException {
            JSONObject data = new JSONObject();
            data.put("namespace", namespace);
            data.put("key", key);
            data.put("value", value);
            data.put("createdAt", createdAt);
            data.put("updatedAt", updatedAt);
            return data;
        }
    }
}
