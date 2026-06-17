package com.termux.app.fragments.settings.termux;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.fragments.settings.StatusCardPreference;
import com.termux.ai.TaiDeviceCapabilities;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Keep
public class TaiPreferencesFragment extends MaterialPreferenceFragment {
    private static final String MODEL_ROW_PREFIX = "tai_model_row_";
    private static final String IMPORT_BACKEND_LITERT = TaiModelSpec.BACKEND_LITERT_LM;
    private static final String IMPORT_BACKEND_MNN = TaiModelSpec.BACKEND_MNN_LLM;

    private static final class OverrideSpec {
        final String key;
        final int titleRes;
        final int entriesRes;
        final int valuesRes;
        final String defaultValue;

        OverrideSpec(String key, int titleRes, int entriesRes, int valuesRes, String defaultValue) {
            this.key = key;
            this.titleRes = titleRes;
            this.entriesRes = entriesRes;
            this.valuesRes = valuesRes;
            this.defaultValue = defaultValue;
        }
    }

    private static final class ImportDraft {
        String backend = IMPORT_BACKEND_LITERT;
        String hfUrl = "";
        String hfToken = "";
        String modelId = "";
        Uri documentUri;
        TaiModelImporter.DocumentMetadata documentMetadata;
    }

    private static final OverrideSpec[] OVERRIDE_SPECS = {
        new OverrideSpec("tai_max_tokens", R.string.termux_ai_max_tokens_title,
            R.array.termux_ai_max_tokens_entries, R.array.termux_ai_max_tokens_values, "auto"),
        new OverrideSpec("tai_top_k", R.string.termux_ai_top_k_title,
            R.array.termux_ai_top_k_entries, R.array.termux_ai_top_k_values, "auto"),
        new OverrideSpec("tai_top_p", R.string.termux_ai_top_p_title,
            R.array.termux_ai_top_p_entries, R.array.termux_ai_top_p_values, "auto"),
        new OverrideSpec("tai_temperature", R.string.termux_ai_temperature_title,
            R.array.termux_ai_temperature_entries, R.array.termux_ai_temperature_values, "auto"),
        new OverrideSpec("tai_accelerator", R.string.termux_ai_accelerator_title,
            R.array.termux_ai_accelerator_entries, R.array.termux_ai_accelerator_values, "auto"),
        new OverrideSpec("tai_thinking", R.string.termux_ai_thinking_title,
            R.array.termux_ai_auto_boolean_entries, R.array.termux_ai_auto_boolean_values, "auto"),
        new OverrideSpec("tai_speculative_decoding", R.string.termux_ai_speculative_decoding_title,
            R.array.termux_ai_auto_boolean_entries, R.array.termux_ai_auto_boolean_values, "auto"),
        new OverrideSpec("tai_idle_unload_minutes", R.string.termux_ai_idle_unload_title,
            R.array.termux_ai_idle_unload_entries, R.array.termux_ai_idle_unload_values, "10"),
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        this::onModelDocumentSelected);
    private ImportDraft pendingImportDraft;
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
        SettingsLayoutUtils.applyScreenLayout(this);
        configureRuntimeControls(context);
        configureOverrides(context);
        configureEndpointPreferences(context);
        configureModelManager(context);
        configureHuggingFaceToken();
        configureAdvancedSection(context);
        configureLanToggle(context);
    }

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        Context context = getContext();
        String key = preference.getKey();
        if (context != null && key != null && preference instanceof EditTextPreference) {
            EditTextPreference editText = (EditTextPreference) preference;
            switch (key) {
                case TaiSettings.KEY_API_PORT:
                    showApiPortDialog(context, editText);
                    return;
                case TaiSettings.KEY_API_TOKEN:
                    showApiTokenDialog(context, editText);
                    return;
                case TaiSettings.KEY_SYSTEM_PROMPT_GENERAL:
                    showGeneralPromptDialog(context, editText);
                    return;
                case "tai_mnn_custom_download":
                    showMnnCustomDownloadDialog(context, editText);
                    return;
                default:
                    break;
            }
        }
        super.onDisplayPreferenceDialog(preference);
    }

    private void showGeneralPromptDialog(Context context, EditTextPreference preference) {
        EditText input = buildDialogEditText(context, preference.getText(), InputType.TYPE_CLASS_TEXT, true);
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_general_prompt_title)
            .setView(wrapDialogView(context, null, input))
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) ->
                preference.setText(input.getText().toString()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.termux_ai_preferences_title);
        }
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
        updateRuntimeStatus(context);
        refreshOverrides();
        refreshEndpointPreferences(context);
        populateModelRows(context);
        refreshDeviceEngineInfo(context);
        refreshLanToggle(context);
    }

    private void configureRuntimeControls(Context context) {
        TaiRuntimeActionsPreference actions = findPreference("tai_runtime_actions");
        if (actions == null) return;
        actions.setOnActionClickListener(new TaiRuntimeActionsPreference.OnActionClickListener() {
            @Override
            public void onStop() {
                cancelGeneration(context);
            }

            @Override
            public void onUnload() {
                unloadRuntime(context);
            }

            @Override
            public void onLogs() {
                showRuntimeLogs(context);
            }
        });
    }

    private void configureOverrides(Context context) {
        TaiOverridesPreference overrides = findPreference("tai_runtime_overrides");
        if (overrides == null) return;
        overrides.setOnOverrideClickListener(index -> showOverrideDialog(context, index));
        refreshOverrides();
    }

    private void refreshOverrides() {
        TaiOverridesPreference overrides = findPreference("tai_runtime_overrides");
        if (overrides == null) return;
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return;
        List<TaiOverridesPreference.Item> items = new ArrayList<>();
        for (OverrideSpec spec : OVERRIDE_SPECS) {
            String value = preferences.getString(spec.key, spec.defaultValue);
            items.add(new TaiOverridesPreference.Item(getString(spec.titleRes), overrideValueLabel(spec.key, value)));
        }
        overrides.setItems(items);
    }

    private String overrideValueLabel(String key, String value) {
        if (value == null || value.isEmpty()) return "auto";
        if ("tai_accelerator".equals(key) || TaiSettings.FIELD_ACCELERATOR.equals(key)) {
            return "auto".equals(value) ? "profile" : value;
        }
        if ("tai_idle_unload_minutes".equals(key)) {
            return "0".equals(value) ? "off" : value + " min";
        }
        if ("tai_thinking".equals(key) || "tai_speculative_decoding".equals(key)
            || TaiSettings.FIELD_ENABLE_THINKING.equals(key)
            || TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING.equals(key)) {
            if ("true".equals(value)) return "on";
            if ("false".equals(value)) return "off";
            return "auto";
        }
        return value;
    }

    private void showOverrideDialog(Context context, int index) {
        if (index < 0 || index >= OVERRIDE_SPECS.length) return;
        OverrideSpec spec = OVERRIDE_SPECS[index];
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return;
        String[] entries = getResources().getStringArray(spec.entriesRes);
        String[] values = getResources().getStringArray(spec.valuesRes);
        String current = preferences.getString(spec.key, spec.defaultValue);
        int checked = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(spec.titleRes)
            .setSingleChoiceItems(entries, checked, (dialog, which) -> {
                preferences.edit().putString(spec.key, values[which]).apply();
                dialog.dismiss();
                refreshOverrides();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void configureEndpointPreferences(Context context) {
        configureApiPortPreference(context);
        configureApiTokenPreference(context);
        Preference endpointCopy = findPreference("tai_endpoint_copy");
        if (endpointCopy != null) {
            endpointCopy.setOnPreferenceClickListener(preference -> {
                copyEndpointInfo(context);
                return true;
            });
        }
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
    }

    private void configureAdvancedSection(Context context) {
        Preference parameters = findPreference("tai_parameters_defaults");
        if (parameters != null) {
            parameters.setOnPreferenceClickListener(preference -> {
                openParameterScreen(null);
                return true;
            });
        }
    }

    private void configureApiPortPreference(Context context) {
        EditTextPreference port = findPreference(TaiSettings.KEY_API_PORT);
        if (port == null) return;
        port.setText(String.valueOf(new TaiSettings(context).getApiPort()));
    }

    private void configureApiTokenPreference(Context context) {
        EditTextPreference token = findPreference(TaiSettings.KEY_API_TOKEN);
        if (token == null) return;
        token.setText(new TaiSettings(context).getOrCreateApiToken());
    }

    private void showApiPortDialog(Context context, EditTextPreference preference) {
        String baseUrl = currentOpenAiBaseUrl(context);
        EditText input = buildDialogEditText(context, preference.getText(), InputType.TYPE_CLASS_NUMBER, false);
        LinearLayout view = wrapDialogView(context,
            getString(R.string.termux_ai_api_port_dialog_header, baseUrl), input);
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_api_port_title)
            .setView(view)
            .setNeutralButton(R.string.termux_ai_dialog_copy, (dialog, which) ->
                copyToClipboard(context, baseUrl, R.string.termux_ai_base_url_copied))
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) ->
                saveApiPort(context, preference, input.getText().toString()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void saveApiPort(Context context, EditTextPreference preference, String rawValue) {
        if (!TaiSettings.isValidApiPort(rawValue)) {
            Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
            return;
        }
        int normalized = TaiSettings.normalizeApiPort(rawValue);
        new TaiSettings(context).setApiPort(normalized);
        preference.setText(String.valueOf(normalized));
        try {
            LauncherCtlApiServer.getInstance().applyEndpointSettings(context);
            refreshEndpointPreferences(context);
            Toast.makeText(context, R.string.termux_ai_api_port_saved, Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showApiTokenDialog(Context context, EditTextPreference preference) {
        String currentToken = preference.getText();
        if (currentToken == null || currentToken.isEmpty()) {
            currentToken = new TaiSettings(context).getOrCreateApiToken();
        }
        EditText input = buildDialogEditText(context, currentToken,
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, false);
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_api_token_title)
            .setView(wrapDialogView(context, null, input))
            .setNeutralButton(R.string.termux_ai_dialog_copy, (dialog, which) ->
                copyToClipboard(context, input.getText().toString(), R.string.termux_ai_api_token_copied))
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) ->
                saveApiToken(context, preference, input.getText().toString()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void saveApiToken(Context context, EditTextPreference preference, String rawValue) {
        String normalized = TaiSettings.normalizeApiToken(rawValue);
        if (normalized.isEmpty()) {
            Toast.makeText(context, R.string.termux_ai_api_token_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        new TaiSettings(context).setApiToken(normalized);
        preference.setText(normalized);
        try {
            LauncherCtlApiServer.getInstance().applyEndpointSettings(context);
            refreshEndpointPreferences(context);
            Toast.makeText(context, R.string.termux_ai_api_token_saved, Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
        }
    }

    private String currentOpenAiBaseUrl(Context context) {
        try {
            return LauncherCtlApiServer.getInstance().endpointSettings(context).optString("openAiBaseUrl", "");
        } catch (JSONException e) {
            return "";
        }
    }

    private void copyToClipboard(Context context, String text, int toastResId) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Termux Launcher", text));
        Toast.makeText(context, toastResId, Toast.LENGTH_SHORT).show();
    }

    private void copyEndpointInfo(Context context) {
        try {
            JSONObject endpoint = LauncherCtlApiServer.getInstance().endpointSettings(context);
            String text = getString(R.string.termux_ai_endpoint_detail_message_dynamic,
                endpoint.optString("baseUrl", ""),
                endpoint.optString("openAiBaseUrl", ""),
                endpoint.optString("token", ""),
                endpoint.optString("endpointFile", "~/.launcherctl/endpoint"),
                endpoint.optString("tokenFile", "~/.launcherctl/token"));
            copyToClipboard(context, text, R.string.termux_ai_endpoint_copied);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showGlobalParametersDialog(Context context) {
        String[] entries = new String[OVERRIDE_SPECS.length];
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        for (int i = 0; i < OVERRIDE_SPECS.length; i++) {
            OverrideSpec spec = OVERRIDE_SPECS[i];
            String value = preferences == null ? spec.defaultValue : preferences.getString(spec.key, spec.defaultValue);
            entries[i] = getString(spec.titleRes) + "  ·  " + overrideValueLabel(spec.key,
                value);
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_parameters_defaults_title)
            .setItems(entries, (dialog, which) -> showOverrideDialog(context, which))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private EditText buildDialogEditText(Context context, String value, int inputType, boolean multiline) {
        EditText input = new EditText(context);
        input.setInputType(inputType | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setSingleLine(!multiline);
        if (multiline) {
            input.setMinLines(3);
            input.setGravity(Gravity.TOP | Gravity.START);
        }
        if (value != null) {
            input.setText(value);
            input.setSelection(value.length());
        }
        return input;
    }

    private LinearLayout wrapDialogView(Context context, CharSequence header, View input) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(24 * density);
        layout.setPadding(padH, Math.round(8 * density), padH, 0);
        if (header != null) {
            TextView headerView = new TextView(context);
            headerView.setText(header);
            headerView.setTextIsSelectable(true);
            headerView.setTypeface(Typeface.MONOSPACE);
            headerView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            headerView.setPadding(0, 0, 0, Math.round(12 * density));
            layout.addView(headerView);
        }
        layout.addView(input);
        return layout;
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
            Preference endpointCopy = findPreference("tai_endpoint_copy");
            if (endpointCopy != null) {
                endpointCopy.setSummary(getString(R.string.termux_ai_endpoint_notice_summary_dynamic,
                    endpoint.optString("openAiBaseUrl", ""),
                    endpoint.optString("tokenFile", "~/.launcherctl/token")));
            }
        } catch (JSONException e) {
            // Endpoint settings unavailable; leave existing summaries in place.
        }
    }

    private void refreshDeviceEngineInfo(Context context) {
        TaiDeviceCapabilities capabilities = TaiDeviceCapabilities.detect(context);
        Preference info = findPreference("tai_device_engine_info");
        if (info == null) return;
        String backend = "none";
        String model = new TaiSettings(context).getDefaultAssistantModel();
        try {
            JSONObject runtime = TaiManager.getInstance(context).runtimeStatus().getJSONObject("runtime");
            backend = runtime.optString("backend", backend);
            model = runtime.optString("loadedModelId", model);
        } catch (JSONException ignored) {
        }
        String mnn = capabilities.mnnSupported
            ? getString(R.string.termux_ai_mnn_support_status_supported)
            : getString(R.string.termux_ai_mnn_support_status_unsupported);
        String reason = capabilities.mnnSupported || capabilities.mnnUnsupportedReason == null
            ? ""
            : "\n" + capabilities.mnnUnsupportedReason;
        info.setSummary(getString(R.string.termux_ai_device_engine_info_summary,
            model, backend, mnn) + reason);
    }

    private void configureLanToggle(Context context) {
        SwitchPreferenceCompat lanToggle = findPreference("tai_lan_enabled");
        if (lanToggle == null) return;
        lanToggle.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = (Boolean) newValue;
            if (enabled) {
                showLanWarningDialog(context, lanToggle);
                return false;
            }
            new TaiSettings(context).setApiBindMode(TaiSettings.BIND_MODE_LOCALHOST);
            try {
                LauncherCtlApiServer.getInstance().applyEndpointSettings(context);
                refreshEndpointPreferences(context);
            } catch (JSONException e) {
                Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
            }
            return true;
        });
    }

    private void refreshLanToggle(Context context) {
        SwitchPreferenceCompat lanToggle = findPreference("tai_lan_enabled");
        if (lanToggle == null) return;
        String bindMode = new TaiSettings(context).getApiBindMode();
        lanToggle.setChecked(TaiSettings.BIND_MODE_LAN.equals(bindMode));
    }

    private void showLanWarningDialog(Context context, SwitchPreferenceCompat lanToggle) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_lan_warning_title)
            .setMessage(R.string.termux_ai_lan_warning_message)
            .setPositiveButton(R.string.termux_ai_dialog_enable, (dialog, which) -> {
                lanToggle.setChecked(true);
                new TaiSettings(context).setApiBindMode(TaiSettings.BIND_MODE_LAN);
                try {
                    LauncherCtlApiServer.getInstance().applyEndpointSettings(context);
                    refreshEndpointPreferences(context);
                } catch (JSONException e) {
                    Toast.makeText(context, R.string.termux_ai_endpoint_update_failed, Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showMnnCustomDownloadDialog(Context context, EditTextPreference preference) {
        EditText input = buildDialogEditText(context, preference.getText(),
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, false);

        TextView warning = new TextView(context);
        warning.setText(R.string.termux_ai_mnn_custom_download_warning);
        warning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        int padH = Math.round(24 * context.getResources().getDisplayMetrics().density);
        warning.setPadding(0, Math.round(12 * context.getResources().getDisplayMetrics().density), 0, 0);

        LinearLayout layout = wrapDialogView(context, null, input);
        layout.addView(warning);

        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_mnn_custom_download_title)
            .setView(layout)
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) -> {
                String url = input.getText().toString().trim();
                if (!url.startsWith("https://")) {
                    Toast.makeText(context, R.string.termux_ai_mnn_custom_download_invalid_url, Toast.LENGTH_LONG).show();
                    return;
                }
                startMnnCustomDownload(context, url);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void startMnnCustomDownload(Context context, String url) {
        String modelId = deriveModelIdFromUrl(url);
        runtimeActionExecutor.execute(() -> {
            JSONObject result = null;
            try {
                JSONObject request = new JSONObject();
                request.put("modelId", modelId);
                request.put("url", url);
                request.put("acceptedTerms", true);
                JSONArray capabilities = new JSONArray();
                capabilities.put(TaiModelSpec.CAPABILITY_TEXT_CHAT);
                request.put("capabilities", capabilities);
                result = TaiManager.getInstance(context.getApplicationContext()).downloadModel(request.toString());
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context currentContext = getContext();
                if (currentContext == null) return;
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    Toast.makeText(currentContext, R.string.termux_ai_model_download_started, Toast.LENGTH_SHORT).show();
                    handler.removeCallbacks(refreshRuntimeRunnable);
                    handler.postDelayed(refreshRuntimeRunnable, 1000L);
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

    private String deriveModelIdFromUrl(String url) {
        try {
            String lastSegment = Uri.parse(url).getLastPathSegment();
            if (lastSegment != null && !lastSegment.isEmpty()) {
                String sanitized = TaiModelImporter.sanitizeModelId(
                    TaiModelImporter.stripModelExtension(lastSegment));
                if (!sanitized.isEmpty()) return sanitized;
            }
        } catch (Exception ignored) {
        }
        return "custom-mnn-model";
    }

    private String buildModelRowSummary(String baseSummary, String backend, java.util.Set<String> capabilities) {
        String backendLabel;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) {
            backendLabel = getString(R.string.termux_ai_backend_label_mnn);
        } else {
            backendLabel = getString(R.string.termux_ai_backend_label_litert);
        }
        StringBuilder capBuilder = new StringBuilder();
        for (String cap : capabilities) {
            if (capBuilder.length() > 0) capBuilder.append(", ");
            if (TaiModelSpec.CAPABILITY_TEXT_CHAT.equals(cap)) {
                capBuilder.append(getString(R.string.termux_ai_capability_chat));
            } else if (TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS.equals(cap)) {
                capBuilder.append(getString(R.string.termux_ai_capability_embeddings));
            } else {
                capBuilder.append(cap);
            }
        }
        return baseSummary + " · [" + backendLabel + "] · " + capBuilder.toString();
    }

    private void updateRuntimeStatus(Context context) {
        Preference status = findPreference("tai_runtime_status");
        TaiRuntimeActionsPreference actions = findPreference("tai_runtime_actions");
        if (status == null && actions == null) return;
        try {
            JSONObject runtimeStatus = TaiManager.getInstance(context).runtimeStatus();
            JSONObject runtime = runtimeStatus.getJSONObject("runtime");
            boolean activeGeneration = runtime.optBoolean("activeGeneration", false);
            boolean loaded = runtime.optBoolean("loaded", false);
            String state = runtime.optString("state", "unloaded");
            boolean loading = "loading".equals(state);
            boolean stopping = "stopping".equals(state);
            if (status != null) {
                if (status instanceof StatusCardPreference) {
                    ((StatusCardPreference) status).setStatus(
                        "RUNTIME · " + state.toUpperCase(Locale.US), loaded || activeGeneration);
                }
                status.setSummary(buildRuntimeCardBody(context, runtime, runtimeStatus));
            }
            if (actions != null) {
                actions.setActionStates(
                    activeGeneration || loading,
                    (loaded || loading) && !activeGeneration && !stopping,
                    true);
            }
        } catch (JSONException e) {
            if (status != null) status.setSummary(R.string.termux_ai_runtime_status_summary);
        }
    }

    private String getDefaultAssistantDisplayName(Context context) {
        String modelId = new TaiSettings(context).getDefaultAssistantModel();
        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            if (entry.modelId.equals(modelId)) return entry.displayName;
        }
        TaiModelSpec spec = new TaiModelStore(context).getUserModels().get(modelId);
        return spec != null ? spec.displayName : modelId;
    }

    private String buildRuntimeCardBody(Context context, JSONObject runtime, JSONObject runtimeStatus) {
        StringBuilder body = new StringBuilder();
        JSONObject device = runtimeStatus.optJSONObject("device");
        if (device != null) {
            StringBuilder deviceLine = new StringBuilder(device.optString("model", "unknown"));
            if (!device.isNull("memoryGiB")) {
                deviceLine.append(" · ").append(String.format(Locale.US, "%.1f GiB", device.optDouble("memoryGiB")));
            }
            appendKv(body, "device", deviceLine.toString());
            appendKv(body, "accel", join(device.optJSONArray("phase1Accelerators")));
        }
        appendKv(body, "model", nullable(runtime, "loadedModelId", "none"));
        appendKv(body, "backend", runtime.optString("backend", "none"));
        String fallback = nullable(runtime, "backendFallbackReason", "");
        if (!fallback.isEmpty()) appendKv(body, "fallback", fallback);
        if (runtime.optBoolean("activeGeneration", false)) appendKv(body, "generate", "active");
        long keepWarmRemaining = runtime.optLong("keepWarmRemainingMs", 0L);
        if (keepWarmRemaining > 0L) appendKv(body, "warm", formatDuration(keepWarmRemaining));
        long idleRemaining = runtime.optLong("idleUnloadRemainingMs", 0L);
        if (idleRemaining > 0L) appendKv(body, "idle", formatDuration(idleRemaining));
        String statusMessage = runtime.optString("status", "");
        if (!statusMessage.isEmpty()) appendKv(body, "status", statusMessage);
        JSONObject profile = runtimeStatus.optJSONObject("modelProfile");
        if (profile != null) {
            StringBuilder compat = new StringBuilder(join(profile.optJSONArray("compatibleAccelerators")));
            if (!profile.isNull("minDeviceMemoryInGb")) {
                compat.append(" · min ").append(profile.optInt("minDeviceMemoryInGb")).append(" GiB");
            }
            appendKv(body, "compat", compat.toString());
        }
        JSONArray warnings = runtimeStatus.optJSONArray("compatibilityWarnings");
        if (warnings != null) {
            for (int i = 0; i < warnings.length(); i++) {
                String warning = warnings.optString(i, "");
                if (!warning.isEmpty()) appendKv(body, "warning", warning);
            }
        }
        try {
            JSONObject endpoint = LauncherCtlApiServer.getInstance().endpointSettings(context);
            String baseUrl = endpoint.optString("openAiBaseUrl", "");
            String token = endpoint.optString("token", "");
            if (!baseUrl.isEmpty()) appendKv(body, "endpoint", baseUrl);
            if (!token.isEmpty()) appendKv(body, "token", token);
        } catch (JSONException ignored) {
        }
        return body.toString().trim();
    }

    private void appendKv(StringBuilder builder, String key, String value) {
        builder.append(String.format(Locale.US, "%-9s", key)).append(value).append('\n');
    }

    private void configureModelManager(Context context) {
        Preference browseCatalog = findPreference("tai_models_browse_catalog");
        if (browseCatalog != null) {
            browseCatalog.setSummary(R.string.termux_ai_models_browse_catalog_summary);
        }

        Preference importModel = findPreference("tai_model_import");
        if (importModel != null) {
            importModel.setOnPreferenceClickListener(preference -> {
                showImportFlowDialog(context, new ImportDraft());
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
        Preference token = findPreference(TaiSettings.KEY_HUGGINGFACE_TOKEN);
        if (token == null) return;
        updateHuggingFaceTokenSummary(token);
        token.setOnPreferenceClickListener(preference -> {
            Context context = getContext();
            if (context == null) return true;
            showHuggingFaceTokenDialog(context, token);
            return true;
        });
    }

    private void updateHuggingFaceTokenSummary(@NonNull Preference preference) {
        String value = preference.getContext()
            .getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TaiSettings.KEY_HUGGINGFACE_TOKEN, "");
        preference.setSummary(value == null || value.trim().isEmpty()
            ? getString(R.string.termux_ai_huggingface_token_summary)
            : getString(R.string.termux_ai_huggingface_token_set_summary));
    }

    private void showHuggingFaceTokenDialog(Context context, Preference preference) {
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, 0, padding, 0);

        TextView message = new TextView(context);
        message.setText(R.string.termux_ai_huggingface_token_dialog_message);
        layout.addView(message);

        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.termux_ai_huggingface_token_title);
        input.setText(context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(TaiSettings.KEY_HUGGINGFACE_TOKEN, ""));
        input.setSelectAllOnFocus(true);
        layout.addView(input);

        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_huggingface_token_title)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(TaiSettings.KEY_HUGGINGFACE_TOKEN, input.getText().toString().trim())
                    .apply();
                updateHuggingFaceTokenSummary(preference);
                Toast.makeText(context, R.string.termux_ai_huggingface_token_saved, Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton(R.string.termux_ai_huggingface_token_get_action,
                (dialog, which) -> openUrl(context, "https://huggingface.co/settings/tokens"))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
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
        Preference empty = findPreference("tai_models_empty");
        if (empty != null) empty.setVisible(installedModels.isEmpty());
        String activeModelId = new TaiSettings(context).getDefaultAssistantModel();

        for (TaiModelSpec model : installedModels.values()) {
            TaiModelPreference row = new TaiModelPreference(context);
            row.setKey(MODEL_ROW_PREFIX + model.id);
            row.setTitle(model.displayName);
            row.setSummary(buildModelRowSummary(model.roleHint + " · " + model.source, model.backend, model.capabilities));
            row.setMetaLine(buildInstalledMetaLine(context, model));
row.setPill(model.id.equals(activeModelId) ? getString(R.string.termux_ai_model_pill_active) : null,
                model.id.equals(activeModelId));
            row.setBackendTone(TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend)
                ? TaiModelPreference.BackendTone.MNN : TaiModelPreference.BackendTone.LITERT);
            configureProgress(row, null);
            row.setPersistent(false);
            row.setOnPreferenceClickListener(preference -> {
                showInstalledModelActions(context, model);
                return true;
            });
            category.addPreference(row);
        }
    }

    private void configureModelPill(TaiModelPreference row, TaiModelSpec installed, JSONObject download, boolean gated) {
        if (installed != null && installed.localPath != null) {
            row.setPill(getString(R.string.termux_ai_model_pill_installed), true);
            return;
        }
        if (download != null) {
            String status = download.optString("status", "");
            if (TaiModelStore.STATE_QUEUED.equals(status)
                || TaiModelStore.STATE_DOWNLOADING.equals(status)
                || TaiModelStore.STATE_VERIFYING.equals(status)) {
                row.setPill(getString(R.string.termux_ai_model_pill_downloading), false);
                return;
            }
        }
        if (gated) {
            row.setPill(getString(R.string.termux_ai_model_pill_gated), false);
            return;
        }
        row.setPill(null, false);
    }

    private CharSequence buildCatalogMetaLine(Context context, TaiModelCatalog.CatalogEntry entry) {
        String accel = null;
        Integer minMemGb = entry.recommendedRamGb > 0 ? entry.recommendedRamGb : null;
        try {
            TaiModelSpec catalogModel = new TaiModelRegistry().getModel(entry.modelId);
            if (catalogModel != null && TaiModelSpec.BACKEND_LITERT_LM.equals(entry.backend)) {
                TaiModelProfile profile = TaiModelProfile.forModel(catalogModel);
                accel = joinAccelerators(profile.compatibleAccelerators);
                if (profile.minDeviceMemoryInGb != null) minMemGb = profile.minDeviceMemoryInGb;
            }
        } catch (Exception ignored) {
        }
        return buildMetaLine(context, entry.sizeBytes, accel, minMemGb);
    }

    private CharSequence buildInstalledMetaLine(Context context, TaiModelSpec model) {
        String accel = null;
        Integer minMemGb = model.recommendedRamGb > 0 ? model.recommendedRamGb : null;
        try {
            TaiModelProfile profile = TaiModelProfile.forModel(model);
            accel = joinAccelerators(profile.compatibleAccelerators);
            if (profile.minDeviceMemoryInGb != null) minMemGb = profile.minDeviceMemoryInGb;
        } catch (Exception ignored) {
        }
        return buildMetaLine(context, model.sizeBytes, accel, minMemGb);
    }

    private CharSequence buildMetaLine(Context context, long sizeBytes, String accel, Integer minMemGb) {
        int keyColor = resolveAttrColor(context, com.termux.shared.R.attr.termuxColorOnSurfaceVariant);
        int valueColor = resolveAttrColor(context, com.termux.shared.R.attr.termuxColorOnSurface);
        SpannableStringBuilder builder = new SpannableStringBuilder();
        appendMeta(builder, "size ", formatBytes(sizeBytes), keyColor, valueColor);
        if (accel != null && !accel.isEmpty()) {
            builder.append("   ");
            appendMeta(builder, "accel ", accel, keyColor, valueColor);
        }
        if (minMemGb != null) {
            builder.append("   ");
            appendMeta(builder, "min mem ", minMemGb + " GiB", keyColor, valueColor);
        }
        return builder;
    }

    private void appendMeta(SpannableStringBuilder builder, String key, String value, int keyColor, int valueColor) {
        int start = builder.length();
        builder.append(key);
        builder.setSpan(new ForegroundColorSpan(keyColor), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        start = builder.length();
        builder.append(value);
        builder.setSpan(new ForegroundColorSpan(valueColor), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String joinAccelerators(List<String> accelerators) {
        if (accelerators == null || accelerators.isEmpty()) return null;
        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < accelerators.size(); i++) {
            if (i > 0) joined.append(" · ");
            joined.append(String.valueOf(accelerators.get(i)).toLowerCase(Locale.US));
        }
        return joined.toString();
    }

    private int resolveAttrColor(Context context, int attr) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, value, true)) {
            return value.data;
        }
        if (context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, value, true)) {
            return value.data;
        }
        return 0;
    }

    private void configureProgress(TaiModelPreference row, JSONObject download) {
        if (download == null) {
            row.setDownloadProgress(false, false, 0);
            return;
        }
        String status = download.optString("status", "");
        boolean active = TaiModelStore.STATE_QUEUED.equals(status)
            || TaiModelStore.STATE_DOWNLOADING.equals(status)
            || TaiModelStore.STATE_VERIFYING.equals(status);
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
        if (catalogModel != null && TaiModelSpec.BACKEND_LITERT_LM.equals(entry.backend)) {
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
        if (download != null && (TaiModelStore.STATE_QUEUED.equals(download.optString("status"))
            || TaiModelStore.STATE_DOWNLOADING.equals(download.optString("status"))
            || TaiModelStore.STATE_VERIFYING.equals(download.optString("status")))) {
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
        boolean active = model.id.equals(new TaiSettings(context).getDefaultAssistantModel());
        CharSequence[] actions = new CharSequence[] {
            active ? getString(R.string.termux_ai_model_active_action) : getString(R.string.termux_ai_model_set_active_action),
            getString(R.string.termux_ai_model_tune_action),
            getString(R.string.termux_ai_model_delete_action)
        };
        new MaterialAlertDialogBuilder(context)
            .setTitle(model.displayName)
            .setMessage(buildInstalledModelSummary(model))
            .setItems(actions, (dialog, which) -> {
                if (which == 0 && !active) setActiveModel(context, model.id);
                else if (which == 1) openParameterScreen(model);
                else if (which == 2) confirmDeleteModel(context, model);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void openParameterScreen(@Nullable TaiModelSpec model) {
        TaiParameterPreferencesFragment fragment = new TaiParameterPreferencesFragment();
        if (model != null) fragment.setArguments(TaiParameterPreferencesFragment.argumentsForModel(model));
        getParentFragmentManager().beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit();
    }

    private void showCatalogBrowser(Context context) {
        ArrayList<TaiModelCatalog.CatalogEntry> entries = new ArrayList<>(TaiModelCatalog.entries().values());
        CharSequence[] labels = new CharSequence[entries.size()];
        TaiModelStore store = new TaiModelStore(context);
        Map<String, TaiModelSpec> installed = store.getUserModels();
        JSONArray downloads = store.getDownloads();
        for (int i = 0; i < entries.size(); i++) {
            TaiModelCatalog.CatalogEntry entry = entries.get(i);
            JSONObject download = findDownload(downloads, entry.modelId);
            String state = installed.containsKey(entry.modelId)
                ? getString(R.string.termux_ai_model_pill_installed)
                : download == null ? getString(R.string.termux_ai_model_catalog_not_installed) : download.optString("status", "");
            labels[i] = entry.displayName + " · " + formatBytes(entry.sizeBytes) + " · " + state;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_models_browse_catalog_title)
            .setItems(labels, (dialog, which) -> {
                TaiModelCatalog.CatalogEntry entry = entries.get(which);
                TaiModelSpec installedSpec = new TaiModelStore(context).getUserModels().get(entry.modelId);
                if (installedSpec != null) {
                    showInstalledModelActions(context, installedSpec);
                } else {
                    showModelActions(context, entry, null, findDownload(new TaiModelStore(context).getDownloads(), entry.modelId));
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void setActiveModel(Context context, String modelId) {
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return;
        preferences.edit().putString(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, modelId).apply();
        Toast.makeText(context, R.string.termux_ai_model_active_saved, Toast.LENGTH_SHORT).show();
        refreshTaiPage(context);
    }

    private void confirmDeleteModel(Context context, TaiModelSpec model) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.termux_ai_model_delete_title, model.displayName))
            .setMessage(R.string.termux_ai_model_delete_message)
            .setPositiveButton(R.string.termux_ai_model_delete_action, (dialog, which) -> deleteModel(context, model.id))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
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
                for (String option : spec.options) values.add(option);
            }
            String[] labels = values.toArray(new String[0]);
            String current = currentModelParameterValue(model.id, spec.field);
            int checked = 0;
            for (int i = 0; i < labels.length; i++) {
                if (labels[i].equalsIgnoreCase(current)) {
                    checked = i;
                    break;
                }
            }
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

        EditText input = buildDialogEditText(context, currentModelParameterValue(model.id, spec.field),
            InputType.TYPE_CLASS_NUMBER | (spec.fallbackValue instanceof Double
                ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0), false);
        String range = spec.minValue == null || spec.maxValue == null
            ? null
            : getString(R.string.termux_ai_model_tune_range, spec.minValue, spec.maxValue);
        new MaterialAlertDialogBuilder(context)
            .setTitle(parameterLabel(spec.field))
            .setView(wrapDialogView(context, range, input))
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

    private void showRuntimeLogs(Context context) {
        try {
            String status = TaiManager.getInstance(context).runtimeStatus().toString(2);
            new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.termux_ai_runtime_logs_title)
                .setMessage(status)
                .setPositiveButton(R.string.termux_ai_dialog_copy, (dialog, which) ->
                    copyToClipboard(context, status, R.string.termux_ai_runtime_logs_copied))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_runtime_action_failed, Toast.LENGTH_LONG).show();
        }
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
        ImportDraft draft = pendingImportDraft == null ? new ImportDraft() : pendingImportDraft;
        TaiModelImporter.DocumentMetadata metadata =
            TaiManager.getInstance(context).modelDocumentMetadata(uri);
        TaiModelImporter.ValidationResult validation =
            TaiModelImporter.validateImportFileNameForBackend(draft.backend, metadata.displayName);
        if (!validation.supported) {
            Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show();
            return;
        }
        draft.documentUri = uri;
        draft.documentMetadata = metadata;
        if (draft.modelId == null || draft.modelId.trim().isEmpty()) {
            draft.modelId = TaiModelImporter.sanitizeModelId(
                TaiModelImporter.stripModelExtension(metadata.displayName));
        }
        pendingImportDraft = draft;
        showImportFlowDialog(context, draft);
    }

    private void showImportFlowDialog(Context context, ImportDraft draft) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(padding, 0, padding, 0);

        RadioGroup backendGroup = new RadioGroup(context);
        backendGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton litert = new RadioButton(context);
        litert.setText(R.string.termux_ai_backend_label_litert);
        litert.setId(View.generateViewId());
        RadioButton mnn = new RadioButton(context);
        mnn.setText(R.string.termux_ai_backend_label_mnn);
        mnn.setId(View.generateViewId());
        backendGroup.addView(litert);
        backendGroup.addView(mnn);
        backendGroup.check(IMPORT_BACKEND_MNN.equals(draft.backend) ? mnn.getId() : litert.getId());
        layout.addView(backendGroup);

        EditText urlInput = new EditText(context);
        urlInput.setSingleLine(true);
        urlInput.setHint(R.string.termux_ai_model_import_hf_url_hint);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(draft.hfUrl == null ? "" : draft.hfUrl);
        layout.addView(urlInput);

        EditText tokenInput = new EditText(context);
        tokenInput.setSingleLine(true);
        tokenInput.setHint(R.string.termux_ai_model_import_hf_token_hint);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setText(draft.hfToken == null ? "" : draft.hfToken);
        layout.addView(tokenInput);

        EditText modelIdInput = new EditText(context);
        modelIdInput.setSingleLine(true);
        modelIdInput.setHint(R.string.termux_ai_model_import_id_hint);
        modelIdInput.setInputType(InputType.TYPE_CLASS_TEXT);
        modelIdInput.setText(draft.modelId == null ? "" : draft.modelId);
        modelIdInput.setSelectAllOnFocus(true);
        layout.addView(modelIdInput);

        TextView selectedFile = new TextView(context);
        selectedFile.setText(importSelectionText(context, draft));
        selectedFile.setPadding(0, padding / 2, 0, 0);
        layout.addView(selectedFile);

        backendGroup.setOnCheckedChangeListener((group, checkedId) -> {
            draft.backend = checkedId == mnn.getId() ? IMPORT_BACKEND_MNN : IMPORT_BACKEND_LITERT;
            selectedFile.setText(importSelectionText(context, draft));
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_model_import_dialog_title)
            .setMessage(R.string.termux_ai_model_import_dialog_message)
            .setView(layout)
            .setPositiveButton(R.string.termux_ai_model_import_verify_action, null)
            .setNeutralButton(R.string.termux_ai_model_import_choose_file, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show();
        Button positive = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        positive.setOnClickListener(view -> {
            draft.backend = backendGroup.getCheckedRadioButtonId() == mnn.getId() ? IMPORT_BACKEND_MNN : IMPORT_BACKEND_LITERT;
            draft.hfUrl = urlInput.getText().toString().trim();
            draft.hfToken = tokenInput.getText().toString();
            draft.modelId = modelIdInput.getText().toString().trim();
            if (startImportDraft(context, draft)) dialog.dismiss();
        });
        Button neutral = dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL);
        neutral.setOnClickListener(view -> {
            draft.backend = backendGroup.getCheckedRadioButtonId() == mnn.getId() ? IMPORT_BACKEND_MNN : IMPORT_BACKEND_LITERT;
            draft.hfUrl = urlInput.getText().toString().trim();
            draft.hfToken = tokenInput.getText().toString();
            draft.modelId = modelIdInput.getText().toString().trim();
            pendingImportDraft = draft;
            modelPicker.launch(new String[]{"application/octet-stream", "application/json", "*/*"});
            dialog.dismiss();
        });
    }

    private CharSequence importSelectionText(Context context, ImportDraft draft) {
        String backend = IMPORT_BACKEND_MNN.equals(draft.backend)
            ? getString(R.string.termux_ai_backend_label_mnn)
            : getString(R.string.termux_ai_backend_label_litert);
        if (draft.documentMetadata == null) {
            return context.getString(R.string.termux_ai_model_import_no_file_selected, backend);
        }
        TaiModelImporter.DocumentMetadata metadata = draft.documentMetadata;
        return context.getString(R.string.termux_ai_model_import_selected,
            metadata.displayName, metadata.sizeBytes > 0L ? formatBytes(metadata.sizeBytes) : "unknown size")
            + "\nBackend: " + backend;
    }

    private boolean startImportDraft(Context context, ImportDraft draft) {
        boolean hasUrl = draft.hfUrl != null && !draft.hfUrl.trim().isEmpty();
        boolean hasFile = draft.documentUri != null && draft.documentMetadata != null;
        if (hasUrl == hasFile) {
            Toast.makeText(context, R.string.termux_ai_model_import_choose_one_source, Toast.LENGTH_LONG).show();
            return false;
        }
        if (hasUrl) {
            TaiModelImporter.ValidationResult validation =
                TaiModelImporter.validateHuggingFaceImportUrl(draft.backend, draft.hfUrl);
            if (!validation.supported) {
                Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show();
                return false;
            }
            startHuggingFaceImport(context, draft);
            return true;
        }
        TaiModelImporter.ValidationResult validation = TaiModelImporter.validateImportFileNameForBackend(
            draft.backend, draft.documentMetadata.displayName);
        if (!validation.supported) {
            Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show();
            return false;
        }
        importModelDocument(context, draft.documentUri, draft.modelId, draft.backend);
        pendingImportDraft = null;
        return true;
    }

    private void startHuggingFaceImport(Context context, ImportDraft draft) {
        String modelId = TaiModelImporter.sanitizeModelId(draft.modelId == null || draft.modelId.trim().isEmpty()
            ? deriveModelIdFromUrl(draft.hfUrl) : draft.modelId);
        if (modelId.isEmpty()) {
            Toast.makeText(context, R.string.termux_ai_model_import_invalid_model_id, Toast.LENGTH_LONG).show();
            return;
        }
        runtimeActionExecutor.execute(() -> {
            JSONObject result = null;
            try {
                JSONObject request = new JSONObject();
                request.put("modelId", modelId);
                request.put("displayName", modelId);
                request.put("url", draft.hfUrl);
                request.put("acceptedTerms", true);
                request.put("backend", draft.backend);
                request.put("format", IMPORT_BACKEND_MNN.equals(draft.backend)
                    ? TaiModelSpec.FORMAT_MNN : TaiModelSpec.FORMAT_LITERTLM);
                JSONArray capabilities = new JSONArray();
                capabilities.put(TaiModelSpec.CAPABILITY_TEXT_CHAT);
                request.put("capabilities", capabilities);
                if (draft.hfToken != null && !draft.hfToken.trim().isEmpty()) {
                    request.put("huggingFaceToken", draft.hfToken.trim());
                }
                result = TaiManager.getInstance(context.getApplicationContext()).downloadModel(request.toString());
            } catch (JSONException | RuntimeException ignored) {
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                Context currentContext = getContext();
                if (currentContext == null) return;
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    Toast.makeText(currentContext, R.string.termux_ai_model_download_started, Toast.LENGTH_SHORT).show();
                    handler.removeCallbacks(refreshRuntimeRunnable);
                    handler.postDelayed(refreshRuntimeRunnable, 1000L);
                } else {
                    String message = finalResult == null
                        ? currentContext.getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", currentContext.getString(R.string.termux_ai_model_action_failed));
                    Toast.makeText(currentContext, message, Toast.LENGTH_LONG).show();
                }
                pendingImportDraft = null;
                refreshTaiPage(currentContext);
            });
        });
    }

    private void importModelDocument(Context context, Uri uri, String modelId, String backend) {
        Context appContext = context.getApplicationContext();
        Preference importPreference = findPreference("tai_model_import");
        if (importPreference != null) {
            importPreference.setEnabled(false);
            importPreference.setSummary(R.string.termux_ai_model_import_copying);
        }
        runtimeActionExecutor.execute(() -> {
            JSONObject result = null;
            try {
                result = new TaiModelImporter(appContext, new TaiModelStore(appContext))
                    .importDocument(uri, modelId, backend);
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
            request.put("confirm", true);
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
            if (TaiModelStore.STATE_QUEUED.equals(status)
                || TaiModelStore.STATE_DOWNLOADING.equals(status)
                || TaiModelStore.STATE_VERIFYING.equals(status)) return true;
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
