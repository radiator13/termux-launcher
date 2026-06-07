package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONArray;

public interface TaiGenerationCallback {
    void onToken(@NonNull String text);
    default void onToolCalls(@NonNull JSONArray toolCalls) {
    }
    void onComplete(@NonNull String fullText);
    void onError(@NonNull Throwable throwable);
}
