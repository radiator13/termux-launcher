package com.termux.app.launcher.notifications;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.termux.launcherctl.LauncherCtlNotificationListener;

public final class LauncherNotificationAccess {

    private LauncherNotificationAccess() {
    }

    @NonNull
    public static ComponentName listenerComponent(@NonNull Context context) {
        return new ComponentName(context, LauncherCtlNotificationListener.class);
    }

    public static boolean isEnabled(@NonNull Context context) {
        String enabledListeners = Settings.Secure.getString(
            context.getContentResolver(),
            "enabled_notification_listeners"
        );
        if (TextUtils.isEmpty(enabledListeners)) {
            return false;
        }
        String expected = listenerComponent(context).flattenToString();
        String expectedShort = listenerComponent(context).flattenToShortString();
        for (String item : enabledListeners.split(":")) {
            if (expected.equals(item) || expectedShort.equals(item)) {
                return true;
            }
            ComponentName componentName = ComponentName.unflattenFromString(item);
            if (listenerComponent(context).equals(componentName)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public static Intent detailSettingsIntent(@NonNull Context context) {
        return new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                listenerComponent(context).flattenToString()
            );
    }

    @NonNull
    public static Intent listSettingsIntent() {
        return new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
    }
}
