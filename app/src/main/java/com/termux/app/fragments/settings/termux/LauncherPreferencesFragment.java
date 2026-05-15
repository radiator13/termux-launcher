package com.termux.app.fragments.settings.termux;

import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.termux.R;
import com.termux.app.launcher.data.LauncherUsageStatsStore;

@Keep
public class LauncherPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.launcher_preferences, rootKey);
        LauncherIconPackPreferenceController.configure(this, context);

        SwitchPreferenceCompat appsRowPreference = findPreference("app_launcher_apps_row_enabled");
        SwitchPreferenceCompat azRowPreference = findPreference("app_launcher_az_row_enabled");
        if (appsRowPreference != null && azRowPreference != null) {
            azRowPreference.setEnabled(appsRowPreference.isChecked());
            appsRowPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean appsRowEnabled = Boolean.TRUE.equals(newValue);
                azRowPreference.setEnabled(appsRowEnabled);
                if (!appsRowEnabled) {
                    azRowPreference.setChecked(false);
                }
                return true;
            });
        }

        Preference setHomePreference = findPreference("app_launcher_set_home");
        if (setHomePreference != null) {
            setHomePreference.setOnPreferenceClickListener(preference -> {
                openHomeLauncherSettings(context);
                return true;
            });
        }

        Preference resetRankingPreference = findPreference("app_launcher_reset_usage_ranking");
        if (resetRankingPreference != null) {
            resetRankingPreference.setOnPreferenceClickListener(preference -> {
                Context ctx = getContext();
                if (ctx == null) return true;
                new AlertDialog.Builder(ctx)
                    .setTitle(R.string.termux_app_launcher_reset_usage_ranking_confirm_title)
                    .setMessage(R.string.termux_app_launcher_reset_usage_ranking_confirm_message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        new LauncherUsageStatsStore(ctx).clear();
                        Toast.makeText(ctx, R.string.termux_app_launcher_reset_usage_ranking_done, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context == null) return;
        LauncherIconPackPreferenceController.configure(this, context);
    }

    private void openHomeLauncherSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
        if (startSettingsIntent(context, intent)) {
            return;
        }

        intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        if (startSettingsIntent(context, intent)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = context.getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
                if (startSettingsIntent(context, intent)) {
                    return;
                }
            }
        }

        Toast.makeText(context, R.string.termux_app_launcher_set_home_unavailable, Toast.LENGTH_SHORT).show();
    }

    private boolean startSettingsIntent(Context context, Intent intent) {
        if (intent == null) return false;
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
