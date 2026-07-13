package com.termux.app;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarPagingTest {
    @Test
    public void pinnedPages_wrapInBothDirections() {
        assertEquals(0, SuggestionBarView.wrapPageIndex(3, 3));
        assertEquals(2, SuggestionBarView.wrapPageIndex(-1, 3));
        assertEquals(1, SuggestionBarView.wrapPageIndex(4, 3));
    }

    @Test
    public void pageWrap_handlesEmptyAndSinglePage() {
        assertEquals(0, SuggestionBarView.wrapPageIndex(5, 0));
        assertEquals(0, SuggestionBarView.wrapPageIndex(-5, 1));
    }
}
