package com.termux.app.terminal.unexpected;

import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;
import com.termux.app.terminal.unexpected.vendor.KeyboardData;
import com.termux.app.terminal.unexpected.vendor.KeyValue;
import com.termux.app.terminal.unexpected.vendor.Pointers;
import com.termux.shared.logger.Logger;
import com.termux.shared.view.KeyboardUtils;
import com.termux.view.TerminalView;

public final class TermuxUnexpectedKeyboardController
    implements TermuxUnexpectedKeyboardView.Callback, UnexpectedKeyboardActionDispatcher.TerminalTarget {

    private static final String LOG_TAG = "TermuxUnexpectedKeyboard";

    private enum LayoutKind {
        TEXT,
        NUMERIC,
        GREEK_MATH
    }

    private final TermuxActivity mActivity;
    private final UnexpectedKeyboardLayoutRepository mLayoutRepository;
    private final UnexpectedKeyboardActionDispatcher mActionDispatcher;

    private KeyboardData mTextLayout;
    private KeyboardData mNumericLayout;
    private KeyboardData mGreekMathLayout;
    private LayoutKind mCurrentLayoutKind = LayoutKind.TEXT;
    private TermuxUnexpectedKeyboardView mKeyboardView;

    public TermuxUnexpectedKeyboardController(@NonNull TermuxActivity activity) {
        mActivity = activity;
        mLayoutRepository = new UnexpectedKeyboardLayoutRepository(activity);
        mActionDispatcher = new UnexpectedKeyboardActionDispatcher(this);
    }

    public void attach(@Nullable TermuxUnexpectedKeyboardView keyboardView) {
        mKeyboardView = keyboardView;
        if (mKeyboardView == null) {
            return;
        }
        mKeyboardView.setCallback(this);
        ensureLayoutsLoaded();
        applyCurrentLayout();
        mKeyboardView.setVisibility(isVisible() ? View.VISIBLE : View.GONE);
    }

    public void ensureLayoutsLoaded() {
        if (mTextLayout == null) {
            mTextLayout = mLayoutRepository.loadDefaultTextLayout();
        }
        if (mNumericLayout == null) {
            mNumericLayout = mLayoutRepository.loadNumericLayout();
        }
        if (mGreekMathLayout == null) {
            mGreekMathLayout = mLayoutRepository.loadGreekMathLayout();
        }
    }

    public boolean isVisible() {
        return mActivity.getPreferences() != null && mActivity.getPreferences().isUnexpectedKeyboardEnabled();
    }

    public void setVisible(boolean visible) {
        if (mActivity.getPreferences() == null) {
            return;
        }
        mActivity.getPreferences().setUnexpectedKeyboardEnabled(visible);
        if (visible) {
            KeyboardUtils.hideSoftKeyboard(mActivity, mActivity.getTerminalView());
            KeyboardUtils.disableSoftKeyboard(mActivity, mActivity.getTerminalView());
        } else {
            KeyboardUtils.clearDisableSoftKeyboardFlags(mActivity);
            if (mActivity.getTermuxTerminalViewClient() != null) {
                mActivity.getTermuxTerminalViewClient().setSoftKeyboardState(false, false);
            }
        }
        if (mKeyboardView != null) {
            if (visible) {
                ensureLayoutsLoaded();
                applyCurrentLayout();
            }
            mKeyboardView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (mActivity.getTerminalView() != null) {
            mActivity.getTerminalView().requestFocus();
        }
        mActivity.onUnexpectedKeyboardVisibilityChanged(visible);
    }

    public void toggleVisibility() {
        setVisible(!isVisible());
    }

    public int getKeyboardHeightPx(int availableWidthPx) {
        if (!isVisible() || mKeyboardView == null) {
            return 0;
        }
        ensureLayoutsLoaded();
        applyCurrentLayout();
        return mKeyboardView.getDesiredHeightPx(availableWidthPx);
    }

    @Override
    public void onUnexpectedKey(@NonNull KeyValue keyValue, @NonNull Pointers.Modifiers modifiers) {
        mActionDispatcher.dispatch(keyValue, modifiers);
    }

    @Override
    public android.content.Context getContext() {
        return mActivity;
    }

    @Override
    public void sendText(@NonNull String text, boolean altDown) {
        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView == null) {
            return;
        }
        if (text.length() == 1) {
            terminalView.inputCodePoint(
                TerminalView.KEY_EVENT_SOURCE_VIRTUAL_KEYBOARD,
                text.codePointAt(0),
                false,
                altDown
            );
        } else if (terminalView.getCurrentSession() != null) {
            terminalView.getCurrentSession().write(text);
        }
    }

    @Override
    public void sendKeyEvent(int keyCode, int keyMod) {
        TerminalView terminalView = mActivity.getTerminalView();
        if (terminalView == null) {
            return;
        }
        terminalView.handleKeyCode(keyCode, keyMod);
    }

    @Override
    public void onSwitchToTextLayout() {
        mCurrentLayoutKind = LayoutKind.TEXT;
        applyCurrentLayout();
        mActivity.refreshUnexpectedKeyboardLayout();
    }

    @Override
    public void onSwitchToNumericLayout() {
        mCurrentLayoutKind = LayoutKind.NUMERIC;
        applyCurrentLayout();
        mActivity.refreshUnexpectedKeyboardLayout();
    }

    @Override
    public void onSwitchToGreekMathLayout() {
        mCurrentLayoutKind = LayoutKind.GREEK_MATH;
        applyCurrentLayout();
        mActivity.refreshUnexpectedKeyboardLayout();
    }

    @Override
    public void onSwitchForward() {
        switch (mCurrentLayoutKind) {
            case TEXT:
                mCurrentLayoutKind = LayoutKind.NUMERIC;
                break;
            case NUMERIC:
                mCurrentLayoutKind = LayoutKind.GREEK_MATH;
                break;
            case GREEK_MATH:
            default:
                mCurrentLayoutKind = LayoutKind.TEXT;
                break;
        }
        applyCurrentLayout();
        mActivity.refreshUnexpectedKeyboardLayout();
    }

    @Override
    public void onSwitchBackward() {
        switch (mCurrentLayoutKind) {
            case TEXT:
                mCurrentLayoutKind = LayoutKind.GREEK_MATH;
                break;
            case NUMERIC:
                mCurrentLayoutKind = LayoutKind.TEXT;
                break;
            case GREEK_MATH:
            default:
                mCurrentLayoutKind = LayoutKind.NUMERIC;
                break;
        }
        applyCurrentLayout();
        mActivity.refreshUnexpectedKeyboardLayout();
    }

    @Override
    public void onToggleShiftLock() {
        if (mKeyboardView != null) {
            mKeyboardView.toggleShiftLock();
        }
    }

    @Override
    public void onPasteFromClipboard() {
        CharSequence clipboardText = UnexpectedKeyboardActionDispatcher.readClipboardText(mActivity);
        if (TextUtils.isEmpty(clipboardText)) {
            Toast.makeText(mActivity, com.termux.R.string.paste_error_text, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mActivity.getCurrentSession() != null && mActivity.getCurrentSession().getEmulator() != null) {
            mActivity.getCurrentSession().getEmulator().paste(clipboardText.toString());
        }
    }

    @Override
    public void onShowKeyboardMessage(int stringResId) {
        Logger.showToast(mActivity, mActivity.getString(stringResId), true);
    }

    private void applyCurrentLayout() {
        if (mKeyboardView == null) {
            return;
        }
        ensureLayoutsLoaded();
        KeyboardData keyboardData;
        switch (mCurrentLayoutKind) {
            case NUMERIC:
                keyboardData = mNumericLayout;
                break;
            case GREEK_MATH:
                keyboardData = mGreekMathLayout;
                break;
            case TEXT:
            default:
                keyboardData = mTextLayout;
                break;
        }
        if (keyboardData == null) {
            Logger.logError(LOG_TAG, "No keyboard layout available for " + mCurrentLayoutKind);
            return;
        }
        mKeyboardView.setKeyboard(keyboardData);
    }
}
