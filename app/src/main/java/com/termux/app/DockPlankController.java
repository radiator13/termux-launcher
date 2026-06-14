package com.termux.app;

import android.view.Choreographer;
import android.view.View;

/**
 * Spring-physics "glass plank" reactive treatment for the launcher dock.
 *
 * <p>The whole dock stack behaves like a tactile glass slab: touching it tilts the plank in 3D
 * toward the finger, dips it slightly on press, and springs it back on release. A moving specular
 * highlight tracks the touch point and the accent rim glow swells on contact. All motion is driven
 * by critically-damped springs integrated on a {@link Choreographer} frame loop that sleeps when the
 * springs settle, so there is no idle cost. When the system animator duration scale is 0
 * (reduce-motion), the springs snap to their targets instead of animating.</p>
 *
 * <p>This mirrors the {@code dock-ui.jsx} prototype's plank physics (MAX_TILT 5°, the same spring
 * stiffness/damping constants, press dip and glow/specular coupling), recreated natively.</p>
 */
final class DockPlankController implements Choreographer.FrameCallback {

    private static final float MAX_TILT_DEG = 5f;
    private static final float DT = 1f / 60f;
    private static final float SETTLE_EPSILON = 4e-4f;

    private final View mPlank;       // the transformed slab (whole dock stack)
    private final View mSpecular;    // moving specular highlight
    private final View mGlow;        // accent rim glow

    private boolean mEnabled = true;
    private boolean mReducedMotion = false;
    private boolean mPressed = false;
    private boolean mFrameScheduled = false;

    // Spring channels: tilt about X/Y, press dip, rim glow, and the specular's horizontal position.
    private final Spring mRx = new Spring(0f, 170f, 17f);
    private final Spring mRy = new Spring(0f, 170f, 17f);
    private final Spring mPress = new Spring(0f, 320f, 22f);
    private final Spring mGlowLevel = new Spring(0f, 130f, 24f);
    private final Spring mLightX = new Spring(0.5f, 210f, 23f);

    DockPlankController(View plank, View specular, View glow) {
        mPlank = plank;
        mSpecular = specular;
        mGlow = glow;
        if (mPlank != null) {
            // Keep the perspective gentle so the small tilt reads as depth, not distortion.
            mPlank.setCameraDistance(mPlank.getResources().getDisplayMetrics().density * 2600f);
        }
    }

    void setReducedMotion(boolean reduced) {
        mReducedMotion = reduced;
    }

    void setEnabled(boolean enabled) {
        if (mEnabled == enabled) {
            return;
        }
        mEnabled = enabled;
        if (!enabled) {
            reset();
        }
    }

    /** Begin a touch on the plank. {@code nx}/{@code ny} are normalized [0,1] within the plank. */
    void onPointerDown(float nx, float ny) {
        if (!mEnabled) {
            return;
        }
        mPressed = true;
        aim(nx, ny);
        mPress.target = 1f;
        mGlowLevel.target = 1f;
        if (mGlow != null) {
            mGlow.setVisibility(View.VISIBLE);
        }
        if (mSpecular != null) {
            mSpecular.setVisibility(View.VISIBLE);
        }
        kick();
    }

    void onPointerMove(float nx, float ny) {
        if (!mEnabled || !mPressed) {
            return;
        }
        aim(nx, ny);
        kick();
    }

    void onPointerUp() {
        if (!mPressed) {
            return;
        }
        mPressed = false;
        mPress.target = 0f;
        mGlowLevel.target = 0f;
        mRx.target = 0f;
        mRy.target = 0f;
        kick();
    }

    /** Snap everything back to neutral and stop the frame loop. */
    void reset() {
        mPressed = false;
        mRx.reset(0f);
        mRy.reset(0f);
        mPress.reset(0f);
        mGlowLevel.reset(0f);
        mLightX.reset(0.5f);
        applyToViews();
        if (mGlow != null) {
            mGlow.setVisibility(View.GONE);
        }
        if (mSpecular != null) {
            mSpecular.setVisibility(View.GONE);
        }
    }

    private void aim(float nx, float ny) {
        nx = clamp01(nx);
        ny = clamp01(ny);
        mRy.target = (nx - 0.5f) * 2f * MAX_TILT_DEG;
        mRx.target = -(ny - 0.5f) * 2f * MAX_TILT_DEG;
        mLightX.target = nx;
    }

    private void kick() {
        if (!mFrameScheduled) {
            mFrameScheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        mFrameScheduled = false;
        boolean moving = false;
        moving |= mRx.tick(mReducedMotion);
        moving |= mRy.tick(mReducedMotion);
        moving |= mPress.tick(mReducedMotion);
        moving |= mGlowLevel.tick(mReducedMotion);
        moving |= mLightX.tick(mReducedMotion);
        applyToViews();
        if (moving) {
            kick();
        } else if (!mPressed && mGlowLevel.value < 0.002f) {
            // Fully settled and released: drop the transient layers so they cost nothing at rest.
            if (mGlow != null) {
                mGlow.setVisibility(View.GONE);
            }
            if (mSpecular != null) {
                mSpecular.setVisibility(View.GONE);
            }
        }
    }

    private void applyToViews() {
        if (mPlank != null && mPlank.getWidth() > 0 && mPlank.getHeight() > 0) {
            mPlank.setPivotX(mPlank.getWidth() * 0.5f);
            mPlank.setPivotY(mPlank.getHeight() * 0.5f);
            mPlank.setRotationX(mRx.value);
            mPlank.setRotationY(mRy.value);
            float scale = 1f - mPress.value * 0.013f;
            mPlank.setScaleX(scale);
            mPlank.setScaleY(scale);
        }
        if (mGlow != null) {
            mGlow.setAlpha(clamp01(mGlowLevel.value));
        }
        if (mSpecular != null) {
            float plankW = mPlank != null ? mPlank.getWidth() : 0f;
            mSpecular.setTranslationX((mLightX.value - 0.5f) * plankW);
            mSpecular.setAlpha(clamp01(0.07f + mGlowLevel.value * 0.22f + mPress.value * 0.12f));
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** A single critically-damped spring channel integrated with a fixed timestep. */
    private static final class Spring {
        float value;
        float target;
        float vel;
        final float stiffness;
        final float damping;

        Spring(float init, float stiffness, float damping) {
            this.value = init;
            this.target = init;
            this.stiffness = stiffness;
            this.damping = damping;
        }

        void reset(float v) {
            value = v;
            target = v;
            vel = 0f;
        }

        /** @return true if the spring is still in motion and needs another frame. */
        boolean tick(boolean reduced) {
            if (reduced) {
                value = target;
                vel = 0f;
                return false;
            }
            float accel = stiffness * (target - value) - damping * vel;
            vel += accel * DT;
            value += vel * DT;
            return Math.abs(target - value) > SETTLE_EPSILON || Math.abs(vel) > SETTLE_EPSILON;
        }
    }
}
