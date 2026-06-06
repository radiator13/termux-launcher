package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.SecureRandom;

public final class TaiSettings {
    public static final String PREFS_NAME = "termux_ai";

    public static final int DEFAULT_API_PORT = 41237;
    public static final int MIN_API_PORT = 1024;
    public static final int MAX_API_PORT = 65535;
    private static final int RANDOM_API_PORT_MIN = 41000;
    private static final int RANDOM_API_PORT_RANGE = 18000;

    public static final String KEY_ROLE_DEFAULT_ASSISTANT = "tai_role_default_assistant";
    public static final String KEY_SYSTEM_PROMPT_GENERAL = "tai_system_prompt_general";
    public static final String KEY_MAX_TOKENS = "tai_max_tokens";
    public static final String KEY_TOP_K = "tai_top_k";
    public static final String KEY_TOP_P = "tai_top_p";
    public static final String KEY_TEMPERATURE = "tai_temperature";
    public static final String KEY_ACCELERATOR = "tai_accelerator";
    public static final String KEY_THINKING = "tai_thinking";
    public static final String KEY_SPECULATIVE_DECODING = "tai_speculative_decoding";
    public static final String KEY_IDLE_UNLOAD_MINUTES = "tai_idle_unload_minutes";
    public static final String KEY_HUGGINGFACE_TOKEN = "tai_huggingface_token";
    public static final String KEY_API_PORT = "tai_api_port";
    public static final String KEY_API_TOKEN = "tai_api_token";

    private static final String AUTO = "auto";

    private final SharedPreferences preferences;

    public TaiSettings(@NonNull Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public String getDefaultAssistantModel() {
        return preferences.getString(KEY_ROLE_DEFAULT_ASSISTANT, TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
    }

    @NonNull
    public TaiRuntimeOptions getRuntimeOptions() {
        return new TaiRuntimeOptions(
            getNullableInteger(KEY_MAX_TOKENS),
            getNullableInteger(KEY_TOP_K),
            getNullableDouble(KEY_TOP_P),
            getNullableDouble(KEY_TEMPERATURE),
            getAutoNullableString(KEY_ACCELERATOR),
            getNullableBoolean(KEY_THINKING),
            getNullableBoolean(KEY_SPECULATIVE_DECODING),
            getIdleUnloadMinutes()
        );
    }

    public int getIdleUnloadMinutes() {
        String value = preferences.getString(KEY_IDLE_UNLOAD_MINUTES, "10");
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    @NonNull
    public String getHuggingFaceToken() {
        return preferences.getString(KEY_HUGGINGFACE_TOKEN, "");
    }

    public int getApiPort() {
        String value = preferences.getString(KEY_API_PORT, String.valueOf(DEFAULT_API_PORT));
        return normalizeApiPort(value);
    }

    public void setApiPort(int port) {
        preferences.edit().putString(KEY_API_PORT, String.valueOf(normalizeApiPort(port))).apply();
    }

    public int randomizeApiPort(@NonNull SecureRandom random) {
        int port = RANDOM_API_PORT_MIN + random.nextInt(RANDOM_API_PORT_RANGE);
        setApiPort(port);
        return port;
    }

    @NonNull
    public String getOrCreateApiToken() {
        String token = preferences.getString(KEY_API_TOKEN, "");
        if (isValidApiToken(token)) return token.trim();
        token = generateApiToken(new SecureRandom());
        setApiToken(token);
        return token;
    }

    public void setApiToken(@NonNull String token) {
        String normalized = normalizeApiToken(token);
        if (normalized.isEmpty()) normalized = generateApiToken(new SecureRandom());
        preferences.edit().putString(KEY_API_TOKEN, normalized).apply();
    }

    @NonNull
    public String rotateApiToken(@NonNull SecureRandom random) {
        String token = generateApiToken(random);
        setApiToken(token);
        return token;
    }

    @NonNull
    public static String generateApiToken(@NonNull SecureRandom random) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        StringBuilder tokenBuilder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            tokenBuilder.append(String.format("%02x", b & 0xff));
        }
        return tokenBuilder.toString();
    }

    @NonNull
    public static String normalizeApiToken(@Nullable String token) {
        if (token == null) return "";
        String value = token.trim();
        if (value.length() < 16) return "";
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return "";
        }
        return value;
    }

    public static boolean isValidApiToken(@Nullable String token) {
        return !normalizeApiToken(token).isEmpty();
    }

    public static int normalizeApiPort(@Nullable String port) {
        if (port == null) return DEFAULT_API_PORT;
        try {
            return normalizeApiPort(Integer.parseInt(port.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_API_PORT;
        }
    }

    public static boolean isValidApiPort(@Nullable String port) {
        if (port == null) return false;
        try {
            int value = Integer.parseInt(port.trim());
            return value >= MIN_API_PORT && value <= MAX_API_PORT;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static int normalizeApiPort(int port) {
        if (port < MIN_API_PORT || port > MAX_API_PORT) return DEFAULT_API_PORT;
        return port;
    }

    @NonNull
    public String getGeneralSystemPrompt() {
        return preferences.getString(KEY_SYSTEM_PROMPT_GENERAL,
            "You are TAI, Termux AI, a local assistant integrated with Termux Launcher. Prefer safe, reviewable actions.");
    }

    @NonNull
    public JSONObject getRoleAssignmentsJson() throws JSONException {
        JSONObject roles = new JSONObject();
        roles.put(TaiModelRegistry.ROLE_DEFAULT_ASSISTANT, getDefaultAssistantModel());
        return roles;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("roles", getRoleAssignmentsJson());
        json.put("runtimeOptions", getRuntimeOptions().toJson());
        json.put("idleUnloadMinutes", getIdleUnloadMinutes());
        json.put("huggingFaceTokenConfigured", !getHuggingFaceToken().trim().isEmpty());
        json.put("apiPort", getApiPort());
        json.put("apiTokenConfigured", isValidApiToken(preferences.getString(KEY_API_TOKEN, "")));
        json.put("openAiBaseUrl", "http://127.0.0.1:" + getApiPort() + "/v1");
        json.put("autoGenerationDefaultState", "nullable generation overrides use Google AI Edge Gallery defaults in the LiteRT runtime");
        return json;
    }

    @Nullable
    private Integer getNullableInteger(String key) {
        String value = preferences.getString(key, AUTO);
        if (value == null || value.trim().isEmpty() || AUTO.equals(value)) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private Double getNullableDouble(String key) {
        String value = preferences.getString(key, AUTO);
        if (value == null || value.trim().isEmpty() || AUTO.equals(value)) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private String getAutoNullableString(String key) {
        String value = preferences.getString(key, AUTO);
        if (value == null || value.trim().isEmpty() || AUTO.equals(value)) return null;
        return value;
    }

    @Nullable
    private Boolean getNullableBoolean(String key) {
        String value = preferences.getString(key, AUTO);
        if (value == null || value.trim().isEmpty() || AUTO.equals(value)) return null;
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        return null;
    }
}
