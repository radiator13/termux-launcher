package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

final class LlamaCppBridge {
    interface TokenCallback {
        void onToken(@NonNull String text);
    }

    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded;
        try {
            System.loadLibrary("tai_llama");
            loaded = true;
        } catch (UnsatisfiedLinkError error) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private LlamaCppBridge() {}

    static boolean isAvailable() {
        return LIBRARY_LOADED && nativeAvailable();
    }

    static native boolean nativeAvailable();
    @NonNull static native String nativeLastError();
    static native long nativeLoad(@NonNull String path, int contextSize, int gpuLayers, int threads);
    static native void nativeUnload(long handle);
    static native void nativeCancel(long handle);
    @Nullable static native String nativeGenerate(long handle, @NonNull String prompt, boolean chat,
        int maxTokens, int topK, double topP, double temperature, @Nullable TokenCallback callback);
}
