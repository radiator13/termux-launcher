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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.termux.ai.TaiModelImporter;
import com.termux.ai.TaiModelProfile;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.launcherctl.LauncherCtlApiServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Keep
public class TaiPreferencesFragment extends PreferenceFragmentCompat {
    private static final String MODEL_ROW_PREFIX = "tai_model_row_";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        this::onModelDocumentSelected);
    private final ExecutorService runtimeActionExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "tai-settings-runtime");
        thread.setDaemon(true);
        return thread;
    });
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
        configureEndpointPreferences(context);
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

    @Override
    public void onDestroy() {
        runtimeActionExecutor.shutdownNow();
        super.onDestroy();
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
        refreshEndpointPreferences(context);
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

    private void configureEndpointPreferences(Context context) {
        configureApiPortPreference(context);
        configureApiTokenPreference(context);
        Preference randomizePort = findPreference("tai_api_port_randomize");
        if (randomizePort != null) {
            randomizePort.setOnPreferenceClickListener(preference -> {
                try {
                    LauncherCtlApiServer.getInstance().randomizeApiPortFromSettings(context);
                    refreshEndpointPreferences(context);
                    Toast.makeText(context, R.string.termux_ai_api_port_randomized, Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
                }
                return true;
            });
        }

        Preference rotateToken = findPreference("tai_api_token_rotate");
        if (rotateToken != null) {
            rotateToken.setOnPreferenceClickListener(preference -> {
                try {
                    JSONObject endpoint = LauncherCtlApiServer.getInstance().rotateAuthTokenFromSettings(context)
                        .optJSONObject("endpoint");
                    EditTextPreference tokenPreference = findPreference(TaiSettings.KEY_API_TOKEN);
                    if (endpoint != null && tokenPreference != null) {
                        tokenPreference.setText(endpoint.optString("token", ""));
                    }
                    refreshEndpointPreferences(context);
                    Toast.makeText(context, R.string.termux_ai_api_token_rotated, Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
                }
                return true;
            });
        }

        Preference endpoint = findPreference("tai_endpoint_notice");
        if (endpoint != null) {
            endpoint.setOnPreferenceClickListener(preference -> {
                Context currentContext = getContext();
                if (currentContext != null) {
                    new MaterialAlertDialogBuilder(currentContext)
                        .setTitle(R.string.termux_ai_endpoint_detail_title)
                        .setMessage(buildEndpointDetailMessage(currentContext))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                }
                return true;
            });
        }
    }

    private void configureApiPortPreference(Context context) {
        EditTextPreference port = findPreference(TaiSettings.KEY_API_PORT);
        if (port == null) return;
        TaiSettings settings = new TaiSettings(context);
        port.setText(String.valueOf(settings.getApiPort()));
        port.setOnBindEditTextListener(editText -> {
            editText.setSingleLine(true);
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        });
        port.setOnPreferenceChangeListener((preference, newValue) -> {
            String rawValue = String.valueOf(newValue);
            if (!TaiSettings.isValidApiPort(rawValue)) {
                Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
                return false;
            }
            int normalized = TaiSettings.normalizeApiPort(rawValue);
            new TaiSettings(context).setApiPort(normalized);
            ((EditTextPreference) preference).setText(String.valueOf(normalized));
            try {
                LauncherCtlApiServer.getInstance().applyEndpointSettings(context);
                refreshEndpointPreferences(context);
                Toast.makeText(context, R.string.termux_ai_api_port_saved, Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
            }
            return false;
        });
    }

    private void configureApiTokenPreference(Context context) {
        EditTextPreference token = findPreference(TaiSettings.KEY_API_TOKEN);
        if (token == null) return;
        TaiSettings settings = new TaiSettings(context);
        token.setText(settings.getOrCreateApiToken());
        token.setOnBindEditTextListener(editText -> {
            editText.setSingleLine(true);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        });
        token.setOnPreferenceChangeListener((preference, newValue) -> {
            String normalized = TaiSettings.normalizeApiToken(String.valueOf(newValue));
            if (normalized.isEmpty()) {
                Toast.makeText(context, R.string.termux_ai_api_token_invalid, Toast.LENGTH_LONG).show();
                return false;
            }
            new TaiSettings(context).setApiToken(normalized);
            ((EditTextPreference) preference).setText(normalized);
            try {
                LauncherCtlApiServer.getInstance().applyEndpointSettings(context);
                refreshEndpointPreferences(context);
                Toast.makeText(context, R.string.termux_ai_api_token_saved, Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
            }
            return false;
        });
    }

    private void refreshEndpointPreferences(Context context) {
        try {
            JSONObject endpoint = LauncherCtlApiServer.getInstance().endpointSettings(context);
            EditTextPreference port = findPreference(TaiSettings.KEY_API_PORT);
            if (port != null) {
                port.setText(String.valueOf(endpoint.optInt("configuredPort", TaiSettings.DEFAULT_API_PORT)));
                port.setSummary(context.getString(R.string.termux_ai_api_port_summary,
                    endpoint.optString("openAiBaseUrl", "")));
            }
            EditTextPreference token = findPreference(TaiSettings.KEY_API_TOKEN);
            if (token != null) {
                token.setText(endpoint.optString("token", new TaiSettings(context).getOrCreateApiToken()));
                token.setSummary(endpoint.optString("token", ""));
            }
            Preference endpointNotice = findPreference("tai_endpoint_notice");
            if (endpointNotice != null) {
                endpointNotice.setSummary(context.getString(R.string.termux_ai_endpoint_notice_summary_dynamic,
                    endpoint.optString("openAiBaseUrl", ""),
                    endpoint.optString("tokenFile", "")));
            }
        } catch (JSONException e) {
            Preference endpointNotice = findPreference("tai_endpoint_notice");
            if (endpointNotice != null) endpointNotice.setSummary(R.string.termux_ai_endpoint_notice_summary);
        }
    }

    private String buildEndpointDetailMessage(Context context) {
        try {
            JSONObject endpoint = LauncherCtlApiServer.getInstance().endpointSettings(context);
            return context.getString(R.string.termux_ai_endpoint_detail_message_dynamic,
                endpoint.optString("baseUrl", ""),
                endpoint.optString("openAiBaseUrl", ""),
                endpoint.optString("token", ""),
                endpoint.optString("endpointFile", ""),
                endpoint.optString("tokenFile", ""));
        } catch (JSONException e) {
            return context.getString(R.string.termux_ai_endpoint_detail_message);
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
            JSONObject runtimeStatus = TaiManager.getInstance(context).runtimeStatus();
            JSONObject runtime = runtimeStatus.getJSONObject("runtime");
            status.setSummary(buildRuntimeSummary(runtime, runtimeStatus));
            Preference load = findPreference("tai_runtime_load");
            Preference keepWarm = findPreference("tai_runtime_keep_warm");
            Preference cancel = findPreference("tai_runtime_cancel");
            Preference unload = findPreference("tai_runtime_unload");
            boolean activeGeneration = runtime.optBoolean("activeGeneration", false);
            boolean loaded = runtime.optBoolean("loaded", false);
            String state = runtime.optString("state", "unloaded");
            boolean loading = "loading".equals(state);
            boolean stopping = "stopping".equals(state);
            if (load != null) load.setEnabled(!activeGeneration && !loading && !stopping);
            if (keepWarm != null) keepWarm.setEnabled(!activeGeneration && !loading && !stopping);
            if (cancel != null) cancel.setEnabled(activeGeneration || loading);
            if (unload != null) unload.setEnabled((loaded || loading) && !activeGeneration && !stopping);
        } catch (JSONException e) {
            status.setSummary(R.string.termux_ai_runtime_status_summary);
        }
    }

    private void configureModelManager(Context context) {
        Preference importModel = findPreference("tai_model_import");
        if (importModel != null) {
            importModel.setOnPreferenceClickListener(preference -> {
                modelPicker.launch(new String[]{"application/octet-stream", "application/zip", "*/*"});
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

    private String buildRuntimeSummary(JSONObject runtime, JSONObject runtimeStatus) {
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
        JSONObject profile = runtimeStatus.optJSONObject("modelProfile");
        if (profile != null) {
            summary.append("\nCompatible: ").append(join(profile.optJSONArray("compatibleAccelerators")));
            if (!profile.isNull("minDeviceMemoryInGb")) {
                summary.append(" - minimum memory ").append(profile.optInt("minDeviceMemoryInGb")).append(" GiB");
            }
        }
        JSONObject device = runtimeStatus.optJSONObject("device");
        if (device != null) {
            summary.append("\nDevice: ").append(device.optString("model", "unknown"));
            if (!device.isNull("memoryGiB")) {
                summary.append(" - ").append(String.format(Locale.US, "%.1f GiB", device.optDouble("memoryGiB")));
            }
            summary.append(" - ").append(join(device.optJSONArray("phase1Accelerators")));
        }
        JSONArray warnings = runtimeStatus.optJSONArray("compatibilityWarnings");
        if (warnings != null) {
            for (int i = 0; i < warnings.length(); i++) {
                String warning = warnings.optString(i, "");
                if (!warning.isEmpty()) summary.append("\nWarning: ").append(warning);
            }
        }
        return summary.toString();
    }

    private void loadDefaultModel(Context context) {
        Context appContext = context.getApplicationContext();
        runRuntimeAction(
            () -> TaiManager.getInstance(appContext).loadModel("{}"),
            R.string.termux_ai_model_loaded);
    }

    private void keepWarmDefaultModel(Context context) {
        Context appContext = context.getApplicationContext();
        runRuntimeAction(
            () -> TaiManager.getInstance(appContext).keepWarmRuntime("{}"),
            R.string.termux_ai_runtime_keep_warm_started);
    }

    private void cancelGeneration(Context context) {
        Context appContext = context.getApplicationContext();
        runRuntimeAction(
            () -> TaiManager.getInstance(appContext).cancelRuntime(),
            R.string.termux_ai_runtime_cancel_requested);
    }

    private void unloadRuntime(Context context) {
        Context appContext = context.getApplicationContext();
        runRuntimeAction(
            () -> TaiManager.getInstance(appContext).unloadModel(),
            R.string.termux_ai_runtime_unloaded);
    }

    private void runRuntimeAction(RuntimeAction action, int successResId) {
        runtimeActionExecutor.execute(() -> {
            JSONObject result = null;
            try {
                result = action.run();
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context currentContext = getContext();
                if (currentContext == null) return;
                if (finalResult == null) {
                    Toast.makeText(currentContext, R.string.termux_ai_runtime_action_failed, Toast.LENGTH_LONG).show();
                } else {
                    toastRuntimeResult(currentContext, finalResult, successResId);
                }
                refreshTaiPage(currentContext);
            });
        });
    }

    private interface RuntimeAction {
        JSONObject run() throws JSONException;
    }

    private void toastRuntimeResult(Context context, JSONObject result, int successResId) {
        if (result.optBoolean("loadCancellationRequested", false)) {
            Toast.makeText(context,
                result.optString("message", context.getString(R.string.termux_ai_runtime_cancel_requested)),
                Toast.LENGTH_LONG).show();
        } else if (result.optBoolean("ok", false)) {
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
        summary.append("\nBackend: ").append(entry.backend).append(" - ").append(entry.format);
        if (entry.quantization != null) summary.append(" - ").append(entry.quantization);
        if (entry.recommendedRamGb > 0) summary.append("\nRecommended memory: ").append(entry.recommendedRamGb).append(" GiB");
        TaiModelSpec catalogModel = new TaiModelRegistry().getModel(entry.modelId);
        if (catalogModel != null) {
            TaiModelProfile profile = TaiModelProfile.forModel(catalogModel);
            summary.append("\nAccelerators: ").append(profile.compatibleAccelerators.toString());
            if (profile.minDeviceMemoryInGb != null) {
                summary.append(" - minimum memory ").append(profile.minDeviceMemoryInGb).append(" GiB");
            }
        }
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

    private String join(JSONArray values) {
        if (values == null || values.length() == 0) return "none";
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            if (i > 0) joined.append(", ");
            joined.append(values.optString(i, ""));
        }
        return joined.toString();
    }

    private String buildInstalledModelSummary(TaiModelSpec model) {
        StringBuilder summary = new StringBuilder();
        summary.append(model.roleHint).append(" - ").append(model.source);
        summary.append("\nBackend: ").append(model.backend).append(" - ").append(model.format);
        if (model.quantization != null) summary.append(" - ").append(model.quantization);
        if (model.recommendedRamGb > 0) summary.append("\nRecommended memory: ").append(model.recommendedRamGb).append(" GiB");
        if (model.localPath != null) {
            summary.append("\nInstalled: ").append(formatBytes(model.sizeBytes));
            summary.append("\n").append(model.localPath);
        }
        summary.append("\nCapabilities: ").append(model.capabilities.toString());
        try {
            TaiModelProfile profile = TaiModelProfile.forModel(model);
            summary.append("\nAccelerators: ").append(profile.compatibleAccelerators.toString());
            if (profile.minDeviceMemoryInGb != null) {
                summary.append(" - minimum memory ").append(profile.minDeviceMemoryInGb).append(" GiB");
            }
            summary.append("\nDefaults: ").append(profile.defaultMaxTokens).append(" tokens, temperature ")
                .append(profile.defaultTemperature);
        } catch (Exception ignored) {
        }
        return summary.toString();
    }

    private void showModelActions(Context context, TaiModelCatalog.CatalogEntry entry, TaiModelSpec installed, JSONObject download) {
        if (download != null && ("queued".equals(download.optString("status")) || "running".equals(download.optString("status")))) {
            new MaterialAlertDialogBuilder(context)
                .setTitle(entry.displayName)
                .setMessage(buildModelSummary(entry, installed, download))
                .setPositiveButton(R.string.termux_ai_model_download_cancel, (dialog, which) -> cancelModelDownload(context, entry.modelId))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }
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

    private void cancelModelDownload(Context context, String modelId) {
        try {
            JSONObject result = TaiManager.getInstance(context).cancelDownload(
                new JSONObject().put("modelId", modelId).toString());
            Toast.makeText(context, result.optBoolean("ok", false)
                ? R.string.termux_ai_model_download_cancelled
                : R.string.termux_ai_model_action_failed, Toast.LENGTH_SHORT).show();
            refreshTaiPage(context);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_model_action_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void onModelDocumentSelected(Uri uri) {
        if (uri == null) return;
        Context context = getContext();
        if (context == null) return;
        TaiModelImporter.DocumentMetadata metadata =
            TaiManager.getInstance(context).modelDocumentMetadata(uri);
        if (!TaiModelImporter.isSupportedFileName(metadata.displayName)) {
            Toast.makeText(context, R.string.termux_ai_model_import_invalid_file, Toast.LENGTH_LONG).show();
            return;
        }
        showImportDialog(context, uri, metadata);
    }

    private void showImportDialog(Context context, Uri uri, TaiModelImporter.DocumentMetadata metadata) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, 0, padding, 0);

        EditText modelIdInput = new EditText(context);
        modelIdInput.setSingleLine(true);
        modelIdInput.setHint(R.string.termux_ai_model_import_id_hint);
        modelIdInput.setInputType(InputType.TYPE_CLASS_TEXT);
        modelIdInput.setText(TaiModelImporter.sanitizeModelId(
            TaiModelImporter.stripModelExtension(metadata.displayName)));
        modelIdInput.setSelectAllOnFocus(true);
        layout.addView(modelIdInput);

        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_model_import_title)
            .setMessage(context.getString(R.string.termux_ai_model_import_selected,
                metadata.displayName, metadata.sizeBytes > 0L ? formatBytes(metadata.sizeBytes) : "unknown size"))
            .setView(layout)
            .setPositiveButton(R.string.termux_ai_model_import_title, (dialog, which) ->
                importModelDocument(context, uri, modelIdInput.getText().toString()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void importModelDocument(Context context, Uri uri, String modelId) {
        Context appContext = context.getApplicationContext();
        Preference importPreference = findPreference("tai_model_import");
        if (importPreference != null) {
            importPreference.setEnabled(false);
            importPreference.setSummary(R.string.termux_ai_model_import_copying);
        }
        runtimeActionExecutor.execute(() -> {
            JSONObject result = null;
            try {
                result = TaiManager.getInstance(appContext).importModelDocument(uri, modelId);
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context currentContext = getContext();
                if (currentContext == null) return;
                Preference currentImportPreference = findPreference("tai_model_import");
                if (currentImportPreference != null) {
                    currentImportPreference.setEnabled(true);
                    currentImportPreference.setSummary(R.string.termux_ai_model_import_summary);
                }
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    Toast.makeText(currentContext, R.string.termux_ai_model_imported, Toast.LENGTH_SHORT).show();
                } else {
                    String message = finalResult == null
                        ? currentContext.getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", currentContext.getString(R.string.termux_ai_model_action_failed));
                    Toast.makeText(currentContext, message, Toast.LENGTH_LONG).show();
                }
                refreshTaiPage(currentContext);
            });
        });
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
