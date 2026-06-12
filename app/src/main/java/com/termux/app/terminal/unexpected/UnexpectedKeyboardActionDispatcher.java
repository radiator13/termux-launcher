package com.termux.app.terminal.unexpected;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.app.terminal.unexpected.vendor.KeyValue;
import com.termux.app.terminal.unexpected.vendor.Pointers;
import com.termux.terminal.KeyHandler;

final class UnexpectedKeyboardActionDispatcher {

    interface TerminalTarget {
        Context getContext();
        void sendText(@NonNull String text, boolean altDown);
        void sendKeyEvent(int keyCode, int keyMod);
        void onSwitchToTextLayout();
        void onSwitchToNumericLayout();
        void onSwitchToGreekMathLayout();
        void onSwitchForward();
        void onSwitchBackward();
        void onToggleShiftLock();
        void onPasteFromClipboard();
        void onShowKeyboardMessage(int stringResId);
    }

    private final TerminalTarget mTarget;

    UnexpectedKeyboardActionDispatcher(@NonNull TerminalTarget target) {
        mTarget = target;
    }

    void dispatch(@NonNull KeyValue keyValue, @NonNull Pointers.Modifiers modifiers) {
        switch (keyValue.getKind()) {
            case Char:
                sendText(String.valueOf(keyValue.getChar()), modifiers);
                return;
            case String:
                sendText(keyValue.getString(), modifiers);
                return;
            case Keyevent:
                sendKeyEvent(keyValue.getKeyevent(), modifiers);
                return;
            case Event:
                dispatchEvent(keyValue.getEvent());
                return;
            case Editing:
                dispatchEditing(keyValue.getEditing(), modifiers);
                return;
            case Slider:
                dispatchSlider(keyValue.getSlider(), keyValue.getSliderRepeat(), modifiers);
                return;
            case Macro:
                for (KeyValue child : keyValue.getMacro()) {
                    dispatch(child, modifiers);
                }
                return;
            default:
                return;
        }
    }

    private void sendText(@NonNull String text, @NonNull Pointers.Modifiers modifiers) {
        boolean altDown = modifiers.has(KeyValue.Modifier.ALT) || modifiers.has(KeyValue.Modifier.META);
        mTarget.sendText(text, altDown);
    }

    private void sendKeyEvent(int keyCode, @NonNull Pointers.Modifiers modifiers) {
        int keyMod = 0;
        if (modifiers.has(KeyValue.Modifier.CTRL)) {
            keyMod |= KeyHandler.KEYMOD_CTRL;
        }
        if (modifiers.has(KeyValue.Modifier.ALT) || modifiers.has(KeyValue.Modifier.META)) {
            keyMod |= KeyHandler.KEYMOD_ALT;
        }
        if (modifiers.has(KeyValue.Modifier.SHIFT)) {
            keyMod |= KeyHandler.KEYMOD_SHIFT;
        }
        mTarget.sendKeyEvent(keyCode, keyMod);
    }

    private void dispatchEvent(@NonNull KeyValue.Event event) {
        switch (event) {
            case SWITCH_TEXT:
            case SWITCH_BACK_EMOJI:
            case SWITCH_BACK_CLIPBOARD:
                mTarget.onSwitchToTextLayout();
                return;
            case SWITCH_NUMERIC:
                mTarget.onSwitchToNumericLayout();
                return;
            case SWITCH_GREEKMATH:
                mTarget.onSwitchToGreekMathLayout();
                return;
            case SWITCH_FORWARD:
                mTarget.onSwitchForward();
                return;
            case SWITCH_BACKWARD:
                mTarget.onSwitchBackward();
                return;
            case CAPS_LOCK:
                mTarget.onToggleShiftLock();
                return;
            case SWITCH_CLIPBOARD:
                mTarget.onPasteFromClipboard();
                return;
            case CONFIG:
                mTarget.onShowKeyboardMessage(R.string.msg_unexpected_keyboard_settings_unavailable);
                return;
            case CHANGE_METHOD_PICKER:
            case CHANGE_METHOD_NEXT:
            case CHANGE_METHOD_PREV:
                mTarget.onShowKeyboardMessage(R.string.msg_unexpected_keyboard_change_method_unavailable);
                return;
            case SWITCH_VOICE_TYPING:
            case SWITCH_VOICE_TYPING_CHOOSER:
                mTarget.onShowKeyboardMessage(R.string.msg_unexpected_keyboard_voice_typing_unavailable);
                return;
            case ACTION:
                sendKeyEvent(KeyEvent.KEYCODE_ENTER, Pointers.Modifiers.EMPTY);
                return;
            case SWITCH_EMOJI:
                mTarget.onShowKeyboardMessage(R.string.msg_unexpected_keyboard_emoji_not_yet_supported);
                return;
            default:
                return;
        }
    }

    private void dispatchEditing(@NonNull KeyValue.Editing editing, @NonNull Pointers.Modifiers modifiers) {
        switch (editing) {
            case SPACE_BAR:
                sendText(" ", modifiers);
                return;
            case BACKSPACE:
                sendKeyEvent(KeyEvent.KEYCODE_DEL, modifiers);
                return;
            case PASTE:
            case PASTE_PLAIN:
                mTarget.onPasteFromClipboard();
                return;
            default:
                return;
        }
    }

    private void dispatchSlider(@NonNull KeyValue.Slider slider, int repeatCount, @NonNull Pointers.Modifiers modifiers) {
        int iterations = Math.max(1, Math.abs(repeatCount));
        int keyCode;
        switch (slider) {
            case Cursor_left:
            case Selection_cursor_left:
                keyCode = KeyEvent.KEYCODE_DPAD_LEFT;
                break;
            case Cursor_right:
            case Selection_cursor_right:
                keyCode = KeyEvent.KEYCODE_DPAD_RIGHT;
                break;
            case Cursor_up:
                keyCode = KeyEvent.KEYCODE_DPAD_UP;
                break;
            case Cursor_down:
                keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
                break;
            default:
                return;
        }
        for (int i = 0; i < iterations; i++) {
            sendKeyEvent(keyCode, modifiers);
        }
    }

    static CharSequence readClipboardText(@NonNull Context context) {
        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            return null;
        }
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            return null;
        }
        return clipData.getItemAt(0).coerceToText(context);
    }
}
