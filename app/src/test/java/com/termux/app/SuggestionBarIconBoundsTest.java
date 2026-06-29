package com.termux.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class SuggestionBarIconBoundsTest {

    @Test
    public void visibleAlphaBounds_ignoreTransparentCustomIconPadding() {
        Bitmap bitmap = Bitmap.createBitmap(12, 10, Bitmap.Config.ARGB_8888);
        for (int y = 3; y < 8; y++) {
            for (int x = 2; x < 9; x++) {
                bitmap.setPixel(x, y, Color.WHITE);
            }
        }

        assertEquals(new Rect(2, 3, 9, 8), SuggestionBarView.findVisibleAlphaBounds(bitmap));
        bitmap.recycle();
    }

    @Test
    public void visibleAlphaBounds_ignoreLowAlphaShadow() {
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
        bitmap.setPixel(1, 1, 0x20000000);
        for (int y = 3; y < 7; y++) {
            for (int x = 4; x < 8; x++) {
                bitmap.setPixel(x, y, Color.WHITE);
            }
        }

        assertEquals(new Rect(4, 3, 8, 7), SuggestionBarView.findVisibleAlphaBounds(bitmap));
        bitmap.recycle();
    }
}
