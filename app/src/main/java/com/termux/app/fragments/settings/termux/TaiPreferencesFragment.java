package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.termux.R;
import com.termux.ai.TaiSettings;

@Keep
public class TaiPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null)
            return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(TaiSettings.PREFS_NAME);
        setPreferencesFromResource(R.xml.termux_ai_preferences, rootKey);
        setStaticSummary("tai_model_privacy_notice", R.string.termux_ai_model_privacy_notice_summary);
        setStaticSummary("tai_notification_privacy_notice", R.string.termux_ai_notification_privacy_notice_summary);
    }

    private void setStaticSummary(String key, int summaryResId) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setSummary(summaryResId);
        }
    }
}
