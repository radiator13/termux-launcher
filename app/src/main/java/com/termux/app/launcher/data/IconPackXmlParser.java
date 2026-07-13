package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.IconPackInfo;

import org.xmlpull.v1.XmlPullParser;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IconPackXmlParser {
    private IconPackXmlParser() {
    }

    @NonNull
    public static IconPack parse(@NonNull IconPackInfo info, @Nullable XmlPullParser appfilter, @Nullable XmlPullParser drawable) throws Exception {
        Map<String, String> componentDrawables = new LinkedHashMap<>();
        Map<String, String> calendarPrefixes = new LinkedHashMap<>();
        Map<String, IconPackDrawableItem> drawableItems = new LinkedHashMap<>();
        List<String> iconBacks = new ArrayList<>();
        List<String> iconMasks = new ArrayList<>();
        List<String> iconUpons = new ArrayList<>();
        float scale = 1f;

        if (appfilter != null) {
            int eventType = appfilter.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = appfilter.getName();
                    if ("item".equals(name)) {
                        String component = normalizeComponent(appfilter.getAttributeValue(null, "component"));
                        String drawableName = normalizeDrawableName(appfilter.getAttributeValue(null, "drawable"));
                        if (component != null && drawableName != null) {
                            componentDrawables.put(component, drawableName);
                            putDrawable(drawableItems, drawableName, labelFromDrawable(drawableName));
                        }
                    } else if ("calendar".equals(name)) {
                        String component = normalizeComponent(appfilter.getAttributeValue(null, "component"));
                        String prefix = normalizeDrawableName(firstNonEmpty(
                            appfilter.getAttributeValue(null, "prefix"),
                            appfilter.getAttributeValue(null, "drawable")
                        ));
                        if (component != null && prefix != null) {
                            calendarPrefixes.put(component, prefix);
                        }
                    } else if ("iconback".equals(name)) {
                        addImageAttributes(appfilter, iconBacks);
                    } else if ("iconmask".equals(name)) {
                        addImageAttributes(appfilter, iconMasks);
                    } else if ("iconupon".equals(name)) {
                        addImageAttributes(appfilter, iconUpons);
                    } else if ("scale".equals(name)) {
                        String value = appfilter.getAttributeValue(null, "factor");
                        if (value == null) value = appfilter.getAttributeValue(null, "value");
                        try {
                            if (value != null) scale = Float.parseFloat(value);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                eventType = appfilter.next();
            }
        }

        if (drawable != null) {
            int eventType = drawable.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = drawable.getName();
                    if ("item".equals(name) || "icon".equals(name)) {
                        String drawableName = normalizeDrawableName(firstNonEmpty(
                            drawable.getAttributeValue(null, "drawable"),
                            drawable.getAttributeValue(null, "name"),
                            drawable.getAttributeValue(null, "component")
                        ));
                        String label = firstNonEmpty(
                            drawable.getAttributeValue(null, "name"),
                            drawable.getAttributeValue(null, "label"),
                            drawableName
                        );
                        if (drawableName != null) {
                            putDrawable(drawableItems, drawableName, label);
                        }
                    }
                }
                eventType = drawable.next();
            }
        }

        return new IconPack(info, componentDrawables, calendarPrefixes, drawableItems,
            iconBacks, iconMasks, iconUpons, scale);
    }

    @NonNull
    public static String componentKey(@NonNull String packageName, @NonNull String activityName) {
        return (packageName + "/" + normalizeActivityName(packageName, activityName)).toLowerCase(Locale.US);
    }

    @NonNull
    public static String normalizeActivityName(@NonNull String packageName, @NonNull String activityName) {
        if (activityName.startsWith(".")) return packageName + activityName;
        return activityName;
    }

    @Nullable
    static String normalizeComponent(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.startsWith("ComponentInfo{") && value.endsWith("}")) {
            value = value.substring("ComponentInfo{".length(), value.length() - 1);
        }
        int slash = value.indexOf('/');
        if (slash <= 0 || slash >= value.length() - 1) return null;
        String pkg = value.substring(0, slash);
        String cls = value.substring(slash + 1);
        return componentKey(pkg, cls);
    }

    @Nullable
    static String normalizeDrawableName(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        int slash = value.lastIndexOf('/');
        if (slash >= 0 && slash < value.length() - 1) value = value.substring(slash + 1);
        int dot = value.lastIndexOf('.');
        if (dot > 0) value = value.substring(0, dot);
        return value.toLowerCase(Locale.US);
    }

    private static void putDrawable(@NonNull Map<String, IconPackDrawableItem> out, @NonNull String drawableName, @Nullable String label) {
        String key = drawableName.toLowerCase(Locale.US);
        if (!out.containsKey(key)) {
            out.put(key, new IconPackDrawableItem(drawableName, labelFromDrawable(label == null ? drawableName : label)));
        }
    }

    @Nullable
    private static void addImageAttributes(@NonNull XmlPullParser parser, @NonNull List<String> out) {
        for (int i = 1; i <= 6; i++) {
            String value = normalizeDrawableName(parser.getAttributeValue(null, "img" + i));
            if (value != null && !out.contains(value)) out.add(value);
        }
        String fallback = normalizeDrawableName(firstNonEmpty(
            parser.getAttributeValue(null, "drawable"), parser.getAttributeValue(null, "name")));
        if (fallback != null && !out.contains(fallback)) out.add(fallback);
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    @NonNull
    private static String labelFromDrawable(@Nullable String drawableName) {
        if (drawableName == null || drawableName.trim().isEmpty()) return "";
        String[] parts = drawableName.replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part.substring(1));
        }
        return out.toString();
    }
}
