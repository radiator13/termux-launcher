package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AccessoryStackLayoutPolicyTest {

    @Test
    public void combinedHeight_sumsAllVisibleSegments() {
        int combined = AccessoryStackLayoutPolicy.computeCombinedHeight(120, 42, 20, 6);
        assertEquals(188, combined);
    }

    @Test
    public void combinedHeight_clampsNegativeValues() {
        int combined = AccessoryStackLayoutPolicy.computeCombinedHeight(120, -5, 20, -2);
        assertEquals(140, combined);
    }

    @Test
    public void appsBarInterRowGap_isZeroWhenAzDisabled() {
        int gap = AccessoryStackLayoutPolicy.computeAppsBarInterRowGapPx(false, 3f, 1.5f);
        assertEquals(0, gap);
    }

    @Test
    public void appsBarInterRowGap_scalesWithIconScaleWhenAzEnabled() {
        int gapDefaultScale = AccessoryStackLayoutPolicy.computeAppsBarInterRowGapPx(true, 3f, 1f);
        int gapLargeScale = AccessoryStackLayoutPolicy.computeAppsBarInterRowGapPx(true, 3f, 1.5f);
        assertEquals(9, gapDefaultScale);
        assertEquals(12, gapLargeScale);
    }

    @Test
    public void pageIndicatorBandHeight_usesThinStripInCompactMode() {
        assertEquals(27, AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx(true, false, 3f));
        assertEquals(9, AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx(true, true, 3f));
        assertEquals(0, AccessoryStackLayoutPolicy.computePageIndicatorBandHeightPx(false, true, 3f));
    }

    @Test
    public void azRowHeight_tightensInCompactMode() {
        assertEquals(57, AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, false, 3f));
        assertEquals(51, AccessoryStackLayoutPolicy.computeAzRowHeightPx(true, true, 3f));
        assertEquals(0, AccessoryStackLayoutPolicy.computeAzRowHeightPx(false, true, 3f));
    }

    @Test
    public void terminalToolbarHeight_reducesForCompactDock() {
        assertEquals(228, AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx(38, 2, 3f, false));
        assertEquals(196, AccessoryStackLayoutPolicy.computeTerminalToolbarHeightPx(38, 2, 3f, true));
    }
}
