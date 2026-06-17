package com.termux.app.fragments.settings.termux;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelImporter;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Keep
public class TaiModelCatalogPreferencesFragment extends MaterialPreferenceFragment {
    private static final String DYNAMIC_GROUP_PREFIX = "tai_catalog_group_";
    private static final LinkedHashSet<String> ALLOWED_CAPABILITY_TAGS = new LinkedHashSet<>(Arrays.asList(
        "Text", "Code", "Vision", "Audio", "Reasoning", "Tools", "Multilingual"));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-catalog-settings");
        thread.setDaemon(true);
        return thread;
    });
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            Context context = getContext();
            if (context == null) return;
            refreshCatalogRows(context);
            if (hasActiveDownloads(context)) handler.postDelayed(this, 2000L);
        }
    };
    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(), this::onModelDocumentSelected);

    private BackendFilter backendFilter = BackendFilter.ALL;
    private String searchQuery = "";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(TaiSettings.PREFS_NAME);
        setPreferencesFromResource(R.xml.termux_ai_model_catalog_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        configureControls(context);
        refreshCatalogRows(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context != null) {
            refreshCatalogRows(context);
            if (hasActiveDownloads(context)) handler.postDelayed(refreshRunnable, 2000L);
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        actionExecutor.shutdownNow();
        super.onDestroy();
    }

    private void configureControls(Context context) {
        ListPreference backend = findPreference("tai_catalog_backend_filter");
        if (backend != null) {
            backend.setValue(backendFilter.value);
            backend.setSummaryProvider(preference -> backend.getEntry());
            backend.setOnPreferenceChangeListener((preference, newValue) -> {
                backendFilter = BackendFilter.fromValue(String.valueOf(newValue));
                refreshCatalogRows(context);
                return true;
            });
        }
        EditTextPreference search = findPreference("tai_catalog_search");
        if (search != null) {
            search.setText(searchQuery);
            search.setOnPreferenceChangeListener((preference, newValue) -> {
                searchQuery = newValue == null ? "" : String.valueOf(newValue).trim();
                refreshCatalogRows(context);
                return true;
            });
        }
        Preference importModel = findPreference("tai_catalog_import");
        if (importModel != null) {
            importModel.setOnPreferenceClickListener(preference -> {
                modelPicker.launch(new String[]{"application/octet-stream", "application/zip", "*/*"});
                return true;
            });
        }
    }

    private void refreshCatalogRows(Context context) {
        PreferenceCategory results = findPreference("tai_catalog_results_category");
        if (results == null) return;
        results.removeAll();

        TaiModelStore store = new TaiModelStore(context);
        Map<String, TaiModelSpec> installed = store.getUserModels();
        JSONArray downloads = store.getDownloads();
        String activeModelId = new TaiSettings(context).getDefaultAssistantModel();
        TaiDeviceCapabilities capabilities = TaiDeviceCapabilities.detect(context);

        Map<String, List<TaiModelCatalog.CatalogEntry>> groups = groupByJobGroup(
            filterEntries(TaiModelCatalog.entries().values(), backendFilter, searchQuery));
        if (groups.isEmpty()) {
            Preference empty = new Preference(context);
            empty.setTitle(R.string.termux_ai_catalog_empty_title);
            empty.setSummary(R.string.termux_ai_catalog_empty_summary);
            empty.setSelectable(false);
            results.addPreference(empty);
            return;
        }

        for (Map.Entry<String, List<TaiModelCatalog.CatalogEntry>> group : groups.entrySet()) {
            PreferenceCategory category = new PreferenceCategory(context);
            category.setKey(DYNAMIC_GROUP_PREFIX + group.getKey());
            category.setTitle(labelForJobGroup(group.getKey()));
            results.addPreference(category);
            for (TaiModelCatalog.CatalogEntry entry : group.getValue()) {
                category.addPreference(buildRow(context, entry, installed.get(entry.modelId),
                    findDownload(downloads, entry.modelId), activeModelId, capabilities));
            }
        }
    }

    private TaiModelPreference buildRow(Context context, TaiModelCatalog.CatalogEntry entry,
                                       @Nullable TaiModelSpec installed, @Nullable JSONObject download,
                                       String activeModelId, TaiDeviceCapabilities capabilities) {
        TaiModelPreference row = new TaiModelPreference(context);
        row.setKey("tai_catalog_model_" + entry.modelId);
        row.setTitle((entry.recommended ? "★ " : "") + entry.displayName + "  [" + backendLabel(entry.backend) + "]");
        row.setSummary(buildSummary(entry));
        row.setMetaLine(buildMetaLine(entry));
        CatalogActionState state = actionStateFor(entry, installed != null, download, activeModelId,
            capabilities.mlcSupported, capabilities.mlcUnsupportedReason);
        row.setPill(state.pill, state.accentPill);
        row.setBackendTone(TaiModelSpec.BACKEND_MLC_LLM.equals(entry.backend)
            ? TaiModelPreference.BackendTone.MLC : TaiModelPreference.BackendTone.LITERT);
        configureProgress(row, download);
        row.setTuneAction(installed == null ? null : getString(R.string.termux_ai_model_tune_action),
            view -> openParameterScreen(installed));
        row.setPrimaryAction(actionText(context, state), state.enabled, state.type == CatalogActionType.ACTIVE,
            view -> handlePrimaryAction(context, entry, installed, state));
        row.setPersistent(false);
        row.setOnPreferenceClickListener(preference -> {
            showCatalogDetails(context, entry, installed, download, state);
            return true;
        });
        return row;
    }

    private void handlePrimaryAction(Context context, TaiModelCatalog.CatalogEntry entry,
                                     @Nullable TaiModelSpec installed, CatalogActionState state) {
        switch (state.type) {
            case INSTALL:
                confirmStartCatalogDownload(context, entry);
                break;
            case DOWNLOADING:
                cancelModelDownload(context, entry.modelId);
                break;
            case INSTALLED:
                setActiveModel(context, entry.modelId);
                break;
            case ACTIVE:
                if (installed != null) confirmDeleteModel(context, installed);
                break;
            case IMPORT_ONLY:
                showImportOnlyGuidance(context, entry);
                break;
            default:
                break;
        }
    }

    private void showCatalogDetails(Context context, TaiModelCatalog.CatalogEntry entry,
                                    @Nullable TaiModelSpec installed, @Nullable JSONObject download,
                                    CatalogActionState state) {
        StringBuilder message = new StringBuilder();
        message.append(buildSummary(entry)).append('\n').append(buildMetaLine(entry));
        message.append("\n\nID: ").append(entry.modelId);
        message.append("\nProvider: ").append(entry.repositoryId);
        if (!entry.downloadAvailable) message.append("\n\n").append(entry.unavailableReason);
        if (download != null) message.append("\n\nDownload: ").append(download.optString("status", "unknown"));
        if (CatalogActionType.MLC_UNSUPPORTED == state.type && state.disabledReason != null) {
            message.append("\n\n").append(state.disabledReason);
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(entry.displayName)
            .setMessage(message.toString())
            .setNeutralButton(R.string.termux_ai_model_open_provider, (dialog, which) -> openUrl(context, entry.providerPageUrl))
            .setNegativeButton(android.R.string.cancel, null);
        if (installed != null) {
            builder.setPositiveButton(R.string.termux_ai_model_tune_action, (dialog, which) -> openParameterScreen(installed));
        }
        builder.show();
    }

    private void confirmStartCatalogDownload(Context context, TaiModelCatalog.CatalogEntry entry) {
        if (!entry.downloadAvailable) {
            showImportOnlyGuidance(context, entry);
            return;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.termux_ai_model_download_title, entry.displayName))
            .setMessage(context.getString(R.string.termux_ai_model_download_message,
                entry.displayName, formatBytes(entry.sizeBytes), entry.license))
            .setPositiveButton(R.string.termux_ai_model_download_start, (dialog, which) -> startCatalogDownload(context, entry))
            .setNeutralButton(R.string.termux_ai_model_open_provider, (dialog, which) -> openUrl(context, entry.providerPageUrl))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showImportOnlyGuidance(Context context, TaiModelCatalog.CatalogEntry entry) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_catalog_import_only_title)
            .setMessage(context.getString(R.string.termux_ai_catalog_import_only_message,
                entry.displayName, entry.unavailableReason))
            .setPositiveButton(R.string.termux_ai_model_open_provider, (dialog, which) -> openUrl(context, entry.providerPageUrl))
            .setNeutralButton(R.string.termux_ai_dialog_copy, (dialog, which) -> copyToClipboard(context, entry.providerPageUrl))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private String actionText(Context context, CatalogActionState state) {
        switch (state.type) {
            case INSTALL:
                return getString(R.string.termux_ai_catalog_action_install);
            case DOWNLOADING:
                return getString(R.string.termux_ai_catalog_action_downloading, state.progressLabel);
            case INSTALLED:
                return getString(R.string.termux_ai_catalog_action_installed);
            case ACTIVE:
                return getString(R.string.termux_ai_catalog_action_active);
            case IMPORT_ONLY:
                return getString(R.string.termux_ai_catalog_action_import_only);
            case MLC_UNSUPPORTED:
                return getString(R.string.termux_ai_catalog_action_mlc_unsupported);
            default:
                return "";
        }
    }

    private String buildSummary(TaiModelCatalog.CatalogEntry entry) {
        return entry.roleHint + " · " + entry.modelId;
    }

    private String buildMetaLine(TaiModelCatalog.CatalogEntry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append("size ").append(!entry.sizeEstimate.isEmpty() ? entry.sizeEstimate : formatBytes(entry.sizeBytes));
        if (!entry.ramTier.isEmpty()) builder.append("   min-RAM ").append(entry.ramTier);
        if (entry.quantization != null && !entry.quantization.isEmpty()) builder.append("   quant ").append(entry.quantization);
        String tags = joinTags(displayTags(entry));
        if (!tags.isEmpty()) builder.append("   tags ").append(tags);
        return builder.toString();
    }

    static List<TaiModelCatalog.CatalogEntry> filterEntries(Iterable<TaiModelCatalog.CatalogEntry> entries,
                                                            BackendFilter filter, @Nullable String query) {
        ArrayList<TaiModelCatalog.CatalogEntry> result = new ArrayList<>();
        String normalizedQuery = normalize(query);
        for (TaiModelCatalog.CatalogEntry entry : entries) {
            if (filter != BackendFilter.ALL && !filter.backend.equals(entry.backend)) continue;
            if (!normalizedQuery.isEmpty() && !matchesSearch(entry, normalizedQuery)) continue;
            result.add(entry);
        }
        return result;
    }

    static Map<String, List<TaiModelCatalog.CatalogEntry>> groupByJobGroup(List<TaiModelCatalog.CatalogEntry> entries) {
        LinkedHashMap<String, List<TaiModelCatalog.CatalogEntry>> groups = new LinkedHashMap<>();
        for (TaiModelCatalog.CatalogEntry entry : entries) {
            String key = entry.jobGroup == null || entry.jobGroup.isEmpty() ? "other" : entry.jobGroup;
            List<TaiModelCatalog.CatalogEntry> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(entry);
        }
        return groups;
    }

    static CatalogActionState actionStateFor(TaiModelCatalog.CatalogEntry entry, boolean installed,
                                             @Nullable JSONObject download, @Nullable String activeModelId,
                                             boolean mlcSupported, @Nullable String mlcUnsupportedReason) {
        if (installed) {
            boolean active = entry.modelId.equals(activeModelId);
            return active
                ? CatalogActionState.of(CatalogActionType.ACTIVE, "● Active", true, true, null, "")
                : CatalogActionState.of(CatalogActionType.INSTALLED, "Installed ✓", true, true, null, "");
        }
        if (download != null && isActiveDownload(download.optString("status", ""))) {
            return CatalogActionState.of(CatalogActionType.DOWNLOADING, "Downloading", false, true,
                null, progressLabel(download));
        }
        if (TaiModelSpec.BACKEND_MLC_LLM.equals(entry.backend) && !mlcSupported) {
            String reason = mlcUnsupportedReason == null || mlcUnsupportedReason.isEmpty()
                ? "MLC backend is not supported on this device." : mlcUnsupportedReason;
            return CatalogActionState.of(CatalogActionType.MLC_UNSUPPORTED, "MLC blocked", false, false,
                reason, "");
        }
        if (!entry.downloadAvailable) {
            return CatalogActionState.of(CatalogActionType.IMPORT_ONLY, "Import only", false, true,
                entry.unavailableReason, "");
        }
        return CatalogActionState.of(CatalogActionType.INSTALL, null, false, true, null, "");
    }

    static LinkedHashSet<String> displayTags(TaiModelCatalog.CatalogEntry entry) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String tag : entry.displayCapabilityTags) {
            if (ALLOWED_CAPABILITY_TAGS.contains(tag)) tags.add(tag);
        }
        return tags;
    }

    private static boolean matchesSearch(TaiModelCatalog.CatalogEntry entry, String query) {
        if (contains(entry.displayName, query) || contains(entry.modelId, query) || contains(entry.backend, query)
            || contains(entry.jobGroup, query)) return true;
        for (String tag : entry.displayCapabilityTags) if (contains(tag, query)) return true;
        return false;
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US).replace('_', '-');
    }

    private static boolean contains(@Nullable String value, String query) {
        return value != null && value.toLowerCase(Locale.US).replace('_', '-').contains(query);
    }

    private static boolean isActiveDownload(String status) {
        return TaiModelStore.STATE_QUEUED.equals(status)
            || TaiModelStore.STATE_DOWNLOADING.equals(status)
            || TaiModelStore.STATE_VERIFYING.equals(status);
    }

    private static String progressLabel(@Nullable JSONObject download) {
        if (download == null) return "…";
        long total = download.optLong("totalBytes", 0L);
        if (total <= 0L) return "…";
        return String.format(Locale.US, "%.0f%%", Math.max(0d, Math.min(100d,
            download.optLong("bytesRead", 0L) * 100d / total)));
    }

    @Nullable
    private static JSONObject findDownload(JSONArray downloads, String modelId) {
        if (downloads == null) return null;
        for (int i = downloads.length() - 1; i >= 0; i--) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null && modelId.equals(item.optString("modelId", ""))) return item;
        }
        return null;
    }

    private void configureProgress(TaiModelPreference row, @Nullable JSONObject download) {
        if (download == null || !isActiveDownload(download.optString("status", ""))) {
            row.setDownloadProgress(false, false, 0);
            return;
        }
        long bytesRead = download.optLong("bytesRead", 0L);
        long totalBytes = download.optLong("totalBytes", 0L);
        row.setDownloadProgress(true, totalBytes <= 0L,
            totalBytes > 0L ? (int) (bytesRead * 10000L / totalBytes) : 0);
    }

    private void startCatalogDownload(Context context, TaiModelCatalog.CatalogEntry entry) {
        try {
            JSONObject result = TaiManager.getInstance(context).downloadCatalogModel(entry.modelId);
            Toast.makeText(context, result.optBoolean("ok", false)
                ? R.string.termux_ai_model_download_started : R.string.termux_ai_model_action_failed,
                Toast.LENGTH_SHORT).show();
            refreshCatalogRows(context);
            handler.removeCallbacks(refreshRunnable);
            handler.postDelayed(refreshRunnable, 1000L);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_model_action_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void cancelModelDownload(Context context, String modelId) {
        try {
            JSONObject result = TaiManager.getInstance(context).cancelDownload(
                new JSONObject().put("modelId", modelId).toString());
            Toast.makeText(context, result.optBoolean("ok", false)
                ? R.string.termux_ai_model_download_cancelled : R.string.termux_ai_model_action_failed,
                Toast.LENGTH_SHORT).show();
            refreshCatalogRows(context);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_model_action_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void setActiveModel(Context context, String modelId) {
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return;
        preferences.edit().putString(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, modelId).apply();
        Toast.makeText(context, R.string.termux_ai_model_active_saved, Toast.LENGTH_SHORT).show();
        refreshCatalogRows(context);
    }

    private void confirmDeleteModel(Context context, TaiModelSpec model) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.termux_ai_model_delete_title, model.displayName))
            .setMessage(R.string.termux_ai_model_delete_message)
            .setPositiveButton(R.string.termux_ai_model_delete_action, (dialog, which) -> deleteModel(context, model.id))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void deleteModel(Context context, String modelId) {
        try {
            JSONObject request = new JSONObject();
            request.put("modelId", modelId);
            request.put("confirm", true);
            JSONObject result = TaiManager.getInstance(context).deleteModel(request.toString());
            Toast.makeText(context,
                result.optBoolean("deleted", false) ? R.string.termux_ai_model_deleted : R.string.termux_ai_model_delete_missing,
                Toast.LENGTH_SHORT).show();
            refreshCatalogRows(context);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_model_action_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void openParameterScreen(@NonNull TaiModelSpec model) {
        TaiParameterPreferencesFragment fragment = new TaiParameterPreferencesFragment();
        fragment.setArguments(TaiParameterPreferencesFragment.argumentsForModel(model));
        getParentFragmentManager().beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit();
    }

    private void showTuneModelDialog(Context context, TaiModelSpec model) {
        TaiSettings.ParameterSchema schema = TaiSettings.getParameterSchema(model.backend);
        ArrayList<TaiSettings.ParameterSpec> specs = new ArrayList<>(schema.fields().values());
        CharSequence[] labels = new CharSequence[specs.size() + 1];
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        for (int i = 0; i < specs.size(); i++) {
            TaiSettings.ParameterSpec spec = specs.get(i);
            String value = preferences == null ? "auto" : preferences.getString(modelParameterKey(model.id, spec.field), "auto");
            labels[i] = parameterLabel(spec.field) + "  ·  " + overrideValueLabel(spec.field, value);
        }
        labels[specs.size()] = getString(R.string.termux_ai_model_tune_reset_action);
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.termux_ai_model_tune_title, model.displayName))
            .setItems(labels, (dialog, which) -> {
                if (which == specs.size()) {
                    new TaiSettings(context).resetModelParametersToGlobal(model.id);
                    Toast.makeText(context, R.string.termux_ai_model_tune_reset_done, Toast.LENGTH_SHORT).show();
                } else {
                    showTuneParameterDialog(context, model, specs.get(which));
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showTuneParameterDialog(Context context, TaiModelSpec model, TaiSettings.ParameterSpec spec) {
        if (spec.options.length > 0 || spec.fallbackValue instanceof Boolean) {
            ArrayList<String> values = new ArrayList<>();
            values.add("auto");
            if (spec.fallbackValue instanceof Boolean) {
                values.add("true");
                values.add("false");
            } else {
                values.addAll(Arrays.asList(spec.options));
            }
            String[] labels = values.toArray(new String[0]);
            String current = currentModelParameterValue(model.id, spec.field);
            int checked = 0;
            for (int i = 0; i < labels.length; i++) if (labels[i].equalsIgnoreCase(current)) checked = i;
            new MaterialAlertDialogBuilder(context)
                .setTitle(parameterLabel(spec.field))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    saveModelParameter(context, model.id, spec, labels[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | (spec.fallbackValue instanceof Double
            ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        input.setSingleLine(true);
        input.setText(currentModelParameterValue(model.id, spec.field));
        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(context);
        layout.setPadding(padding, 0, padding, 0);
        layout.addView(input);
        new MaterialAlertDialogBuilder(context)
            .setTitle(parameterLabel(spec.field))
            .setView(layout)
            .setNeutralButton(R.string.termux_ai_model_tune_reset_one_action, (dialog, which) ->
                saveModelParameter(context, model.id, spec, "auto"))
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) ->
                saveModelParameter(context, model.id, spec, input.getText().toString()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private String currentModelParameterValue(String modelId, String field) {
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        return preferences == null ? "auto" : preferences.getString(modelParameterKey(modelId, field), "auto");
    }

    private void saveModelParameter(Context context, String modelId, TaiSettings.ParameterSpec spec, String rawValue) {
        String value = rawValue == null ? "auto" : rawValue.trim();
        TaiSettings settings = new TaiSettings(context);
        if (value.isEmpty() || "auto".equalsIgnoreCase(value)) {
            settings.resetModelParameterToGlobal(modelId, spec.field);
            Toast.makeText(context, R.string.termux_ai_model_tune_saved, Toast.LENGTH_SHORT).show();
            return;
        }
        Object parsed = spec.parse(value);
        if (parsed == null) {
            Toast.makeText(context, R.string.termux_ai_model_tune_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        settings.setModelParameter(modelId, spec.field, parsed);
        Toast.makeText(context, R.string.termux_ai_model_tune_saved, Toast.LENGTH_SHORT).show();
    }

    private void onModelDocumentSelected(Uri uri) {
        if (uri == null) return;
        Context context = getContext();
        if (context == null) return;
        TaiModelImporter.DocumentMetadata metadata = TaiManager.getInstance(context).modelDocumentMetadata(uri);
        if (!TaiModelImporter.isSupportedFileName(metadata.displayName)) {
            Toast.makeText(context, R.string.termux_ai_model_import_invalid_file, Toast.LENGTH_LONG).show();
            return;
        }
        showImportDialog(context, uri, metadata);
    }

    private void showImportDialog(Context context, Uri uri, TaiModelImporter.DocumentMetadata metadata) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * context.getResources().getDisplayMetrics().density);
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
        actionExecutor.execute(() -> {
            JSONObject result = null;
            try {
                result = TaiManager.getInstance(appContext).importModelDocument(uri, modelId);
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context currentContext = getContext();
                if (currentContext == null) return;
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    Toast.makeText(currentContext, R.string.termux_ai_model_imported, Toast.LENGTH_SHORT).show();
                } else {
                    String message = finalResult == null
                        ? currentContext.getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", currentContext.getString(R.string.termux_ai_model_action_failed));
                    Toast.makeText(currentContext, message, Toast.LENGTH_LONG).show();
                }
                refreshCatalogRows(currentContext);
            });
        });
    }

    private boolean hasActiveDownloads(Context context) {
        JSONArray downloads = new TaiModelStore(context).getDownloads();
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null && isActiveDownload(item.optString("status", ""))) return true;
        }
        return false;
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("TAI provider", text));
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    private void openUrl(Context context, String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(context, url, Toast.LENGTH_LONG).show();
        }
    }

    private String backendLabel(String backend) {
        if (TaiModelSpec.BACKEND_MLC_LLM.equals(backend)) return getString(R.string.termux_ai_backend_label_mlc);
        return getString(R.string.termux_ai_backend_label_litert);
    }

    private static String labelForJobGroup(String jobGroup) {
        String[] parts = jobGroup.split("_");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (label.length() > 0) label.append(' ');
            label.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return label.length() == 0 ? "Other" : label.toString();
    }

    private static String joinTags(LinkedHashSet<String> tags) {
        StringBuilder builder = new StringBuilder();
        for (String tag : tags) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(tag);
        }
        return builder.toString();
    }

    private String modelParameterKey(String modelId, String field) {
        return "tai_model_parameter." + modelId + "." + field;
    }

    private String parameterLabel(String field) {
        if (TaiSettings.FIELD_MAX_TOKENS.equals(field)) return getString(R.string.termux_ai_max_tokens_title);
        if (TaiSettings.FIELD_TOP_K.equals(field)) return getString(R.string.termux_ai_top_k_title);
        if (TaiSettings.FIELD_TOP_P.equals(field)) return getString(R.string.termux_ai_top_p_title);
        if (TaiSettings.FIELD_TEMPERATURE.equals(field)) return getString(R.string.termux_ai_temperature_title);
        if (TaiSettings.FIELD_ACCELERATOR.equals(field)) return getString(R.string.termux_ai_accelerator_title);
        if (TaiSettings.FIELD_ENABLE_THINKING.equals(field)) return getString(R.string.termux_ai_thinking_title);
        if (TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING.equals(field)) return getString(R.string.termux_ai_speculative_decoding_title);
        if (TaiSettings.FIELD_CONTEXT_WINDOW.equals(field)) return getString(R.string.termux_ai_context_window_title);
        return field;
    }

    private String overrideValueLabel(String key, String value) {
        if (value == null || value.isEmpty()) return "auto";
        if (TaiSettings.FIELD_ACCELERATOR.equals(key)) return "auto".equals(value) ? "profile" : value;
        if (TaiSettings.FIELD_ENABLE_THINKING.equals(key) || TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING.equals(key)) {
            if ("true".equals(value)) return "on";
            if ("false".equals(value)) return "off";
            return "auto";
        }
        return value;
    }

    private static String formatBytes(long bytes) {
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

    enum BackendFilter {
        ALL("all", ""), LITERT(TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.BACKEND_LITERT_LM), MLC(TaiModelSpec.BACKEND_MLC_LLM, TaiModelSpec.BACKEND_MLC_LLM);
        final String value;
        final String backend;
        BackendFilter(String value, String backend) { this.value = value; this.backend = backend; }
        static BackendFilter fromValue(String value) {
            for (BackendFilter filter : values()) if (filter.value.equals(value)) return filter;
            return ALL;
        }
    }

    enum CatalogActionType { INSTALL, DOWNLOADING, INSTALLED, ACTIVE, IMPORT_ONLY, MLC_UNSUPPORTED }

    static final class CatalogActionState {
        final CatalogActionType type;
        @Nullable final String pill;
        final boolean accentPill;
        final boolean enabled;
        @Nullable final String disabledReason;
        final String progressLabel;

        private CatalogActionState(CatalogActionType type, @Nullable String pill, boolean accentPill,
                                   boolean enabled, @Nullable String disabledReason, String progressLabel) {
            this.type = type;
            this.pill = pill;
            this.accentPill = accentPill;
            this.enabled = enabled;
            this.disabledReason = disabledReason;
            this.progressLabel = progressLabel;
        }

        static CatalogActionState of(CatalogActionType type, @Nullable String pill, boolean accentPill,
                                     boolean enabled, @Nullable String disabledReason, String progressLabel) {
            return new CatalogActionState(type, pill, accentPill, enabled, disabledReason, progressLabel);
        }
    }
}
