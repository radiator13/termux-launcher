package com.termux.app.nativebridge;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * JNI bridge to the {@code launcher_perf} Rust crate (focus-outline mask hot path).
 */
public final class LauncherPerfNative {

    private static final String LIBRARY_NAME = "launcher_perf";
    private static final boolean AVAILABLE;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary(LIBRARY_NAME);
            loaded = true;
        } catch (UnsatisfiedLinkError ignored) {
            loaded = false;
        }
        AVAILABLE = loaded;
    }

    private LauncherPerfNative() {}

    /** Whether {@link #LIBRARY_NAME} loaded successfully. */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static String libraryName() {
        return LIBRARY_NAME;
    }

    /**
     * Native entry: returns {@code [outWidth, outHeight, pixel...]} packed ARGB, or {@code null}
     * if the native library is unavailable / throws.
     */
    @Nullable
    private static native int[] buildFocusOutlineMask(
        @NonNull int[] argb,
        int width,
        int height,
        int gap,
        int stroke
    );

    /**
     * Build a focus-outline mask bitmap via Rust. Returns {@code null} if native code is not
     * loaded or the call fails.
     */
    @Nullable
    public static Bitmap buildFocusOutlineMaskBitmap(
        @NonNull int[] argb,
        int width,
        int height,
        int gap,
        int stroke
    ) {
        if (!AVAILABLE) {
            return null;
        }
        if (width <= 0 || height <= 0 || argb.length < width * height) {
            return null;
        }
        try {
            int[] packed = buildFocusOutlineMask(argb, width, height, gap, stroke);
            if (packed == null || packed.length < 2) {
                return null;
            }
            int outW = packed[0];
            int outH = packed[1];
            if (outW <= 0 || outH <= 0 || packed.length < 2 + outW * outH) {
                return null;
            }
            int[] pixels = new int[outW * outH];
            System.arraycopy(packed, 2, pixels, 0, pixels.length);
            Bitmap bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, outW, 0, 0, outW, outH);
            return bitmap;
        } catch (Throwable t) {
            return null;
        }
    }
}
