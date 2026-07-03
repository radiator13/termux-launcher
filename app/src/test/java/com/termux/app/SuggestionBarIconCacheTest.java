package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarIconCacheTest {

    private SuggestionBarView suggestionBarView;
    private LruCache<String, Drawable> renderedIconCache;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        suggestionBarView = new SuggestionBarView(context, null);
        renderedIconCache = ReflectionHelpers.getField(suggestionBarView, "normalizedIconCache");
    }

    @Test
    public void clearAppCache_evictsRenderedIconBitmaps() {
        renderedIconCache.put("cached-app", new ColorDrawable(0xFF112233));

        suggestionBarView.clearAppCache();

        assertEquals(0, renderedIconCache.size());
    }

    @Test
    public void persistedPinnedIconMutation_evictsRenderedIconBitmaps() {
        renderedIconCache.put("cached-pinned-app", new ColorDrawable(0xFF445566));

        ReflectionHelpers.callInstanceMethod(suggestionBarView, "persistPinsAndReload");

        assertEquals(0, renderedIconCache.size());
    }
}
