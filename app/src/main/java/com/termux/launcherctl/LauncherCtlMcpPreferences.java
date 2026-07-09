package com.termux.launcherctl;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LauncherCtlMcpPreferences {
    public static final String PREFS_NAME = "launcherctl_mcp";

    public static final String KEY_WEB_PROVIDER = "launcherctl_mcp_web_provider";
    public static final String KEY_BRAVE_API_KEY = "launcherctl_mcp_brave_api_key";
    public static final String KEY_SEARXNG_URL = "launcherctl_mcp_searxng_url";
    public static final String KEY_SEARXNG_API_KEY = "launcherctl_mcp_searxng_api_key";

    public static final String PROVIDER_OFF = "off";
    public static final String PROVIDER_BRAVE = "brave";
    public static final String PROVIDER_SEARXNG = "searxng";

    public static final String ENV_BRAVE_API_KEY = "LAUNCHERCTL_BRAVE_API_KEY";
    public static final String ENV_SEARXNG_URL = "LAUNCHERCTL_SEARXNG_URL";
    public static final String ENV_SEARXNG_API_KEY = "LAUNCHERCTL_SEARXNG_API_KEY";

    private LauncherCtlMcpPreferences() {
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    public static String getWebProvider(@NonNull Context context) {
        String provider = prefs(context).getString(KEY_WEB_PROVIDER, PROVIDER_OFF);
        if (PROVIDER_BRAVE.equals(provider) || PROVIDER_SEARXNG.equals(provider)) {
            return provider;
        }
        return PROVIDER_OFF;
    }

    @NonNull
    public static String getSecret(@NonNull Context context, @NonNull String key) {
        String value = prefs(context).getString(key, "");
        return value == null ? "" : value.trim();
    }

    public static void putString(@NonNull Context context, @NonNull String key, @Nullable String value) {
        prefs(context).edit().putString(key, value == null ? "" : value.trim()).apply();
    }

    @NonNull
    public static Map<String, String> buildMcpEnvironment(@NonNull Context context) {
        Map<String, String> env = new LinkedHashMap<>();
        putIfNotEmpty(env, ENV_BRAVE_API_KEY, getSecret(context, KEY_BRAVE_API_KEY));
        putIfNotEmpty(env, ENV_SEARXNG_URL, getSecret(context, KEY_SEARXNG_URL));
        putIfNotEmpty(env, ENV_SEARXNG_API_KEY, getSecret(context, KEY_SEARXNG_API_KEY));
        return env;
    }

    private static void putIfNotEmpty(@NonNull Map<String, String> env,
                                      @NonNull String key,
                                      @NonNull String value) {
        if (!value.isEmpty()) {
            env.put(key, value);
        }
    }

    public static boolean isConfigured(@NonNull Context context) {
        String provider = getWebProvider(context);
        if (PROVIDER_BRAVE.equals(provider)) {
            return !getSecret(context, KEY_BRAVE_API_KEY).isEmpty();
        }
        if (PROVIDER_SEARXNG.equals(provider)) {
            return !getSecret(context, KEY_SEARXNG_URL).isEmpty();
        }
        return false;
    }

    public static void writePresetConfig(@NonNull Context context) {
        try {
            File file = LauncherCtlStorage.getMcpConfigJsonFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
            JSONObject root = new JSONObject();
            JSONObject servers = new JSONObject();
            String provider = getWebProvider(context);
            if (PROVIDER_BRAVE.equals(provider) && !getSecret(context, KEY_BRAVE_API_KEY).isEmpty()) {
                servers.put("web", braveServer());
            } else if (PROVIDER_SEARXNG.equals(provider) && !getSecret(context, KEY_SEARXNG_URL).isEmpty()) {
                servers.put("web", searxngServer());
            }
            root.put("servers", servers);
            try (FileOutputStream stream = new FileOutputStream(file, false)) {
                stream.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            LauncherCtlMcpBridge.getInstance().setContext(context);
            LauncherCtlMcpBridge.getInstance().refresh();
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private static JSONObject braveServer() throws Exception {
        return new JSONObject()
            .put("transport", "stdio")
            .put("command", termuxCommand("npx"))
            .put("args", new JSONArray()
                .put("-y")
                .put("@modelcontextprotocol/server-brave-search"))
            .put("env", new JSONObject()
                .put("BRAVE_API_KEY", "$" + ENV_BRAVE_API_KEY))
            .put("tools", new JSONObject()
                .put("allow", new JSONArray()
                    .put("brave_web_search")
                    .put("web.brave_web_search"))
                .put("deny", new JSONArray()))
            .put("timeout_ms", 10000);
    }

    @NonNull
    private static JSONObject searxngServer() throws Exception {
        return new JSONObject()
            .put("transport", "stdio")
            .put("command", termuxCommand("npx"))
            .put("args", new JSONArray()
                .put("-y")
                .put("mcp-searxng"))
            .put("env", new JSONObject()
                .put("SEARXNG_URL", "$" + ENV_SEARXNG_URL)
                .put("SEARXNG_API_KEY", "$" + ENV_SEARXNG_API_KEY))
            .put("tools", new JSONObject()
                .put("allow", new JSONArray()
                    .put("search")
                    .put("web_search")
                    .put("searxng_web_search")
                    .put("web.search")
                    .put("web.web_search")
                    .put("web.searxng_web_search"))
                .put("deny", new JSONArray()))
            .put("timeout_ms", 10000);
    }

    @NonNull
    private static String termuxCommand(@NonNull String command) {
        return "/data/data/com.termux/files/usr/bin/" + command;
    }
}
