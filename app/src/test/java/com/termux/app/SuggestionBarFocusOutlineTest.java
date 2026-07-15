package com.termux.app;

import android.graphics.Bitmap;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the focus-outline pixel path used by the dock AZ focus ring.
 * Production uses Rust via JNI; this test drives the Java thin fallback which
 * mirrors the same dilate semantics when the native library is absent under Robolectric.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P)
public class SuggestionBarFocusOutlineTest {

    @Test
    public void solidRectProducesNonEmptyHollowOutline() {
        int w = 16;
        int h = 16;
        int[] src = new int[w * h];
        for (int i = 0; i < src.length; i++) {
            src[i] = 0xFF0000FF;
        }
        Bitmap mask = SuggestionBarView.buildFocusOutlineMaskJavaFallback(src, w, h, 2, 2);
        assertNotNull(mask);
        assertEquals(w + 2 * (2 + 2), mask.getWidth());
        assertEquals(h + 2 * (2 + 2), mask.getHeight());

        int opaque = 0;
        int[] out = new int[mask.getWidth() * mask.getHeight()];
        mask.getPixels(out, 0, mask.getWidth(), 0, 0, mask.getWidth(), mask.getHeight());
        for (int p : out) {
            if ((p >>> 24) > 0) {
                opaque++;
            }
        }
        assertTrue("outline must have opaque pixels", opaque > 0);
        assertTrue("outline should be hollow", opaque < out.length);

        int cx = mask.getWidth() / 2;
        int cy = mask.getHeight() / 2;
        int center = out[cy * mask.getWidth() + cx];
        // With gap=2, center of a solid rect should be punched out
        assertEquals("center should be transparent (hollow ring)", 0, center >>> 24);
    }

    @Test
    public void transparentSourceProducesEmptyMask() {
        int w = 10;
        int h = 10;
        int[] src = new int[w * h];
        Bitmap mask = SuggestionBarView.buildFocusOutlineMaskJavaFallback(src, w, h, 1, 2);
        int[] out = new int[mask.getWidth() * mask.getHeight()];
        mask.getPixels(out, 0, mask.getWidth(), 0, 0, mask.getWidth(), mask.getHeight());
        for (int p : out) {
            assertEquals(0, p >>> 24);
        }
    }
}
