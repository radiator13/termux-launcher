package com.termux.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;

import com.termux.R;
import com.termux.app.terminal.unexpected.TermuxUnexpectedKeyboardController;
import com.termux.app.terminal.unexpected.TermuxUnexpectedKeyboardView;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class TermuxActivityUnexpectedKeyboardTest {

    @Test
    public void controllerTogglesEmbeddedKeyboardVisibilityAndPrimaryHeight() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        activity.mTerminalView = activity.findViewById(R.id.terminal_view);
        activity.mUnexpectedKeyboardView = activity.findViewById(R.id.unexpected_keyboard_view);

        TermuxAppSharedPreferences preferences = ReflectionHelpers.callConstructor(
            TermuxAppSharedPreferences.class,
            ReflectionHelpers.ClassParameter.from(Context.class, activity)
        );
        ReflectionHelpers.setField(activity, "mPreferences", preferences);

        TermuxUnexpectedKeyboardController controller = new TermuxUnexpectedKeyboardController(activity);
        activity.mUnexpectedKeyboardController = controller;
        controller.attach((TermuxUnexpectedKeyboardView) activity.mUnexpectedKeyboardView);

        controller.setVisible(true);
        assertTrue(preferences.isUnexpectedKeyboardEnabled());
        assertTrue(activity.isUnexpectedKeyboardVisible());
        assertEqualsVisible(View.VISIBLE, activity.mUnexpectedKeyboardView);

        int primaryHeight = ReflectionHelpers.callInstanceMethod(
            activity,
            "getAccessoryPrimaryHeightPx",
            ReflectionHelpers.ClassParameter.from(int.class, 120)
        );
        assertTrue(primaryHeight > 120);

        controller.setVisible(false);
        assertFalse(preferences.isUnexpectedKeyboardEnabled());
        assertFalse(activity.isUnexpectedKeyboardVisible());
        assertEqualsVisible(View.GONE, activity.mUnexpectedKeyboardView);
    }

    private static void assertEqualsVisible(int expectedVisibility, View view) {
        org.junit.Assert.assertEquals(expectedVisibility, view.getVisibility());
    }
}
