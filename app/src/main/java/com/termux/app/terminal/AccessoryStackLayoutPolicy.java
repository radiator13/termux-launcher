package com.termux.app.terminal;

public final class AccessoryStackLayoutPolicy {

    private AccessoryStackLayoutPolicy() {}

    public static int computeCombinedHeight(int toolbarHeightPx, int appsBarHeightPx, int azRowHeightPx, int appsBarGapPx) {
        int toolbar = Math.max(0, toolbarHeightPx);
        int apps = Math.max(0, appsBarHeightPx);
        int az = Math.max(0, azRowHeightPx);
        int gap = Math.max(0, appsBarGapPx);
        return toolbar + apps + az + gap;
    }

    public static int computeAppsBarInterRowGapPx(boolean azEnabled, float density, float iconScale) {
        if (!azEnabled)
            return 0;
        float safeDensity = Math.max(0f, density);
        float safeIconScale = Math.max(0f, iconScale);
        return Math.round(safeDensity * (3f + (Math.max(0f, safeIconScale - 1f) * 2f)));
    }

    public static int computePageIndicatorBandHeightPx(boolean azEnabled, boolean compactDock, float density) {
        if (!azEnabled)
            return 0;
        return Math.round(Math.max(0f, density) * (compactDock ? 2f : 9f));
    }

    public static int computeAzRowHeightPx(boolean azEnabled, boolean compactDock, float density) {
        if (!azEnabled)
            return 0;
        return Math.round(Math.max(0f, density) * (compactDock ? 14f : 19f));
    }

    public static int computeTerminalToolbarHeightPx(int baseHeightPx, int rowCount, float scaleFactor, boolean compactDock) {
        int safeBaseHeight = Math.max(0, baseHeightPx);
        int safeRows = Math.max(0, rowCount);
        float safeScale = Math.max(0f, scaleFactor);
        float compactFactor = compactDock ? 0.78f : 1f;
        return Math.round(safeBaseHeight * safeRows * safeScale * compactFactor);
    }
}
