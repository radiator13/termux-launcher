package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LauncherCtlMcpConfig {
    public final Map<String, Server> servers;

    private LauncherCtlMcpConfig(@NonNull Map<String, Server> servers) {
        this.servers = servers;
    }

    @NonNull
    public static LauncherCtlMcpConfig loadDefault() {
        return load(LauncherCtlStorage.getMcpConfigJsonFile());
    }

    @NonNull
    public static LauncherCtlMcpConfig load(@NonNull File file) {
        if (!file.exists() || !file.isFile()) {
            return new LauncherCtlMcpConfig(new LinkedHashMap<>());
        }
        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return parse(new JSONObject(raw));
        } catch (Exception ignored) {
            return new LauncherCtlMcpConfig(new LinkedHashMap<>());
        }
    }

    @NonNull
    static LauncherCtlMcpConfig parse(@NonNull JSONObject root) {
        Map<String, Server> servers = new LinkedHashMap<>();
        JSONObject serverJson = root.optJSONObject("servers");
        if (serverJson == null) {
            return new LauncherCtlMcpConfig(servers);
        }
        JSONArray names = serverJson.names();
        if (names == null) {
            return new LauncherCtlMcpConfig(servers);
        }
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, "").trim();
            JSONObject json = serverJson.optJSONObject(name);
            if (name.isEmpty() || json == null) continue;
            Server server = parseServer(name, json);
            if (server != null) {
                servers.put(name, server);
            }
        }
        return new LauncherCtlMcpConfig(servers);
    }

    @Nullable
    private static Server parseServer(@NonNull String name, @NonNull JSONObject json) {
        String transport = json.optString("transport", "stdio").trim();
        String command = json.optString("command", "").trim();
        if (!"stdio".equals(transport) || command.isEmpty()) {
            return null;
        }
        List<String> args = new ArrayList<>();
        JSONArray argArray = json.optJSONArray("args");
        if (argArray != null) {
            for (int i = 0; i < argArray.length(); i++) {
                args.add(expandEnv(argArray.optString(i, "")));
            }
        }
        Map<String, String> env = new LinkedHashMap<>();
        JSONObject envJson = json.optJSONObject("env");
        if (envJson != null) {
            JSONArray envNames = envJson.names();
            if (envNames != null) {
                for (int i = 0; i < envNames.length(); i++) {
                    String key = envNames.optString(i, "").trim();
                    if (!key.isEmpty()) {
                        env.put(key, envJson.optString(key, ""));
                    }
                }
            }
        }
        JSONObject tools = json.optJSONObject("tools");
        Set<String> allow = stringSet(tools != null ? tools.optJSONArray("allow") : null);
        Set<String> deny = stringSet(tools != null ? tools.optJSONArray("deny") : null);
        int timeoutMs = json.optInt("timeout_ms", 10_000);
        if (timeoutMs < 1_000) timeoutMs = 1_000;
        if (timeoutMs > 30_000) timeoutMs = 30_000;
        return new Server(name, command, args, env, allow, deny, timeoutMs);
    }

    @NonNull
    private static Set<String> stringSet(@Nullable JSONArray array) {
        Set<String> out = new LinkedHashSet<>();
        if (array == null) return out;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) out.add(value);
        }
        return out;
    }

    @NonNull
    static String expandEnv(@Nullable String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.startsWith("$") && value.indexOf('/', 1) < 0) {
            String env = System.getenv(value.substring(1));
            return env == null ? "" : env;
        }
        return value;
    }

    public static final class Server {
        public final String name;
        public final String command;
        public final List<String> args;
        public final Map<String, String> env;
        public final Set<String> allow;
        public final Set<String> deny;
        public final int timeoutMs;

        Server(@NonNull String name, @NonNull String command, @NonNull List<String> args,
               @NonNull Map<String, String> env, @NonNull Set<String> allow,
               @NonNull Set<String> deny, int timeoutMs) {
            this.name = name;
            this.command = expandEnv(command);
            this.args = args;
            this.env = env;
            this.allow = allow;
            this.deny = deny;
            this.timeoutMs = timeoutMs;
        }

        public boolean allows(@NonNull String toolName) {
            if (deny.contains(toolName)) return false;
            return allow.isEmpty() || allow.contains(toolName);
        }
    }
}
