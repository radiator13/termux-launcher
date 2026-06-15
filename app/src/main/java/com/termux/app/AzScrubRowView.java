package com.termux.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AzScrubRowView extends AppCompatTextView {
    public static final class LetterVisualMetrics {
        public final RectF glyphBoundsRaw = new RectF();
        public final RectF glassBoundsRaw = new RectF();
        public float baselineRawY;
        public float centerRawX;
        public char letter;

        public void clear() {
            glyphBoundsRaw.setEmpty();
            glassBoundsRaw.setEmpty();
            baselineRawY = 0f;
            centerRawX = 0f;
            letter = '\0';
        }

        public boolean isValid() {
            return !glassBoundsRaw.isEmpty();
        }
    }

    public enum InteractionMode {
        WAVE_TRACK,
        INLINE_EMPHASIS_TRACK
    }

    public enum GesturePhase {
        DOWN,
        MOVE,
        UP
    }

    public interface ScrubCallback {
        void onScrub(char letter, int selectionIndex, float touchX, float touchY, float rawX, float rawY, @NonNull GesturePhase phase);
        void onCancel();
        default void onDoubleTap() {}
    }

    public static final char PINNED_APPS_SYMBOL = '\u2606';
    private static final char[] ALPHABET_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#".toCharArray();
    private static final char[] LETTERS = (PINNED_APPS_SYMBOL + "ABCDEFGHIJKLMNOPQRSTUVWXYZ#").toCharArray();
    private char[] visibleLetters = LETTERS;

    @Nullable private ScrubCallback callback;
    private int currentSelectionIndex = 0;
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint railFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint railStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint railSheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint railShimmerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF railRect = new RectF();
    private final Rect glyphRect = new Rect();
    private static final int RAIL_SHIMMER_PARTICLES = 14;
    private float shimmerPhase = 0f;
    @Nullable private ValueAnimator shimmerAnimator;
    private float activeTouchX = -1f;
    private float waveStrength = 0f;
    private int accentColor = Color.WHITE;
    @Nullable private ValueAnimator settleAnimator;
    private long lastTapUpTimeMs;
    private float lastTapUpX = Float.NaN;
    private int doubleTapTimeoutMs;
    private int doubleTapSlopPx;
    private boolean suppressUpScrub;
    @NonNull private InteractionMode interactionMode = InteractionMode.WAVE_TRACK;
    @Nullable private Character lockedInlineLetter;
    private int activeLetterIndex = -1;
    private static final float LETTER_SLOT_HYSTERESIS_RATIO = 0.22f;
    private boolean interactionRenderActive;
    private boolean capsuleDock;

    public AzScrubRowView(Context context) {
        super(context);
        init();
    }

    public AzScrubRowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AzScrubRowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setText("");
        setSingleLine(true);
        setTextSize(11f);
        setPadding(0, dp(1), 0, dp(1));
        setClickable(true);
        updateInteractionRenderLayer(false);
        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setTextSize(getTextSize());
        letterPaint.setColor(getCurrentTextColor());
        railFillPaint.setStyle(Paint.Style.FILL);
        railStrokePaint.setStyle(Paint.Style.STROKE);
        ViewConfiguration viewConfiguration = ViewConfiguration.get(getContext());
        doubleTapTimeoutMs = ViewConfiguration.getDoubleTapTimeout();
        doubleTapSlopPx = viewConfiguration.getScaledDoubleTapSlop();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dpf(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    // Letters are laid out within a horizontally-inset content band so the rail can wrap fully
    // around the first/last letters (☆ … #) with margin to spare. The same band drives the
    // touch->letter mapping so scrub selection stays aligned with what's drawn.
    private float letterInsetPx() {
        return dpf(12f);
    }

    private float letterContentWidth() {
        return Math.max(1f, getWidth() - (letterInsetPx() * 2f));
    }

    private float letterSlotWidth() {
        return letterContentWidth() / Math.max(1, visibleLetters.length);
    }

    private float letterCenterX(int index) {
        float slot = letterSlotWidth();
        return letterInsetPx() + (slot * index) + (slot * 0.5f);
    }

    private int indexForTouchX(float x) {
        int len = Math.max(1, visibleLetters.length);
        int index = (int) (((x - letterInsetPx()) / letterContentWidth()) * len);
        return Math.max(0, Math.min(len - 1, index));
    }

    /**
     * Subtle recessed "rail" (a small pane of glass) behind the A–Z letters, per the wireframe's
     * recessed scrub track. Toned down at rest, it brightens with the scrub wave so it animates in
     * step with the rest of the dock; the lifted-letter magnifier is drawn separately and unchanged.
     */
    private void drawScrubRail(Canvas canvas, float width, float height) {
        float interact = clamp01(waveStrength);
        float railTop = getPaddingTop() + dp(1);
        float railBottom = height - getPaddingBottom() - dp(1);
        if (railBottom - railTop < dp(6)) {
            float cy = height * 0.5f;
            railTop = cy - dp(6);
            railBottom = cy + dp(6);
        }
        float railHeight = railBottom - railTop;
        // The rail wraps fully around the letters: it sits just inside the view edge while the
        // letters are inset further (letterInsetPx), so even ☆/# have clear margin from the
        // rounded ends. Capsule: fully-rounded pill ends; Default: a softer rounded rectangle.
        float sidePad = dpf(3f);
        float radius = capsuleDock ? (railHeight * 0.5f) : dpf(9f);
        railRect.set(sidePad, railTop, width - sidePad, railBottom);

        // Its own surface: a touch deeper/cooler than the dock so it reads as a separate pane.
        // Darker glass on interaction — at rest a faint frosting, while scrubbing it deepens toward
        // a dark recess so the track reads as "pushed in".
        int base = getCurrentTextColor();
        railFillPaint.setColor(lerpColor(withAlpha(base, 18), withAlpha(0xFF000000, 86), interact));
        canvas.drawRoundRect(railRect, radius, radius, railFillPaint);
        railStrokePaint.setStrokeWidth(Math.max(1f, dpf(1f)));
        railStrokePaint.setColor(lerpColor(withAlpha(base, 30), withAlpha(0xFF000000, 104), interact));
        canvas.drawRoundRect(railRect, radius, radius, railStrokePaint);

        // Thin top sheen line just inside the rail to give it a distinct glassy surface.
        float sheenInset = Math.max(dpf(2f), radius * 0.5f);
        railSheenPaint.setStyle(Paint.Style.STROKE);
        railSheenPaint.setStrokeWidth(Math.max(1f, dpf(1f)));
        railSheenPaint.setColor(withAlpha(base, Math.round(lerp(26f, 64f, interact))));
        float sheenY = railTop + Math.max(dpf(1.5f), railHeight * 0.18f);
        canvas.drawLine(railRect.left + sheenInset, sheenY, railRect.right - sheenInset, sheenY, railSheenPaint);

        if (interact > 0.02f) {
            drawRailShimmer(canvas, railRect, radius, interact);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + ((b - a) * t);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    /**
     * Light particle shimmer that drifts along the rail while scrubbing — sparkles concentrate near
     * the fingertip and twinkle, reinforcing that the rail is its own little pane of glass. Tinted
     * with the focus-letter accent so it blends with the dock's glow.
     */
    private void drawRailShimmer(Canvas canvas, RectF rail, float radius, float interact) {
        float focusX = activeTouchX < 0f ? rail.centerX() : activeTouchX;
        float cyMid = rail.centerY();
        float span = Math.max(1f, rail.width() - (radius * 2f));
        float left = rail.left + radius;
        int tint = resolveFocusLetterColor();
        railShimmerPaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < RAIL_SHIMMER_PARTICLES; i++) {
            float seed = (i * 12.9898f);
            float phase = shimmerPhase + (i * 0.62f);
            float twinkle = 0.5f + (0.5f * (float) Math.sin(phase));
            float drift = fract((seed * 0.0173f) + (shimmerPhase * 0.04f) + (i * 0.067f));
            float px = left + (drift * span);
            float bob = (float) Math.sin((phase * 1.3f) + seed) * (rail.height() * 0.26f);
            float py = cyMid + bob;
            float proximity = 1f - clamp01(Math.abs(px - focusX) / Math.max(1f, rail.width() * 0.32f));
            float alpha = interact * twinkle * (0.22f + (0.78f * proximity));
            if (alpha <= 0.02f) {
                continue;
            }
            float r = dpf(1.1f) * (0.55f + (0.9f * twinkle));
            railShimmerPaint.setColor(withAlpha(tint, Math.round(190f * clamp01(alpha))));
            canvas.drawCircle(px, py, r, railShimmerPaint);
        }
    }

    private void startRailShimmer() {
        if (shimmerAnimator != null) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2f));
        animator.setDuration(1500L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            shimmerPhase = (float) a.getAnimatedValue();
            if (waveStrength > 0.01f) {
                invalidate();
            }
        });
        shimmerAnimator = animator;
        animator.start();
    }

    private void stopRailShimmer() {
        if (shimmerAnimator != null) {
            shimmerAnimator.cancel();
            shimmerAnimator = null;
        }
    }

    public void setCapsuleDockStyle(boolean capsule) {
        if (capsuleDock == capsule) {
            return;
        }
        capsuleDock = capsule;
        invalidate();
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static int lerpColor(int from, int to, float t) {
        float k = clamp01(t);
        int a = Math.round(((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * k));
        int r = Math.round(((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k));
        int g = Math.round(((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k));
        int b = Math.round((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * k));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) return;

        drawScrubRail(canvas, width, height);

        int baseColor = getCurrentTextColor();
        int focusColor = resolveFocusLetterColor();
        letterPaint.setColor(baseColor);
        float baseTextSize = getTextSize();
        letterPaint.setTextSize(baseTextSize);
        float contentBottom = height - getPaddingBottom();
        float slot = letterSlotWidth();
        float anchorX = activeTouchX < 0f ? (width * 0.5f) : activeTouchX;
        float waveAmplitude = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK ? 0f : (dp(15) * waveStrength);
        int activeIndex = resolveActiveIndex(anchorX);
        boolean hasInlineFocus = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
            && waveStrength > 0.01f
            && (activeTouchX >= 0f || lockedInlineLetter != null);

        for (int i = 0; i < visibleLetters.length; i++) {
            float x = letterCenterX(i);
            float distance = Math.abs(x - anchorX) / Math.max(1f, slot);
            float envelope = (float) Math.exp(-(distance * distance) * 0.85f);
            float waveLift = (float) Math.sin(Math.min(1f, envelope) * (Math.PI * 0.5f)) * waveAmplitude;
            boolean activeFocus = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
                ? (hasInlineFocus && i == activeIndex)
                : (waveStrength > 0.01f && i == activeIndex);
            if (activeFocus && interactionMode != InteractionMode.INLINE_EMPHASIS_TRACK) {
                waveLift *= 1.2f;
                waveLift = Math.max(waveLift, dp(6));
            }
            float scale = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
                ? (activeFocus ? 1.14f : 1f)
                : (1f + (0.34f * envelope * waveStrength));
            letterPaint.setTextSize(baseTextSize * scale);
            applyLetterWeight(envelope, activeFocus);
            Paint.FontMetrics letterMetrics = letterPaint.getFontMetrics();
            float baseline = (contentBottom - dp(2) - letterMetrics.descent) - waveLift;
            if (activeFocus) {
                letterPaint.setColor(focusColor);
            } else {
                float colorProgress = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
                    ? 0f
                    : clamp01(envelope * waveStrength * 0.72f);
                letterPaint.setColor(blendColors(baseColor, focusColor, colorProgress));
            }
            canvas.drawText(String.valueOf(visibleLetters[i]), x, baseline, letterPaint);
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public void setScrubCallback(@Nullable ScrubCallback callback) {
        this.callback = callback;
    }

    public void setVisibleLetters(@NonNull Set<Character> letters) {
        if (letters.isEmpty()) {
            if (Arrays.equals(visibleLetters, LETTERS)) {
                return;
            }
            visibleLetters = LETTERS;
            invalidate();
            return;
        }
        LinkedHashSet<Character> normalized = new LinkedHashSet<>();
        for (Character c : letters) {
            if (c == null) continue;
            char upper = Character.toUpperCase(c);
            if ((upper >= 'A' && upper <= 'Z') || upper == '#') {
                normalized.add(upper);
            }
        }
        if (normalized.isEmpty()) {
            if (Arrays.equals(visibleLetters, LETTERS)) {
                return;
            }
            visibleLetters = LETTERS;
            invalidate();
            return;
        }
        char[] out = new char[normalized.size() + 1];
        int i = 0;
        out[i++] = PINNED_APPS_SYMBOL;
        for (char base : ALPHABET_LETTERS) {
            if (normalized.contains(base)) {
                out[i++] = base;
            }
        }
        char[] nextVisibleLetters;
        if (i <= 1) {
            nextVisibleLetters = LETTERS;
        } else if (i == out.length) {
            nextVisibleLetters = out;
        } else {
            char[] trimmed = new char[i];
            System.arraycopy(out, 0, trimmed, 0, i);
            nextVisibleLetters = trimmed;
        }
        if (Arrays.equals(visibleLetters, nextVisibleLetters)) {
            return;
        }
        visibleLetters = nextVisibleLetters;
        invalidate();
    }

    public void setInteractionAccentColor(int color) {
        if (accentColor == color) {
            return;
        }
        accentColor = color;
        invalidate();
    }

    public void setInteractionMode(@NonNull InteractionMode mode) {
        if (interactionMode == mode && (mode != InteractionMode.WAVE_TRACK || lockedInlineLetter == null)) {
            return;
        }
        interactionMode = mode;
        if (mode == InteractionMode.WAVE_TRACK) {
            lockedInlineLetter = null;
        }
        invalidate();
    }

    public void setLockedInlineLetter(@Nullable Character letter) {
        if (lockedInlineLetter == null ? letter == null : lockedInlineLetter.equals(letter)) {
            return;
        }
        lockedInlineLetter = letter;
        invalidate();
    }

    public void getLetterFocusBoundsOnScreen(char letter, @NonNull RectF out) {
        LetterVisualMetrics metrics = new LetterVisualMetrics();
        if (getLetterVisualMetricsOnScreen(letter, metrics)) {
            out.set(metrics.glassBoundsRaw);
        } else {
            out.setEmpty();
        }
    }

    public boolean getLetterVisualMetricsOnScreen(char letter, @NonNull LetterVisualMetrics out) {
        out.clear();
        if (getWidth() <= 0 || getHeight() <= 0 || visibleLetters.length == 0) {
            return false;
        }

        float width = Math.max(1f, getWidth());
        float slot = letterSlotWidth();
        float anchorX = activeTouchX < 0f ? (width * 0.5f) : activeTouchX;
        int activeIndex = resolveActiveIndex(anchorX);
        int index = indexOfVisibleLetter(letter);
        if (index < 0) {
            index = activeIndex;
        }
        if (index < 0 || index >= visibleLetters.length) {
            return false;
        }

        boolean activeFocus = index == activeIndex;
        float x = letterCenterX(index);
        float distance = Math.abs(x - anchorX) / Math.max(1f, slot);
        float envelope = (float) Math.exp(-(distance * distance) * 0.85f);
        float waveLift = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
            ? 0f
            : (float) Math.sin(Math.min(1f, envelope) * (Math.PI * 0.5f)) * (dp(15) * waveStrength);
        if (activeFocus && interactionMode != InteractionMode.INLINE_EMPHASIS_TRACK) {
            waveLift *= 1.2f;
            waveLift = Math.max(waveLift, dp(6));
        }
        float baseTextSize = getTextSize();
        float scale = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
            ? (activeFocus ? 1.14f : 1f)
            : (1f + (0.34f * envelope * waveStrength));
        letterPaint.setTextSize(baseTextSize * scale);
        applyLetterWeight(envelope, activeFocus);

        Paint.FontMetrics fontMetrics = letterPaint.getFontMetrics();
        float baseline = (getHeight() - getPaddingBottom() - dp(2) - fontMetrics.descent) - waveLift;
        String label = String.valueOf(visibleLetters[index]);
        glyphRect.setEmpty();
        letterPaint.getTextBounds(label, 0, label.length(), glyphRect);
        float glyphLeft = x + glyphRect.left;
        float glyphRight = x + glyphRect.right;
        float glyphTop = baseline + glyphRect.top;
        float glyphBottom = baseline + glyphRect.bottom;
        float glyphWidth = glyphRight - glyphLeft;
        if (glyphRight <= glyphLeft) {
            float textWidth = Math.max(letterPaint.measureText(label), dp(8));
            glyphLeft = x - (textWidth * 0.5f);
            glyphRight = x + (textWidth * 0.5f);
            glyphWidth = textWidth;
        } else {
            glyphWidth = Math.max(dp(8), glyphWidth);
        }
        float padX = Math.max(dp(3), Math.min(dp(5), slot * 0.10f));
        float padY = Math.max(dp(2), Math.min(dp(4), Math.max(1f, glyphBottom - glyphTop) * 0.22f));
        float glassLeft = Math.max(0f, glyphLeft - padX);
        float glassRight = Math.min(getWidth(), glyphRight + padX);
        float glassTop = Math.max(0f, glyphTop - padY);
        float glassBottom = Math.min(getHeight(), glyphBottom + padY);

        int[] loc = new int[2];
        getLocationOnScreen(loc);
        out.letter = visibleLetters[index];
        out.centerRawX = loc[0] + x;
        out.baselineRawY = loc[1] + baseline;
        out.glyphBoundsRaw.set(
            loc[0] + (x - (glyphWidth * 0.5f)),
            loc[1] + glyphTop,
            loc[0] + (x + (glyphWidth * 0.5f)),
            loc[1] + glyphBottom
        );
        out.glassBoundsRaw.set(
            loc[0] + glassLeft,
            loc[1] + glassTop,
            loc[0] + glassRight,
            loc[1] + glassBottom
        );
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (callback == null) return super.onTouchEvent(event);
        float x = Math.max(0f, Math.min(getWidth(), event.getX()));
        char letter = pickLetter(x, event.getActionMasked() != MotionEvent.ACTION_DOWN);
        int selectionIndex = Math.max(0, (int) ((-event.getY()) / Math.max(12f, getHeight() / 2f)));
        currentSelectionIndex = selectionIndex;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopSettleAnimation();
                startRailShimmer();
                updateInteractionRenderLayer(true);
                activeTouchX = x;
                activeLetterIndex = indexOfVisibleLetter(letter);
                waveStrength = 1f;
                if (interactionMode == InteractionMode.WAVE_TRACK) {
                    lockedInlineLetter = null;
                }
                bringToFront();
                updateInteractionLayerOffset();
                invalidate();
                long now = event.getEventTime();
                boolean isDoubleTap = (now - lastTapUpTimeMs) <= doubleTapTimeoutMs
                    && !Float.isNaN(lastTapUpX)
                    && Math.abs(x - lastTapUpX) <= doubleTapSlopPx;
                if (isDoubleTap) {
                    suppressUpScrub = true;
                    callback.onDoubleTap();
                    return true;
                }
                suppressUpScrub = false;
                callback.onScrub(letter, currentSelectionIndex, event.getX(), event.getY(), event.getRawX(), event.getRawY(), GesturePhase.DOWN);
                return true;
            case MotionEvent.ACTION_MOVE:
                activeTouchX = x;
                waveStrength = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK ? 0.92f : 1f;
                updateInteractionLayerOffset();
                invalidate();
                callback.onScrub(letter, currentSelectionIndex, event.getX(), event.getY(), event.getRawX(), event.getRawY(), GesturePhase.MOVE);
                return true;
            case MotionEvent.ACTION_UP:
                lastTapUpTimeMs = event.getEventTime();
                lastTapUpX = x;
                if (!suppressUpScrub) {
                    callback.onScrub(letter, currentSelectionIndex, event.getX(), event.getY(), event.getRawX(), event.getRawY(), GesturePhase.UP);
                }
                suppressUpScrub = false;
                if (interactionMode == InteractionMode.WAVE_TRACK) {
                    activeLetterIndex = -1;
                    animateWaveRelease();
                } else {
                    waveStrength = 0f;
                    activeTouchX = -1f;
                    activeLetterIndex = -1;
                    stopRailShimmer();
                    updateInteractionRenderLayer(false);
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                suppressUpScrub = false;
                callback.onCancel();
                if (interactionMode == InteractionMode.WAVE_TRACK) {
                    activeLetterIndex = -1;
                    animateWaveRelease();
                } else {
                    waveStrength = 0f;
                    activeTouchX = -1f;
                    activeLetterIndex = -1;
                    stopRailShimmer();
                    updateInteractionRenderLayer(false);
                    invalidate();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void animateWaveRelease() {
        stopSettleAnimation();
        if (!isAttachedToWindow()) {
            waveStrength = 0f;
            activeTouchX = -1f;
            stopRailShimmer();
            updateInteractionRenderLayer(false);
            invalidate();
            return;
        }
        settleAnimator = ValueAnimator.ofFloat(waveStrength, 0f);
        settleAnimator.setDuration(165L);
        settleAnimator.addUpdateListener(animation -> {
            waveStrength = (float) animation.getAnimatedValue();
            updateInteractionLayerOffset();
            invalidate();
        });
        settleAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                waveStrength = 0f;
                activeTouchX = -1f;
                stopRailShimmer();
                updateInteractionRenderLayer(false);
                invalidate();
            }

            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                stopRailShimmer();
                updateInteractionRenderLayer(false);
            }
        });
        settleAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopRailShimmer();
        stopSettleAnimation();
    }

    private void stopSettleAnimation() {
        if (settleAnimator != null) {
            settleAnimator.cancel();
            settleAnimator = null;
        }
    }

    private void updateInteractionRenderLayer(boolean active) {
        if (interactionRenderActive == active && getLayerType() != LAYER_TYPE_NONE) {
            if (!active) return;
        }
        interactionRenderActive = active;
        setLayerType(active ? LAYER_TYPE_NONE : LAYER_TYPE_HARDWARE, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            float z = dp(active ? 46 : 24);
            setElevation(z);
            setTranslationZ(z);
        }
    }

    private char pickLetter(float x, boolean applyHysteresis) {
        float slotWidth = letterSlotWidth();
        int index = indexForTouchX(x);
        if (interactionMode == InteractionMode.WAVE_TRACK) {
            if (activeLetterIndex < 0 || !applyHysteresis) {
                activeLetterIndex = index;
            } else if (Math.abs(index - activeLetterIndex) > 1) {
                activeLetterIndex = index;
            } else if (index != activeLetterIndex) {
                float boundary = letterInsetPx() + (Math.max(index, activeLetterIndex) * slotWidth);
                float hysteresis = slotWidth * LETTER_SLOT_HYSTERESIS_RATIO;
                if (index > activeLetterIndex) {
                    if (x >= (boundary + hysteresis)) {
                        activeLetterIndex = index;
                    }
                } else if (x <= (boundary - hysteresis)) {
                    activeLetterIndex = index;
                }
            }
            index = activeLetterIndex;
        } else {
            activeLetterIndex = index;
        }
        return visibleLetters[index];
    }

    private int indexOfVisibleLetter(char letter) {
        char upper = Character.toUpperCase(letter);
        for (int i = 0; i < visibleLetters.length; i++) {
            if (visibleLetters[i] == upper) {
                return i;
            }
        }
        return -1;
    }

    private int resolveActiveIndex(float anchorX) {
        int activeIndex = indexForTouchX(anchorX);
        if (interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK && lockedInlineLetter != null) {
            int lockedIndex = indexOfVisibleLetter(lockedInlineLetter);
            if (lockedIndex >= 0) {
                activeIndex = lockedIndex;
            }
        }
        return activeIndex;
    }

    private void updateInteractionLayerOffset() {
        setTranslationY(0f);
    }

    private int resolveFocusLetterColor() {
        int vivid = boostColor(accentColor, 1.22f, 1.12f);
        return blendColors(vivid, Color.WHITE, 0.18f);
    }

    private void applyLetterWeight(float envelope, boolean active) {
        float influence = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK
            ? (active ? 1f : 0f)
            : Math.max(0f, Math.min(1f, envelope * waveStrength));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            int weight = (int) (420 + (influence * 380));
            if (active) weight = interactionMode == InteractionMode.INLINE_EMPHASIS_TRACK ? 920 : 900;
            weight = Math.max(200, Math.min(900, weight));
            letterPaint.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        } else {
            if (active) {
                letterPaint.setTypeface(Typeface.DEFAULT_BOLD);
                letterPaint.setFakeBoldText(true);
            } else if (influence > 0.55f) {
                letterPaint.setTypeface(Typeface.DEFAULT_BOLD);
                letterPaint.setFakeBoldText(false);
            } else {
                letterPaint.setTypeface(Typeface.DEFAULT);
                letterPaint.setFakeBoldText(false);
            }
        }
    }

    private static int blendColors(int from, int to, float ratio) {
        float t = Math.max(0f, Math.min(1f, ratio));
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    private static int boostColor(int color, float saturationMultiplier, float valueMultiplier) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * saturationMultiplier));
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * valueMultiplier));
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
