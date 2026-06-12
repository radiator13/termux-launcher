package com.termux.app.terminal.unexpected;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.terminal.unexpected.vendor.Config;
import com.termux.app.terminal.unexpected.vendor.KeyModifier;
import com.termux.app.terminal.unexpected.vendor.KeyboardData;
import com.termux.app.terminal.unexpected.vendor.KeyValue;
import com.termux.app.terminal.unexpected.vendor.Pointers;

public final class TermuxUnexpectedKeyboardView extends View
    implements View.OnTouchListener, Pointers.IPointerEventHandler {

    interface Callback {
        void onUnexpectedKey(@NonNull KeyValue keyValue, @NonNull Pointers.Modifiers modifiers);
    }

    private static final Paint.Align[] LABEL_POSITION_H = new Paint.Align[]{
        Paint.Align.CENTER, Paint.Align.LEFT, Paint.Align.RIGHT, Paint.Align.LEFT,
        Paint.Align.RIGHT, Paint.Align.LEFT, Paint.Align.RIGHT,
        Paint.Align.CENTER, Paint.Align.CENTER
    };

    private static final Vertical[] LABEL_POSITION_V = new Vertical[]{
        Vertical.CENTER, Vertical.TOP, Vertical.TOP, Vertical.BOTTOM,
        Vertical.BOTTOM, Vertical.CENTER, Vertical.CENTER, Vertical.TOP,
        Vertical.BOTTOM
    };

    private final RectF mTmpRect = new RectF();
    private final Paint mKeyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mKeyActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSubLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private KeyboardData mKeyboardData;
    private KeyboardData.Key mShiftKey;
    private KeyboardData.Key mComposeKey;
    private Pointers mPointers;
    private Pointers.Modifiers mModifiers = Pointers.Modifiers.EMPTY;
    private Config mConfig;
    private Callback mCallback;

    private float mKeyWidth;
    private float mRowHeight;
    private float mMainLabelSize;
    private float mSubLabelSize;
    private float mHorizontalMargin;
    private float mVerticalMargin;
    private float mMarginLeft;
    private float mMarginRight;
    private float mMarginTop;
    private float mMarginBottom;
    private float mCornerRadius;
    private float mBorderWidth;

    private enum Vertical {
        TOP,
        CENTER,
        BOTTOM
    }

    public TermuxUnexpectedKeyboardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mConfig = buildConfig(getResources().getDisplayMetrics());
        Config.setGlobalConfig(mConfig);
        mPointers = new Pointers(this, mConfig);
        setOnTouchListener(this);
        setFocusable(false);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mLabelPaint.setTextAlign(Paint.Align.CENTER);
        mSubLabelPaint.setTextAlign(Paint.Align.CENTER);
        updatePalette();
    }

    private void updatePalette() {
        int keyColor = MaterialColors.getColor(this, R.attr.termuxColorSurfacePanelHigh);
        int keyActiveColor = MaterialColors.getColor(this, R.attr.termuxColorSurfacePanelHighest, keyColor);
        int borderColor = MaterialColors.getColor(this, R.attr.termuxColorOutlineVariant, keyColor);
        int labelColor = MaterialColors.getColor(this, R.attr.termuxColorOnSurface);
        int subLabelColor = MaterialColors.getColor(this, R.attr.termuxColorOnSurfaceVariant, labelColor);

        mKeyPaint.setColor(keyColor);
        mKeyActivePaint.setColor(keyActiveColor);
        mBorderPaint.setColor(borderColor);
        mLabelPaint.setColor(labelColor);
        mSubLabelPaint.setColor(subLabelColor);
    }

    static Config buildConfig(@NonNull DisplayMetrics displayMetrics) {
        float density = Math.max(1f, displayMetrics.density);
        Config config = new Config();
        config.swipe_dist_px = density * 18f;
        config.slide_step_px = density * 22f;
        config.longPressTimeout = 450L;
        config.longPressInterval = 55L;
        config.keyrepeat_enabled = true;
        config.double_tap_lock_shift = true;
        config.circle_sensitivity = 2;
        return config;
    }

    void setKeyboard(@NonNull KeyboardData keyboardData) {
        mKeyboardData = keyboardData;
        mShiftKey = keyboardData.findKeyWithValue(KeyValue.SHIFT);
        mComposeKey = keyboardData.findKeyWithValue(KeyValue.COMPOSE);
        KeyModifier.set_modmap(keyboardData.modmap);
        reset();
        requestLayout();
        invalidate();
    }

    void setCallback(@Nullable Callback callback) {
        mCallback = callback;
    }

    void reset() {
        mModifiers = Pointers.Modifiers.EMPTY;
        if (mPointers != null) {
            mPointers.clear();
        }
    }

    void toggleShiftLock() {
        int flags = mPointers.getKeyFlags(KeyValue.SHIFT);
        boolean locked = (flags & Pointers.FLAG_P_LOCKED) != 0;
        boolean active = flags != -1;
        if (locked || active) {
            mPointers.set_fake_pointer_state(mShiftKey, KeyValue.SHIFT, false, true);
        } else {
            mPointers.set_fake_pointer_state(mShiftKey, KeyValue.SHIFT, true, true);
        }
        invalidate();
    }

    int getDesiredHeightPx(int widthPx) {
        if (mKeyboardData == null) {
            return 0;
        }
        computeMetrics(widthPx);
        return Math.round((mRowHeight * mKeyboardData.keysHeight) + mMarginTop + mMarginBottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (width == 0) {
            width = getResources().getDisplayMetrics().widthPixels;
        }
        int desiredHeight = getDesiredHeightPx(width);
        setMeasuredDimension(width, resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mKeyboardData == null) {
            return;
        }
        updatePalette();
        float y = mMarginTop;
        for (KeyboardData.Row row : mKeyboardData.rows) {
            y += row.shift * mRowHeight;
            float x = mMarginLeft + (mHorizontalMargin / 2f);
            float keyHeight = row.height * mRowHeight - mVerticalMargin;
            for (KeyboardData.Key key : row.keys) {
                x += key.shift * mKeyWidth;
                float keyWidth = mKeyWidth * key.width - mHorizontalMargin;
                boolean isPressed = mPointers.isKeyDown(key);
                drawKeyFrame(canvas, x, y, keyWidth, keyHeight, isPressed);
                if (key.keys[0] != null) {
                    drawLabel(canvas, key.keys[0], x + (keyWidth / 2f), y, keyHeight, isPressed);
                }
                for (int i = 1; i < 9; i++) {
                    if (key.keys[i] != null) {
                        drawSubLabel(canvas, key.keys[i], x, y, keyWidth, keyHeight, i, isPressed);
                    }
                }
                x += mKeyWidth * key.width;
            }
            y += row.height * mRowHeight;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mKeyboardData == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mPointers.onTouchUp(event.getPointerId(event.getActionIndex()));
                break;
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                int pointerIndex = event.getActionIndex();
                KeyboardData.Key key = getKeyAtPosition(event.getX(pointerIndex), event.getY(pointerIndex));
                if (key != null) {
                    mPointers.onTouchDown(event.getX(pointerIndex), event.getY(pointerIndex), event.getPointerId(pointerIndex), key);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    mPointers.onTouchMove(event.getX(i), event.getY(i), event.getPointerId(i));
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                mPointers.onTouchCancel();
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public KeyValue modifyKey(KeyValue keyValue, Pointers.Modifiers modifiers) {
        return KeyModifier.modify(keyValue, modifiers);
    }

    @Override
    public void onPointerDown(KeyValue keyValue, boolean isSwipe) {
        updateModifiers();
        invalidate();
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    public void onPointerUp(KeyValue keyValue, Pointers.Modifiers modifiers) {
        if (mCallback != null && keyValue != null) {
            mCallback.onUnexpectedKey(keyValue, modifiers);
        }
        updateModifiers();
        invalidate();
    }

    @Override
    public void onPointerHold(KeyValue keyValue, Pointers.Modifiers modifiers) {
        if (mCallback != null && keyValue != null) {
            mCallback.onUnexpectedKey(keyValue, modifiers);
        }
        updateModifiers();
        invalidate();
    }

    @Override
    public void onPointerFlagsChanged(boolean shouldVibrate) {
        updateModifiers();
        invalidate();
        if (shouldVibrate) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    private void updateModifiers() {
        mModifiers = mPointers.getModifiers();
    }

    private void drawKeyFrame(Canvas canvas, float left, float top, float width, float height, boolean pressed) {
        float padding = mBorderWidth / 2f;
        mTmpRect.set(left + padding, top + padding, left + width - padding, top + height - padding);
        canvas.drawRoundRect(mTmpRect, mCornerRadius, mCornerRadius, pressed ? mKeyActivePaint : mKeyPaint);
        if (mBorderWidth > 0f) {
            mBorderPaint.setStrokeWidth(mBorderWidth);
            canvas.drawRoundRect(mTmpRect, mCornerRadius, mCornerRadius, mBorderPaint);
        }
    }

    private void drawLabel(Canvas canvas, KeyValue keyValue, float centerX, float top, float keyHeight, boolean pressed) {
        KeyValue displayKey = modifyKey(keyValue, mModifiers);
        if (displayKey == null) {
            return;
        }
        mLabelPaint.setTextSize(scaleTextSize(displayKey, true));
        mLabelPaint.setColor(resolveLabelColor(displayKey, pressed, false));
        canvas.drawText(displayKey.getString(), centerX, (keyHeight - mLabelPaint.ascent() - mLabelPaint.descent()) / 2f + top, mLabelPaint);
    }

    private void drawSubLabel(Canvas canvas, KeyValue keyValue, float left, float top, float keyWidth, float keyHeight, int index, boolean pressed) {
        KeyValue displayKey = modifyKey(keyValue, mModifiers);
        if (displayKey == null) {
            return;
        }
        Paint.Align align = LABEL_POSITION_H[index];
        Vertical vertical = LABEL_POSITION_V[index];
        mSubLabelPaint.setTextAlign(align);
        mSubLabelPaint.setTextSize(scaleTextSize(displayKey, false));
        mSubLabelPaint.setColor(resolveLabelColor(displayKey, pressed, true));
        float drawX = align == Paint.Align.CENTER ? left + (keyWidth / 2f) : (align == Paint.Align.LEFT ? left + dp(8f) : left + keyWidth - dp(8f));
        float drawY;
        if (vertical == Vertical.CENTER) {
            drawY = top + ((keyHeight - mSubLabelPaint.ascent() - mSubLabelPaint.descent()) / 2f);
        } else if (vertical == Vertical.TOP) {
            drawY = top + dp(7f) - mSubLabelPaint.ascent();
        } else {
            drawY = top + keyHeight - dp(7f) - mSubLabelPaint.descent();
        }
        String label = displayKey.getString();
        int length = displayKey.getKind() == KeyValue.Kind.String ? Math.min(3, label.length()) : label.length();
        canvas.drawText(label, 0, length, drawX, drawY, mSubLabelPaint);
    }

    private int resolveLabelColor(KeyValue keyValue, boolean pressed, boolean subLabel) {
        if (pressed) {
            return MaterialColors.getColor(this, R.attr.termuxColorOnSurface);
        }
        if (keyValue.hasFlagsAny(KeyValue.FLAG_GREYED)) {
            return MaterialColors.getColor(this, R.attr.termuxColorOutline);
        }
        if (keyValue.hasFlagsAny(KeyValue.FLAG_SECONDARY) || subLabel) {
            return MaterialColors.getColor(this, R.attr.termuxColorOnSurfaceVariant);
        }
        return MaterialColors.getColor(this, R.attr.termuxColorOnSurface);
    }

    private float scaleTextSize(KeyValue keyValue, boolean mainLabel) {
        float baseSize = mainLabel ? mMainLabelSize : mSubLabelSize;
        return keyValue.hasFlagsAny(KeyValue.FLAG_SMALLER_FONT) ? (baseSize * 0.78f) : baseSize;
    }

    private void computeMetrics(int widthPx) {
        float availableWidth = Math.max(0f, widthPx);
        mMarginLeft = dp(6f);
        mMarginRight = dp(6f);
        mMarginTop = dp(6f);
        mMarginBottom = dp(6f);
        mHorizontalMargin = dp(4f);
        mVerticalMargin = dp(4f);
        mBorderWidth = dp(1f);
        mCornerRadius = dp(10f);
        mKeyWidth = Math.max(dp(18f), (availableWidth - mMarginLeft - mMarginRight) / Math.max(1f, mKeyboardData.keysWidth));
        mRowHeight = Math.max(dp(40f), Math.min(dp(62f), (mKeyWidth - mHorizontalMargin) * 0.68f));
        float labelBase = Math.min(mRowHeight - mVerticalMargin, (availableWidth / 10f) * 0.62f);
        mMainLabelSize = labelBase * 0.36f;
        mSubLabelSize = labelBase * 0.22f;
    }

    @Nullable
    private KeyboardData.Key getKeyAtPosition(float x, float y) {
        if (mKeyboardData == null) {
            return null;
        }
        float currentY = mMarginTop;
        for (KeyboardData.Row row : mKeyboardData.rows) {
            currentY += row.shift * mRowHeight;
            float keyTop = currentY;
            float keyBottom = keyTop + (row.height * mRowHeight);
            float currentX = mMarginLeft + (mHorizontalMargin / 2f);
            for (KeyboardData.Key key : row.keys) {
                currentX += key.shift * mKeyWidth;
                float keyLeft = currentX;
                float keyRight = keyLeft + (key.width * mKeyWidth);
                if (x >= keyLeft && x <= keyRight && y >= keyTop && y <= keyBottom) {
                    return key;
                }
                currentX += key.width * mKeyWidth;
            }
            currentY += row.height * mRowHeight;
        }
        return null;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
