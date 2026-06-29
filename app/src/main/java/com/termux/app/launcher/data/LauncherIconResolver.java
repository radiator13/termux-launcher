package com.termux.app.launcher.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.PinnedIconOverride;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.Calendar;

public final class LauncherIconResolver {
    public interface SystemIconLoader {
        @Nullable Drawable load(@NonNull AppRef ref);
    }

    private final Context context;
    private final PackageManager packageManager;
    private final IconPackRepository iconPackRepository;
    @Nullable private final TermuxAppSharedPreferences preferences;
    @Nullable private final SystemIconLoader systemIconLoader;

    public LauncherIconResolver(@NonNull Context context) {
        this(context, new IconPackRepository(context), TermuxAppSharedPreferences.build(context, false), null);
    }

    public LauncherIconResolver(
        @NonNull Context context,
        @NonNull IconPackRepository iconPackRepository,
        @Nullable TermuxAppSharedPreferences preferences,
        @Nullable SystemIconLoader systemIconLoader
    ) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();
        this.iconPackRepository = iconPackRepository;
        this.preferences = preferences;
        this.systemIconLoader = systemIconLoader;
    }

    @Nullable
    public Drawable resolve(@NonNull AppRef ref) {
        return resolve(ref, null);
    }

    @Nullable
    public Drawable resolve(@NonNull AppRef ref, @Nullable PinnedIconOverride override) {
        Drawable icon = loadOverride(override);
        if (icon != null) return icon;

        if (preferences != null) {
            icon = loadFromPack(preferences.getAppLauncherIconPackPackage(), ref);
            if (icon != null) return icon;
        }

        return loadSystemIcon(ref);
    }

    @Nullable
    public Drawable resolvePinned(@NonNull AppRef ref, @Nullable PinnedIconOverride override) {
        return resolvePinned(ref, override, resolve(ref, null));
    }

    /**
     * Applies pinned-only choices over the already-resolved global icon. Keeping that baseline
     * explicit prevents pinned pages from drifting back to a system icon when no pinned pack is
     * selected.
     */
    @Nullable
    public Drawable resolvePinned(
        @NonNull AppRef ref,
        @Nullable PinnedIconOverride override,
        @Nullable Drawable globalIcon
    ) {
        Drawable icon = loadOverride(override);
        if (icon != null) return icon;

        if (preferences != null) {
            String pinnedPack = preferences.getAppLauncherPinnedIconPackPackage();
            if (pinnedPack != null && !pinnedPack.trim().isEmpty()) {
                icon = loadFromPack(pinnedPack, ref);
                if (icon != null) return icon;
            }
        }

        return globalIcon != null ? globalIcon : resolve(ref, null);
    }

    @Nullable
    public Drawable loadOverride(@Nullable PinnedIconOverride override) {
        if (override == null || !override.isValid()) return null;
        return loadDrawableFromPack(override.iconPackPackage, override.drawableName);
    }

    @Nullable
    public Drawable loadDrawableFromPack(@Nullable String packageName, @Nullable String drawableName) {
        if (packageName == null || drawableName == null) return null;
        try {
            Context iconPackContext = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY);
            int id = iconPackContext.getResources().getIdentifier(drawableName, "drawable", packageName);
            if (id == 0) id = iconPackContext.getResources().getIdentifier(drawableName, "mipmap", packageName);
            if (id == 0) return null;
            return iconPackContext.getResources().getDrawable(id);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    public Drawable loadFromPack(@Nullable String packageName, @NonNull AppRef ref) {
        IconPack pack = iconPackRepository.loadIconPack(packageName);
        if (pack == null) return null;
        String drawableName = pack.drawableForComponent(ref.packageName, ref.activityName, Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
        if (drawableName != null) {
            Drawable mapped = loadDrawableFromPack(pack.info.packageName, drawableName);
            if (mapped != null) return mapped;
        }
        return composeUnmappedIcon(pack, ref);
    }

    @Nullable
    private Drawable composeUnmappedIcon(@NonNull IconPack pack, @NonNull AppRef ref) {
        if (pack.iconBack == null && pack.iconUpon == null && pack.iconMask == null) return null;
        Drawable systemIcon = loadSystemIcon(ref);
        if (systemIcon == null) return null;
        Drawable back = loadDrawableFromPack(pack.info.packageName, pack.iconBack);
        Drawable upon = loadDrawableFromPack(pack.info.packageName, pack.iconUpon);
        if (back == null && upon == null) return null;
        if (back != null && upon != null) {
            return new LayerDrawable(new Drawable[] { back, systemIcon, upon });
        } else if (back != null) {
            return new LayerDrawable(new Drawable[] { back, systemIcon });
        } else {
            return new LayerDrawable(new Drawable[] { systemIcon, upon });
        }
    }

    @Nullable
    private Drawable loadSystemIcon(@NonNull AppRef ref) {
        if (systemIconLoader != null) {
            return systemIconLoader.load(ref);
        }
        try {
            ComponentName componentName = new ComponentName(ref.packageName, IconPackXmlParser.normalizeActivityName(ref.packageName, ref.activityName));
            return packageManager.getActivityIcon(componentName);
        } catch (Exception ignored) {
            try {
                return packageManager.getApplicationIcon(ref.packageName);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }
}
