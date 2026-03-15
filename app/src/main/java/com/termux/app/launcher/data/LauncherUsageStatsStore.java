package com.termux.app.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists app usage stats and provides stable ranking for AZ workflow.
 */
public final class LauncherUsageStatsStore {

    private static final String PREFS_KEY_USAGE_STATS_V1 = "app_launcher_az_usage_stats_v1";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_LAST = "last";

    private final SharedPreferences sharedPreferences;
    private final Map<String, UsageStat> usageByStableId = new HashMap<>();
    private boolean loaded;

    public LauncherUsageStatsStore(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.sharedPreferences = appContext.getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE
        );
    }

    public synchronized void recordLaunch(@NonNull String stableId) {
        ensureLoaded();
        UsageStat stat = usageByStableId.get(stableId);
        if (stat == null) {
            stat = new UsageStat();
            usageByStableId.put(stableId, stat);
        }
        stat.count = Math.max(0, stat.count) + 1;
        stat.lastLaunchEpochMs = System.currentTimeMillis();
        persist();
    }

    public synchronized void clear() {
        usageByStableId.clear();
        loaded = true;
        sharedPreferences.edit().putString(PREFS_KEY_USAGE_STATS_V1, "").apply();
    }

    @NonNull
    public synchronized List<LauncherAppEntry> rankForAz(@NonNull List<LauncherAppEntry> entries) {
        ensureLoaded();
        if (entries.size() <= 1) {
            return new ArrayList<>(entries);
        }
        List<LauncherAppEntry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, new Comparator<LauncherAppEntry>() {
            @Override
            public int compare(LauncherAppEntry a, LauncherAppEntry b) {
                UsageStat sa = usageByStableId.get(a.appRef.stableId());
                UsageStat sb = usageByStableId.get(b.appRef.stableId());
                int ca = sa == null ? 0 : sa.count;
                int cb = sb == null ? 0 : sb.count;

                // Stability guard: only reorder by usage if difference is meaningful.
                if (Math.abs(ca - cb) >= 2) {
                    return Integer.compare(cb, ca);
                }

                long la = sa == null ? 0L : sa.lastLaunchEpochMs;
                long lb = sb == null ? 0L : sb.lastLaunchEpochMs;
                if (ca != cb && Math.abs(ca - cb) >= 1 && (la > 0L || lb > 0L)) {
                    return Long.compare(lb, la);
                }
                return safeLabel(a).compareToIgnoreCase(safeLabel(b));
            }
        });
        return sorted;
    }

    private static String safeLabel(@NonNull LauncherAppEntry entry) {
        return entry.label == null ? "" : entry.label;
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        usageByStableId.clear();
        String raw = sharedPreferences.getString(PREFS_KEY_USAGE_STATS_V1, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(raw);
            for (String key : root.keySet()) {
                JSONObject statJson = root.optJSONObject(key);
                if (statJson == null) continue;
                UsageStat stat = new UsageStat();
                stat.count = Math.max(0, statJson.optInt(FIELD_COUNT, 0));
                stat.lastLaunchEpochMs = Math.max(0L, statJson.optLong(FIELD_LAST, 0L));
                usageByStableId.put(key, stat);
            }
        } catch (JSONException ignored) {
        }
    }

    private void persist() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, UsageStat> entry : usageByStableId.entrySet()) {
                UsageStat stat = entry.getValue();
                if (stat == null || stat.count <= 0) continue;
                JSONObject statJson = new JSONObject();
                statJson.put(FIELD_COUNT, stat.count);
                statJson.put(FIELD_LAST, stat.lastLaunchEpochMs);
                root.put(entry.getKey(), statJson);
            }
        } catch (JSONException ignored) {
        }
        sharedPreferences.edit().putString(PREFS_KEY_USAGE_STATS_V1, root.toString()).apply();
    }

    private static final class UsageStat {
        int count;
        long lastLaunchEpochMs;
    }
}
