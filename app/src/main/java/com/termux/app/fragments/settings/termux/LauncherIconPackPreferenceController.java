package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.app.launcher.data.IconPackRepository;
import com.termux.app.launcher.model.IconPackInfo;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.List;

final class LauncherIconPackPreferenceController {
    private LauncherIconPackPreferenceController() {
    }

    static void configure(@NonNull PreferenceFragmentCompat fragment, @NonNull Context context) {
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        populateIconPackList(context, preferences, fragment.findPreference("app_launcher_icon_pack_package"), false);
        populateIconPackList(context, preferences, fragment.findPreference("app_launcher_pinned_icon_pack_package"), false);
    }

    private static void populateIconPackList(
        Context context,
        TermuxAppSharedPreferences preferences,
        ListPreference preference,
        boolean themedOnly
    ) {
        if (preference == null) return;
        List<IconPackInfo> packs = new IconPackRepository(context).discoverIconPacks();
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        entries.add("System default");
        values.add("");
        for (IconPackInfo pack : packs) {
            if (themedOnly && !pack.themed) continue;
            entries.add(pack.label);
            values.add(pack.packageName);
        }
        preference.setEntries(entries.toArray(new CharSequence[0]));
        preference.setEntryValues(values.toArray(new CharSequence[0]));
        if (preferences != null) {
            if ("app_launcher_pinned_icon_pack_package".equals(preference.getKey())) {
                preference.setValue(preferences.getAppLauncherPinnedIconPackPackage());
            } else {
                preference.setValue(preferences.getAppLauncherIconPackPackage());
            }
        }
        preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    }
}
