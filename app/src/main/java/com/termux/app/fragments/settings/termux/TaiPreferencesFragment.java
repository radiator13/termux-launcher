package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

@Keep
public class TaiPreferencesFragment extends PreferenceFragmentCompat {
    private static final String MODEL_ROW_PREFIX = "tai_model_row_";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRuntimeRunnable = new Runnable() {
        @Override
        public void run() {
            Context context = getContext();
            if (context != null) {
                refreshTaiPage(context);
                if (shouldContinueRefreshing(context)) {
                    handler.postDelayed(this, 2000L);
                }
            }
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(TaiSettings.PREFS_NAME);
        setPreferencesFromResource(R.xml.termux_ai_preferences, rootKey);
        setStaticSummary("tai_model_privacy_notice", R.string.termux_ai_model_privacy_notice_summary);
        configureRuntimeControls(context);
        configureHuggingFaceToken();
        configureEndpointInfo();
        configureModelManager(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context != null) {
            refreshTaiPage(context);
            handler.postDelayed(refreshRuntimeRunnable, 2000L);
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(refreshRuntimeRunnable);
        super.onPause();
    }

    private void setStaticSummary(String key, int summaryResId) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setSummary(summaryResId);
        }
    }

    private void refreshTaiPage(Context context) {
        configureDefaultModelSelector(context);
        updateRuntimeStatus(context);
        populateModelRows(context);
    }

    private void configureRuntimeControls(Context context) {
        Preference load = findPreference("tai_runtime_load");
        if (load != null) {
            load.setOnPreferenceClickListener(preference -> {
                loadDefaultModel(context);
                return true;
            });
        }

        Preference keepWarm = findPreference("tai_runtime_keep_warm");
        if (keepWarm != null) {
            keepWarm.setOnPreferenceClickListener(preference -> {
                keepWarmDefaultModel(context);
                return true;
            });
        }

        Preference cancel = findPreference("tai_runtime_cancel");
        if (cancel != null) {
            cancel.setOnPreferenceClickListener(preference -> {
                cancelGeneration(context);
                return true;
            });
        }

        Preference unload = findPreference("tai_runtime_unload");
        if (unload != null) {
            unload.setOnPreferenceClickListener(preference -> {
                unloadRuntime(context);
                return true;
            });
        }
    }

    private void configureEndpointInfo() {
        Preference endpoint = findPreference("tai_endpoint_notice");
        if (endpoint != null) {
            endpoint.setOnPreferenceClickListener(preference -> {
                Context context = getContext();
                if (context != null) {
                    new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.termux_ai_endpoint_detail_title)
                        .setMessage(R.string.termux_ai_endpoint_detail_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                }
                return true;
            });
        }
    }

    private void configureDefaultModelSelector(Context context) {
        ListPreference preference = findPreference(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT);
        if (preference == null) return;

        TaiModelStore store = new TaiModelStore(context);
        Map<String, TaiModelSpec> installedModels = store.getUserModels();
        ArrayList<String> entries = new ArrayList<>();
        ArrayList<String> values = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            entries.add(entry.displayName);
            values.add(entry.modelId);
            seen.add(entry.modelId);
        }
        for (TaiModelSpec model : installedModels.values()) {
            if (seen.contains(model.id)) continue;
            entries.add(model.displayName + " (" + model.id + ")");
            values.add(model.id);
            seen.add(model.id);
        }

        String current = preference.getValue();
        if (current != null && !current.isEmpty() && !seen.contains(current)) {
            entries.add(current);
            values.add(current);
        }
        preference.setEntries(entries.toArray(new CharSequence[0]));
        preference.setEntryValues(values.toArray(new CharSequence[0]));
    }

    private void updateRuntimeStatus(Context context) {
        Preference status = findPreference("tai_runtime_status");
        if (status == null) return;
        try {
            JSONObject runtime = TaiManager.getInstance(context).runtimeStatus().getJSONObject("runtime");
            status.setSummary(buildRuntimeSummary(runtime));
            Preference load = findPreference("tai_runtime_load");
            Preference keepWarm = findPreference("tai_runtime_keep_warm");
            Preference cancel = findPreference("tai_runtime_cancel");
            Preference unload = findPreference("tai_runtime_unload");
            boolean activeGeneration = runtime.optBoolean("activeGeneration", false);
            boolean loaded = runtime.optBoolean("loaded", false);
            if (load != null) load.setEnabled(!activeGeneration);
            if (keepWarm != null) keepWarm.setEnabled(!activeGeneration);
            if (cancel != null) cancel.setEnabled(activeGeneration);
            if (unload != null) unload.setEnabled(loaded && !activeGeneration);
        } catch (JSONException e) {
            status.setSummary(R.string.termux_ai_runtime_status_summary);
        }
    }

    private void configureModelManager(Context context) {
        Preference importModel = findPreference("tai_model_import");
        if (importModel != null) {
            importModel.setOnPreferenceClickListener(preference -> {
                showImportDialog(context);
                return true;
            });
        }

        Preference refresh = findPreference("tai_models_refresh");
        if (refresh != null) {
            refresh.setOnPreferenceClickListener(preference -> {
                refreshTaiPage(context);
                return true;
            });
        }
        refreshTaiPage(context);
    }

    private String buildRuntimeSummary(JSONObject runtime) {
        StringBuilder summary = new StringBuilder();
        summary.append("State: ").append(runtime.optString("state", "unknown"));
        summary.append("\nModel: ").append(nullable(runtime, "loadedModelId", "none"));
        summary.append("\nBackend: ").append(runtime.optString("backend", "none"));
        String fallback = nullable(runtime, "backendFallbackReason", "");
        if (!fallback.isEmpty()) summary.append("\n").append(fallback);
        if (runtime.optBoolean("activeGeneration", false)) {
            summary.append("\nGeneration: active");
        }
        long keepWarmRemaining = runtime.optLong("keepWarmRemainingMs", 0L);
        if (keepWarmRemaining > 0L) {
            summary.append("\nKeep warm: ").append(formatDuration(keepWarmRemaining));
        }
        long idleRemaining = runtime.optLong("idleUnloadRemainingMs", 0L);
        if (idleRemaining > 0L) {
            summary.append("\nIdle unload: ").append(formatDuration(idleRemaining));
        }
        String status = runtime.optString("status", "");
        if (!status.isEmpty()) {
            summary.append("\n").append(status);
        }
        return summary.toString();
    }

    private void loadDefaultModel(Context context) {
        try {
            JSONObject result = TaiManager.getInstance(context).loadModel("{}");
            toastRuntimeResult(context, result, R.string.termux_ai_model_loaded);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_runtime_action_failed, Toast.LENGTH_LONG).show();
        }
        refreshTaiPage(context);
    }

    private void keepWarmDefaultModel(Context context) {
        try {
            JSONObject result = TaiManager.getInstance(context).keepWarmRuntime("{}");
            toastRuntimeResult(context, result, R.string.termux_ai_runtime_keep_warm_started);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_runtime_action_failed, Toast.LENGTH_LONG).show();
        }
        refreshTaiPage(context);
    }

    private void cancelGeneration(Context context) {
        try {
            JSONObject result = TaiManager.getInstance(context).cancelRuntime();
            toastRuntimeResult(context, result, R.string.termux_ai_runtime_cancel_requested);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_runtime_action_failed, Toast.LENGTH_LONG).show();
        }
        refreshTaiPage(context);
    }

    private void unloadRuntime(Context context) {
        try {
            JSONObject result = TaiManager.getInstance(context).unloadModel();
            toastRuntimeResult(context, result, R.string.termux_ai_runtime_unloaded);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_runtime_action_failed, Toast.LENGTH_LONG).show();
        }
        refreshTaiPage(context);
    }

    private void toastRuntimeResult(Context context, JSONObject result, int successResId) {
        if (result.optBoolean("ok", false)) {
            Toast.makeText(context, successResId, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, result.optString("message", context.getString(R.string.termux_ai_runtime_action_failed)), Toast.LENGTH_LONG).show();
        }
    }

    private void configureHuggingFaceToken() {
        EditTextPreference token = findPreference(TaiSettings.KEY_HUGGINGFACE_TOKEN);
        if (token == null) return;
        token.setOnBindEditTextListener(editText -> {
            editText.setSingleLine(true);
            editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        });
        token.setSummaryProvider(preference -> {
            String value = ((EditTextPreference) preference).getText();
            return value == null || value.trim().isEmpty()
                ? getString(R.string.termux_ai_huggingface_token_summary)
                : getString(R.string.termux_ai_huggingface_token_set_summary);
        });
    }

    private void populateModelRows(Context context) {
        PreferenceCategory category = findPreference("tai_models_category");
        if (category == null) return;

        for (int i = category.getPreferenceCount() - 1; i >= 0; i--) {
            Preference preference = category.getPreference(i);
            if (preference != null && preference.getKey() != null && preference.getKey().startsWith(MODEL_ROW_PREFIX)) {
                category.removePreference(preference);
            }
        }

        TaiModelStore store = new TaiModelStore(context);
        Map<String, TaiModelSpec> installedModels = store.getUserModels();
        JSONArray downloads = store.getDownloads();
        HashSet<String> catalogIds = new HashSet<>();

        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            catalogIds.add(entry.modelId);
            TaiModelPreference row = new TaiModelPreference(context);
            JSONObject download = findDownload(downloads, entry.modelId);
            row.setKey(MODEL_ROW_PREFIX + entry.modelId);
            row.setTitle(entry.displayName);
            row.setSummary(buildModelSummary(entry, installedModels.get(entry.modelId), download));
            configureProgress(row, download);
            row.setPersistent(false);
            row.setOnPreferenceClickListener(preference -> {
                showModelActions(context, entry, installedModels.get(entry.modelId), findDownload(new TaiModelStore(context).getDownloads(), entry.modelId));
                return true;
            });
            category.addPreference(row);
        }

        for (TaiModelSpec model : installedModels.values()) {
            if (catalogIds.contains(model.id)) continue;
            TaiModelPreference row = new TaiModelPreference(context);
            row.setKey(MODEL_ROW_PREFIX + model.id);
            row.setTitle(model.displayName);
            row.setSummary(buildInstalledModelSummary(model));
            configureProgress(row, null);
            row.setPersistent(false);
            row.setOnPreferenceClickListener(preference -> {
                showInstalledModelActions(context, model);
                return true;
            });
            category.addPreference(row);
        }
    }

    private void configureProgress(TaiModelPreference row, JSONObject download) {
        if (download == null) {
            row.setDownloadProgress(false, false, 0);
            return;
        }
        String status = download.optString("status", "");
        boolean active = "queued".equals(status) || "running".equals(status);
        long bytesRead = download.optLong("bytesRead", 0L);
        long totalBytes = download.optLong("totalBytes", 0L);
        row.setDownloadProgress(active, totalBytes <= 0L, totalBytes > 0L ? (int) (bytesRead * 10000L / totalBytes) : 0);
    }

    private String buildModelSummary(TaiModelCatalog.CatalogEntry entry, TaiModelSpec installed, JSONObject download) {
        StringBuilder summary = new StringBuilder();
        summary.append(entry.roleHint).append(" - ").append(formatBytes(entry.sizeBytes));
        if (entry.gated) {
            summary.append(" - gated");
        }
        if (installed != null && installed.localPath != null) {
            summary.append("\nInstalled: ").append(formatBytes(installed.sizeBytes));
            summary.append("\n").append(installed.localPath);
        } else if (download != null) {
            summary.append("\nDownload: ").append(download.optString("status", "unknown"));
            long bytesRead = download.optLong("bytesRead", 0L);
            long totalBytes = download.optLong("totalBytes", 0L);
            if (totalBytes > 0) {
                summary.append(" ").append(formatPercent(bytesRead, totalBytes));
            }
            String error = download.optString("error", "");
            if (!error.isEmpty()) {
                summary.append("\n").append(error);
            }
        } else {
            summary.append("\nNot installed");
        }
        return summary.toString();
    }

    private String buildInstalledModelSummary(TaiModelSpec model) {
        StringBuilder summary = new StringBuilder();
        summary.append(model.roleHint).append(" - ").append(model.source);
        if (model.localPath != null) {
            summary.append("\nInstalled: ").append(formatBytes(model.sizeBytes));
            summary.append("\n").append(model.localPath);
        }
        summary.append("\nCapabilities: ").append(model.capabilities.toString());
        return summary.toString();
    }

    private void showModelActions(Context context, TaiModelCatalog.CatalogEntry entry, TaiModelSpec installed, JSONObject download) {
        if (installed != null) {
            new MaterialAlertDialogBuilder(context)
                .setTitle(entry.displayName)
                .setMessage(buildModelSummary(entry, installed, download))
                .setPositiveButton(R.string.termux_ai_model_load_action, (dialog, which) -> loadModel(context, entry.modelId))
                .setNeutralButton(R.string.termux_ai_model_delete_action, (dialog, which) -> deleteModel(context, entry.modelId))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }

        if (entry.gated) {
            new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.termux_ai_model_gated_title)
                .setMessage(context.getString(R.string.termux_ai_model_gated_message, entry.displayName))
                .setPositiveButton(R.string.termux_ai_model_download_start, (dialog, which) -> startCatalogDownload(context, entry))
                .setNeutralButton(R.string.termux_ai_model_open_provider, (dialog, which) -> openUrl(context, entry.providerPageUrl))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }

        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.termux_ai_model_download_title, entry.displayName))
            .setMessage(context.getString(R.string.termux_ai_model_download_message,
                entry.displayName, formatBytes(entry.sizeBytes), entry.license))
            .setPositiveButton(R.string.termux_ai_model_download_start, (dialog, which) -> startCatalogDownload(context, entry))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showInstalledModelActions(Context context, TaiModelSpec model) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(model.displayName)
            .setMessage(buildInstalledModelSummary(model))
            .setPositiveButton(R.string.termux_ai_model_load_action, (dialog, which) -> loadModel(context, model.id))
            .setNeutralButton(R.string.termux_ai_model_delete_action, (dialog, which) -> deleteModel(context, model.id))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void startCatalogDownload(Context context, TaiModelCatalog.CatalogEntry entry) {
        try {
            JSONObject result = TaiManager.getInstance(context).downloadCatalogModel(entry.modelId);
            if (result.optBoolean("ok", false)) {
                Toast.makeText(context, R.string.termux_ai_model_download_started, Toast.LENGTH_SHORT).show();
                handler.removeCallbacks(refreshRuntimeRunnable);
                handler.postDelayed(refreshRuntimeRunnable, 1000L);
            } else {
                Toast.makeText(context, result.optString("message", context.getString(R.string.termux_ai_model_action_failed)), Toast.LENGTH_LONG).show();
            }
            refreshTaiPage(context);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_model_action_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showImportDialog(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, 0, padding, 0);

        EditText pathInput = new EditText(context);
        pathInput.setSingleLine(true);
        pathInput.setHint(R.string.termux_ai_model_import_path_hint);
        pathInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(pathInput);

        EditText modelIdInput = new EditText(context);
        modelIdInput.setSingleLine(true);
        modelIdInput.setHint(R.string.termux_ai_model_import_id_hint);
        modelIdInput.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(modelIdInput);

        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_model_import_title)
            .setView(layout)
            .setPositiveButton(R.string.termux_ai_model_import_title, (dialog, which) ->
                importModel(context, pathInput.getText().toString(), modelIdInput.getText().toString()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void importModel(Context context, String path, String modelId) {
        try {
            JSONObject request = new JSONObject();
            request.put("path", path == null ? "" : path.trim());
            if (modelId != null && !modelId.trim().isEmpty()) request.put("modelId", modelId.trim());
            JSONObject result = TaiManager.getInstance(context).importModel(request.toString());
            if (result.optBoolean("ok", false)) {
                Toast.makeText(context, R.string.termux_ai_model_imported, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, result.optString("message", context.getString(R.string.termux_ai_model_action_failed)), Toast.LENGTH_LONG).show();
            }
            refreshTaiPage(context);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_model_action_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void loadModel(Context context, String modelId) {
        try {
            JSONObject request = new JSONObject();
            request.put("model", modelId);
            JSONObject result = TaiManager.getInstance(context).loadModel(request.toString());
            toastRuntimeResult(context, result, R.string.termux_ai_model_loaded);
            refreshTaiPage(context);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_runtime_action_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void deleteModel(Context context, String modelId) {
        try {
            JSONObject request = new JSONObject();
            request.put("modelId", modelId);
            JSONObject result = TaiManager.getInstance(context).deleteModel(request.toString());
            Toast.makeText(context,
                result.optBoolean("deleted", false) ? R.string.termux_ai_model_deleted : R.string.termux_ai_model_delete_missing,
                Toast.LENGTH_SHORT).show();
            refreshTaiPage(context);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_model_action_failed, Toast.LENGTH_LONG).show();
        }
    }

    private JSONObject findDownload(JSONArray downloads, String modelId) {
        if (downloads == null) return null;
        for (int i = downloads.length() - 1; i >= 0; i--) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null && modelId.equals(item.optString("modelId", ""))) {
                return item;
            }
        }
        return null;
    }

    private boolean hasActiveDownloads(Context context) {
        JSONArray downloads = new TaiModelStore(context).getDownloads();
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject item = downloads.optJSONObject(i);
            if (item == null) continue;
            String status = item.optString("status", "");
            if ("queued".equals(status) || "running".equals(status)) return true;
        }
        return false;
    }

    private boolean shouldContinueRefreshing(Context context) {
        if (hasActiveDownloads(context)) return true;
        try {
            JSONObject runtime = TaiManager.getInstance(context).runtimeStatus().getJSONObject("runtime");
            return runtime.optBoolean("activeGeneration", false)
                || runtime.optBoolean("loaded", false)
                || runtime.optLong("keepWarmRemainingMs", 0L) > 0L
                || runtime.optLong("idleUnloadRemainingMs", 0L) > 0L;
        } catch (JSONException e) {
            return false;
        }
    }

    private String formatPercent(long value, long total) {
        if (total <= 0) return "";
        double percent = (double) value * 100.0 / (double) total;
        return String.format(Locale.US, "%.1f%%", percent);
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "unknown size";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes > 0L) return minutes + "m " + remainingSeconds + "s";
        return remainingSeconds + "s";
    }

    private String nullable(JSONObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.isNull(key)) return fallback;
        return object.optString(key, fallback);
    }

    private void openUrl(Context context, String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(context, url, Toast.LENGTH_LONG).show();
        }
    }
}
