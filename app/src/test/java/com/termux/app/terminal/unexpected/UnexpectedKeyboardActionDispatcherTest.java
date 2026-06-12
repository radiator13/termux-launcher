package com.termux.app.terminal.unexpected;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.KeyEvent;

import androidx.test.core.app.ApplicationProvider;

import com.termux.app.terminal.unexpected.vendor.KeyModifier;
import com.termux.app.terminal.unexpected.vendor.KeyValue;
import com.termux.app.terminal.unexpected.vendor.Pointers;
import com.termux.terminal.KeyHandler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
public class UnexpectedKeyboardActionDispatcherTest {

    private FakeTarget target;
    private UnexpectedKeyboardActionDispatcher dispatcher;

    @Before
    public void setUp() {
        target = new FakeTarget();
        dispatcher = new UnexpectedKeyboardActionDispatcher(target);
    }

    @Test
    public void dispatch_sendsPlainTextAndSpecialKeys() {
        dispatcher.dispatch(KeyValue.getKeyByName("a"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("esc"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("tab"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("enter"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("left"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("delete"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("home"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("end"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("page_up"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("page_down"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("f5"), Pointers.Modifiers.EMPTY);

        assertEquals("a", target.lastText);
        assertTrue(target.sentKeyCodes.contains(KeyEvent.KEYCODE_ESCAPE));
        assertTrue(target.sentKeyCodes.contains(KeyEvent.KEYCODE_TAB));
        assertTrue(target.sentKeyCodes.contains(KeyEvent.KEYCODE_ENTER));
        assertTrue(target.sentKeyCodes.contains(KeyEvent.KEYCODE_DPAD_LEFT));
        assertTrue(target.sentKeyCodes.contains(KeyEvent.KEYCODE_FORWARD_DEL));
        assertTrue(target.sentKeyCodes.contains(KeyEvent.KEYCODE_MOVE_HOME));
        assertTrue(target.sentKeyCodes.contains(KeyEvent.KEYCODE_MOVE_END));
        assertTrue(target.sentKeyCodes.contains(KeyEvent.KEYCODE_PAGE_UP));
        assertTrue(target.sentKeyCodes.contains(KeyEvent.KEYCODE_PAGE_DOWN));
        assertTrue(target.sentKeyCodes.contains(KeyEvent.KEYCODE_F5));
    }

    @Test
    public void dispatch_appliesCtrlAndAltModifiers() {
        dispatcher.dispatch(
            KeyModifier.modify(
                KeyValue.getKeyByName("a"),
                Pointers.Modifiers.ofArrayForTests(
                    KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL)
                )
            ),
            Pointers.Modifiers.ofArrayForTests(
                KeyValue.makeInternalModifier(KeyValue.Modifier.CTRL)
            )
        );
        dispatcher.dispatch(
            KeyModifier.modify(
                KeyValue.getKeyByName("b"),
                Pointers.Modifiers.ofArrayForTests(
                    KeyValue.makeInternalModifier(KeyValue.Modifier.ALT)
                )
            ),
            Pointers.Modifiers.ofArrayForTests(
                KeyValue.makeInternalModifier(KeyValue.Modifier.ALT)
            )
        );

        assertEquals(KeyEvent.KEYCODE_A, (int) target.sentKeyCodes.get(0));
        assertEquals(KeyHandler.KEYMOD_CTRL, (int) target.sentKeyMods.get(0));
        assertEquals(KeyEvent.KEYCODE_B, (int) target.sentKeyCodes.get(1));
        assertEquals(KeyHandler.KEYMOD_ALT, (int) target.sentKeyMods.get(1));
    }

    @Test
    public void dispatch_switchEventsRouteToLayoutCallbacks() {
        dispatcher.dispatch(KeyValue.getKeyByName("switch_numeric"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("switch_greekmath"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("switch_text"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("switch_forward"), Pointers.Modifiers.EMPTY);
        dispatcher.dispatch(KeyValue.getKeyByName("switch_backward"), Pointers.Modifiers.EMPTY);

        assertEquals(1, target.numericSwitchCount);
        assertEquals(1, target.greekSwitchCount);
        assertEquals(1, target.textSwitchCount);
        assertEquals(1, target.forwardSwitchCount);
        assertEquals(1, target.backwardSwitchCount);
    }

    private static final class FakeTarget implements UnexpectedKeyboardActionDispatcher.TerminalTarget {
        final java.util.List<Integer> sentKeyCodes = new java.util.ArrayList<>();
        final java.util.List<Integer> sentKeyMods = new java.util.ArrayList<>();
        String lastText;
        boolean lastAltDown;
        int textSwitchCount;
        int numericSwitchCount;
        int greekSwitchCount;
        int forwardSwitchCount;
        int backwardSwitchCount;

        @Override
        public android.content.Context getContext() {
            return ApplicationProvider.getApplicationContext();
        }

        @Override
        public void sendText(String text, boolean altDown) {
            lastText = text;
            lastAltDown = altDown;
        }

        @Override
        public void sendKeyEvent(int keyCode, int keyMod) {
            sentKeyCodes.add(keyCode);
            sentKeyMods.add(keyMod);
        }

        @Override
        public void onSwitchToTextLayout() {
            textSwitchCount++;
        }

        @Override
        public void onSwitchToNumericLayout() {
            numericSwitchCount++;
        }

        @Override
        public void onSwitchToGreekMathLayout() {
            greekSwitchCount++;
        }

        @Override
        public void onSwitchForward() {
            forwardSwitchCount++;
        }

        @Override
        public void onSwitchBackward() {
            backwardSwitchCount++;
        }

        @Override
        public void onToggleShiftLock() {
        }

        @Override
        public void onPasteFromClipboard() {
        }

        @Override
        public void onShowKeyboardMessage(int stringResId) {
        }
    }
}
