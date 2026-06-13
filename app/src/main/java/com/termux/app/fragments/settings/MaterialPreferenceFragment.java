package com.termux.app.fragments.settings;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Base preference fragment that renders ListPreference / EditTextPreference dialogs as
 * rounded Material dialogs (via {@link SettingsMaterialDialogs}) instead of the platform
 * AppCompat alert dialog. Settings fragments extend this to pick up the redesigned look.
 */
@Keep
public abstract class MaterialPreferenceFragment extends PreferenceFragmentCompat {

    @Override
    public void onDisplayPreferenceDialog(@NonNull Preference preference) {
        if (getContext() != null && SettingsMaterialDialogs.show(getContext(), preference)) {
            return;
        }
        super.onDisplayPreferenceDialog(preference);
    }
}
