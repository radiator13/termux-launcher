package com.termux.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Reactive rim glow for the floating glass dock.
 *
 * <p>Replaces the old single blurred stroke-ring (a uniform outline that lost its glow at the
 * rounded corners because the host clips children to its rounded outline). Instead this view draws
 * a continuous rounded-rect rim that is <em>inset</em> from the edge, so the whole glow — corners
 * included — stays inside the clip and never breaks.</p>
 *
 * <p>On top of a uniform base rim it lays a tilt-driven "hot lobe": a sweep gradient whose bright
 * arc points toward the direction the plank is tilting. As the glass-plank tilt animates, that
 * bright band sweeps around the perimeter and swells with the tilt magnitude, so the rim reads as
 * light catching the edge of a physical glass slab rather than a flat, uniform ring.</p>
 */
public class DockEdgeGlowView extends View {

    /** Matches DockPlankController.MAX_TILT_DEG — the tilt magnitude that maps to a full hot lobe. */
    private static final float MAX_TILT_DEG = 4f;

    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rimRect = new RectF();
    private final RectF tmpRect = new RectF();
    private final Matrix sweepMatrix = new Matrix();
    private final int[] sweepColors = new int[5];
    private final float[] sweepStops = {0f, 0.28f, 0.5f, 0.72f, 1f};

    private int accentColor = 0xFF7CE2FF;
    private float cornerRadiusPx = 0f;
    private float glowLevel = 0f;       // 0..1 overall intensity (fades in on press, out on release)
    private float tiltAmount = 0f;      // 0..1 normalized plank tilt magnitude
    private float hotAngleDeg = -90f;   // perimeter angle the edge light pools toward (screen space)

    public DockEdgeGlowView(Context context) {
        super(context);
        init();
    }

    public DockEdgeGlowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DockEdgeGlowView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeCap(Paint.Cap.ROUND);
        rimPaint.setStrokeJoin(Paint.Join.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
    }

    public void setAccentColor(int color) {
        if (accentColor != color) {
            accentColor = color;
            invalidate();
        }
    }

    public void setCornerRadiusPx(float radius) {
        if (cornerRadiusPx != radius) {
            cornerRadiusPx = radius;
            invalidate();
        }
    }

    /**
     * Push the current plank state. Called every animation frame by {@link DockPlankController}.
     *
     * @param level overall glow strength, 0..1 (touch-driven fade)
     * @param tiltXDeg plank rotationX in degrees (tips the top/bottom edges)
     * @param tiltYDeg plank rotationY in degrees (tips the left/right edges)
     */
    public void setGlowState(float level, float tiltXDeg, float tiltYDeg) {
        glowLevel = clamp01(level);
        // Direction the edge light pools toward. rotationY lifts a left/right edge (x component);
        // rotationX lifts a top/bottom edge (y component, screen y grows downward).
        float dx = tiltYDeg;
        float dy = -tiltXDeg;
        float mag = (float) Math.hypot(dx, dy);
        tiltAmount = clamp01(mag / MAX_TILT_DEG);
        if (mag > 0.05f) {
            hotAngleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        // Inset the rim so the (host-blurred) glow stays inside the rounded clip on every side and
        // corner — this continuity at the corners is the whole point of drawing it ourselves.
        float inset = density * 4.5f;
        rimRect.set(inset, inset, w - inset, h - inset);
        float r = Math.max(0f, cornerRadiusPx - inset);
        float touch = clamp01(glowLevel);
        float presence = 1f + (touch * 1.55f);

        drawRestingGlassPresence(canvas, w, h, r, density, presence);

        // Touch adds intensity to the persistent rim instead of replacing it.
        rimPaint.setShader(null);
        rimPaint.setStrokeWidth(density * (1.15f + (0.55f * touch)));
        rimPaint.setColor(withAlpha(accentColor, Math.round(18f + (58f * touch))));
        canvas.drawRoundRect(rimRect, r, r, rimPaint);

        // Tilt/touch-driven specular: a soft, broad highlight that pools on the edge the glass tips
        //    toward — leaning white like a real edge catch-light rather than a saturated accent
        //    band. A sweep gradient centred on the dock places the highlight at the tilt direction
        //    and rotating it as the plank tips makes the catch-light glide around the perimeter.
        if (touch > 0.02f || tiltAmount > 0.02f) {
            float cx = w * 0.5f;
            float cy = h * 0.5f;
            int specular = lerpColor(accentColor, Color.WHITE, 0.6f);
            int hot = withAlpha(specular, Math.round(132f * touch * (0.45f + 0.55f * Math.max(tiltAmount, 0.35f))));
            int faint = withAlpha(accentColor, Math.round(28f * touch * (0.45f + 0.55f * tiltAmount)));
            int dim = withAlpha(specular, 0);
            // Broad highlight centred at local angle 0 (positions 0 and 1 are the same angle), a
            // whisper of counter-glow on the opposite edge, transparent in between.
            sweepColors[0] = hot;
            sweepColors[1] = dim;
            sweepColors[2] = faint;
            sweepColors[3] = dim;
            sweepColors[4] = hot;
            SweepGradient sweep = new SweepGradient(cx, cy, sweepColors, sweepStops);
            sweepMatrix.setRotate(hotAngleDeg, cx, cy);
            sweep.setLocalMatrix(sweepMatrix);
            rimPaint.setShader(sweep);
            rimPaint.setStrokeWidth((density * 1.6f) + (density * 2.4f * Math.max(tiltAmount, touch * 0.45f)));
            rimPaint.setColor(Color.WHITE); // colour comes from the shader
            canvas.drawRoundRect(rimRect, r, r, rimPaint);
            rimPaint.setShader(null);
        }
    }

    private void drawRestingGlassPresence(Canvas canvas, int w, int h, float r, float density, float presence) {
        float cx = w * 0.5f;
        float cy = h * 0.5f;
        int brightEdge = lerpColor(accentColor, Color.WHITE, 0.78f);
        int darkEdge = lerpColor(accentColor, Color.BLACK, 0.64f);

        // Directional inner refraction: white catch-light on the upper/left edge and a darker
        // counter-edge on the lower/right side, all inside the clipped dock shape.
        SweepGradient refraction = new SweepGradient(
            cx,
            cy,
            new int[] {
                withAlpha(brightEdge, Math.round(54f * presence)),
                withAlpha(accentColor, Math.round(22f * presence)),
                withAlpha(darkEdge, Math.round(34f * presence)),
                withAlpha(accentColor, Math.round(16f * presence)),
                withAlpha(brightEdge, Math.round(54f * presence))
            },
            sweepStops
        );
        sweepMatrix.setRotate(-132f, cx, cy);
        refraction.setLocalMatrix(sweepMatrix);
        rimPaint.setShader(refraction);
        rimPaint.setStrokeWidth(density * 1.25f);
        canvas.drawRoundRect(rimRect, r, r, rimPaint);
        rimPaint.setShader(null);

        tmpRect.set(rimRect);
        tmpRect.inset(density * 2.2f, density * 2.2f);
        float innerR = Math.max(0f, r - (density * 2.2f));
        rimPaint.setStrokeWidth(density * 0.72f);
        rimPaint.setColor(withAlpha(Color.WHITE, Math.round(22f * presence)));
        canvas.drawRoundRect(tmpRect, innerR, innerR, rimPaint);

        // Faint top catch-light and opposite inner shadow. These are very low alpha so the dock
        // remains transparent glass rather than an opaque card.
        tmpRect.set(rimRect);
        tmpRect.inset(density * 3.4f, density * 3.4f);
        tmpRect.bottom = tmpRect.top + Math.max(density * 11f, tmpRect.height() * 0.28f);
        fillPaint.setShader(new LinearGradient(
            0f,
            tmpRect.top,
            0f,
            tmpRect.bottom,
            withAlpha(Color.WHITE, Math.round(20f * presence)),
            withAlpha(Color.WHITE, 0),
            Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(tmpRect, Math.max(0f, innerR - density), Math.max(0f, innerR - density), fillPaint);
        fillPaint.setShader(null);

        tmpRect.set(rimRect);
        tmpRect.inset(density * 3.8f, density * 3.8f);
        tmpRect.top = tmpRect.bottom - Math.max(density * 12f, tmpRect.height() * 0.30f);
        fillPaint.setShader(new LinearGradient(
            0f,
            tmpRect.top,
            0f,
            tmpRect.bottom,
            withAlpha(Color.BLACK, 0),
            withAlpha(Color.BLACK, Math.round(23f * presence)),
            Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(tmpRect, Math.max(0f, innerR - density), Math.max(0f, innerR - density), fillPaint);
        fillPaint.setShader(null);

        // Two quiet caustic lobes keep the resting glass alive on busy wallpaper without adding blur.
        SweepGradient caustics = new SweepGradient(
            cx,
            cy,
            new int[] {
                withAlpha(accentColor, Math.round(34f * presence)),
                withAlpha(accentColor, 0),
                withAlpha(brightEdge, Math.round(24f * presence)),
                withAlpha(accentColor, 0),
                withAlpha(accentColor, Math.round(34f * presence))
            },
            new float[] {0f, 0.20f, 0.52f, 0.78f, 1f}
        );
        sweepMatrix.setRotate(24f, cx, cy);
        caustics.setLocalMatrix(sweepMatrix);
        rimPaint.setShader(caustics);
        rimPaint.setStrokeWidth(density * 4.3f);
        canvas.drawRoundRect(rimRect, r, r, rimPaint);
        rimPaint.setShader(null);
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = Math.round(ar + (br - ar) * t);
        int rg = Math.round(ag + (bg - ag) * t);
        int rb = Math.round(ab + (bb - ab) * t);
        return (0xFF << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
