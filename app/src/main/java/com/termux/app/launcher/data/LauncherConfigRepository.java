package com.termux.app.launcher.data;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedIconOverride;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedFolderItem;
import com.termux.app.launcher.model.PinnedItem;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LauncherConfigRepository {
    public static final int SCHEMA_VERSION = 4;

    public interface PreferencesStore {
        String getPinnedItemsV2();
        void setPinnedItemsV2(String value);
        void setPinnedItemsSchemaVersion(int version);
        String getLegacyDefaultButtons();
    }

    public interface IconOverrideValidator {
        boolean isAvailable(@NonNull PinnedIconOverride iconOverride);
    }

    private final PreferencesStore preferences;

    public LauncherConfigRepository(@NonNull TermuxAppSharedPreferences preferences) {
        this(new PreferencesStore() {
            @Override
            public String getPinnedItemsV2() {
                return preferences.getAppLauncherPinnedItemsV2();
            }

            @Override
            public void setPinnedItemsV2(String value) {
                preferences.setAppLauncherPinnedItemsV2(value);
            }

            @Override
            public void setPinnedItemsSchemaVersion(int version) {
                preferences.setAppLauncherPinnedItemsSchemaVersion(version);
            }

            @Override
            public String getLegacyDefaultButtons() {
                return preferences.getAppLauncherDefaultButtons();
            }
        });
    }

    public LauncherConfigRepository(@NonNull PreferencesStore preferences) {
        this.preferences = preferences;
    }

    public List<PinnedItem> loadPinnedItems() {
        String raw = preferences.getPinnedItemsV2();
        if (raw == null || raw.trim().isEmpty()) {
            return migrateFromLegacyIfNeeded();
        }

        try {
            JSONObject root = new JSONObject(raw);
            JSONArray items = root.optJSONArray("items");
            if (items == null) return migrateFromLegacyIfNeeded();
            List<PinnedItem> out = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String type = item.optString("type", "");
                if ("app".equals(type)) {
                    AppRef ref = appRefFromJson(item);
                    if (!ref.packageName.isEmpty()) {
                        out.add(new PinnedAppItem(ref, parseIconOverride(item.optJSONObject("iconOverride"))));
                    }
                } else if ("folder".equals(type)) {
                    String id = item.optString("id", UUID.randomUUID().toString());
                    String title = item.optString("title", "Folder");
                    PinnedFolderItem folder = new PinnedFolderItem(id, title);
                    folder.rows = clamp(item.optInt("rows", PinnedFolderItem.DEFAULT_ROWS), 1, PinnedFolderItem.MAX_GRID);
                    folder.cols = clamp(item.optInt("cols", PinnedFolderItem.DEFAULT_COLS), 1, PinnedFolderItem.MAX_GRID);
                    folder.tintOverrideEnabled = item.optBoolean("tintOverrideEnabled", false);
                    folder.tintColor = item.optInt("tintColor", 0xFF202020);
                    JSONArray apps = item.optJSONArray("apps");
                    if (apps != null) {
                        for (int j = 0; j < apps.length(); j++) {
                            JSONObject app = apps.optJSONObject(j);
                            if (app == null) continue;
                            String packageName = app.optString("packageName", "");
                            String activityName = app.optString("activityName", "");
                            if (!packageName.isEmpty()) {
                                folder.apps.add(new PinnedAppItem(
                                    appRefFromJson(app),
                                    parseIconOverride(app.optJSONObject("iconOverride"))
                                ));
                            }
                        }
                    }
                    out.add(folder);
                }
            }
            if (out.isEmpty()) {
                return migrateFromLegacyIfNeeded();
            }
            return out;
        } catch (JSONException ignored) {
            return migrateFromLegacyIfNeeded();
        }
    }

    public void savePinnedItems(@NonNull List<PinnedItem> pinnedItems) {
        // App-wide icon choices live beside (not inside) the pin list. Preserve them whenever the
        // dock is reordered so changing dock membership can never silently reset app theming.
        JSONObject root = readRoot();
        JSONArray items = new JSONArray();
        for (PinnedItem pinnedItem : pinnedItems) {
            if (pinnedItem instanceof PinnedAppItem) {
                PinnedAppItem appItem = (PinnedAppItem) pinnedItem;
                JSONObject item = new JSONObject();
                try {
                    item.put("type", "app");
                    item.put("packageName", appItem.appRef.packageName);
                    item.put("activityName", appItem.appRef.activityName);
                    putAppRefProfile(item, appItem.appRef);
                    putIconOverrideIfValid(item, appItem.iconOverride);
                    items.put(item);
                } catch (JSONException ignored) {
                }
            } else if (pinnedItem instanceof PinnedFolderItem) {
                PinnedFolderItem folderItem = (PinnedFolderItem) pinnedItem;
                JSONObject item = new JSONObject();
                JSONArray apps = new JSONArray();
                for (PinnedAppItem folderApp : folderItem.apps) {
                    JSONObject app = new JSONObject();
                    try {
                        AppRef ref = folderApp.appRef;
                        app.put("packageName", ref.packageName);
                        app.put("activityName", ref.activityName);
                        putAppRefProfile(app, ref);
                        putIconOverrideIfValid(app, folderApp.iconOverride);
                        apps.put(app);
                    } catch (JSONException ignored) {
                    }
                }
                try {
                    item.put("type", "folder");
                    item.put("id", folderItem.id);
                    item.put("title", folderItem.title);
                    item.put("rows", clamp(folderItem.rows, 1, PinnedFolderItem.MAX_GRID));
                    item.put("cols", clamp(folderItem.cols, 1, PinnedFolderItem.MAX_GRID));
                    item.put("tintOverrideEnabled", folderItem.tintOverrideEnabled);
                    item.put("tintColor", folderItem.tintColor);
                    item.put("apps", apps);
                    items.put(item);
                } catch (JSONException ignored) {
                }
            }
        }

        try {
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("items", items);
            preferences.setPinnedItemsV2(root.toString());
            preferences.setPinnedItemsSchemaVersion(SCHEMA_VERSION);
        } catch (JSONException ignored) {
        }
    }

    /** Returns the app-wide icon override for this exact app/profile, if one was selected. */
    public PinnedIconOverride loadAppIconOverride(@NonNull AppRef ref) {
        JSONArray overrides = readRoot().optJSONArray("appIconOverrides");
        if (overrides == null) return null;
        String targetId = ref.stableId();
        for (int i = 0; i < overrides.length(); i++) {
            JSONObject item = overrides.optJSONObject(i);
            if (item == null) continue;
            AppRef storedRef = appRefFromJson(item);
            if (targetId.equals(storedRef.stableId())) {
                return parseIconOverride(item.optJSONObject("iconOverride"));
            }
        }
        return null;
    }

    /**
     * Stores an app-wide icon without changing whether the app is pinned. A null/invalid value
     * removes the override. Clone/profile identity is retained so the primary and cloned app may
     * be themed independently.
     */
    public void saveAppIconOverride(@NonNull AppRef ref, PinnedIconOverride iconOverride) {
        JSONObject root = readRoot();
        JSONArray current = root.optJSONArray("appIconOverrides");
        JSONArray updated = new JSONArray();
        String targetId = ref.stableId();
        if (current != null) {
            for (int i = 0; i < current.length(); i++) {
                JSONObject item = current.optJSONObject(i);
                if (item == null || targetId.equals(appRefFromJson(item).stableId())) continue;
                updated.put(item);
            }
        }
        if (iconOverride != null && iconOverride.isValid()) {
            JSONObject item = new JSONObject();
            try {
                item.put("packageName", ref.packageName);
                item.put("activityName", ref.activityName);
                putAppRefProfile(item, ref);
                putIconOverrideIfValid(item, iconOverride);
                updated.put(item);
            } catch (JSONException ignored) {
            }
        }
        try {
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("appIconOverrides", updated);
            preferences.setPinnedItemsV2(root.toString());
            preferences.setPinnedItemsSchemaVersion(SCHEMA_VERSION);
        } catch (JSONException ignored) {
        }
    }

    /** Removes overrides whose pack or drawable no longer exists, without changing dock order. */
    public boolean pruneInvalidIconOverrides(@NonNull IconOverrideValidator validator) {
        JSONObject root = readRoot();
        boolean changed = false;

        JSONArray appOverrides = root.optJSONArray("appIconOverrides");
        if (appOverrides != null) {
            JSONArray valid = new JSONArray();
            for (int i = 0; i < appOverrides.length(); i++) {
                JSONObject item = appOverrides.optJSONObject(i);
                PinnedIconOverride override = item == null ? null
                    : parseIconOverride(item.optJSONObject("iconOverride"));
                if (item != null && override != null && validator.isAvailable(override)) {
                    valid.put(item);
                } else {
                    changed = true;
                }
            }
            if (changed) {
                try {
                    root.put("appIconOverrides", valid);
                } catch (JSONException ignored) {
                }
            }
        }

        JSONArray items = root.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                if (removeInvalidOverride(item, validator)) changed = true;
                JSONArray folderApps = item.optJSONArray("apps");
                if (folderApps == null) continue;
                for (int j = 0; j < folderApps.length(); j++) {
                    JSONObject folderApp = folderApps.optJSONObject(j);
                    if (folderApp != null && removeInvalidOverride(folderApp, validator)) changed = true;
                }
            }
        }
        if (!changed) return false;
        try {
            root.put("schemaVersion", SCHEMA_VERSION);
            preferences.setPinnedItemsV2(root.toString());
            preferences.setPinnedItemsSchemaVersion(SCHEMA_VERSION);
        } catch (JSONException ignored) {
        }
        return true;
    }

    private static boolean removeInvalidOverride(
        @NonNull JSONObject item,
        @NonNull IconOverrideValidator validator
    ) {
        JSONObject raw = item.optJSONObject("iconOverride");
        if (raw == null) return false;
        PinnedIconOverride override = parseIconOverride(raw);
        if (override != null && validator.isAvailable(override)) return false;
        item.remove("iconOverride");
        return true;
    }

    public List<PinnedItem> migrateFromLegacyIfNeeded() {
        List<PinnedItem> out = new ArrayList<>();
        String legacy = preferences.getLegacyDefaultButtons();
        if ("phone,bromite,whatsapp,telegram,spotify".equalsIgnoreCase(legacy == null ? "" : legacy.trim())) {
            legacy = "";
        }
        if (legacy != null && !legacy.trim().isEmpty()) {
            String[] parts = legacy.split(",");
            for (String part : parts) {
                String value = part.trim();
                if (value.isEmpty()) continue;
                // ActivityName is unknown in legacy mode, use package only and resolve at runtime.
                out.add(new PinnedAppItem(new AppRef(value, "")));
            }
        }
        savePinnedItems(out);
        return out;
    }

    @NonNull
    private static AppRef appRefFromJson(@NonNull JSONObject item) {
        return new AppRef(
            item.optString("packageName", ""),
            item.optString("activityName", ""),
            item.optInt("userId", -1),
            item.optLong("userSerialNumber", -1L),
            item.optBoolean("clonedProfile", false),
            item.optString("profileLabel", "")
        );
    }

    private static void putAppRefProfile(@NonNull JSONObject target, @NonNull AppRef ref) throws JSONException {
        if (ref.userId < 0 && ref.userSerialNumber < 0 && !ref.clonedProfile
            && (ref.profileLabel == null || ref.profileLabel.isEmpty())) {
            return;
        }
        target.put("userId", ref.userId);
        target.put("userSerialNumber", ref.userSerialNumber);
        target.put("clonedProfile", ref.clonedProfile);
        if (ref.profileLabel != null && !ref.profileLabel.isEmpty()) {
            target.put("profileLabel", ref.profileLabel);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @NonNull
    private JSONObject readRoot() {
        String raw = preferences.getPinnedItemsV2();
        if (raw == null || raw.trim().isEmpty()) return new JSONObject();
        try {
            return new JSONObject(raw);
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private static PinnedIconOverride parseIconOverride(JSONObject raw) {
        if (raw == null) return null;
        PinnedIconOverride override = new PinnedIconOverride(
            raw.optString("sourceType", ""),
            raw.optString("iconPackPackage", ""),
            raw.optString("drawableName", ""),
            raw.optString("displayLabel", "")
        );
        return override.isValid() ? override : null;
    }

    private static void putIconOverrideIfValid(@NonNull JSONObject target, PinnedIconOverride iconOverride) throws JSONException {
        if (iconOverride == null || !iconOverride.isValid()) return;
        JSONObject override = new JSONObject();
        override.put("sourceType", iconOverride.sourceType);
        override.put("iconPackPackage", iconOverride.iconPackPackage);
        override.put("drawableName", iconOverride.drawableName);
        override.put("displayLabel", iconOverride.displayLabel);
        target.put("iconOverride", override);
    }
}
