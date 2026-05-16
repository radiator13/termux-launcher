package com.termux.app.launcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.List;

public final class LauncherAppLauncher {

    private LauncherAppLauncher() {
    }

    public static boolean launchEntry(@NonNull Context context, @NonNull LauncherAppEntry entry) {
        if (entry.appRef.packageName.startsWith("injected.test")) {
            return false;
        }

        PackageManager packageManager = context.getPackageManager();
        String activityName = entry.appRef.activityName;
        if (!TextUtils.isEmpty(activityName) && activityName.startsWith(".")) {
            activityName = entry.appRef.packageName + activityName;
        }

        Intent explicit = null;
        Intent explicitNoCategory = null;
        if (!TextUtils.isEmpty(activityName)) {
            explicit = new Intent(Intent.ACTION_MAIN);
            explicit.addCategory(Intent.CATEGORY_LAUNCHER);
            explicit.setComponent(new ComponentName(entry.appRef.packageName, activityName));

            explicitNoCategory = new Intent(Intent.ACTION_MAIN);
            explicitNoCategory.setComponent(new ComponentName(entry.appRef.packageName, activityName));
        }

        Intent packageDefault = packageManager.getLaunchIntentForPackage(entry.appRef.packageName);
        ComponentName packageDefaultComponent = packageDefault != null ? packageDefault.getComponent() : null;
        ComponentName explicitComponent = explicit != null ? explicit.getComponent() : null;
        boolean explicitIsPackageDefault = sameComponent(explicitComponent, packageDefaultComponent);

        if (explicitIsPackageDefault && tryStartActivity(context, packageDefault)) {
            return true;
        }
        if (tryStartActivity(context, explicit)) {
            return true;
        }
        if (!explicitIsPackageDefault && tryStartActivity(context, packageDefault)) {
            return true;
        }

        Intent resolveFallback = new Intent(Intent.ACTION_MAIN);
        resolveFallback.addCategory(Intent.CATEGORY_LAUNCHER);
        resolveFallback.setPackage(entry.appRef.packageName);
        ComponentName resolved = resolveFallback.resolveActivity(packageManager);
        if (resolved != null) {
            resolveFallback.setComponent(resolved);
        }

        if (tryStartActivity(context, explicitNoCategory)) {
            return true;
        }
        if (resolved != null && tryStartActivity(context, resolveFallback)) {
            return true;
        }
        if (tryStartMainActivity(context, explicit != null ? explicit.getComponent() : null)) {
            return true;
        }
        if (tryStartMainActivity(context, packageDefault != null ? packageDefault.getComponent() : null)) {
            return true;
        }
        if (tryStartMainActivity(context, resolved)) {
            return true;
        }

        Intent packageMain = new Intent(Intent.ACTION_MAIN);
        packageMain.addCategory(Intent.CATEGORY_LAUNCHER);
        packageMain.setPackage(entry.appRef.packageName);
        List<android.content.pm.ResolveInfo> matches = packageManager.queryIntentActivities(packageMain, 0);
        for (android.content.pm.ResolveInfo match : matches) {
            if (match == null || match.activityInfo == null) continue;
            String pkg = match.activityInfo.packageName;
            String cls = match.activityInfo.name;
            if (TextUtils.isEmpty(pkg) || TextUtils.isEmpty(cls)) continue;
            Intent fallbackExplicit = new Intent(Intent.ACTION_MAIN);
            fallbackExplicit.addCategory(Intent.CATEGORY_LAUNCHER);
            fallbackExplicit.setComponent(new ComponentName(pkg, cls));
            if (tryStartActivity(context, fallbackExplicit)
                || tryStartMainActivity(context, fallbackExplicit.getComponent())) {
                return true;
            }
        }

        return false;
    }

    private static boolean sameComponent(@Nullable ComponentName first, @Nullable ComponentName second) {
        return first != null && second != null && first.equals(second);
    }

    private static boolean tryStartMainActivity(@NonNull Context context, @Nullable ComponentName componentName) {
        if (componentName == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return false;
        }
        try {
            LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            if (launcherApps == null) {
                return false;
            }
            launcherApps.startMainActivity(componentName, Process.myUserHandle(), null, null);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryStartActivity(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null) return false;
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (context instanceof Activity) {
                ((Activity) context).startActivity(intent);
            } else {
                context.startActivity(intent);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
