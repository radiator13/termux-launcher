package com.termux.app.theme;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.R;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.theme.ThemeUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class TermuxThemeManager {

    private static final String LOG_TAG = "TermuxThemeManager";

    private TermuxThemeManager() {}

    @NonNull
    public static TermuxAppTheme getSelectedTheme(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        if (preferences == null) return TermuxAppTheme.DYNAMIC;
        return TermuxAppTheme.of(preferences.getAppTheme());
    }

    public static boolean shouldUsePureBlack(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        if (preferences == null || !preferences.isPureBlackThemeEnabled()) {
            return false;
        }
        return ThemeUtils.shouldEnableDarkTheme(context, TermuxSharedProperties.getNightMode(context));
    }

    public static void applyThemeOverlays(@NonNull Activity activity) {
        TermuxAppTheme theme = getSelectedTheme(activity);
        if (theme.getOverlayStyleResId() != 0) {
            activity.getTheme().applyStyle(theme.getOverlayStyleResId(), true);
        }
        if (shouldUsePureBlack(activity)) {
            activity.getTheme().applyStyle(R.style.ThemeOverlay_TermuxPalette_PureBlack, true);
        }
    }

    public static void syncTerminalThemeIfNeeded(@NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        if (preferences == null) return;

        TermuxAppTheme theme = TermuxAppTheme.of(preferences.getAppTheme());
        if (!theme.supportsTerminalSync()) {
            if (preferences.isThemeTerminalSyncEnabled()) {
                preferences.setThemeTerminalSyncEnabled(false);
            }
            return;
        }
        if (!preferences.isThemeTerminalSyncEnabled()) {
            return;
        }

        boolean darkTheme = ThemeUtils.shouldEnableDarkTheme(context, TermuxSharedProperties.getNightMode(context));
        String assetPath = theme.getTerminalAssetPath(darkTheme);
        if (assetPath == null) return;

        try {
            String desired = readAssetText(context, assetPath);
            if (desired == null) return;
            writeTerminalThemeIfChanged(desired);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to sync terminal theme", e);
        }
    }

    @Nullable
    private static String readAssetText(@NonNull Context context, @NonNull String assetPath) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = context.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static void writeTerminalThemeIfChanged(@NonNull String desiredContents) {
        File colorsFile = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE;
        File parent = colorsFile.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        String currentContents = colorsFile.exists() ? readFileText(colorsFile) : null;
        if (desiredContents.equals(currentContents)) {
            return;
        }
        FileUtils.writeTextToFile(colorsFile.getName(), colorsFile.getAbsolutePath(), StandardCharsets.UTF_8, desiredContents, false);
    }

    @Nullable
    private static String readFileText(@NonNull File file) {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (IOException e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read colors.properties", e);
            return null;
        }
    }
}
