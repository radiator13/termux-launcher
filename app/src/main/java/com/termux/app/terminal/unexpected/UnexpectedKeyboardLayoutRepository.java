package com.termux.app.terminal.unexpected;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.app.terminal.unexpected.vendor.KeyboardData;
import com.termux.shared.logger.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class UnexpectedKeyboardLayoutRepository {

    static final String DEFAULT_LAYOUT_ASSET = "unexpected_keyboard/default_terminal_layout.xml";
    static final String FALLBACK_LAYOUT_ASSET = "unexpected_keyboard/fallback_qwerty.xml";
    static final String NUMERIC_LAYOUT_ASSET = "unexpected_keyboard/numeric.xml";
    static final String GREEK_LAYOUT_ASSET = "unexpected_keyboard/greekmath.xml";

    private static final String LOG_TAG = "UnexpectedKeyboardRepo";

    private final Context mContext;

    UnexpectedKeyboardLayoutRepository(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    KeyboardData loadDefaultTextLayout() {
        return loadLayoutWithFallback(DEFAULT_LAYOUT_ASSET, FALLBACK_LAYOUT_ASSET);
    }

    KeyboardData loadFallbackTextLayout() {
        return loadLayoutRequired(FALLBACK_LAYOUT_ASSET);
    }

    KeyboardData loadNumericLayout() {
        return loadLayoutRequired(NUMERIC_LAYOUT_ASSET);
    }

    KeyboardData loadGreekMathLayout() {
        return loadLayoutRequired(GREEK_LAYOUT_ASSET);
    }

    KeyboardData loadLayoutWithFallback(@NonNull String primaryAssetPath, @NonNull String fallbackAssetPath) {
        KeyboardData keyboardData = loadLayout(primaryAssetPath);
        if (keyboardData != null) {
            return keyboardData;
        }
        return loadLayoutRequired(fallbackAssetPath);
    }

    KeyboardData loadLayoutRequired(@NonNull String assetPath) {
        KeyboardData keyboardData = loadLayout(assetPath);
        if (keyboardData == null) {
            throw new IllegalStateException("Failed to load keyboard layout asset " + assetPath);
        }
        return keyboardData;
    }

    KeyboardData loadLayout(@NonNull String assetPath) {
        try {
            String xml = readAssetText(assetPath);
            return KeyboardData.load_string_exn(xml);
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load keyboard layout asset " + assetPath, e);
            return null;
        }
    }

    String readAssetText(@NonNull String assetPath) throws IOException {
        try (InputStream inputStream = mContext.getAssets().open(assetPath);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }
}
