package com.termux.app.launcher.notifications;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.Nullable;

import com.termux.shared.logger.Logger;

/**
 * Lite notification listener: keeps dock badge store in sync and allows dismiss from the dock.
 * Replaces the removed LauncherCtl HTTP/media control-plane listener.
 */
public class LauncherNotificationListener extends NotificationListenerService {

    private static final String LOG_TAG = "LauncherNotifListener";

    private static volatile LauncherNotificationListener sActive;

    @Override
    public void onListenerConnected() {
        sActive = this;
        Logger.logInfo(LOG_TAG, "Notification listener connected");
        try {
            LauncherNotificationBadgeStore.syncFromActiveNotifications(getActiveNotifications(), getCurrentRanking());
        } catch (Throwable t) {
            Logger.logWarn(LOG_TAG, "Failed to sync notifications: " + t.getMessage());
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (sActive == this) {
            sActive = null;
        }
        LauncherNotificationBadgeStore.clear();
        Logger.logWarn(LOG_TAG, "Notification listener disconnected");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        LauncherNotificationBadgeStore.onNotificationPosted(sbn, null);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        LauncherNotificationBadgeStore.onNotificationPosted(sbn, rankingMap);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        LauncherNotificationBadgeStore.onNotificationRemoved(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        onNotificationRemoved(sbn);
    }

    /** Dismiss a notification by key when the listener is connected. */
    public static boolean dismissNotification(@Nullable String key) {
        LauncherNotificationListener listener = sActive;
        if (listener == null || key == null || key.isEmpty()) {
            return false;
        }
        try {
            listener.cancelNotification(key);
            return true;
        } catch (Throwable t) {
            Logger.logWarn(LOG_TAG, "Failed to dismiss notification: " + t.getMessage());
            return false;
        }
    }
}
