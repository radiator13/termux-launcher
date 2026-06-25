package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelImporter;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Keep
public class TaiModelImportFragment extends MaterialPreferenceFragment {
    private static final String SOURCE_HF = "hf";
    private static final String SOURCE_FILE = "file";

    private String source = SOURCE_HF;
    private Uri documentUri;
    private TaiModelImporter.DocumentMetadata documentMetadata;

    private Preference hfSource;
    private Preference fileSource;
    private EditTextPreference hfUrl;
    private EditTextPreference modelId;
    private Preference token;
    private Preference file;
    private SwitchPreferenceCompat chat;
    private SwitchPreferenceCompat embeddings;
    private SwitchPreferenceCompat media;
    private Preference status;
    private Preference action;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String[]> modelPicker = registerForActivityResult(
        new ActivityResultContracts.OpenDocument(),
        this::onModelDocumentSelected);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(TaiSettings.PREFS_NAME);
        PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
        setPreferenceScreen(screen);

        buildSource(context, screen);
        buildDetails(context, screen);
        buildCapabilities(context, screen);
        buildAction(context, screen);
        updateUi(context);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.termux_ai_model_import_title);
        Context context = getContext();
        if (context != null) updateUi(context);
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildSource(@NonNull Context context, @NonNull PreferenceScreen screen) {
        PreferenceCategory category = category(context, R.string.termux_ai_import_source_title);
        screen.addPreference(category);

        hfSource = row(context, R.string.termux_ai_import_source_hf_title, R.string.termux_ai_import_source_hf_summary);
        hfSource.setOnPreferenceClickListener(preference -> {
            source = SOURCE_HF;
            applyGuess(hfUrl.getText(), null);
            updateUi(context);
            return true;
        });
        category.addPreference(hfSource);

        fileSource = row(context, R.string.termux_ai_import_source_file_title, R.string.termux_ai_import_source_file_summary);
        fileSource.setOnPreferenceClickListener(preference -> {
            source = SOURCE_FILE;
            applyGuess("", documentMetadata == null ? null : documentMetadata.displayName);
            updateUi(context);
            return true;
        });
        category.addPreference(fileSource);
    }

    private void buildDetails(@NonNull Context context, @NonNull PreferenceScreen screen) {
        PreferenceCategory category = category(context, R.string.termux_ai_import_details_title);
        screen.addPreference(category);

        hfUrl = new EditTextPreference(context);
        hfUrl.setKey("tai_import_hf_url");
        hfUrl.setTitle(R.string.termux_ai_import_hf_url_title);
        hfUrl.setSummary(R.string.termux_ai_import_hf_url_summary);
        hfUrl.setPersistent(false);
        hfUrl.setOnBindEditTextListener(editText -> {
            editText.setSingleLine(true);
            editText.setHint(R.string.termux_ai_import_hf_url_summary);
            editText.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        });
        hfUrl.setOnPreferenceChangeListener((preference, value) -> {
            applyGuess(String.valueOf(value), null);
            updateUi(context);
            return true;
        });
        category.addPreference(hfUrl);

        token = row(context, R.string.termux_ai_huggingface_token_title, R.string.termux_ai_import_hf_token_row_unset);
        token.setOnPreferenceClickListener(preference -> {
            promptHuggingFaceToken(context);
            return true;
        });
        category.addPreference(token);

        file = row(context, R.string.termux_ai_import_local_file_title, R.string.termux_ai_import_local_file_empty);
        file.setOnPreferenceClickListener(preference -> {
            modelPicker.launch(new String[]{"application/octet-stream", "application/json", "*/*"});
            return true;
        });
        category.addPreference(file);

        modelId = new EditTextPreference(context);
        modelId.setKey("tai_import_model_id");
        modelId.setTitle(R.string.termux_ai_model_import_id_hint);
        modelId.setSummary(R.string.termux_ai_model_import_id_hint);
        modelId.setPersistent(false);
        modelId.setOnBindEditTextListener(editText -> {
            editText.setSingleLine(true);
            editText.setInputType(InputType.TYPE_CLASS_TEXT);
            editText.setSelectAllOnFocus(true);
        });
        category.addPreference(modelId);
    }

    private void buildCapabilities(@NonNull Context context, @NonNull PreferenceScreen screen) {
        PreferenceCategory category = category(context, R.string.termux_ai_import_advanced_title);
        screen.addPreference(category);

        Preference help = row(context, R.string.termux_ai_import_advanced_title, R.string.termux_ai_import_cap_summary);
        help.setSelectable(false);
        category.addPreference(help);

        chat = capability(context, R.string.termux_ai_import_cap_chat);
        embeddings = capability(context, R.string.termux_ai_import_cap_embeddings);
        media = capability(context, R.string.termux_ai_import_cap_media);
        category.addPreference(chat);
        category.addPreference(embeddings);
        category.addPreference(media);
    }

    private void buildAction(@NonNull Context context, @NonNull PreferenceScreen screen) {
        PreferenceCategory category = category(context, R.string.termux_ai_model_import_title);
        screen.addPreference(category);

        status = row(context, R.string.termux_ai_model_import_title, R.string.termux_ai_import_status_empty);
        status.setSelectable(false);
        category.addPreference(status);

        action = row(context, R.string.termux_ai_import_action, R.string.termux_ai_import_status_empty);
        action.setOnPreferenceClickListener(preference -> {
            startImport(context);
            return true;
        });
        category.addPreference(action);
    }

    private PreferenceCategory category(@NonNull Context context, int title) {
        PreferenceCategory category = new PreferenceCategory(context);
        category.setTitle(title);
        category.setIconSpaceReserved(false);
        return category;
    }

    private Preference row(@NonNull Context context, int title, int summary) {
        Preference preference = new Preference(context);
        preference.setTitle(title);
        preference.setSummary(summary);
        preference.setPersistent(false);
        preference.setIconSpaceReserved(false);
        return preference;
    }

    private SwitchPreferenceCompat capability(@NonNull Context context, int title) {
        SwitchPreferenceCompat preference = new SwitchPreferenceCompat(context);
        preference.setTitle(title);
        preference.setPersistent(false);
        preference.setIconSpaceReserved(false);
        preference.setOnPreferenceChangeListener((p, value) -> {
            updateStatus(context);
            return true;
        });
        return preference;
    }

    private void updateUi(@NonNull Context context) {
        boolean hf = SOURCE_HF.equals(source);
        hfSource.setSummary((hf ? "Selected. " : "") + getString(R.string.termux_ai_import_source_hf_summary));
        fileSource.setSummary((hf ? "" : "Selected. ") + getString(R.string.termux_ai_import_source_file_summary));
        hfUrl.setVisible(hf);
        token.setVisible(hf);
        file.setVisible(!hf);
        token.setSummary(new TaiSettings(context).getHuggingFaceToken().trim().isEmpty()
            ? getString(R.string.termux_ai_import_hf_token_row_unset)
            : getString(R.string.termux_ai_import_hf_token_row_set));
        updateFileSummary(context);
        updateStatus(context);
    }

    private void updateFileSummary(@NonNull Context context) {
        if (documentMetadata == null) {
            file.setSummary(R.string.termux_ai_import_local_file_empty);
            return;
        }
        file.setSummary(getString(R.string.termux_ai_import_local_file_selected,
            documentMetadata.displayName,
            documentMetadata.sizeBytes > 0L ? formatBytes(documentMetadata.sizeBytes) : "unknown size"));
    }

    private void updateStatus(@NonNull Context context) {
        if (SOURCE_HF.equals(source) && (hfUrl.getText() == null || hfUrl.getText().trim().isEmpty())) {
            status.setSummary(R.string.termux_ai_import_status_empty);
            action.setSummary(R.string.termux_ai_import_action);
            return;
        }
        if (SOURCE_FILE.equals(source) && documentMetadata == null) {
            status.setSummary(R.string.termux_ai_import_status_empty);
            action.setSummary(R.string.termux_ai_import_action);
            return;
        }
        Guess guess = currentGuess();
        if (!guess.error.isEmpty()) {
            status.setSummary(getString(R.string.termux_ai_import_status_error, guess.error));
            action.setSummary(guess.error);
            return;
        }
        status.setSummary(getString(R.string.termux_ai_import_status_ready,
            guess.backendLabel(), guess.capabilityLabel()));
        action.setSummary(R.string.termux_ai_import_action);
    }

    private void onModelDocumentSelected(@Nullable Uri uri) {
        if (uri == null) return;
        Context context = getContext();
        if (context == null) return;
        TaiModelImporter.DocumentMetadata metadata = TaiManager.getInstance(context).modelDocumentMetadata(uri);
        String name = metadata.displayName == null ? "" : metadata.displayName;
        TaiModelImporter.ValidationResult validation =
            TaiModelImporter.validateImportFileNameForBackend(backendForFile(name), name);
        if (!validation.supported) {
            Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show();
            return;
        }
        source = SOURCE_FILE;
        documentUri = uri;
        documentMetadata = metadata;
        if (modelId.getText() == null || modelId.getText().trim().isEmpty()) {
            modelId.setText(TaiModelImporter.sanitizeModelId(TaiModelImporter.stripModelExtension(name)));
        }
        applyGuess("", name);
        updateUi(context);
    }

    private void applyGuess(@Nullable String url, @Nullable String fileName) {
        Guess guess = Guess.from(url, fileName);
        chat.setChecked(guess.chat);
        embeddings.setChecked(guess.embeddings);
        media.setChecked(guess.media);
    }

    private Guess currentGuess() {
        String url = hfUrl.getText() == null ? "" : hfUrl.getText().trim();
        String fileName = documentMetadata == null ? "" : documentMetadata.displayName;
        Guess guess = Guess.from(SOURCE_HF.equals(source) ? url : "", SOURCE_FILE.equals(source) ? fileName : "");
        guess.chat = chat.isChecked();
        guess.embeddings = embeddings.isChecked();
        guess.media = media.isChecked();
        if (!guess.chat && !guess.embeddings && !guess.media) {
            guess.error = getString(R.string.termux_ai_import_no_capability);
        }
        return guess;
    }

    private void startImport(@NonNull Context context) {
        if (SOURCE_HF.equals(source)) startHuggingFaceImport(context);
        else startLocalImport(context);
    }

    private void startHuggingFaceImport(@NonNull Context context) {
        String url = hfUrl.getText() == null ? "" : hfUrl.getText().trim();
        if (url.isEmpty()) {
            Toast.makeText(context, R.string.termux_ai_import_missing_hf_url, Toast.LENGTH_LONG).show();
            return;
        }
        TaiModelImporter.ValidationResult validation = TaiModelImporter.validateHuggingFaceImportUrl(url);
        if (!validation.supported) {
            Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show();
            return;
        }
        LinkedHashSet<String> capabilities = selectedCapabilities();
        if (capabilities.isEmpty()) {
            Toast.makeText(context, R.string.termux_ai_import_no_capability, Toast.LENGTH_LONG).show();
            return;
        }
        String id = resolvedModelId(url, "");
        setBusy(true);
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            JSONObject result = null;
            try {
                JSONObject request = new JSONObject();
                request.put("modelId", id);
                request.put("displayName", id);
                request.put("url", url);
                request.put("acceptedTerms", true);
                request.put("capabilities", jsonArray(capabilities));
                String hfToken = new TaiSettings(appContext).getHuggingFaceToken();
                if (!hfToken.trim().isEmpty()) request.put("huggingFaceToken", hfToken.trim());
                result = TaiManager.getInstance(appContext).downloadModel(request.toString());
            } catch (JSONException | RuntimeException ignored) {
            }
            postResult(result, R.string.termux_ai_model_download_started);
        });
    }

    private void startLocalImport(@NonNull Context context) {
        if (documentUri == null || documentMetadata == null) {
            Toast.makeText(context, R.string.termux_ai_import_missing_file, Toast.LENGTH_LONG).show();
            return;
        }
        LinkedHashSet<String> capabilities = selectedCapabilities();
        if (capabilities.isEmpty()) {
            Toast.makeText(context, R.string.termux_ai_import_no_capability, Toast.LENGTH_LONG).show();
            return;
        }
        String backend = backendForFile(documentMetadata.displayName);
        TaiModelImporter.ValidationResult validation =
            TaiModelImporter.validateImportFileNameForBackend(backend, documentMetadata.displayName);
        if (!validation.supported) {
            Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show();
            return;
        }
        String id = resolvedModelId("", documentMetadata.displayName);
        setBusy(true);
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            JSONObject result = null;
            try {
                result = new TaiModelImporter(appContext, new TaiModelStore(appContext))
                    .importDocument(documentUri, id, backend, capabilities);
            } catch (JSONException | RuntimeException ignored) {
            }
            postResult(result, R.string.termux_ai_model_imported);
        });
    }

    private void postResult(@Nullable JSONObject result, int successMessage) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            Context context = getContext();
            if (context == null) return;
            setBusy(false);
            if (result != null && result.optBoolean("ok", false)) {
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show();
                if (getActivity() != null) getActivity().onBackPressed();
                return;
            }
            if (result != null && "gated_model_requires_auth".equals(result.optString("error"))) {
                promptHuggingFaceToken(context);
                return;
            }
            String message = result == null
                ? context.getString(R.string.termux_ai_model_action_failed)
                : result.optString("message", context.getString(R.string.termux_ai_model_action_failed));
            status.setSummary(getString(R.string.termux_ai_import_status_error, message));
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        });
    }

    private void setBusy(boolean busy) {
        action.setEnabled(!busy);
        action.setTitle(busy ? R.string.termux_ai_import_action_busy : R.string.termux_ai_import_action);
        hfUrl.setEnabled(!busy);
        modelId.setEnabled(!busy);
        file.setEnabled(!busy);
        chat.setEnabled(!busy);
        embeddings.setEnabled(!busy);
        media.setEnabled(!busy);
    }

    private LinkedHashSet<String> selectedCapabilities() {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (chat.isChecked()) capabilities.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        if (embeddings.isChecked()) capabilities.add(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
        if (media.isChecked()) {
            capabilities.add(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
            capabilities.add(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
        }
        return capabilities;
    }

    private JSONArray jsonArray(@NonNull LinkedHashSet<String> capabilities) {
        JSONArray array = new JSONArray();
        for (String capability : capabilities) array.put(capability);
        return array;
    }

    private String resolvedModelId(@NonNull String url, @NonNull String fileName) {
        String typed = modelId.getText() == null ? "" : modelId.getText().trim();
        if (!typed.isEmpty()) return TaiModelImporter.sanitizeModelId(typed);
        String basis = SOURCE_HF.equals(source) ? repoName(url) : TaiModelImporter.stripModelExtension(fileName);
        String sanitized = TaiModelImporter.sanitizeModelId(basis);
        return sanitized.isEmpty() ? "imported-model" : sanitized;
    }

    private String backendForFile(@Nullable String fileName) {
        return TaiModelSpec.BACKEND_LITERT_LM;
    }

    private void promptHuggingFaceToken(@NonNull Context context) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(R.string.termux_ai_huggingface_token_title);
        input.setText(new TaiSettings(context).getHuggingFaceToken());
        input.setSelectAllOnFocus(true);
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_huggingface_token_title)
            .setMessage(R.string.termux_ai_huggingface_token_dialog_message)
            .setView(input)
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) -> {
                new TaiSettings(context).setHuggingFaceToken(input.getText().toString().trim());
                updateUi(context);
            })
            .setNeutralButton(R.string.termux_ai_huggingface_token_get_action,
                (dialog, which) -> startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    Uri.parse("https://huggingface.co/settings/tokens"))))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private static String repoName(@Nullable String url) {
        try {
            String value = url == null ? "" : url.trim();
            int resolve = value.indexOf("/resolve/");
            if (resolve > 0) value = value.substring(0, resolve);
            String segment = Uri.parse(value).getLastPathSegment();
            return segment == null ? "" : segment;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024D && unit < units.length - 1) {
            value /= 1024D;
            unit++;
        }
        return String.format(Locale.US, value >= 10D ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }

    private static final class Guess {
        boolean chat;
        boolean embeddings;
        boolean media;
        String backend = "Auto";
        String error = "";

        static Guess from(@Nullable String url, @Nullable String fileName) {
            Guess guess = new Guess();
            String value = ((url == null ? "" : url) + " " + (fileName == null ? "" : fileName)).toLowerCase(Locale.ROOT);
            guess.backend = value.contains("mnn") || value.contains("config.json") ? "MNN" : "LiteRT";

            if (value.contains("embeddinggemma-300m")
                || value.contains("qwen3-embedding-0.6b-mnn")
                || value.contains("qwen3-embedding-4b-mnn")
                || value.contains("qwen3-embedding-8b-mnn")
                || value.contains("bge-")
                || value.contains("e5-")
                || value.contains("gte-")
                || value.contains("jina-embedding")
                || value.contains("embed")) {
                guess.embeddings = true;
            } else {
                guess.chat = true;
            }

            if (value.contains("vl") || value.contains("vision") || value.contains("image")
                || value.contains("audio") || value.contains("multimodal")
                || value.contains("gemma-4-e2b") || value.contains("gemma-4-e4b")) {
                guess.media = true;
            }
            return guess;
        }

        String backendLabel() {
            return backend;
        }

        String capabilityLabel() {
            StringBuilder builder = new StringBuilder();
            if (chat) appendLabel(builder, "chat");
            if (embeddings) appendLabel(builder, "embeddings");
            if (media) appendLabel(builder, "vision/audio");
            return builder.length() == 0 ? "none" : builder.toString();
        }

        private void appendLabel(@NonNull StringBuilder builder, @NonNull String label) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(label);
        }
    }
}
