package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.termux.app.TermuxActivity;
import com.termux.app.launcher.data.IconPackRepository;
import com.termux.app.launcher.data.LauncherAppDataProvider;
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
        populateIconPackList(context, preferences, fragment.findPreference("app_launcher_themed_icon_pack_package"), true);

        Preference refresh = fragment.findPreference("app_launcher_refresh_icon_packs");
        if (refresh != null) {
            refresh.setOnPreferenceClickListener(preference -> {
                configure(fragment, context);
                Toast.makeText(context, "Icon packs refreshed", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        Preference reset = fragment.findPreference("app_launcher_reset_icon_packs");
        if (reset != null) {
            reset.setOnPreferenceClickListener(preference -> {
                if (preferences != null) {
                    preferences.setAppLauncherIconPackPackage("");
                    preferences.setAppLauncherThemedIconPackPackage("");
                    preferences.setAppLauncherThemedIconsEnabled(false);
                    LauncherAppDataProvider.getInstance(context).invalidate();
                    TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
                }
                configure(fragment, context);
                Toast.makeText(context, "Icon packs reset", Toast.LENGTH_SHORT).show();
                return true;
            });
        }
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
            preference.setValue(themedOnly
                ? preferences.getAppLauncherThemedIconPackPackage()
                : preferences.getAppLauncherIconPackPackage());
        }
        preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    }
}
