package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class FullOptimizationModePreferencesTest {

    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences(
            "full-optimization-mode-preferences-test",
            Context.MODE_PRIVATE
        );
        sharedPreferences.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, sharedPreferences, null);
    }

    @Test
    public void enablingAppliesLeanProfileAndDisablingRestoresValues() {
        preferences.setUseSystemWallpaperEnabled(true);
        preferences.setTerminalBackgroundOpacity(41);
        preferences.setAppBarOpacity(58);
        preferences.setExtraKeysBlurRadius(17);
        preferences.setDockGlassGrain(24);
        preferences.setAppLauncherAnimationsEnabled(true);
        preferences.setAppLauncherIconShadowEnabled(true);

        TermuxActivity.applyFullOptimizationModePreferences(preferences, true);

        assertTrue(preferences.isFullOptimizationModeEnabled());
        assertFalse(preferences.isUseSystemWallpaperEnabled());
        assertEquals(100, preferences.getTerminalBackgroundOpacity());
        assertEquals(100, preferences.getAppBarOpacity());
        assertEquals(0, preferences.getExtraKeysBlurRadius());
        assertEquals(0, preferences.getDockGlassGrain());
        assertFalse(preferences.isAppLauncherAnimationsEnabled());
        assertFalse(preferences.isAppLauncherIconShadowEnabled());

        // Cached pre-mode values.
        assertEquals(17, preferences.getFullOptSavedExtraKeysBlurRadius());
        assertEquals(24, preferences.getFullOptSavedDockGlassGrain());
        assertEquals(58, preferences.getFullOptSavedAppBarOpacity());
        assertEquals(41, preferences.getFullOptSavedTerminalBackgroundOpacity());
        assertTrue(preferences.getFullOptSavedUseSystemWallpaper());
        assertTrue(preferences.getFullOptSavedAnimationsEnabled());
        assertTrue(preferences.getFullOptSavedIconShadow());

        TermuxActivity.applyFullOptimizationModePreferences(preferences, false);

        assertFalse(preferences.isFullOptimizationModeEnabled());
        assertTrue(preferences.isUseSystemWallpaperEnabled());
        assertEquals(41, preferences.getTerminalBackgroundOpacity());
        assertEquals(58, preferences.getAppBarOpacity());
        assertEquals(17, preferences.getExtraKeysBlurRadius());
        assertEquals(24, preferences.getDockGlassGrain());
        assertTrue(preferences.isAppLauncherAnimationsEnabled());
        assertTrue(preferences.isAppLauncherIconShadowEnabled());
    }

    @Test
    public void reEnablingDoesNotOverwriteSavedProfileWithLeanValues() {
        preferences.setExtraKeysBlurRadius(12);
        preferences.setDockGlassGrain(30);
        preferences.setAppBarOpacity(40);
        preferences.setTerminalBackgroundOpacity(55);
        preferences.setUseSystemWallpaperEnabled(true);
        preferences.setAppLauncherAnimationsEnabled(true);
        preferences.setAppLauncherIconShadowEnabled(true);

        TermuxActivity.applyFullOptimizationModePreferences(preferences, true);
        // Simulate someone writing lean values again while already on.
        TermuxActivity.applyFullOptimizationModePreferences(preferences, true);

        assertEquals(12, preferences.getFullOptSavedExtraKeysBlurRadius());
        assertEquals(30, preferences.getFullOptSavedDockGlassGrain());
        assertEquals(40, preferences.getFullOptSavedAppBarOpacity());
        assertEquals(55, preferences.getFullOptSavedTerminalBackgroundOpacity());
    }
}
