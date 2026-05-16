package com.termux.app.launcher.notifications;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Lightweight active-notification index for launcher dots.
 *
 * The notification listener owns updates; the apps bar only observes package-level
 * badge state, so notification contents are never pulled into the UI path.
 */
public final class LauncherNotificationBadgeStore {

    public interface Listener {
        void onNotificationBadgePackagesChanged(@NonNull Set<String> packages);
    }

    private static final ConcurrentHashMap<String, String> ACTIVE_BADGE_KEYS = new ConcurrentHashMap<>();
    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();

    @NonNull private static volatile Set<String> activePackages = Collections.emptySet();

    private LauncherNotificationBadgeStore() {
    }

    public static void addListener(@NonNull Listener listener) {
        LISTENERS.add(listener);
        listener.onNotificationBadgePackagesChanged(getActivePackages());
    }

    public static void removeListener(@Nullable Listener listener) {
        if (listener != null) {
            LISTENERS.remove(listener);
        }
    }

    public static boolean hasBadge(@Nullable String packageName) {
        return packageName != null && activePackages.contains(packageName);
    }

    @NonNull
    public static Set<String> getActivePackages() {
        return activePackages;
    }

    public static void syncFromActiveNotifications(
        @Nullable StatusBarNotification[] notifications,
        @Nullable NotificationListenerService.RankingMap rankingMap
    ) {
        ACTIVE_BADGE_KEYS.clear();
        if (notifications != null) {
            for (StatusBarNotification sbn : notifications) {
                putIfBadgeable(sbn, rankingMap);
            }
        }
        publishIfChanged();
    }

    public static void onNotificationPosted(
        @Nullable StatusBarNotification sbn,
        @Nullable NotificationListenerService.RankingMap rankingMap
    ) {
        if (sbn == null) {
            return;
        }
        String key = sbn.getKey();
        boolean changed;
        if (isBadgeable(sbn, rankingMap)) {
            changed = !sbn.getPackageName().equals(ACTIVE_BADGE_KEYS.put(key, sbn.getPackageName()));
        } else {
            changed = ACTIVE_BADGE_KEYS.remove(key) != null;
        }
        if (changed) {
            publishIfChanged();
        }
    }

    public static void onNotificationRemoved(@Nullable StatusBarNotification sbn) {
        if (sbn != null && ACTIVE_BADGE_KEYS.remove(sbn.getKey()) != null) {
            publishIfChanged();
        }
    }

    public static void clear() {
        ACTIVE_BADGE_KEYS.clear();
        publishIfChanged();
    }

    private static void putIfBadgeable(
        @Nullable StatusBarNotification sbn,
        @Nullable NotificationListenerService.RankingMap rankingMap
    ) {
        if (isBadgeable(sbn, rankingMap)) {
            ACTIVE_BADGE_KEYS.put(sbn.getKey(), sbn.getPackageName());
        }
    }

    private static boolean isBadgeable(
        @Nullable StatusBarNotification sbn,
        @Nullable NotificationListenerService.RankingMap rankingMap
    ) {
        if (sbn == null || sbn.getNotification() == null || sbn.getPackageName() == null) {
            return false;
        }
        Notification notification = sbn.getNotification();
        if (sbn.isOngoing() || !sbn.isClearable()) {
            return false;
        }
        if ((notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return false;
        }
        if (rankingMap != null) {
            NotificationListenerService.Ranking ranking = new NotificationListenerService.Ranking();
            if (rankingMap.getRanking(sbn.getKey(), ranking) && !ranking.canShowBadge()) {
                return false;
            }
        }
        return true;
    }

    private static void publishIfChanged() {
        Set<String> packages = Collections.unmodifiableSet(new HashSet<>(ACTIVE_BADGE_KEYS.values()));
        if (packages.equals(activePackages)) {
            return;
        }
        activePackages = packages;
        for (Listener listener : LISTENERS) {
            listener.onNotificationBadgePackagesChanged(packages);
        }
    }
}
