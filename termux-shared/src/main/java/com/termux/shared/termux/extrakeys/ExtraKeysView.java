package com.termux.shared.termux.extrakeys;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.AttributeSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.GridLayout;
import android.widget.PopupWindow;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.button.MaterialButton;
import com.termux.shared.R;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.shared.theme.ThemeUtils;

/**
 * A {@link View} showing extra keys (such as Escape, Ctrl, Alt) not normally available on an Android soft
 * keyboards.
 *
 * To use it, add following to a layout file and import it in your activity layout file or inflate
 * it with a {@link androidx.viewpager.widget.ViewPager}.:
 * {@code
 * <?xml version="1.0" encoding="utf-8"?>
 * <com.termux.shared.termux.extrakeys.ExtraKeysView xmlns:android="http://schemas.android.com/apk/res/android"
 *     android:id="@+id/extra_keys"
 *     style="?android:attr/buttonBarStyle"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     android:layout_alignParentBottom="true"
 *     android:orientation="horizontal" />
 * }
 *
 * Then in your activity, get its reference by a call to {@link android.app.Activity#findViewById(int)}
 * or {@link LayoutInflater#inflate(int, ViewGroup)} if using {@link androidx.viewpager.widget.ViewPager}.
 * Then call {@link #setExtraKeysViewClient(IExtraKeysView)} and pass it the implementation of
 * {@link IExtraKeysView} so that you can receive callbacks. You can also override other values set
 * in {@link ExtraKeysView#ExtraKeysView(Context, AttributeSet)} by calling the respective functions.
 * If you extend {@link ExtraKeysView}, you can also set them in the constructor, but do call super().
 *
 * After this you will have to make a call to {@link ExtraKeysView#reload(ExtraKeysInfo, float) and pass
 * it the {@link ExtraKeysInfo} to load and display the extra keys. Read its class javadocs for more
 * info on how to create it.
 *
 * Termux app defines the view in res/layout/view_terminal_toolbar_extra_keys and
 * inflates it in TerminalToolbarViewPager.instantiateItem() and sets the {@link ExtraKeysView} client
 * and calls {@link ExtraKeysView#reload(ExtraKeysInfo).
 * The {@link ExtraKeysInfo} is created by TermuxAppSharedProperties.setExtraKeys().
 * Then its got and the view height is adjusted in TermuxActivity.setTerminalToolbarHeight().
 * The client used is TermuxTerminalExtraKeys, which extends
 * {@link TerminalExtraKeys } to handle Termux app specific logic and
 * leave the rest to the super class.
 */
public final class ExtraKeysView extends GridLayout {

    private enum KeyVisualState {
        RESTING,
        PRESSED,
        REPEAT_HELD,
        STICKY_ACTIVE,
        STICKY_LOCKED,
        POPUP_ARMED,
        POPUP_SELECTED
    }

    /**
     * The client for the {@link ExtraKeysView}.
     */
    public interface IExtraKeysView {

        /**
         * This is called by {@link ExtraKeysView} when a button is clicked. This is also called
         * for {@link #mRepetitiveKeys} and {@link ExtraKeyButton} that have a popup set.
         * However, this is not called for {@link #mSpecialButtons}, whose state can instead be read
         * via a call to {@link #readSpecialButton(SpecialButton, boolean)}.
         *
         * @param view The view that was clicked.
         * @param buttonInfo The {@link ExtraKeyButton} for the button that was clicked.
         *                   The button may be a {@link ExtraKeyButton#KEY_MACRO} set which can be
         *                   checked with a call to {@link ExtraKeyButton#isMacro()}.
         * @param button The {@link MaterialButton} that was clicked.
         */
        void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button);

        /**
         * This is called by {@link ExtraKeysView} when a button is clicked so that the client
         * can perform any hepatic feedback. This is only called in the {@link MaterialButton.OnClickListener}
         * and not for every repeat. Its also called for {@link #mSpecialButtons}.
         *
         * @param view The view that was clicked.
         * @param buttonInfo The {@link ExtraKeyButton} for the button that was clicked.
         * @param button The {@link MaterialButton} that was clicked.
         * @return Return {@code true} if the client handled the feedback, otherwise {@code false}
         * so that {@link ExtraKeysView#performExtraKeyButtonHapticFeedback(View, ExtraKeyButton, MaterialButton)}
         * can handle it depending on system settings.
         */
        boolean performExtraKeyButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button);
    }

    /**
     * Defines the default value for {@link #mButtonTextColor} defined by current theme.
     */
    public static final int ATTR_BUTTON_TEXT_COLOR = R.attr.extraKeysButtonTextColor;

    /**
     * Defines the default value for {@link #mButtonActiveTextColor} defined by current theme.
     */
    public static final int ATTR_BUTTON_ACTIVE_TEXT_COLOR = R.attr.extraKeysButtonActiveTextColor;

    /**
     * Defines the default value for {@link #mButtonBackgroundColor} defined by current theme.
     */
    public static final int ATTR_BUTTON_BACKGROUND_COLOR = R.attr.extraKeysButtonBackgroundColor;

    /**
     * Defines the default value for {@link #mButtonActiveBackgroundColor} defined by current theme.
     */
    public static final int ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR = R.attr.extraKeysButtonActiveBackgroundColor;

    /**
     * Defines the default fallback value for {@link #mButtonTextColor} if {@link #ATTR_BUTTON_TEXT_COLOR} is undefined.
     */
    public static final int DEFAULT_BUTTON_TEXT_COLOR = 0xFFFFFFFF;

    /**
     * Defines the default fallback value for {@link #mButtonActiveTextColor} if {@link #ATTR_BUTTON_ACTIVE_TEXT_COLOR} is undefined.
     */
    public static final int DEFAULT_BUTTON_ACTIVE_TEXT_COLOR = 0xFF80DEEA;

    /**
     * Defines the default fallback value for {@link #mButtonBackgroundColor} if {@link #ATTR_BUTTON_BACKGROUND_COLOR} is undefined.
     */
    public static final int DEFAULT_BUTTON_BACKGROUND_COLOR = 0x00000000;

    /**
     * Defines the default fallback value for {@link #mButtonActiveBackgroundColor} if {@link #ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR} is undefined.
     */
    public static final int DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR = 0xFF7F7F7F;

    /**
     * Defines the minimum allowed duration in milliseconds for {@link #mLongPressTimeout}.
     */
    public static final int MIN_LONG_PRESS_DURATION = 200;

    /**
     * Defines the maximum allowed duration in milliseconds for {@link #mLongPressTimeout}.
     */
    public static final int MAX_LONG_PRESS_DURATION = 3000;

    /**
     * Defines the fallback duration in milliseconds for {@link #mLongPressTimeout}.
     */
    public static final int FALLBACK_LONG_PRESS_DURATION = 400;

    /**
     * Defines the minimum allowed duration in milliseconds for {@link #mLongPressRepeatDelay}.
     */
    public static final int MIN_LONG_PRESS__REPEAT_DELAY = 5;

    /**
     * Defines the maximum allowed duration in milliseconds for {@link #mLongPressRepeatDelay}.
     */
    public static final int MAX_LONG_PRESS__REPEAT_DELAY = 2000;

    /**
     * Defines the default duration in milliseconds for {@link #mLongPressRepeatDelay}.
     */
    public static final int DEFAULT_LONG_PRESS_REPEAT_DELAY = 80;

    /**
     * The implementation of the {@link IExtraKeysView} that acts as a client for the {@link ExtraKeysView}.
     */
    protected IExtraKeysView mExtraKeysViewClient;

    /**
     * The map for the {@link SpecialButton} and their {@link SpecialButtonState}. Defaults to
     * the one returned by {@link #getDefaultSpecialButtons(ExtraKeysView)}.
     */
    protected Map<SpecialButton, SpecialButtonState> mSpecialButtons;

    /**
     * The keys for the {@link SpecialButton} added to {@link #mSpecialButtons}. This is automatically
     * set when the call to {@link #setSpecialButtons(Map)} is made.
     */
    protected Set<String> mSpecialButtonsKeys;

    /**
     * The list of keys for which auto repeat of key should be triggered if its extra keys button
     * is long pressed. This is done by calling {@link IExtraKeysView#onExtraKeyButtonClick(View, ExtraKeyButton, MaterialButton)}
     * every {@link #mLongPressRepeatDelay} seconds after {@link #mLongPressTimeout} has passed.
     * The default keys are defined by {@link ExtraKeysConstants#PRIMARY_REPETITIVE_KEYS}.
     */
    protected List<String> mRepetitiveKeys;

    /**
     * The text color for the extra keys button. Defaults to {@link #DEFAULT_BUTTON_TEXT_COLOR}.
     */
    protected int mButtonTextColor;

    /**
     * The text color for the extra keys button when its active.
     * Defaults to {@link #DEFAULT_BUTTON_ACTIVE_TEXT_COLOR}.
     */
    protected int mButtonActiveTextColor;

    /**
     * The background color for the extra keys button. Defaults to {@link #DEFAULT_BUTTON_BACKGROUND_COLOR}.
     */
    protected int mButtonBackgroundColor;

    /**
     * The background color for the extra keys button when its active. Defaults to
     * {@link #DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR}.
     */
    protected int mButtonActiveBackgroundColor;

    /**
     * Optional accent used to tint the press "glow pill". When 0 (unset) the press feedback falls
     * back to {@link #mButtonActiveBackgroundColor}. The launcher dock sets this to its accent so
     * the key feedback matches the dock's rim glow.
     */
    protected int mKeyPressFeedbackColor = 0;

    /**
     * Whether a blur backdrop is active behind the keys. When true the press feedback is a feathered
     * radial wash (it relies on the blur for softness); when false it uses a more present rounded
     * fill. The launcher dock sets this from its blur preference.
     */
    protected boolean mKeyPressFeedbackBlurAvailable = true;

    /**
     * Defines whether text for the extra keys button should be all capitalized automatically.
     */
    protected boolean mButtonTextAllCaps = true;

    /**
     * Defines the duration in milliseconds before a press turns into a long press. The default
     * duration used is the one returned by a call to {@link ViewConfiguration#getLongPressTimeout()}
     * which will return the system defined duration which can be changed in accessibility settings.
     * The duration must be in between {@link #MIN_LONG_PRESS_DURATION} and {@link #MAX_LONG_PRESS_DURATION},
     * otherwise {@link #FALLBACK_LONG_PRESS_DURATION} is used.
     */
    protected int mLongPressTimeout;

    /**
     * Defines the duration in milliseconds for the delay between trigger of each repeat of
     * {@link #mRepetitiveKeys}. The default value is defined by {@link #DEFAULT_LONG_PRESS_REPEAT_DELAY}.
     * The duration must be in between {@link #MIN_LONG_PRESS__REPEAT_DELAY} and
     * {@link #MAX_LONG_PRESS__REPEAT_DELAY}, otherwise {@link #DEFAULT_LONG_PRESS_REPEAT_DELAY} is used.
     */
    protected int mLongPressRepeatDelay;

    /**
     * The popup window shown if {@link ExtraKeyButton#getPopup()} returns a {@code non-null} value
     * and a swipe up action is done on an extra key.
     */
    protected PopupWindow mPopupWindow;

    protected ScheduledExecutorService mScheduledExecutor;

    protected Handler mHandler;

    protected SpecialButtonsLongHoldRunnable mSpecialButtonsLongHoldRunnable;

    protected int mLongPressCount;

    protected boolean mAccessibilityEnabled;

    /**
     * Press-and-hold "bloom": a soft radial glow centred on the held key, drawn in this view's
     * {@link #getOverlay() overlay} so it can bleed outward past the key bounds while still being
     * contained by the dock's rounded-capsule clip. {@link #mKeyBloomButton} is the key it belongs
     * to so a late hide for a different key can't tear down the wrong bloom.
     */
    @Nullable private GradientDrawable mKeyBloom;
    @Nullable private Animator mKeyBloomAnimator;
    @Nullable private MaterialButton mKeyBloomButton;

    public ExtraKeysView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // The hold bloom lives in this view's overlay and must be allowed to draw past the
        // GridLayout content box; the dock's ancestor capsule clip still contains it.
        setClipChildren(false);
        setClipToPadding(false);
        setRepetitiveKeys(ExtraKeysConstants.PRIMARY_REPETITIVE_KEYS);
        setSpecialButtons(getDefaultSpecialButtons(this));
        setButtonColors(ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_TEXT_COLOR, DEFAULT_BUTTON_TEXT_COLOR), ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_TEXT_COLOR, DEFAULT_BUTTON_ACTIVE_TEXT_COLOR), ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_BACKGROUND_COLOR, DEFAULT_BUTTON_BACKGROUND_COLOR), ThemeUtils.getSystemAttrColor(context, ATTR_BUTTON_ACTIVE_BACKGROUND_COLOR, DEFAULT_BUTTON_ACTIVE_BACKGROUND_COLOR));
        setLongPressTimeout(ViewConfiguration.getLongPressTimeout());
        setLongPressRepeatDelay(DEFAULT_LONG_PRESS_REPEAT_DELAY);

        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        mAccessibilityEnabled = am.isEnabled();
    }

    /**
     * Get {@link #mExtraKeysViewClient}.
     */
    public IExtraKeysView getExtraKeysViewClient() {
        return mExtraKeysViewClient;
    }

    /**
     * Set {@link #mExtraKeysViewClient}.
     */
    public void setExtraKeysViewClient(IExtraKeysView extraKeysViewClient) {
        mExtraKeysViewClient = extraKeysViewClient;
    }

    /**
     * Get {@link #mRepetitiveKeys}.
     */
    public List<String> getRepetitiveKeys() {
        if (mRepetitiveKeys == null)
            return null;
        return mRepetitiveKeys.stream().map(String::new).collect(Collectors.toList());
    }

    /**
     * Set {@link #mRepetitiveKeys}. Must not be {@code null}.
     */
    public void setRepetitiveKeys(@NonNull List<String> repetitiveKeys) {
        mRepetitiveKeys = repetitiveKeys;
    }

    /**
     * Get {@link #mSpecialButtons}.
     */
    public Map<SpecialButton, SpecialButtonState> getSpecialButtons() {
        if (mSpecialButtons == null)
            return null;
        return mSpecialButtons.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Get {@link #mSpecialButtonsKeys}.
     */
    public Set<String> getSpecialButtonsKeys() {
        if (mSpecialButtonsKeys == null)
            return null;
        return mSpecialButtonsKeys.stream().map(String::new).collect(Collectors.toSet());
    }

    /**
     * Set {@link #mSpecialButtonsKeys}. Must not be {@code null}.
     */
    public void setSpecialButtons(@NonNull Map<SpecialButton, SpecialButtonState> specialButtons) {
        mSpecialButtons = specialButtons;
        mSpecialButtonsKeys = this.mSpecialButtons.keySet().stream().map(SpecialButton::getKey).collect(Collectors.toSet());
    }

    /**
     * Set the {@link ExtraKeysView} button colors.
     *
     * @param buttonTextColor The value for {@link #mButtonTextColor}.
     * @param buttonActiveTextColor The value for {@link #mButtonActiveTextColor}.
     * @param buttonBackgroundColor The value for {@link #mButtonBackgroundColor}.
     * @param buttonActiveBackgroundColor The value for {@link #mButtonActiveBackgroundColor}.
     */
    /** Tint for the press glow pill; set to 0 to fall back to the active background colour. */
    public void setKeyPressFeedbackColor(int color) {
        mKeyPressFeedbackColor = color;
    }

    /** Tells the press feedback whether a blur backdrop is active (soft radial vs rounded fill). */
    public void setKeyPressFeedbackBlurAvailable(boolean available) {
        mKeyPressFeedbackBlurAvailable = available;
    }

    public void setButtonColors(int buttonTextColor, int buttonActiveTextColor, int buttonBackgroundColor, int buttonActiveBackgroundColor) {
        mButtonTextColor = buttonTextColor;
        mButtonActiveTextColor = buttonActiveTextColor;
        mButtonBackgroundColor = buttonBackgroundColor;
        mButtonActiveBackgroundColor = buttonActiveBackgroundColor;
    }

    /**
     * Get {@link #mButtonTextColor}.
     */
    public int getButtonTextColor() {
        return mButtonTextColor;
    }

    /**
     * Set {@link #mButtonTextColor}.
     */
    public void setButtonTextColor(int buttonTextColor) {
        mButtonTextColor = buttonTextColor;
    }

    /**
     * Get {@link #mButtonActiveTextColor}.
     */
    public int getButtonActiveTextColor() {
        return mButtonActiveTextColor;
    }

    /**
     * Set {@link #mButtonActiveTextColor}.
     */
    public void setButtonActiveTextColor(int buttonActiveTextColor) {
        mButtonActiveTextColor = buttonActiveTextColor;
    }

    /**
     * Get {@link #mButtonBackgroundColor}.
     */
    public int getButtonBackgroundColor() {
        return mButtonBackgroundColor;
    }

    /**
     * Set {@link #mButtonBackgroundColor}.
     */
    public void setButtonBackgroundColor(int buttonBackgroundColor) {
        mButtonBackgroundColor = buttonBackgroundColor;
    }

    /**
     * Get {@link #mButtonActiveBackgroundColor}.
     */
    public int getButtonActiveBackgroundColor() {
        return mButtonActiveBackgroundColor;
    }

    /**
     * Set {@link #mButtonActiveBackgroundColor}.
     */
    public void setButtonActiveBackgroundColor(int buttonActiveBackgroundColor) {
        mButtonActiveBackgroundColor = buttonActiveBackgroundColor;
    }

    /**
     * Set {@link #mButtonTextAllCaps}.
     */
    public void setButtonTextAllCaps(boolean buttonTextAllCaps) {
        mButtonTextAllCaps = buttonTextAllCaps;
    }

    /**
     * Get {@link #mLongPressTimeout}.
     */
    public int getLongPressTimeout() {
        return mLongPressTimeout;
    }

    /**
     * Set {@link #mLongPressTimeout}.
     */
    public void setLongPressTimeout(int longPressDuration) {
        if (longPressDuration >= MIN_LONG_PRESS_DURATION && longPressDuration <= MAX_LONG_PRESS_DURATION) {
            mLongPressTimeout = longPressDuration;
        } else {
            mLongPressTimeout = FALLBACK_LONG_PRESS_DURATION;
        }
    }

    /**
     * Get {@link #mLongPressRepeatDelay}.
     */
    public int getLongPressRepeatDelay() {
        return mLongPressRepeatDelay;
    }

    /**
     * Set {@link #mLongPressRepeatDelay}.
     */
    public void setLongPressRepeatDelay(int longPressRepeatDelay) {
        if (mLongPressRepeatDelay >= MIN_LONG_PRESS__REPEAT_DELAY && mLongPressRepeatDelay <= MAX_LONG_PRESS__REPEAT_DELAY) {
            mLongPressRepeatDelay = longPressRepeatDelay;
        } else {
            mLongPressRepeatDelay = DEFAULT_LONG_PRESS_REPEAT_DELAY;
        }
    }

    /**
     * Get the default map that can be used for {@link #mSpecialButtons}.
     */
    @NonNull
    public Map<SpecialButton, SpecialButtonState> getDefaultSpecialButtons(ExtraKeysView extraKeysView) {
        return new HashMap<SpecialButton, SpecialButtonState>() {

            {
                put(SpecialButton.CTRL, new SpecialButtonState(extraKeysView));
                put(SpecialButton.ALT, new SpecialButtonState(extraKeysView));
                put(SpecialButton.SHIFT, new SpecialButtonState(extraKeysView));
                put(SpecialButton.FN, new SpecialButtonState(extraKeysView));
            }
        };
    }

    /**
     * Reload this instance of {@link ExtraKeysView} with the info passed in {@code extraKeysInfo}.
     *
     * @param extraKeysInfo The {@link ExtraKeysInfo} that defines the necessary info for the extra keys.
     * @param heightPx The height in pixels of the parent surrounding the {@link ExtraKeysView}. It must
     *                 be a single child.
     */
    @SuppressLint("ClickableViewAccessibility")
    public void reload(ExtraKeysInfo extraKeysInfo, float heightPx) {
        if (extraKeysInfo == null)
            return;
        for (SpecialButtonState state : mSpecialButtons.values()) state.buttons = new ArrayList<>();
        removeAllViews();
        ExtraKeyButton[][] buttons = extraKeysInfo.getMatrix();
        setRowCount(buttons.length);
        setColumnCount(maximumLength(buttons));
        for (int row = 0; row < buttons.length; row++) {
            for (int col = 0; col < buttons[row].length; col++) {
                final ExtraKeyButton buttonInfo = buttons[row][col];
                MaterialButton button;
                if (isSpecialButton(buttonInfo)) {
                    button = createSpecialButton(buttonInfo.getKey(), true);
                    if (button == null)
                        return;
                } else {
                    button = new MaterialButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
                }
                button.setAccessibilityDelegate(new AccessibilityDelegate() {
                    @Override
                    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        // these should funtion like soft keyboard keys;
                        // with this flag set, they honor the TalkBack setting
                        // of hold/release to activate, 2x tap to activate or hybrid;
                        // assuming your Android and TalkBack are recent enough
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            info.setTextEntryKey(true);
                        }
                    }
                });

                button.setText(buttonInfo.getDisplay());
                button.setTextColor(mButtonTextColor);
                button.setAllCaps(mButtonTextAllCaps);
                button.setPadding(0, 0, 0, 0);
                // MaterialButton applies a default vertical inset (~6dp top/bottom) to its background.
                // On this short row that squeezes the pressed-state background into a thin strip and
                // clips the key to its top half. Zero it so the cap fills the full button height.
                button.setInsetTop(0);
                button.setInsetBottom(0);
                button.setOnClickListener(view -> {
                    performExtraKeyButtonHapticFeedback(view, buttonInfo, button);
                    onAnyExtraKeyButtonClick(view, buttonInfo, button);
                });
                final float popupSwipeThresholdPx = Math.max(
                    ViewConfiguration.get(getContext()).getScaledTouchSlop(),
                    getResources().getDisplayMetrics().density * 8f
                );
                final float[] popupSwipeDownRawY = new float[1];
                button.setOnTouchListener((view, event) -> {
                    switch(event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            popupSwipeDownRawY[0] = event.getRawY();
                            requestParentDisallowIntercept(view, true);
                            setButtonPressedVisualState(button, buttonInfo, true);
                            // Start long press scheduled executors which will be stopped in next MotionEvent
                            startScheduledExecutors(view, buttonInfo, button);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            requestParentDisallowIntercept(view, true);
                            if (buttonInfo.getPopup() != null) {
                                float upwardTravelPx = popupSwipeDownRawY[0] - event.getRawY();
                                // Show popup on swipe up
                                if (mPopupWindow == null && (event.getY() < 0 || upwardTravelPx >= popupSwipeThresholdPx)) {
                                    stopScheduledExecutors();
                                    showPopup(view, buttonInfo.getPopup());
                                    setButtonVisualState(button, buttonInfo, KeyVisualState.POPUP_ARMED);
                                    animateKeyCapDip(button, KeyVisualState.POPUP_ARMED);
                                    showKeyBloom(button, KeyVisualState.POPUP_ARMED);
                                }
                                if (mPopupWindow != null && event.getY() > 0 && upwardTravelPx < (popupSwipeThresholdPx * 0.35f)) {
                                    setButtonPressedVisualState(button, buttonInfo, true);
                                    dismissPopup();
                                }
                            }
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            requestParentDisallowIntercept(view, false);
                            setButtonPressedVisualState(button, buttonInfo, false);
                            stopScheduledExecutors();
                            return true;
                        case MotionEvent.ACTION_UP:
                            requestParentDisallowIntercept(view, false);
                            setButtonPressedVisualState(button, buttonInfo, false);
                            stopScheduledExecutors();
                            // If ACTION_UP up was not from a repetitive key or was with a key with a popup button
                            if (mLongPressCount == 0 || mPopupWindow != null) {
                                // Trigger popup button click if swipe up complete
                                if (mPopupWindow != null) {
                                    dismissPopup();
                                    if (buttonInfo.getPopup() != null) {
                                        onAnyExtraKeyButtonClick(view, buttonInfo.getPopup(), button);
                                    }
                                } else {
                                    view.performClick();
                                }
                            }
                            return true;
                        default:
                            return true;
                    }
                });
                LayoutParams param = new GridLayout.LayoutParams();
                param.width = 0;
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                    param.height = (int) (heightPx + 0.5);
                } else {
                    param.height = 0;
                }
                param.setMargins(0, 0, 0, 0);
                param.columnSpec = GridLayout.spec(col, GridLayout.FILL, 1.f);
                param.rowSpec = GridLayout.spec(row, GridLayout.FILL, 1.f);
                button.setLayoutParams(param);
                addView(button);
            }
        }
    }

    private static void requestParentDisallowIntercept(View view, boolean disallowIntercept) {
        ViewParent parent = view == null ? null : view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
            parent = parent.getParent();
        }
    }

    public void onExtraKeyButtonClick(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (mExtraKeysViewClient != null)
            mExtraKeysViewClient.onExtraKeyButtonClick(view, buttonInfo, button);
    }

    public void performExtraKeyButtonHapticFeedback(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        if (mExtraKeysViewClient != null) {
            // If client handled the feedback, then just return
            if (mExtraKeysViewClient.performExtraKeyButtonHapticFeedback(view, buttonInfo, button))
                return;
        }
        if (Settings.System.getInt(getContext().getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0) {
            if (Build.VERSION.SDK_INT >= 28) {
                button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } else {
                // Perform haptic feedback only if no total silence mode enabled.
                if (Settings.Global.getInt(getContext().getContentResolver(), "zen_mode", 0) != 2) {
                    button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            }
        }
    }

    public void onAnyExtraKeyButtonClick(View view, @NonNull ExtraKeyButton buttonInfo, MaterialButton button) {
        if (isSpecialButton(buttonInfo)) {
            if (mLongPressCount > 0)
                return;
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state == null)
                return;
            // Toggle active state and disable lock state if new state is not active
            state.setIsActive(!state.isActive);
            if (!state.isActive)
                state.setIsLocked(false);

            announceSpecialKeyStateChangeForAccessibility(buttonInfo.getKey(), state);
        } else {
            onExtraKeyButtonClick(view, buttonInfo, button);
        }
    }

    private void announceSpecialKeyStateChangeForAccessibility(CharSequence buttonName, SpecialButtonState state) {
        if(mAccessibilityEnabled) {
            CharSequence stateText;
            if(!state.isActive) {
                stateText = getResources().getText(R.string.a11y_special_key_off);
            } else if(state.isLocked) {
                stateText = getResources().getText(R.string.a11y_special_key_latched_on);
            } else {
                stateText = getResources().getText(R.string.a11y_special_key_on);

            }
            String announcementText = buttonName
                + " "
                + stateText
                ;
            announceForAccessibility(announcementText);
        }
    }


    public void startScheduledExecutors(View view, ExtraKeyButton buttonInfo, MaterialButton button) {
        stopScheduledExecutors();
        mLongPressCount = 0;
        if (mRepetitiveKeys.contains(buttonInfo.getKey())) {
            // Auto repeat key if long pressed until ACTION_UP stops it by calling stopScheduledExecutors.
            // Currently, only one (last) repeat key can run at a time. Old ones are stopped.
            mScheduledExecutor = Executors.newSingleThreadScheduledExecutor();
            mScheduledExecutor.scheduleWithFixedDelay(() -> {
                mLongPressCount++;
                // Deepen the glow pill on the first repeat to signal the held/auto-repeat state.
                if (mLongPressCount == 1) {
                    button.post(() -> {
                        setButtonVisualState(button, buttonInfo, KeyVisualState.REPEAT_HELD);
                        animateKeyCapDip(button, KeyVisualState.REPEAT_HELD);
                        showKeyBloom(button, KeyVisualState.REPEAT_HELD);
                    });
                }
                onExtraKeyButtonClick(view, buttonInfo, button);
            }, mLongPressTimeout, mLongPressRepeatDelay, TimeUnit.MILLISECONDS);
        } else if (isSpecialButton(buttonInfo)) {
            // Lock the key if long pressed by running mSpecialButtonsLongHoldRunnable after
            // waiting for mLongPressTimeout milliseconds. If user does not long press, then the
            // ACTION_UP triggered will cancel the runnable by calling stopScheduledExecutors before
            // it has a chance to run.
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state == null)
                return;
            if (mHandler == null)
                mHandler = new Handler(Looper.getMainLooper());
            mSpecialButtonsLongHoldRunnable = new SpecialButtonsLongHoldRunnable(state, buttonInfo, button);
            mHandler.postDelayed(mSpecialButtonsLongHoldRunnable, mLongPressTimeout);
        }
    }

    public void stopScheduledExecutors() {
        if (mScheduledExecutor != null) {
            mScheduledExecutor.shutdownNow();
            mScheduledExecutor = null;
        }
        if (mSpecialButtonsLongHoldRunnable != null && mHandler != null) {
            mHandler.removeCallbacks(mSpecialButtonsLongHoldRunnable);
            mSpecialButtonsLongHoldRunnable = null;
        }
    }

    public class SpecialButtonsLongHoldRunnable implements Runnable {

        public final SpecialButtonState mState;
        public final ExtraKeyButton mButtonInfo;
        public final MaterialButton mButton;

        public SpecialButtonsLongHoldRunnable(SpecialButtonState state, ExtraKeyButton buttonInfo, MaterialButton button) {
            mState = state;
            mButtonInfo = buttonInfo;
            mButton = button;
        }

        public void run() {
            // Toggle active and lock state
            mState.setIsLocked(!mState.isActive);
            mState.setIsActive(!mState.isActive);
            mLongPressCount++;
            if (mButton != null) {
                updateSpecialButtonVisualState(mButton, mState);
                animateKeyCapDip(mButton, KeyVisualState.REPEAT_HELD);
                showKeyBloom(mButton, KeyVisualState.REPEAT_HELD);
            }

            announceSpecialKeyStateChangeForAccessibility(mButtonInfo.getKey(), mState);
        }
    }

    void showPopup(View view, ExtraKeyButton extraButton) {
        int width = Math.max(1, view.getMeasuredWidth());
        int height = Math.max(1, view.getMeasuredHeight());
        MaterialButton button;
        if (isSpecialButton(extraButton)) {
            button = createSpecialButton(extraButton.getKey(), false);
            if (button == null)
                return;
        } else {
            button = new MaterialButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
            button.setTextColor(mButtonTextColor);
        }
        button.setText(extraButton.getDisplay());
        button.setAllCaps(mButtonTextAllCaps);
        button.setGravity(Gravity.CENTER);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        button.setIncludeFontPadding(false);
        button.setPadding(0, 0, 0, 0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(width);
        button.setMinimumHeight(height);
        button.setWidth(width);
        button.setHeight(height);
        setPopupButtonVisualState(button);

        int widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY);
        int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        button.measure(widthSpec, heightSpec);
        int popupWidth = width;
        int popupHeight = height;

        mPopupWindow = new PopupWindow(button, popupWidth, popupHeight, false);
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setFocusable(false);
        mPopupWindow.setClippingEnabled(true);
        mPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPopupWindow.setElevation(dpToPx(6f));
        }
        int[] viewLocation = new int[2];
        view.getLocationOnScreen(viewLocation);
        View root = getRootView();
        if (root == null) {
            root = view;
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int desiredLeft = viewLocation[0] + Math.round((width - popupWidth) / 2f);
        int desiredTop = viewLocation[1] - popupHeight - Math.round(dpToPx(5f));
        int maxLeft = Math.max(0, screenWidth - popupWidth);
        int maxTop = Math.max(0, screenHeight - popupHeight);
        int clampedLeft = Math.max(0, Math.min(maxLeft, desiredLeft));
        int clampedTop = Math.max(0, Math.min(maxTop, desiredTop));
        mPopupWindow.showAtLocation(root, Gravity.NO_GRAVITY, clampedLeft, clampedTop);
    }

    private void setButtonPressedVisualState(@NonNull MaterialButton button, @NonNull ExtraKeyButton buttonInfo,
                                             boolean pressed) {
        if (pressed) {
            setButtonVisualState(button, buttonInfo, KeyVisualState.PRESSED);
            animateKeyCapDip(button, KeyVisualState.PRESSED);
        } else {
            // restoreButtonVisualState repaints the rest background, which also clears the highlight.
            restoreButtonVisualState(button, buttonInfo);
            animateKeyCapDip(button, KeyVisualState.RESTING);
            hideKeyBloom();
        }
    }

    private void setButtonVisualState(@NonNull MaterialButton button, @NonNull ExtraKeyButton buttonInfo,
                                      @NonNull KeyVisualState state) {
        if (state == KeyVisualState.RESTING) {
            restoreButtonVisualState(button, buttonInfo);
            return;
        }
        applyButtonVisualState(button, state, true);
    }

    void updateSpecialButtonVisualState(@NonNull MaterialButton button, @NonNull SpecialButtonState state) {
        KeyVisualState visualState = state.isLocked
            ? KeyVisualState.STICKY_LOCKED
            : (state.isActive ? KeyVisualState.STICKY_ACTIVE : KeyVisualState.RESTING);
        applyButtonVisualState(button, visualState, state.isActive);
    }

    private void applyButtonVisualState(@NonNull MaterialButton button, @NonNull KeyVisualState state,
                                        boolean activeText) {
        button.setTextColor(activeText ? mButtonActiveTextColor : mButtonTextColor);
        button.setBackground(buildKeyBackground(state));
    }

    @NonNull
    private Drawable buildKeyBackground(@NonNull KeyVisualState state) {
        if (state == KeyVisualState.RESTING) {
            return new ColorDrawable(mButtonBackgroundColor);
        }
        if (state == KeyVisualState.POPUP_SELECTED) {
            return buildPopupKeyBackground(true);
        }

        int tint = mKeyPressFeedbackColor != 0 ? mKeyPressFeedbackColor : mButtonActiveBackgroundColor;
        // In-bounds key cap: a tinted rounded chip + white inner rim. The soft radial "bloom" that
        // used to live here (and overflowed the short row, getting clipped to the top half) now
        // lives in the view overlay via showKeyBloom(), so this drawable always fits the key.
        int fillAlpha;
        int rimAlpha;
        int whiteRimAlpha;
        switch (state) {
            case REPEAT_HELD:
                fillAlpha = mKeyPressFeedbackBlurAvailable ? 64 : 134;
                rimAlpha = 200;
                whiteRimAlpha = 78;
                break;
            case STICKY_LOCKED:
                fillAlpha = mKeyPressFeedbackBlurAvailable ? 48 : 116;
                rimAlpha = 210;
                whiteRimAlpha = 86;
                break;
            case STICKY_ACTIVE:
                fillAlpha = mKeyPressFeedbackBlurAvailable ? 34 : 88;
                rimAlpha = 138;
                whiteRimAlpha = 42;
                break;
            case POPUP_ARMED:
                // Source key reads as a "lifted/empty socket" while the popup floats above it.
                fillAlpha = mKeyPressFeedbackBlurAvailable ? 30 : 84;
                rimAlpha = 210;
                whiteRimAlpha = 64;
                break;
            case PRESSED:
            default:
                fillAlpha = mKeyPressFeedbackBlurAvailable ? 52 : 118;
                rimAlpha = 170;
                whiteRimAlpha = 60;
                break;
        }

        GradientDrawable chip = new GradientDrawable();
        chip.setShape(GradientDrawable.RECTANGLE);
        chip.setCornerRadius(dpToPx(13f));
        chip.setColor(withAlpha(tint, fillAlpha));
        chip.setStroke(Math.max(1, Math.round(dpToPx(1.1f))), withAlpha(tint, rimAlpha));
        chip.setDither(true);

        GradientDrawable innerRim = new GradientDrawable();
        innerRim.setShape(GradientDrawable.RECTANGLE);
        innerRim.setCornerRadius(dpToPx(11f));
        innerRim.setColor(Color.TRANSPARENT);
        innerRim.setStroke(Math.max(1, Math.round(dpToPx(0.75f))), withAlpha(Color.WHITE, whiteRimAlpha));

        int chipInset = Math.round(dpToPx(4f));
        int rimInset = chipInset + Math.round(dpToPx(2f));
        return new LayerDrawable(new Drawable[] {
            new InsetDrawable(chip, chipInset, chipInset, chipInset, chipInset),
            new InsetDrawable(innerRim, rimInset, rimInset, rimInset, rimInset)
        });
    }

    private void animateKeyCapDip(@NonNull MaterialButton button, @NonNull KeyVisualState state) {
        button.animate().cancel();
        float target;
        long duration;
        switch (state) {
            case REPEAT_HELD:
                target = 0.86f;
                duration = 70L;
                break;
            case PRESSED:
                target = 0.90f;
                duration = 55L;
                break;
            case POPUP_ARMED:
                target = 0.94f;
                duration = 70L;
                break;
            default:
                target = 1f;
                duration = 120L;
                break;
        }
        if (shouldSnapKeyMotion()) {
            button.setScaleX(target);
            button.setScaleY(target);
            return;
        }
        button.animate()
            .scaleX(target)
            .scaleY(target)
            .setDuration(duration)
            .start();
    }

    /**
     * Show a soft radial "bloom" centred on {@code button}, in this view's overlay so it can bleed
     * past the key bounds. Used to signal the press-and-hold / repeat state and the swipe-up armed
     * state. The bloom radius and peak intensity are keyed to {@code state}.
     */
    private void showKeyBloom(@NonNull MaterialButton button, @NonNull KeyVisualState state) {
        // Tear down any previous bloom (possibly on another key) before starting a new one.
        clearKeyBloom();

        int tint = mKeyPressFeedbackColor != 0 ? mKeyPressFeedbackColor : mButtonActiveBackgroundColor;
        final float radius = dpToPx(state == KeyVisualState.POPUP_ARMED ? 22f : 30f);
        final int peakAlpha = state == KeyVisualState.POPUP_ARMED ? 120 : 150;
        final float cx = button.getX() + button.getWidth() / 2f;
        final float cy = button.getY() + button.getHeight() / 2f;

        final GradientDrawable bloom = new GradientDrawable();
        bloom.setShape(GradientDrawable.RECTANGLE);
        bloom.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bloom.setGradientCenter(0.5f, 0.5f);
        bloom.setGradientRadius(radius);
        bloom.setColors(new int[] {
            withAlpha(tint, peakAlpha),
            withAlpha(tint, Math.round(peakAlpha * 0.4f)),
            withAlpha(tint, 0)
        });
        bloom.setDither(true);

        mKeyBloom = bloom;
        mKeyBloomButton = button;
        getOverlay().add(bloom);

        if (shouldSnapKeyMotion()) {
            applyKeyBloomFrame(bloom, cx, cy, radius, 1f);
            return;
        }
        applyKeyBloomFrame(bloom, cx, cy, radius, 0f);
        ValueAnimator in = ValueAnimator.ofFloat(0f, 1f);
        in.setDuration(120L);
        in.addUpdateListener(a -> applyKeyBloomFrame(bloom, cx, cy, radius, (float) a.getAnimatedValue()));
        mKeyBloomAnimator = in;
        in.start();
    }

    /** Fade and remove the active bloom, if any. */
    private void hideKeyBloom() {
        final GradientDrawable bloom = mKeyBloom;
        if (bloom == null)
            return;
        final MaterialButton button = mKeyBloomButton;
        if (mKeyBloomAnimator != null) {
            mKeyBloomAnimator.cancel();
            mKeyBloomAnimator = null;
        }
        // The fields are about to be reassigned; the animator captures the local refs.
        mKeyBloom = null;
        mKeyBloomButton = null;

        if (button == null || shouldSnapKeyMotion()) {
            getOverlay().remove(bloom);
            return;
        }
        final float cx = button.getX() + button.getWidth() / 2f;
        final float cy = button.getY() + button.getHeight() / 2f;
        final float radius = bloom.getBounds().width() / 2f;
        ValueAnimator out = ValueAnimator.ofFloat(1f, 0f);
        out.setDuration(140L);
        out.addUpdateListener(a -> applyKeyBloomFrame(bloom, cx, cy, radius, (float) a.getAnimatedValue()));
        out.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                getOverlay().remove(bloom);
            }
        });
        out.start();
    }

    /** Immediately drop the active bloom with no animation (used before replacing it). */
    private void clearKeyBloom() {
        if (mKeyBloomAnimator != null) {
            mKeyBloomAnimator.cancel();
            mKeyBloomAnimator = null;
        }
        if (mKeyBloom != null) {
            getOverlay().remove(mKeyBloom);
            mKeyBloom = null;
        }
        mKeyBloomButton = null;
    }

    /** Position/scale/fade one frame of the bloom. {@code f} is 0 (hidden) .. 1 (full). */
    private void applyKeyBloomFrame(@NonNull GradientDrawable bloom, float cx, float cy,
                                    float radius, float f) {
        float scale = 0.6f + 0.4f * f;
        float half = radius * scale;
        bloom.setBounds(
            Math.round(cx - half), Math.round(cy - half),
            Math.round(cx + half), Math.round(cy + half));
        bloom.setAlpha(Math.round(255 * clamp01(f)));
        bloom.invalidateSelf();
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private boolean shouldSnapKeyMotion() {
        try {
            return Settings.Global.getFloat(
                getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) <= 0f;
        } catch (Exception ignored) {
            return false;
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private void restoreButtonVisualState(@NonNull MaterialButton button, @NonNull ExtraKeyButton buttonInfo) {
        if (isSpecialButton(buttonInfo)) {
            SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonInfo.getKey()));
            if (state != null) {
                updateSpecialButtonVisualState(button, state);
            } else {
                applyButtonVisualState(button, KeyVisualState.RESTING, false);
            }
        } else {
            applyButtonVisualState(button, KeyVisualState.RESTING, false);
        }
    }

    private void setPopupButtonVisualState(@NonNull MaterialButton button) {
        button.setTextColor(Color.WHITE);
        button.setBackground(buildPopupKeyBackground(true));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(dpToPx(4f));
            button.setTranslationZ(dpToPx(4f));
        }
    }

    @NonNull
    private Drawable buildPopupKeyBackground(boolean selected) {
        int tint = mKeyPressFeedbackColor != 0 ? mKeyPressFeedbackColor : mButtonActiveBackgroundColor;
        GradientDrawable glow = new GradientDrawable();
        glow.setShape(GradientDrawable.RECTANGLE);
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.5f, 0.45f);
        glow.setGradientRadius(dpToPx(32f));
        glow.setColors(new int[] {
            withAlpha(tint, selected ? 96 : 54),
            withAlpha(tint, selected ? 36 : 18),
            withAlpha(tint, 0)
        });
        glow.setDither(true);

        GradientDrawable key = new GradientDrawable();
        key.setShape(GradientDrawable.RECTANGLE);
        key.setCornerRadius(dpToPx(12f));
        key.setColor(withAlpha(Color.rgb(10, 18, 24), mKeyPressFeedbackBlurAvailable ? 228 : 242));
        key.setStroke(Math.max(1, Math.round(dpToPx(selected ? 2f : 1.25f))), withAlpha(tint, selected ? 255 : 190));

        GradientDrawable innerRim = new GradientDrawable();
        innerRim.setShape(GradientDrawable.RECTANGLE);
        innerRim.setCornerRadius(dpToPx(10f));
        innerRim.setColor(Color.TRANSPARENT);
        innerRim.setStroke(Math.max(1, Math.round(dpToPx(0.75f))), withAlpha(Color.WHITE, selected ? 72 : 38));

        int chipInset = Math.round(dpToPx(2f));
        int rimInset = Math.round(dpToPx(4f));
        return new LayerDrawable(new Drawable[] {
            glow,
            new InsetDrawable(key, chipInset, chipInset, chipInset, chipInset),
            new InsetDrawable(innerRim, rimInset, rimInset, rimInset, rimInset)
        });
    }

    public void dismissPopup() {
        hideKeyBloom();
        if (mPopupWindow == null) {
            return;
        }
        mPopupWindow.setContentView(null);
        mPopupWindow.dismiss();
        mPopupWindow = null;
    }

    /**
     * Check whether a {@link ExtraKeyButton} is a {@link SpecialButton}.
     */
    public boolean isSpecialButton(ExtraKeyButton button) {
        return mSpecialButtonsKeys.contains(button.getKey());
    }

    /**
     * Read whether {@link SpecialButton} registered in {@link #mSpecialButtons} is active or not.
     *
     * @param specialButton The {@link SpecialButton} to read.
     * @param autoSetInActive Set to {@code true} if {@link SpecialButtonState#isActive} should be
     *                        set {@code false} if button is not locked.
     * @return Returns {@code null} if button does not exist in {@link #mSpecialButtons}. If button
     *         exists, then returns {@code true} if the button is created in {@link ExtraKeysView}
     *         and is active, otherwise {@code false}.
     */
    @Nullable
    public Boolean readSpecialButton(SpecialButton specialButton, boolean autoSetInActive) {
        SpecialButtonState state = mSpecialButtons.get(specialButton);
        if (state == null)
            return null;
        if (!state.isCreated || !state.isActive)
            return false;
        // Disable active state only if not locked
        if (autoSetInActive && !state.isLocked) {
            state.setIsActive(false);
            announceSpecialKeyStateChangeForAccessibility(specialButton.getKey(), state);
        }

        return true;
    }

    public MaterialButton createSpecialButton(String buttonKey, boolean needUpdate) {
        SpecialButtonState state = mSpecialButtons.get(SpecialButton.valueOf(buttonKey));
        if (state == null)
            return null;
        state.setIsCreated(true);
        MaterialButton button = new MaterialButton(getContext(), null, android.R.attr.buttonBarButtonStyle);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        updateSpecialButtonVisualState(button, state);
        if (needUpdate) {
            state.buttons.add(button);
        }
        return button;
    }

    /**
     * General util function to compute the longest column length in a matrix.
     */
    public static int maximumLength(Object[][] matrix) {
        int m = 0;
        for (Object[] row : matrix) m = Math.max(m, row.length);
        return m;
    }
}
