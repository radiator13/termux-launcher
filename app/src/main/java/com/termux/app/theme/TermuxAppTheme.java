package com.termux.app.theme;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.R;

public enum TermuxAppTheme {
    DYNAMIC("dynamic", R.string.termux_app_theme_dynamic, 0, null, null),
    NORD("nord", R.string.termux_app_theme_nord, R.style.ThemeOverlay_TermuxPalette_Nord,
        "terminal-themes/nord-light.properties", "terminal-themes/nord-dark.properties"),
    CATPPUCCIN("catppuccin", R.string.termux_app_theme_catppuccin, R.style.ThemeOverlay_TermuxPalette_Catppuccin,
        "terminal-themes/catppuccin-light.properties", "terminal-themes/catppuccin-dark.properties"),
    ROSE_PINE("rosepine", R.string.termux_app_theme_rose_pine, R.style.ThemeOverlay_TermuxPalette_RosePine,
        "terminal-themes/rose-pine-light.properties", "terminal-themes/rose-pine-dark.properties"),
    SYNTHWAVE84("synthwave84", R.string.termux_app_theme_synthwave84, R.style.ThemeOverlay_TermuxPalette_Synthwave84,
        "terminal-themes/synthwave84-light.properties", "terminal-themes/synthwave84-dark.properties"),
    ONEDARK("onedark", R.string.termux_app_theme_onedark, R.style.ThemeOverlay_TermuxPalette_OneDark,
        "terminal-themes/onedark-light.properties", "terminal-themes/onedark-dark.properties"),
    DRACULA("dracula", R.string.termux_app_theme_dracula, R.style.ThemeOverlay_TermuxPalette_Dracula,
        "terminal-themes/dracula-light.properties", "terminal-themes/dracula-dark.properties");

    private final String value;
    private final int labelResId;
    private final int overlayStyleResId;
    @Nullable private final String lightTerminalAssetPath;
    @Nullable private final String darkTerminalAssetPath;

    TermuxAppTheme(@NonNull String value, int labelResId, int overlayStyleResId,
        @Nullable String lightTerminalAssetPath, @Nullable String darkTerminalAssetPath) {
        this.value = value;
        this.labelResId = labelResId;
        this.overlayStyleResId = overlayStyleResId;
        this.lightTerminalAssetPath = lightTerminalAssetPath;
        this.darkTerminalAssetPath = darkTerminalAssetPath;
    }

    @NonNull
    public String getValue() {
        return value;
    }

    public int getLabelResId() {
        return labelResId;
    }

    public int getOverlayStyleResId() {
        return overlayStyleResId;
    }

    public boolean supportsTerminalSync() {
        return lightTerminalAssetPath != null && darkTerminalAssetPath != null;
    }

    @Nullable
    public String getTerminalAssetPath(boolean darkTheme) {
        return darkTheme ? darkTerminalAssetPath : lightTerminalAssetPath;
    }

    @NonNull
    public static TermuxAppTheme of(@Nullable String value) {
        if (value != null) {
            for (TermuxAppTheme theme : values()) {
                if (theme.value.equalsIgnoreCase(value.trim())) {
                    return theme;
                }
            }
        }
        return DYNAMIC;
    }
}
