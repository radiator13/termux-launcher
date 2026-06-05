package com.termux.ai;

import androidx.annotation.NonNull;

public interface TaiGenerationCallback {
    void onToken(@NonNull String text);
    void onComplete(@NonNull String fullText);
    void onError(@NonNull Throwable throwable);
}
