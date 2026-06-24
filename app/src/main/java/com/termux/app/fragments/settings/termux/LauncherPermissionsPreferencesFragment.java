package com.termux.app.fragments.settings.termux;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.Preference;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.PillPreference;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.launcher.LauncherLockAccessibilityAccess;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.termux.TermuxConstants;

@Keep
public class LauncherPermissionsPreferencesFragment extends MaterialPreferenceFragment {

    private static final String KEY_STORAGE = "app_launcher_storage_access";
    private static final String KEY_NOTIFICATION_ACCESS = "app_launcher_notification_access";
    private static final String KEY_ACCESSIBILITY_LOCK = "app_launcher_accessibility_lock_access";
    private static final String KEY_NOTIFICATION_SETTINGS = "app_launcher_notification_settings";
    private static final String KEY_APP_PERMISSIONS = "app_launcher_app_permissions";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        setPreferencesFromResource(R.xml.launcher_permissions_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        configureActions(context);
        updateSummaries(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        Context context = getContext();
        if (context != null) {
            updateSummaries(context);
        }
    }

    private void configureActions(@NonNull Context context) {
        setClickListener(KEY_STORAGE, preference -> {
            Intent intent = new Intent(context, TermuxActivity.class)
                .setAction(TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
            startActivity(intent);
            return true;
        });
        setClickListener(KEY_NOTIFICATION_ACCESS, preference -> {
            openNotificationAccessSettings(context);
            return true;
        });
        setClickListener(KEY_ACCESSIBILITY_LOCK, preference -> {
            startSettingsIntent(context, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return true;
        });
        setClickListener(KEY_NOTIFICATION_SETTINGS, preference -> {
            openNotificationSettings(context);
            return true;
        });
        setClickListener(KEY_APP_PERMISSIONS, preference -> {
            openAppDetails(context);
            return true;
        });
    }

    private void setClickListener(String key, Preference.OnPreferenceClickListener listener) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceClickListener(listener);
        }
    }

    private void updateSummaries(@NonNull Context context) {
        setStatusPill(
            KEY_STORAGE,
            PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(context, -1, true, false)
        );
        setStatusPill(KEY_NOTIFICATION_ACCESS, LauncherNotificationAccess.isEnabled(context));
        setStatusPill(KEY_ACCESSIBILITY_LOCK, LauncherLockAccessibilityAccess.isEnabled(context));
        setStatusPill(KEY_NOTIFICATION_SETTINGS, NotificationManagerCompat.from(context).areNotificationsEnabled());
    }

    private void setStatusPill(String key, boolean enabled) {
        Preference preference = findPreference(key);
        if (preference instanceof PillPreference) {
            ((PillPreference) preference).setPill(
                getString(enabled
                    ? R.string.termux_app_launcher_access_status_on
                    : R.string.termux_app_launcher_access_status_off),
                enabled ? PillPreference.Tone.POSITIVE : PillPreference.Tone.NEGATIVE);
        } else if (preference != null) {
            preference.setSummary(enabled
                ? R.string.termux_app_launcher_access_status_on
                : R.string.termux_app_launcher_access_status_off);
        }
    }

    private void openNotificationAccessSettings(@NonNull Context context) {
        if (startSettingsIntent(context, LauncherNotificationAccess.detailSettingsIntent(context))) {
            return;
        }
        if (startSettingsIntent(context, LauncherNotificationAccess.listSettingsIntent())) {
            return;
        }
        showSettingsUnavailable(context);
    }

    private void openNotificationSettings(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
        if (!startSettingsIntent(context, intent)) {
            openAppDetails(context);
        }
    }

    private void openAppDetails(@NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:" + context.getPackageName()));
        if (!startSettingsIntent(context, intent)) {
            showSettingsUnavailable(context);
        }
    }

    private boolean startSettingsIntent(@NonNull Context context, @NonNull Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private void showSettingsUnavailable(@NonNull Context context) {
        Toast.makeText(context, R.string.termux_app_launcher_permission_settings_unavailable, Toast.LENGTH_SHORT).show();
    }
}
