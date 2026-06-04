package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Keep;
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

import java.util.Locale;
import java.util.Map;

@Keep
public class TaiPreferencesFragment extends PreferenceFragmentCompat {
    private static final String MODEL_ROW_PREFIX = "tai_model_row_";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(TaiSettings.PREFS_NAME);
        setPreferencesFromResource(R.xml.termux_ai_preferences, rootKey);
        setStaticSummary("tai_model_privacy_notice", R.string.termux_ai_model_privacy_notice_summary);
        setStaticSummary("tai_notification_privacy_notice", R.string.termux_ai_notification_privacy_notice_summary);
        configureModelManager(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context != null) {
            populateModelRows(context);
        }
    }

    private void setStaticSummary(String key, int summaryResId) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setSummary(summaryResId);
        }
    }

    private void configureModelManager(Context context) {
        Preference refresh = findPreference("tai_models_refresh");
        if (refresh != null) {
            refresh.setOnPreferenceClickListener(preference -> {
                populateModelRows(context);
                return true;
            });
        }
        populateModelRows(context);
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

        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            Preference row = new Preference(context);
            row.setKey(MODEL_ROW_PREFIX + entry.modelId);
            row.setTitle(entry.displayName);
            row.setSummary(buildModelSummary(entry, installedModels.get(entry.modelId), findDownload(downloads, entry.modelId)));
            row.setPersistent(false);
            row.setOnPreferenceClickListener(preference -> {
                showModelActions(context, entry, installedModels.get(entry.modelId), findDownload(new TaiModelStore(context).getDownloads(), entry.modelId));
                return true;
            });
            category.addPreference(row);
        }
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

    private void showModelActions(Context context, TaiModelCatalog.CatalogEntry entry, TaiModelSpec installed, JSONObject download) {
        if (installed != null) {
            new MaterialAlertDialogBuilder(context)
                .setTitle(entry.displayName)
                .setMessage(buildModelSummary(entry, installed, download))
                .setPositiveButton(R.string.termux_ai_model_delete_action, (dialog, which) -> deleteModel(context, entry.modelId))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return;
        }

        if (entry.gated) {
            new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.termux_ai_model_gated_title)
                .setMessage(context.getString(R.string.termux_ai_model_gated_message, entry.displayName))
                .setPositiveButton(R.string.termux_ai_model_open_provider, (dialog, which) -> openUrl(context, entry.providerPageUrl))
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

    private void startCatalogDownload(Context context, TaiModelCatalog.CatalogEntry entry) {
        try {
            JSONObject result = TaiManager.getInstance(context).downloadCatalogModel(entry.modelId);
            if (result.optBoolean("ok", false)) {
                Toast.makeText(context, R.string.termux_ai_model_download_started, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, result.optString("message", context.getString(R.string.termux_ai_model_action_failed)), Toast.LENGTH_LONG).show();
            }
            populateModelRows(context);
        } catch (JSONException e) {
            Toast.makeText(context, R.string.termux_ai_model_action_failed, Toast.LENGTH_LONG).show();
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
            populateModelRows(context);
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

    private void openUrl(Context context, String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(context, url, Toast.LENGTH_LONG).show();
        }
    }
}
