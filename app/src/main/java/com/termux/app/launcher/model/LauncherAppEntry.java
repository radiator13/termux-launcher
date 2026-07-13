package com.termux.app.launcher.model;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class LauncherAppEntry {
    public final AppRef appRef;
    public final String label;
    @Nullable public final Drawable icon;
    /** True when artwork came from an icon pack and should not receive launcher saturation tuning. */
    public final boolean iconPackArtwork;
    @NonNull public final String labelLower;
    @NonNull public final String labelNormalized;
    @NonNull public final String packageLower;
    @NonNull public final String activityLower;
    @NonNull public final String stableIdLower;
    @NonNull public final String[] normalizedWords;

    public LauncherAppEntry(@NonNull AppRef appRef, @NonNull String label, @Nullable Drawable icon) {
        this(appRef, label, icon, false);
    }

    public LauncherAppEntry(
        @NonNull AppRef appRef,
        @NonNull String label,
        @Nullable Drawable icon,
        boolean iconPackArtwork
    ) {
        this.appRef = appRef;
        this.label = label;
        this.icon = icon;
        this.iconPackArtwork = iconPackArtwork;
        this.labelLower = label.toLowerCase(Locale.US);
        this.labelNormalized = normalizeLookupValue(label);
        this.packageLower = appRef.packageName.toLowerCase(Locale.US);
        this.activityLower = appRef.activityName.toLowerCase(Locale.US);
        this.stableIdLower = appRef.stableId().toLowerCase(Locale.US);
        this.normalizedWords = labelNormalized.isEmpty() ? new String[0] : labelNormalized.split("\\s+");
    }

    @NonNull
    private static String normalizeLookupValue(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder normalized = new StringBuilder(value.length());
        boolean previousWasSpace = true;
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
                previousWasSpace = false;
            } else if (!previousWasSpace) {
                normalized.append(' ');
                previousWasSpace = true;
            }
        }
        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }
        return normalized.toString();
    }
}
