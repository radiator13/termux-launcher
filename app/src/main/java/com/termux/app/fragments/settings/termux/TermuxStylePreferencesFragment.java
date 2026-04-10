package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.ColorInt;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.shared.data.DataUtils;
import com.termux.shared.file.FileUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.settings.properties.SharedProperties;
import com.termux.shared.theme.ThemeUtils;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Keep
public class TermuxStylePreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxStylePreferencesDataStore.getInstance(context));
        setPreferencesFromResource(R.xml.termux_style_preferences, rootKey);
        configureThemePreferences();
    }

    private void configureThemePreferences() {
        ListPreference themeMode = findPreference("theme_mode");
        SwitchPreferenceCompat defaultTheme = findPreference("default_theme_enabled");
        Preference terminalTint = findPreference("terminal_material_tint_enabled");
        Preference accessoryTint = findPreference("accessory_material_tint_enabled");

        if (themeMode != null) {
            themeMode.setOnPreferenceChangeListener((preference, newValue) -> {
                updateThemePreferenceVisibility(defaultTheme, terminalTint, accessoryTint);
                return true;
            });
        }
        if (defaultTheme != null) {
            defaultTheme.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.TRUE.equals(newValue);
                if (terminalTint != null) terminalTint.setEnabled(!enabled);
                if (accessoryTint != null) accessoryTint.setEnabled(!enabled);
                return true;
            });
        }
        updateThemePreferenceVisibility(defaultTheme, terminalTint, accessoryTint);
    }

    private void updateThemePreferenceVisibility(Preference defaultThemePreference,
        Preference terminalTintPreference, Preference accessoryTintPreference) {
        boolean defaultEnabled = maybeIsDefaultThemeEnabled();
        if (defaultThemePreference != null) {
            defaultThemePreference.setVisible(true);
        }
        if (terminalTintPreference != null) {
            terminalTintPreference.setEnabled(!defaultEnabled);
        }
        if (accessoryTintPreference != null) {
            accessoryTintPreference.setEnabled(!defaultEnabled);
        }
    }

    private boolean maybeIsDefaultThemeEnabled() {
        Context context = getContext();
        if (context == null) return false;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        return preferences != null && preferences.isDefaultThemeEnabled();
    }
}

class TermuxStylePreferencesDataStore extends PreferenceDataStore {

    private final Context mContext;

    private final TermuxAppSharedPreferences mPreferences;

    private static TermuxStylePreferencesDataStore mInstance;
    private static final String LOG_TAG = "TermuxStylePreferences";

    private TermuxStylePreferencesDataStore(Context context) {
        mContext = context;
        mPreferences = TermuxAppSharedPreferences.build(context, true);
    }

    public static synchronized TermuxStylePreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxStylePreferencesDataStore(context);
        }
        return mInstance;
    }

    @Override
    public void putBoolean(String key, boolean value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch(key) {
            case "default_theme_enabled":
                mPreferences.setDefaultThemeEnabled(value);
                TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, true);
                break;
            case "use_system_wallpaper":
                mPreferences.setUseSystemWallpaperEnabled(value);
                TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, true);
                break;
            case "terminal_material_tint_enabled":
                setTerminalMaterialTintEnabled(value);
                break;
            case "accessory_material_tint_enabled":
                mPreferences.setAccessoryMaterialTintEnabled(value);
                TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, true);
                break;
            case "extrakeys_blur_enabled":
                // Legacy compatibility: map old boolean writes to the new radius-driven model.
                mPreferences.setExtraKeysBlurRadius(value ? Math.max(1, mPreferences.getExtraKeysBlurRadius()) : 0);
                break;
            case "sessions_blur_enabled":
                // Sessions blur is no longer user-facing in the hybrid model.
                break;
            case "monet_background_enabled":
                setTerminalAndAccessoryMaterialTintEnabled(value);
                break;
            case "monet_overlay_enabled":
                // Legacy compatibility: keep both toggles in sync even if old UI writes this key.
                setTerminalAndAccessoryMaterialTintEnabled(value);
                break;
            case "app_launcher_bw_icons":
                mPreferences.setAppLauncherBwIconsEnabled(value);
                break;
            case "app_launcher_az_row_enabled":
                mPreferences.setAppLauncherAzRowEnabled(value);
                break;
            case "app_launcher_az_double_tap_lock":
                mPreferences.setAppLauncherAzDoubleTapLockEnabled(value);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (mPreferences == null)
            return defValue;
        switch(key) {
            case "default_theme_enabled":
                return mPreferences.isDefaultThemeEnabled();
            case "use_system_wallpaper":
                return mPreferences.isUseSystemWallpaperEnabled();
            case "terminal_material_tint_enabled":
                return mPreferences.isTerminalMaterialTintEnabled();
            case "accessory_material_tint_enabled":
                return mPreferences.isAccessoryMaterialTintEnabled();
            case "extrakeys_blur_enabled":
                return mPreferences.getExtraKeysBlurRadius() > 0;
            case "sessions_blur_enabled":
                return false;
            case "monet_background_enabled":
                return mPreferences.isTerminalMaterialTintEnabled() && mPreferences.isAccessoryMaterialTintEnabled();
            case "monet_overlay_enabled":
                return mPreferences.isTerminalMaterialTintEnabled() && mPreferences.isAccessoryMaterialTintEnabled();
            case "app_launcher_bw_icons":
                return mPreferences.isAppLauncherBwIconsEnabled();
            case "app_launcher_az_row_enabled":
                return mPreferences.isAppLauncherAzRowEnabled();
            case "app_launcher_az_double_tap_lock":
                return mPreferences.isAppLauncherAzDoubleTapLockEnabled();
            default:
                return defValue;
        }
    }

    @Override
    public void putInt(String key, int value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch (key) {
            case "terminal_background_opacity":
                mPreferences.setTerminalBackgroundOpacity(value);
                syncBackgroundOverlayColor(value, null);
                TermuxActivity.updateTermuxActivityStyling(mContext, true);
                break;
            case "sessions_opacity":
                mPreferences.setSessionsOpacity(value);
                break;
            case "extrakeys_blur_radius":
                mPreferences.setExtraKeysBlurRadius(value);
                break;
            case "app_bar_opacity":
                mPreferences.setAppBarOpacity(value);
                break;
            case "app_launcher_button_count":
                mPreferences.setAppLauncherButtonCount(value);
                break;
            case "app_launcher_icon_scale_percent":
                mPreferences.setAppLauncherIconScale(DataUtils.clamp(value, 100, 180) / 100f);
                break;
            case "app_launcher_bar_height_percent":
                mPreferences.setAppLauncherBarHeightScale(value / 100f);
                break;
            default:
                break;
        }
    }

    @Override
    public int getInt(String key, int defValue) {
        if (mPreferences == null)
            return defValue;
        if (key == null)
            return defValue;
        switch (key) {
            case "terminal_background_opacity":
                return mPreferences.getTerminalBackgroundOpacity();
            case "sessions_opacity":
                return mPreferences.getSessionsOpacity();
            case "extrakeys_blur_radius":
                return mPreferences.getExtraKeysBlurRadius();
            case "app_bar_opacity":
                return mPreferences.getAppBarOpacity();
            case "app_launcher_button_count":
                return mPreferences.getAppLauncherButtonCount();
            case "app_launcher_icon_scale_percent":
                return Math.round(mPreferences.getAppLauncherIconScale() * 100f);
            case "app_launcher_bar_height_percent":
                return Math.round(mPreferences.getAppLauncherBarHeightScale() * 100f);
            default:
                return defValue;
        }
    }

    @Override
    public void putString(String key, String value) {
        if (mPreferences == null)
            return;
        if (key == null)
            return;
        switch (key) {
            case "theme_mode":
                writeTermuxPropertyToProperties(TermuxPropertyConstants.KEY_NIGHT_MODE, value);
                TermuxThemeUtils.setAppNightMode(value);
                TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, true);
                break;
            case "app_launcher_button_count":
                mPreferences.setAppLauncherButtonCount(DataUtils.getIntFromString(value, mPreferences.getAppLauncherButtonCount()));
                break;
            case "app_launcher_search_mode":
                mPreferences.setAppLauncherSearchMode(value);
                break;
            case "app_launcher_input_char":
                mPreferences.setAppLauncherInputChar(value);
                break;
            case "app_launcher_default_buttons":
                mPreferences.setAppLauncherDefaultButtons(value);
                break;
            case "app_launcher_bar_height":
                mPreferences.setAppLauncherBarHeightScale(DataUtils.getFloatFromString(value, mPreferences.getAppLauncherBarHeightScale()));
                break;
            case "app_launcher_icon_scale":
                mPreferences.setAppLauncherIconScale(
                    Math.max(1.0f, Math.min(1.8f, DataUtils.getFloatFromString(value, mPreferences.getAppLauncherIconScale())))
                );
                break;
            default:
                break;
        }
    }

    @Override
    public String getString(String key, String defValue) {
        if (mPreferences == null)
            return defValue;
        if (key == null)
            return defValue;
        switch (key) {
            case "theme_mode":
                return TermuxSharedProperties.getNightMode(mContext);
            case "app_launcher_button_count":
                return Integer.toString(mPreferences.getAppLauncherButtonCount());
            case "app_launcher_search_mode":
                return mPreferences.getAppLauncherSearchMode();
            case "app_launcher_input_char":
                return mPreferences.getAppLauncherInputChar();
            case "app_launcher_default_buttons":
                return mPreferences.getAppLauncherDefaultButtons();
            case "app_launcher_bar_height":
                return Float.toString(mPreferences.getAppLauncherBarHeightScale());
            case "app_launcher_icon_scale":
                return Float.toString(mPreferences.getAppLauncherIconScale());
            default:
                return defValue;
        }
    }

    private void syncBackgroundOverlayColor(int opacityPercent, Integer baseColorOverride) {
        int alpha = (int) ((DataUtils.clamp(opacityPercent, 0, 100) / 100f) * 255);
        Properties properties = loadTermuxProperties();
        String currentValue = properties.getProperty(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR);
        int baseColor = baseColorOverride != null ? baseColorOverride : TermuxSharedProperties.getBackgroundOverlayInternalPropertyValueFromValue(currentValue);
        if (mPreferences.isTerminalMaterialTintEnabled()) {
            baseColor = getMonetSurfaceColor(baseColor);
        }
        int newColor = (baseColor & 0x00FFFFFF) | (alpha << 24);
        writeTermuxPropertyToProperties(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR,
            String.format("#%08X", newColor));
    }

    private void setTerminalAndAccessoryMaterialTintEnabled(boolean enabled) {
        setTerminalMaterialTintEnabled(enabled);
        mPreferences.setAccessoryMaterialTintEnabled(enabled);
        TermuxActivity.requestTermuxActivityStylingOnNextResume(mContext, true);
    }

    private void setTerminalMaterialTintEnabled(boolean enabled) {
        if (enabled) {
            String current = getCurrentOverlayColorString();
            if (current != null && !current.isEmpty()) {
                mPreferences.setManualOverlayColor(current);
            }
        }
        mPreferences.setTerminalMaterialTintEnabled(enabled);
        Integer manualOverride = null;
        if (!enabled) {
            String manualColor = mPreferences.getManualOverlayColor();
            if (manualColor != null && !manualColor.isEmpty()) {
                manualOverride = TermuxSharedProperties.getBackgroundOverlayInternalPropertyValueFromValue(manualColor);
            }
        }
        syncBackgroundOverlayColor(mPreferences.getTerminalBackgroundOpacity(), manualOverride);
    }

    private String getCurrentOverlayColorString() {
        Properties properties = loadTermuxProperties();
        return properties.getProperty(TermuxPropertyConstants.KEY_BACKGROUND_OVERLAY_COLOR);
    }

    private Properties loadTermuxProperties() {
        File propertiesFile = SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        if (propertiesFile == null) {
            propertiesFile = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
        }
        Properties properties = SharedProperties.getPropertiesFromFile(mContext, propertiesFile, null);
        return properties == null ? new Properties() : properties;
    }

    private void writeTermuxPropertyToProperties(@NonNull String propertyKey, @NonNull String propertyValue) {
        File propertiesFile = SharedProperties.getPropertiesFileFromList(TermuxConstants.TERMUX_PROPERTIES_FILE_PATHS_LIST, LOG_TAG);
        if (propertiesFile == null) {
            propertiesFile = TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE;
        }
        File parentDir = propertiesFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parentDir.mkdirs();
        }
        List<String> lines = new ArrayList<>();
        boolean updated = false;
        if (propertiesFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(propertiesFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("#") && trimmed.matches("^\\s*" + propertyKey + "\\s*=.*$")) {
                        lines.add(propertyKey + "=" + propertyValue);
                        updated = true;
                    } else {
                        lines.add(line);
                    }
                }
            } catch (Exception e) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to read termux.properties", e);
            }
        }
        if (!updated) {
            lines.add(propertyKey + "=" + propertyValue);
        }
        StringBuilder output = new StringBuilder();
        for (String line : lines) {
            output.append(line).append('\n');
        }
        FileUtils.writeTextToFile("termux.properties", propertiesFile.getAbsolutePath(), StandardCharsets.UTF_8, output.toString(), false);
    }

    @ColorInt
    private int getMonetSurfaceColor(@ColorInt int fallbackColor) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return ThemeUtils.getSystemAttrColor(mContext, com.termux.shared.R.attr.termuxColorAccentContainer, fallbackColor);
            }
            return ThemeUtils.getSystemAttrColor(mContext, com.termux.shared.R.attr.termuxColorAccentContainer, fallbackColor);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to resolve Monet surface color", e);
            return fallbackColor;
        }
    }
}
