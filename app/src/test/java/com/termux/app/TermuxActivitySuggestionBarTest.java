package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivitySuggestionBarTest {

    @Test
    public void applySuggestionBarPreferences_appliesHostMaxButtons() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        TermuxAppSharedPreferences preferences = ReflectionHelpers.callConstructor(
            TermuxAppSharedPreferences.class,
            ReflectionHelpers.ClassParameter.from(Context.class, activity)
        );
        assertNotNull(preferences);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);
        SuggestionBarView suggestionBarView = new SuggestionBarView(activity, null);
        activity.mSuggestionBarView = suggestionBarView;

        ReflectionHelpers.callInstanceMethod(activity, "applySuggestionBarPreferences");

        int appliedMax = ReflectionHelpers.getField(suggestionBarView, "maxButtonCount");
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int configuredMax = preferences.getAppLauncherButtonCount();
        int expectedMax = configuredMax > 0 ? configuredMax : TermuxActivity.calculateSuggestionBarMaxButtons(metrics);

        assertEquals(expectedMax, appliedMax);
    }

    @Test
    public void refreshLauncherIconsIfPreferencesChanged_reconcilesPersistedPackChange() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        TermuxAppSharedPreferences preferences = ReflectionHelpers.callConstructor(
            TermuxAppSharedPreferences.class,
            ReflectionHelpers.ClassParameter.from(Context.class, activity)
        );
        assertNotNull(preferences);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);
        activity.mSuggestionBarView = new SuggestionBarView(activity, null);

        ReflectionHelpers.callInstanceMethod(activity, "applySuggestionBarPreferences");
        int initialSignature = ReflectionHelpers.getField(activity, "mLastLauncherIconPreferencesSignature");

        preferences.setAppLauncherIconPackPackage("com.example.changed.iconpack");
        ReflectionHelpers.callInstanceMethod(activity, "refreshLauncherIconsIfPreferencesChanged");

        int refreshedSignature = ReflectionHelpers.getField(activity, "mLastLauncherIconPreferencesSignature");
        assertNotEquals(initialSignature, refreshedSignature);
        assertEquals(
            "com.example.changed.iconpack",
            preferences.getAppLauncherIconPackPackage()
        );
    }
}
