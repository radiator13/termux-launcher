package com.termux.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Glassmorphic renderer for AZ and icon-row drag interactions.
 */
public final class LauncherAzGestureFxView extends View {

    public enum InteractionMode {
        LETTER_TRACK,
        ICON_TRACK_LOCKED
    }

    public enum RenderLayer {
        UNDERLAY,
        OVERLAY
    }

    private final Paint glassFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bridgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bloomPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pageIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path liquidBridgePath = new Path();

    private final RectF tmpRect = new RectF();
    private final RectF focusDisplayRect = new RectF();
    private final RectF focusRawRect = new RectF();
    private final RectF azRowRawBounds = new RectF();
    private final RectF appsRowRawBounds = new RectF();
    private final RectF indicatorBandRawBounds = new RectF();
    private final RectF extraKeysRawBounds = new RectF();
    private final int[] locationOnScreen = new int[2];

    private int glassTintColor = 0xFF86A7FF;
    private int vividLetterTintColor = 0xFF9AB8FF;
    private int edgeTintColor = 0xFF7CE2FF;
    private int overflowGlowTintColor = 0xFF98F0FF;

    private boolean dragActive;
    private float targetRawX;
    private float targetRawY;
    private float displayRawX;
    private float displayRawY;

    private boolean hasAnchor;
    private float anchorRawX;
    private float anchorRawY;

    private boolean hasFocus;
    private boolean filteredOverflowActive;
    private boolean canPageLeft;
    private boolean canPageRight;
    private int currentPageIndex;
    private int pageCount = 1;
    private boolean interactionOverflowActive;
    private boolean interactionCanPageLeft;
    private boolean interactionCanPageRight;
    private int interactionCurrentPageIndex;
    private int interactionPageCount = 1;
    private boolean interactionShowsPageIndicators;
    private boolean interactionUseSubtlePageIndicators;
    private float edgeProximityLeft;
    private float edgeProximityRight;
    @NonNull private RenderLayer renderLayer = RenderLayer.OVERLAY;

    @NonNull private InteractionMode interactionMode = InteractionMode.LETTER_TRACK;
    private long lastFocusUpdateUptimeMs = 0L;
    private static final long FOCUS_HOLD_MS = 140L;

    private boolean launchBloomActive;
    private float launchBloomRawX;
    private float launchBloomRawY;
    private float launchBloomProgress;
    @Nullable private ValueAnimator launchBloomAnimator;

    public LauncherAzGestureFxView(Context context) {
        super(context);
        init();
    }

    public LauncherAzGestureFxView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LauncherAzGestureFxView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);

        glassFillPaint.setStyle(Paint.Style.FILL);
        glassInnerPaint.setStyle(Paint.Style.FILL);
        glassStrokePaint.setStyle(Paint.Style.STROKE);
        glassStrokePaint.setStrokeCap(Paint.Cap.ROUND);
        glassStrokePaint.setStrokeJoin(Paint.Join.ROUND);

        bridgePaint.setStyle(Paint.Style.FILL);
        edgePaint.setStyle(Paint.Style.FILL);
        edgeInnerPaint.setStyle(Paint.Style.FILL);
        pageIndicatorPaint.setStyle(Paint.Style.FILL);
    }

    public void setColors(int glassTintColor, int edgeTintColor) {
        this.glassTintColor = enforceGlassVisibility(glassTintColor, 0.78f);
        this.vividLetterTintColor = boostColor(this.glassTintColor, 1.20f, 1.08f);
        this.edgeTintColor = enforceGlassVisibility(edgeTintColor, 0.84f);
        this.overflowGlowTintColor = boostColor(this.edgeTintColor, 1.05f, 1.18f);
        invalidate();
    }

    public void updateDrag(
        boolean active,
        float rawX,
        float rawY,
        boolean anchorVisible,
        float anchorRawX,
        float anchorRawY,
        @Nullable RectF focusedBoundsRaw,
        @NonNull InteractionMode mode
    ) {
        dragActive = active;
        targetRawX = rawX;
        targetRawY = rawY;
        hasAnchor = anchorVisible;
        this.anchorRawX = anchorRawX;
        this.anchorRawY = anchorRawY;
        interactionMode = mode;

        if (focusedBoundsRaw != null) {
            hasFocus = true;
            focusRawRect.set(focusedBoundsRaw);
        } else {
            hasFocus = false;
        }
        invalidate();
    }

    public void setRowBounds(@Nullable RectF azRowRaw, @Nullable RectF appsRowRaw, @Nullable RectF indicatorBandRaw, @Nullable RectF extraKeysRowRaw) {
        if (azRowRaw != null) {
            azRowRawBounds.set(azRowRaw);
        } else {
            azRowRawBounds.setEmpty();
        }
        if (appsRowRaw != null) {
            appsRowRawBounds.set(appsRowRaw);
        } else {
            appsRowRawBounds.setEmpty();
        }
        if (indicatorBandRaw != null) {
            indicatorBandRawBounds.set(indicatorBandRaw);
        } else {
            indicatorBandRawBounds.setEmpty();
        }
        if (extraKeysRowRaw != null) {
            extraKeysRawBounds.set(extraKeysRowRaw);
        } else {
            extraKeysRawBounds.setEmpty();
        }
    }

    public void setFilteredOverflowState(boolean active, boolean pageLeft, boolean pageRight, int currentPageIndex, int pageCount) {
        filteredOverflowActive = active;
        canPageLeft = pageLeft;
        canPageRight = pageRight;
        this.currentPageIndex = Math.max(0, currentPageIndex);
        this.pageCount = Math.max(1, pageCount);
        refreshVisibility();
        invalidate();
    }

    public void setInteractionOverflowState(
        boolean active,
        boolean pageLeft,
        boolean pageRight,
        int currentPageIndex,
        int pageCount,
        boolean showPageIndicators,
        boolean useSubtlePageIndicators
    ) {
        interactionOverflowActive = active;
        interactionCanPageLeft = pageLeft;
        interactionCanPageRight = pageRight;
        interactionCurrentPageIndex = Math.max(0, currentPageIndex);
        interactionPageCount = Math.max(1, pageCount);
        interactionShowsPageIndicators = showPageIndicators;
        interactionUseSubtlePageIndicators = useSubtlePageIndicators;
        refreshVisibility();
        invalidate();
    }

    public void setEdgeProximity(float left, float right) {
        edgeProximityLeft = clamp01(left);
        edgeProximityRight = clamp01(right);
        invalidate();
    }

    public void setRenderLayer(@NonNull RenderLayer renderLayer) {
        this.renderLayer = renderLayer;
        refreshVisibility();
        invalidate();
    }

    public void clearDrag(boolean keepOverflowAffordance) {
        dragActive = false;
        hasFocus = false;
        hasAnchor = false;
        displayRawX = 0f;
        displayRawY = 0f;
        edgeProximityLeft = 0f;
        edgeProximityRight = 0f;
        interactionMode = InteractionMode.LETTER_TRACK;
        if (!keepOverflowAffordance) {
            filteredOverflowActive = false;
            canPageLeft = false;
            canPageRight = false;
            currentPageIndex = 0;
            pageCount = 1;
            interactionOverflowActive = false;
            interactionCanPageLeft = false;
            interactionCanPageRight = false;
            interactionCurrentPageIndex = 0;
            interactionPageCount = 1;
            interactionShowsPageIndicators = false;
            interactionUseSubtlePageIndicators = false;
        }
        launchBloomActive = false;
        launchBloomProgress = 0f;
        refreshVisibility();
        invalidate();
    }

    private void refreshVisibility() {
        boolean shouldDrawInteractionOverflow = interactionOverflowActive
            && (interactionCanPageLeft || interactionCanPageRight || interactionPageCount > 1);
        boolean shouldDrawAzOverflow = filteredOverflowActive
            && interactionShowsPageIndicators
            && (canPageLeft || canPageRight || pageCount > 1);
        setVisibility((shouldDrawInteractionOverflow || shouldDrawAzOverflow) ? VISIBLE : GONE);
    }

    public void playLaunchBloom(float rawX, float rawY) {
        // Intentionally disabled: launch glow visuals were removed.
        launchBloomRawX = rawX;
        launchBloomRawY = rawY;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (launchBloomAnimator != null) {
            launchBloomAnimator.cancel();
            launchBloomAnimator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        getLocationOnScreen(locationOnScreen);

        boolean shouldDrawInteractionOverflow = interactionOverflowActive
            && (interactionCanPageLeft || interactionCanPageRight || interactionPageCount > 1);
        boolean shouldDrawAzOverflow = filteredOverflowActive
            && interactionShowsPageIndicators
            && (canPageLeft || canPageRight || pageCount > 1);
        if (!shouldDrawInteractionOverflow && !shouldDrawAzOverflow) {
            return;
        }
        if (shouldDrawInteractionOverflow) {
            if (renderLayer == RenderLayer.UNDERLAY && !interactionUseSubtlePageIndicators) {
                drawEdgeGlowAmbient(canvas);
            }
            if (renderLayer == RenderLayer.OVERLAY) {
                drawInteractionPageIndicators(canvas);
            }
        }
        if (shouldDrawAzOverflow && renderLayer == RenderLayer.OVERLAY) {
            drawGlassEdgeCapsules(canvas);
            drawPageIndicators(canvas);
        }
    }

    private void drawEdgeGlowAmbient(Canvas canvas) {
        if (appsRowRawBounds.isEmpty()) {
            return;
        }
        float top = appsRowRawBounds.top - locationOnScreen[1];
        float bottom = appsRowRawBounds.bottom - locationOnScreen[1];
        int saveCount = canvas.save();
        canvas.clipRect(0f, top, getWidth(), bottom);

        float glowHeight = Math.max(dp(34f), bottom - top);
        float edgeWidth = Math.max(dp(24f), getWidth() * 0.062f);
        float spread = Math.max(dp(20f), edgeWidth * 1.18f);
        float radius = Math.max(dp(18f), glowHeight * 0.30f);

        if (interactionCanPageLeft) {
            drawEdgeGlowAmbient(canvas, 0f, top, edgeWidth, bottom, radius, spread, false);
        }
        if (interactionCanPageRight) {
            drawEdgeGlowAmbient(canvas, getWidth() - edgeWidth, top, getWidth(), bottom, radius, spread, true);
        }
        canvas.restoreToCount(saveCount);
    }

    private void drawEdgeGlowAmbient(Canvas canvas, float left, float top, float right, float bottom, float radius, float spread, boolean rightEdge) {
        RectF glow = new RectF(left, top, right, bottom);
        if (rightEdge) {
            glow.left -= spread * 0.28f;
            glow.right += spread;
        } else {
            glow.left -= spread;
            glow.right += spread * 0.28f;
        }
        glow.top -= dp(10f);
        glow.bottom += dp(10f);
        edgePaint.setColor(withAlpha(overflowGlowTintColor, 68));
        canvas.drawRoundRect(glow, radius, radius, edgePaint);

        RectF innerGlow = new RectF(glow);
        innerGlow.inset(spread * 0.30f, dp(6f));
        edgePaint.setColor(withAlpha(overflowGlowTintColor, 44));
        canvas.drawRoundRect(innerGlow, Math.max(dp(16f), radius - dp(6f)), Math.max(dp(16f), radius - dp(6f)), edgePaint);
    }

    private void drawLetterGlassDroplet(Canvas canvas) {
        float cx = displayRawX - locationOnScreen[0];
        float cy = displayRawY - locationOnScreen[1];
        if (!azRowRawBounds.isEmpty()) {
            float top = azRowRawBounds.top - locationOnScreen[1];
            float bottom = azRowRawBounds.bottom - locationOnScreen[1];
            cy = clamp(cy, top + dp(6f), bottom - dp(6f));
        }

        float w = dp(24f);
        float h = dp(18f);
        float radius = dp(9f);
        if (hasFocus && !focusRawRect.isEmpty()) {
            RectF focusLocal = new RectF(
                focusRawRect.left - locationOnScreen[0],
                focusRawRect.top - locationOnScreen[1],
                focusRawRect.right - locationOnScreen[0],
                focusRawRect.bottom - locationOnScreen[1]
            );
            float expandX = Math.max(dp(0.8f), focusLocal.width() * 0.04f);
            float expandY = Math.max(dp(0.6f), focusLocal.height() * 0.03f);
            focusLocal.inset(-expandX, -expandY);
            if (!azRowRawBounds.isEmpty()) {
                float top = azRowRawBounds.top - locationOnScreen[1];
                float bottom = azRowRawBounds.bottom - locationOnScreen[1];
                float centerY = focusLocal.centerY();
                centerY = clamp(centerY, top + (focusLocal.height() * 0.5f), bottom - (focusLocal.height() * 0.5f));
                float halfH = focusLocal.height() * 0.5f;
                focusLocal.top = centerY - halfH;
                focusLocal.bottom = centerY + halfH;
            }
            drawLetterGlassPopupEvolution(canvas, focusLocal, Math.max(dp(8f), focusLocal.height() * 0.46f));
            return;
        }

        tmpRect.set(cx - (w * 0.5f), cy - (h * 0.5f), cx + (w * 0.5f), cy + (h * 0.5f));
        drawLetterGlassPopupEvolution(canvas, tmpRect, radius);
    }

    private void drawLetterGlassPopupEvolution(Canvas canvas, RectF pill, float radius) {
        glassFillPaint.setColor(withAlpha(vividLetterTintColor, 208));
        canvas.drawRoundRect(pill, radius, radius, glassFillPaint);

        RectF innerVeil = new RectF(pill);
        float veilPad = Math.max(dp(1.4f), Math.min(pill.width(), pill.height()) * 0.10f);
        innerVeil.inset(veilPad, veilPad * 0.95f);
        glassInnerPaint.setColor(withAlpha(Color.WHITE, 48));
        canvas.drawRoundRect(innerVeil, Math.max(dp(4f), radius - veilPad), Math.max(dp(4f), radius - veilPad), glassInnerPaint);

        RectF sheen = new RectF(innerVeil);
        sheen.bottom = sheen.top + Math.max(dp(4.6f), innerVeil.height() * 0.42f);
        glassInnerPaint.setColor(withAlpha(Color.WHITE, 84));
        canvas.drawRoundRect(sheen, Math.max(dp(3f), radius - dp(5f)), Math.max(dp(3f), radius - dp(5f)), glassInnerPaint);

        glassStrokePaint.setStrokeWidth(dp(1.25f));
        glassStrokePaint.setColor(withAlpha(Color.WHITE, 144));
        canvas.drawRoundRect(pill, radius, radius, glassStrokePaint);

        RectF innerRim = new RectF(pill);
        innerRim.inset(dp(0.9f), dp(0.9f));
        glassStrokePaint.setStrokeWidth(dp(0.9f));
        glassStrokePaint.setColor(withAlpha(glassTintColor, 122));
        canvas.drawRoundRect(innerRim, Math.max(dp(4f), radius - dp(1.2f)), Math.max(dp(4f), radius - dp(1.2f)), glassStrokePaint);
    }

    private void drawLockedIconTrackGlass(Canvas canvas) {
        if (hasFocus) {
            lastFocusUpdateUptimeMs = SystemClock.uptimeMillis();
            if (focusDisplayRect.isEmpty()) {
                focusDisplayRect.set(focusRawRect);
            } else {
                blendRect(focusDisplayRect, focusRawRect, 0.31f);
            }
        } else {
            if ((SystemClock.uptimeMillis() - lastFocusUpdateUptimeMs) > FOCUS_HOLD_MS) {
                focusDisplayRect.setEmpty();
            }
        }

        int save = -1;
        if (!appsRowRawBounds.isEmpty()) {
            float left = appsRowRawBounds.left - locationOnScreen[0];
            float top = appsRowRawBounds.top - locationOnScreen[1];
            float right = appsRowRawBounds.right - locationOnScreen[0];
            float bottom = appsRowRawBounds.bottom - locationOnScreen[1];
            save = canvas.save();
            canvas.clipRect(left, top, right, bottom);
        }

        if (hasAnchor && !focusDisplayRect.isEmpty()) {
            drawLiquidBridge(canvas, focusDisplayRect);
        }

        if (!focusDisplayRect.isEmpty()) {
            RectF local = new RectF(
                focusDisplayRect.left - locationOnScreen[0],
                focusDisplayRect.top - locationOnScreen[1],
                focusDisplayRect.right - locationOnScreen[0],
                focusDisplayRect.bottom - locationOnScreen[1]
            );
            float shellPad = Math.max(dp(6f), Math.min(local.width(), local.height()) * 0.17f);
            local.inset(-shellPad, -shellPad);
            drawGlassBody(canvas, local, dp(14f), 0.72f, 0.42f);
        } else {
            float cx = displayRawX - locationOnScreen[0];
            float cy = displayRawY - locationOnScreen[1];
            float halfW = dp(17f);
            float halfH = dp(17f);
            tmpRect.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
            drawGlassBody(canvas, tmpRect, dp(12f), 0.56f, 0.24f);
        }
        if (save >= 0) {
            canvas.restoreToCount(save);
        }
    }

    private void drawLiquidBridge(Canvas canvas, RectF focusRaw) {
        float startX = anchorRawX - locationOnScreen[0];
        float startY = anchorRawY - locationOnScreen[1];
        float endX = focusRaw.centerX() - locationOnScreen[0];
        float endY = Math.min(displayRawY, focusRaw.centerY()) - locationOnScreen[1];

        float neck = dp(7f);
        float shoulder = dp(12f);

        liquidBridgePath.reset();
        liquidBridgePath.moveTo(startX - neck, startY);
        liquidBridgePath.cubicTo(startX - shoulder, lerp(startY, endY, 0.42f), endX - shoulder, lerp(startY, endY, 0.58f), endX - neck, endY);
        liquidBridgePath.lineTo(endX + neck, endY);
        liquidBridgePath.cubicTo(endX + shoulder, lerp(startY, endY, 0.58f), startX + shoulder, lerp(startY, endY, 0.42f), startX + neck, startY);
        liquidBridgePath.close();

        bridgePaint.setColor(withAlpha(glassTintColor, 106));
        canvas.drawPath(liquidBridgePath, bridgePaint);
        bridgePaint.setColor(withAlpha(Color.WHITE, 52));
        canvas.drawPath(liquidBridgePath, bridgePaint);
    }

    private void drawGlassEdgeCapsules(Canvas canvas) {
        // Side capsules removed to keep the overflow affordance clean.
    }

    private void drawPageIndicators(Canvas canvas) {
        if (!filteredOverflowActive || pageCount <= 1 || appsRowRawBounds.isEmpty() || azRowRawBounds.isEmpty()) {
            return;
        }
        drawPageIndicatorStrip(
            canvas,
            currentPageIndex,
            pageCount,
            clamp(getWidth() * 0.34f, dp(120f), dp(220f)),
            dp(8.5f),
            dp(10f),
            withAlpha(boostColor(glassTintColor, 1.22f, 1.08f), 150),
            withAlpha(boostColor(edgeTintColor, 1.28f, 1.14f), 232),
            true
        );
    }

    private void drawInteractionPageIndicators(Canvas canvas) {
        if (!interactionOverflowActive || interactionPageCount <= 1 || appsRowRawBounds.isEmpty()) {
            return;
        }
        boolean subtle = interactionUseSubtlePageIndicators;
        drawPageIndicatorStrip(
            canvas,
            interactionCurrentPageIndex,
            interactionPageCount,
            subtle
                ? clamp(getWidth() * 0.22f, dp(62f), dp(118f))
                : clamp(getWidth() * 0.34f, dp(120f), dp(220f)),
            subtle ? dp(6f) : dp(8.5f),
            subtle ? dp(7f) : dp(10f),
            subtle
                ? withAlpha(boostColor(glassTintColor, 1.16f, 1.02f), 112)
                : withAlpha(boostColor(glassTintColor, 1.22f, 1.08f), 150),
            subtle
                ? withAlpha(boostColor(edgeTintColor, 1.22f, 1.08f), 196)
                : withAlpha(boostColor(edgeTintColor, 1.28f, 1.14f), 232),
            !subtle
        );
    }

    private void drawPageIndicatorStrip(
        Canvas canvas,
        int activePageIndex,
        int totalPages,
        float totalWidth,
        float lineHeight,
        float segmentGap,
        int backgroundLineColor,
        int activeLineColor,
        boolean insetActive
    ) {
        if (totalPages <= 1) {
            return;
        }
        float cy = resolvePageIndicatorCenterY();
        float dotSize = lineHeight;
        float activeWidth = insetActive ? dp(38f) : dp(26f);
        float desiredWidth = activeWidth
            + (dotSize * Math.max(0, totalPages - 1))
            + (segmentGap * Math.max(0, totalPages - 1));
        if (desiredWidth > totalWidth) {
            float scale = totalWidth / Math.max(1f, desiredWidth);
            dotSize = Math.max(dp(4.5f), dotSize * scale);
            activeWidth = Math.max(dp(20f), activeWidth * scale);
            segmentGap = Math.max(dp(4f), segmentGap * scale);
            desiredWidth = activeWidth
                + (dotSize * Math.max(0, totalPages - 1))
                + (segmentGap * Math.max(0, totalPages - 1));
        }

        float left = (getWidth() - desiredWidth) * 0.5f;
        for (int i = 0; i < totalPages; i++) {
            boolean activePage = i == activePageIndex;
            float width = activePage ? activeWidth : dotSize;
            float radius = dotSize * 0.5f;
            tmpRect.set(left, cy - radius, left + width, cy + radius);
            pageIndicatorPaint.setColor(activePage ? activeLineColor : backgroundLineColor);
            canvas.drawRoundRect(tmpRect, radius, radius, pageIndicatorPaint);
            left += width + segmentGap;
        }
    }

    private float resolvePageIndicatorCenterY() {
        if (!indicatorBandRawBounds.isEmpty()) {
            return ((indicatorBandRawBounds.top + indicatorBandRawBounds.bottom) * 0.5f) - locationOnScreen[1];
        }
        float rowBottom = appsRowRawBounds.bottom - locationOnScreen[1];
        if (azRowRawBounds.isEmpty()) {
            return rowBottom + dp(4.5f);
        }
        float azTop = azRowRawBounds.top - locationOnScreen[1];
        float gapTop = rowBottom + dp(4f);
        float gapBottom = azTop - dp(8f);
        return gapTop <= gapBottom
            ? clamp((gapTop + gapBottom) * 0.5f, gapTop, gapBottom)
            : rowBottom + dp(4.5f);
    }

    private void drawLaunchGlassBloom(Canvas canvas) {
        float cx = launchBloomRawX - locationOnScreen[0];
        float cy = launchBloomRawY - locationOnScreen[1];
        float maxR = (float) Math.hypot(getWidth(), getHeight());
        float r = lerp(dp(22f), maxR * 1.08f, launchBloomProgress);
        float alphaFactor = (1f - launchBloomProgress);

        bloomPaint.setColor(withAlpha(glassTintColor, (int) (198 * alphaFactor)));
        canvas.drawCircle(cx, cy, r, bloomPaint);

        bloomPaint.setColor(withAlpha(Color.WHITE, (int) (132 * alphaFactor)));
        canvas.drawCircle(cx, cy, r * 0.55f, bloomPaint);
    }

    private void drawGlassBody(Canvas canvas, RectF rect, float radius, float tintAlpha, float innerAlpha) {
        glassFillPaint.setColor(withAlpha(glassTintColor, (int) (255f * tintAlpha)));
        canvas.drawRoundRect(rect, radius, radius, glassFillPaint);

        float pad = Math.max(dp(1.8f), Math.min(rect.width(), rect.height()) * 0.12f);
        RectF inner = new RectF(rect);
        inner.inset(pad, pad * 0.9f);
        glassInnerPaint.setColor(withAlpha(Color.WHITE, (int) (255f * innerAlpha)));
        canvas.drawRoundRect(inner, Math.max(dp(4f), radius - pad), Math.max(dp(4f), radius - pad), glassInnerPaint);

        glassStrokePaint.setStrokeWidth(dp(1.6f));
        glassStrokePaint.setColor(withAlpha(Color.WHITE, 178));
        canvas.drawRoundRect(rect, radius, radius, glassStrokePaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float lerp(float start, float end, float t) {
        return start + ((end - start) * t);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int enforceGlassVisibility(int color, float minValue) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0.42f, hsv[1]);
        hsv[2] = Math.max(minValue, hsv[2]);
        return Color.HSVToColor((color >>> 24) == 0 ? 0xE8 : (color >>> 24), hsv);
    }

    private static int boostColor(int color, float satMul, float valMul) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = clamp(hsv[1] * satMul, 0f, 1f);
        hsv[2] = clamp(hsv[2] * valMul, 0f, 1f);
        return Color.HSVToColor((color >>> 24) == 0 ? 0xFF : (color >>> 24), hsv);
    }

    private static void blendRect(RectF out, RectF target, float t) {
        out.left = lerp(out.left, target.left, t);
        out.top = lerp(out.top, target.top, t);
        out.right = lerp(out.right, target.right, t);
        out.bottom = lerp(out.bottom, target.bottom, t);
    }
}
