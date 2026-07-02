package com.alibaba.mnnllm.android.llm;

import androidx.annotation.NonNull;

/**
 * VAJ Terminal / TAI addition — thin wrapper over the MNN Transformer::Embedding engine.
 *
 * <p>Chat models go through {@link LlmSession}; embedding models (MNN config.json packages that
 * declare {@code text_embeddings}) go through this session. The native symbols are provided by the
 * TAI embedding JNI ({@code ci/mnn-patch/embedding_jni.cpp}) compiled into {@code libmnnllmapp.so}.
 * Older {@code libmnnllmapp.so} builds without those symbols raise {@link UnsatisfiedLinkError} on
 * first use, which callers treat as "MNN embeddings unavailable in this build".
 */
public final class MnnEmbeddingSession {
    private long nativePtr;

    static {
        System.loadLibrary("MNN");
        System.loadLibrary("mnnllmapp");
    }

    public synchronized void load(@NonNull String configPath) {
        if (nativePtr != 0L) release();
        nativePtr = initNative(configPath);
        if (nativePtr == 0L) throw new IllegalStateException("MNN embedding model load failed.");
    }

    public synchronized boolean isLoaded() {
        return nativePtr != 0L;
    }

    /** Output embedding dimension of the loaded model, or 0 if unknown/unloaded. */
    public synchronized int dim() {
        return nativePtr == 0L ? 0 : dimNative(nativePtr);
    }

    @NonNull
    public synchronized float[] embed(@NonNull String text) {
        if (nativePtr == 0L) throw new IllegalStateException("MNN embedding model is not loaded.");
        float[] vector = embedNative(nativePtr, text);
        if (vector == null) throw new IllegalStateException("MNN embedding inference returned no vector.");
        return vector;
    }

    public synchronized void release() {
        if (nativePtr != 0L) {
            releaseNative(nativePtr);
            nativePtr = 0L;
        }
    }

    private native long initNative(String configPath);
    private native int dimNative(long ptr);
    private native float[] embedNative(long ptr, String text);
    private native void releaseNative(long ptr);
}
