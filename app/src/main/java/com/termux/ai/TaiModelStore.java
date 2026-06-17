package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TaiModelStore {
    public static final String STATE_QUEUED = "queued";
    public static final String STATE_DOWNLOADING = "downloading";
    public static final String STATE_CANCELLED = "cancelled";
    public static final String STATE_FAILED = "failed";
    public static final String STATE_VERIFYING = "verifying";
    public static final String STATE_INSTALLED = "installed";
    public static final String STATE_UNAVAILABLE = "unavailable";

    public static final String ERROR_ACTIVE_MODEL_LOADED = "active_model_loaded";
    public static final String ERROR_DELETE_REQUIRES_CONFIRMATION = "delete_requires_confirmation";

    private static final String KEY_USER_MODELS = "tai_user_models_json";
    private static final String KEY_DOWNLOADS = "tai_downloads_json";

    private final Context appContext;
    private final SharedPreferences preferences;

    public TaiModelStore(@NonNull Context context) {
        appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public File getModelsDirectory() {
        return new File(appContext.getFilesDir(), "tai/models");
    }

    @NonNull
    public synchronized Map<String, TaiModelSpec> getUserModels() {
        LinkedHashMap<String, TaiModelSpec> models = new LinkedHashMap<>();
        JSONArray array = parseArray(preferences.getString(KEY_USER_MODELS, "[]"));
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) continue;
            TaiModelSpec spec;
            try {
                spec = TaiModelSpec.fromJson(json);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!spec.id.isEmpty()
                && TaiModelSpec.isSupportedBackendFormat(spec.backend, spec.format)) {
                models.put(spec.id, spec);
            }
        }
        return models;
    }

    @Nullable
    public synchronized TaiModelSpec getUserModel(@Nullable String modelId) {
        if (modelId == null) return null;
        return getUserModels().get(modelId);
    }

    public synchronized void upsertUserModel(@NonNull TaiModelSpec spec) throws JSONException {
        Map<String, TaiModelSpec> models = getUserModels();
        models.put(spec.id, spec);
        JSONArray array = new JSONArray();
        for (TaiModelSpec model : models.values()) {
            array.put(model.toJson());
        }
        preferences.edit().putString(KEY_USER_MODELS, array.toString()).apply();
    }

    public synchronized boolean deleteUserModel(@NonNull String modelId) {
        return deleteUserModel(modelId, false, true).deleted;
    }

    @NonNull
    public synchronized DeleteResult deleteUserModel(@NonNull String modelId,
                                                     boolean activeModelLoaded,
                                                     boolean confirmed) {
        if (activeModelLoaded) {
            return DeleteResult.blocked(ERROR_ACTIVE_MODEL_LOADED,
                "Unload the active model before deleting it.");
        }
        if (!confirmed) {
            return DeleteResult.blocked(ERROR_DELETE_REQUIRES_CONFIRMATION,
                "Deleting a model requires explicit confirmation.");
        }

        Map<String, TaiModelSpec> models = getUserModels();
        TaiModelSpec removed = models.remove(modelId);
        JSONArray array = new JSONArray();
        for (TaiModelSpec model : models.values()) {
            try {
                array.put(model.toJson());
            } catch (JSONException ignored) {
            }
        }

        JSONArray downloads = getDownloads();
        JSONArray keptDownloads = new JSONArray();
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject item = downloads.optJSONObject(i);
            if (item == null) continue;
            if (!modelId.equals(item.optString("modelId", ""))) {
                keptDownloads.put(item);
            }
        }

        preferences.edit()
            .putString(KEY_USER_MODELS, array.toString())
            .putString(KEY_DOWNLOADS, keptDownloads.toString())
            .apply();

        File modelDir = new File(getModelsDirectory(), modelId);
        deleteRecursively(modelDir);
        return DeleteResult.deleted(removed != null);
    }

    @NonNull
    public synchronized JSONArray getDownloads() {
        return parseArray(preferences.getString(KEY_DOWNLOADS, "[]"));
    }

    public synchronized void upsertDownload(@NonNull JSONObject transfer) {
        String id = transfer.optString("id", "");
        JSONArray current = getDownloads();
        JSONArray next = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) continue;
            if (id.equals(item.optString("id", ""))) {
                next.put(transfer);
                replaced = true;
            } else {
                next.put(item);
            }
        }
        if (!replaced) next.put(transfer);
        preferences.edit().putString(KEY_DOWNLOADS, next.toString()).apply();
    }

    public synchronized void updateDownloadStatus(@NonNull String transferId, @NonNull String status, @NonNull String error) {
        JSONArray current = getDownloads();
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null || !transferId.equals(item.optString("id", ""))) continue;
            try {
                item.put("status", status);
                item.put("error", error);
                item.put("updatedAtMs", System.currentTimeMillis());
            } catch (JSONException ignored) {
            }
            break;
        }
        preferences.edit().putString(KEY_DOWNLOADS, current.toString()).apply();
    }

    @NonNull
    private JSONArray parseArray(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return new JSONArray();
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void deleteRecursively(@Nullable File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    public static final class DeleteResult {
        public final boolean ok;
        public final boolean deleted;
        @NonNull public final String errorCode;
        @NonNull public final String message;

        private DeleteResult(boolean ok, boolean deleted, @NonNull String errorCode, @NonNull String message) {
            this.ok = ok;
            this.deleted = deleted;
            this.errorCode = errorCode;
            this.message = message;
        }

        @NonNull
        static DeleteResult deleted(boolean deleted) {
            return new DeleteResult(true, deleted, "", deleted ? "Model deleted." : "Model was not installed.");
        }

        @NonNull
        static DeleteResult blocked(@NonNull String errorCode, @NonNull String message) {
            return new DeleteResult(false, false, errorCode, message);
        }
    }
}
