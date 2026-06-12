package com.termux.app.terminal.unexpected;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Application;
import android.os.Build;

import com.termux.app.terminal.unexpected.vendor.KeyboardData;
import com.termux.app.terminal.unexpected.vendor.KeyValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class UnexpectedKeyboardLayoutRepositoryTest {

    @Test
    public void defaultLayout_parsesCustomTerminalLayout() {
        UnexpectedKeyboardLayoutRepository repository =
            new UnexpectedKeyboardLayoutRepository(RuntimeEnvironment.getApplication());

        KeyboardData keyboardData = repository.loadDefaultTextLayout();

        assertNotNull(keyboardData);
        assertEquals("QWERTY (cstm)", keyboardData.name);
        assertNotNull(keyboardData.findKeyWithValue(KeyValue.getKeyByName("switch_numeric")));
        assertNotNull(keyboardData.findKeyWithValue(KeyValue.getKeyByName("meta")));
        assertNotNull(keyboardData.findKeyWithValue(KeyValue.getKeyByName("compose")));
    }

    @Test
    public void missingLayout_fallsBackToBundledQwerty() {
        UnexpectedKeyboardLayoutRepository repository =
            new UnexpectedKeyboardLayoutRepository(RuntimeEnvironment.getApplication());

        KeyboardData keyboardData = repository.loadLayoutWithFallback(
            "unexpected_keyboard/missing.xml",
            UnexpectedKeyboardLayoutRepository.FALLBACK_LAYOUT_ASSET
        );

        assertNotNull(keyboardData);
        assertEquals("QWERTY (US)", keyboardData.name);
    }
}
