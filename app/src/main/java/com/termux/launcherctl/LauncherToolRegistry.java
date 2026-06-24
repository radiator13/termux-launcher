package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared launcher tool registry for agent, MCP, and CLI surfaces.
 *
 * <p>Tools are metadata-only in this slice: name, description, JSON schema,
 * risk level, confirmation requirement, and executor classification. Route and
 * execute logic is added in a later slice.
 */
public final class LauncherToolRegistry {
    private static final long JSON_SAFE_INTEGER_MAX = 9_007_199_254_740_991L;

    /** Risk classification used for confirmation gating and UI hints. */
    public enum ToolRisk {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high"),
        CRITICAL("critical");

        public final String label;

        ToolRisk(String label) {
            this.label = label;
        }
    }

    /** Executor classification for future route/execute wiring. */
    public enum ToolExecutor {
        LAUNCHER("launcher"),
        NOTIFICATIONS("notifications"),
        MEDIA("media"),
        SYSTEM("system"),
        INTENT("intent"),
        MEMORY("memory"),
        EVENTS("events"),
        USER("user");

        public final String label;

        ToolExecutor(String label) {
            this.label = label;
        }
    }

    public static final class ToolMetadata {
        public final String name;
        public final String description;
        public final JSONObject schema;
        public final ToolRisk risk;
        public final boolean requiresConfirmation;
        public final ToolExecutor executor;

        public ToolMetadata(
            @NonNull String name,
            @NonNull String description,
            @NonNull JSONObject schema,
            @NonNull ToolRisk risk,
            boolean requiresConfirmation,
            @NonNull ToolExecutor executor
        ) {
            this.name = name;
            this.description = description;
            this.schema = schema;
            this.risk = risk;
            this.requiresConfirmation = requiresConfirmation;
            this.executor = executor;
        }

        @NonNull
        public JSONObject toInternalJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("name", name);
            json.put("openAiName", openAiName());
            json.put("description", description);
            json.put("schema", schema);
            json.put("risk", risk.label);
            json.put("requiresConfirmation", requiresConfirmation);
            json.put("executor", executor.label);
            return json;
        }

        @NonNull
        public JSONObject toOpenAiTool() throws JSONException {
            JSONObject function = new JSONObject();
            function.put("name", openAiName());
            function.put("description", description);
            function.put("parameters", schema);
            JSONObject tool = new JSONObject();
            tool.put("type", "function");
            tool.put("function", function);
            tool.put("x_launcher_tool_name", name);
            return tool;
        }

        @NonNull
        public String openAiName() {
            return name.replace('.', '_');
        }
    }

    public static final String TOOL_CAPABILITIES_GET = "capabilities.get";
    public static final String TOOL_APPS_SEARCH = "apps.search";
    public static final String TOOL_APPS_LAUNCH = "apps.launch";
    public static final String TOOL_NOTIFICATIONS_RECENT = "notifications.recent";
    public static final String TOOL_NOTIFICATIONS_SINCE = "notifications.since";
    public static final String TOOL_NOTIFICATIONS_SEARCH = "notifications.search";
    public static final String TOOL_NOTIFICATIONS_STATS = "notifications.stats";
    public static final String TOOL_MEDIA_NOW_PLAYING = "media.now_playing";
    public static final String TOOL_SYSTEM_RESOURCES = "system.resources";
    public static final String TOOL_INTENT_OPEN = "intent.open";
    public static final String TOOL_MEMORY_WRITE = "memory.write";
    public static final String TOOL_MEMORY_SEARCH = "memory.search";
    public static final String TOOL_EVENTS_TAIL = "events.tail";
    public static final String TOOL_USER_CONFIRM = "user.confirm";

    private static LauncherToolRegistry instance;

    private final Map<String, ToolMetadata> tools;

    private LauncherToolRegistry() {
        Map<String, ToolMetadata> map = new LinkedHashMap<>();
        add(map, TOOL_CAPABILITIES_GET,
            "Get launcher capabilities, device support, and availability warnings.",
            schemaEmpty(),
            ToolRisk.LOW,
            false,
            ToolExecutor.LAUNCHER);
        add(map, TOOL_APPS_SEARCH,
            "Search installed launcher apps by label or package name.",
            schemaObject()
                .withString("query", "App name or package name fragment", true)
                .withInteger("limit", 1, 200, 50, false)
                .build(),
            ToolRisk.LOW,
            false,
            ToolExecutor.LAUNCHER);
        add(map, TOOL_APPS_LAUNCH,
            "Launch an installed app by label, package name, or stable id.",
            schemaObject()
                .withString("query", "App name, package name, or stableId to launch", true)
                .build(),
            ToolRisk.MEDIUM,
            true,
            ToolExecutor.LAUNCHER);
        add(map, TOOL_NOTIFICATIONS_RECENT,
            "Return the most recent notification history events.",
            schemaObject()
                .withInteger("limit", 1, 200, 50, false)
                .build(),
            ToolRisk.LOW,
            false,
            ToolExecutor.NOTIFICATIONS);
        add(map, TOOL_NOTIFICATIONS_SINCE,
            "Return notification history events posted since a timestamp.",
            schemaObject()
                .withLong("since", "Epoch milliseconds", 0L, JSON_SAFE_INTEGER_MAX, 0L, true)
                .withInteger("limit", 1, 1000, 200, false)
                .build(),
            ToolRisk.LOW,
            false,
            ToolExecutor.NOTIFICATIONS);
        add(map, TOOL_NOTIFICATIONS_SEARCH,
            "Search notification history events by text content.",
            schemaObject()
                .withString("query", "Text to search for", true)
                .withInteger("limit", 1, 200, 50, false)
                .build(),
            ToolRisk.LOW,
            false,
            ToolExecutor.NOTIFICATIONS);
        add(map, TOOL_NOTIFICATIONS_STATS,
            "Return statistics about notification history events.",
            schemaObject()
                .withLong("since", "Optional epoch milliseconds", 0L, JSON_SAFE_INTEGER_MAX, 0L, false)
                .build(),
            ToolRisk.LOW,
            false,
            ToolExecutor.NOTIFICATIONS);
        add(map, TOOL_MEDIA_NOW_PLAYING,
            "Return the current media session / now playing information.",
            schemaEmpty(),
            ToolRisk.LOW,
            false,
            ToolExecutor.MEDIA);
        add(map, TOOL_SYSTEM_RESOURCES,
            "Return device resource information such as memory, CPU, storage, battery, and network.",
            schemaEmpty(),
            ToolRisk.LOW,
            false,
            ToolExecutor.SYSTEM);
        add(map, TOOL_INTENT_OPEN,
            "Open a URI or action using an Android intent.",
            schemaObject()
                .withString("action", "Android intent action", false, "android.intent.action.VIEW")
                .withString("data", "URI to open", true)
                .withString("package", "Target package name", false)
                .withString("component", "Target component name", false)
                .withObject("extras", "Optional intent extras", false)
                .build(),
            ToolRisk.MEDIUM,
            true,
            ToolExecutor.INTENT);
        add(map, TOOL_MEMORY_WRITE,
            "Write a short key/value note to agent memory.",
            schemaObject()
                .withString("key", "Memory key", true)
                .withString("value", "Memory value", true)
                .withString("namespace", "Memory namespace", false, "agent")
                .build(),
            ToolRisk.HIGH,
            true,
            ToolExecutor.MEMORY);
        add(map, TOOL_MEMORY_SEARCH,
            "Search agent memory entries by key or value.",
            schemaObject()
                .withString("query", "Text to search for", true)
                .withString("namespace", "Memory namespace", false, "agent")
                .withInteger("limit", 1, 100, 10, false)
                .build(),
            ToolRisk.MEDIUM,
            false,
            ToolExecutor.MEMORY);
        add(map, TOOL_EVENTS_TAIL,
            "Return recent agent/system events.",
            schemaObject()
                .withInteger("limit", 1, 1000, 100, false)
                .withLong("since", "Optional epoch milliseconds", 0L, JSON_SAFE_INTEGER_MAX, 0L, false)
                .build(),
            ToolRisk.LOW,
            false,
            ToolExecutor.EVENTS);
        add(map, TOOL_USER_CONFIRM,
            "Ask the user for explicit confirmation before a sensitive action.",
            schemaObject()
                .withString("message", "Message to show the user", true)
                .withEnum("risk", new String[]{"low", "medium", "high", "critical"}, false, "medium")
                .build(),
            ToolRisk.CRITICAL,
            true,
            ToolExecutor.USER);
        tools = Collections.unmodifiableMap(map);
    }

    @NonNull
    public static synchronized LauncherToolRegistry getInstance() {
        if (instance == null) {
            instance = new LauncherToolRegistry();
        }
        return instance;
    }

    /** Resets the singleton for unit tests. */
    static synchronized void resetForTesting() {
        instance = null;
    }

    @NonNull
    public List<ToolMetadata> getTools() {
        return new ArrayList<>(tools.values());
    }

    @Nullable
    public ToolMetadata getTool(@Nullable String name) {
        return name == null ? null : tools.get(name);
    }

    @Nullable
    public ToolMetadata getToolByOpenAiName(@Nullable String openAiName) {
        if (openAiName == null) return null;
        String internalName = openAiNameToInternalName(openAiName);
        return tools.get(internalName);
    }

    @NonNull
    public static String openAiNameToInternalName(@NonNull String openAiName) {
        int firstSeparator = openAiName.indexOf('_');
        if (firstSeparator < 0) {
            return openAiName;
        }
        return openAiName.substring(0, firstSeparator) + "." + openAiName.substring(firstSeparator + 1);
    }

    /**
     * Shared execution callback used by agent route/execute, MCP, and CLI surfaces.
     * Implementations receive the tool metadata and parsed arguments and return a
     * structured result. The registry itself is platform-agnostic; execution logic
     * is supplied by the caller.
     */
    public interface ToolExecutionHandler {
        @NonNull
        ToolExecutionResult execute(@NonNull ToolMetadata tool, @NonNull JSONObject arguments) throws Exception;
    }

    /** Result of executing a tool through a {@link ToolExecutionHandler}. */
    public static final class ToolExecutionResult {
        public final boolean ok;
        public final JSONObject result;
        public final String errorCode;
        public final String message;
        public final int statusCode;

        public ToolExecutionResult(boolean ok, @Nullable JSONObject result,
                                   @Nullable String errorCode, @Nullable String message, int statusCode) {
            this.ok = ok;
            this.result = result != null ? result : new JSONObject();
            this.errorCode = errorCode;
            this.message = message;
            this.statusCode = statusCode;
        }

        @NonNull
        public static ToolExecutionResult success(@NonNull JSONObject result) {
            return new ToolExecutionResult(true, result, null, null, 200);
        }

        @NonNull
        public static ToolExecutionResult error(int statusCode, @NonNull String errorCode, @NonNull String message) {
            return new ToolExecutionResult(false, null, errorCode, message, statusCode);
        }

        @NonNull
        public JSONObject toJson() throws JSONException {
            JSONObject data = new JSONObject();
            data.put("ok", ok);
            data.put("result", result);
            if (errorCode != null) data.put("error", errorCode);
            if (message != null) data.put("message", message);
            data.put("_statusCode", statusCode);
            return data;
        }
    }

    @NonNull
    public JSONArray toInternalJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (ToolMetadata tool : tools.values()) {
            array.put(tool.toInternalJson());
        }
        return array;
    }

    @NonNull
    public JSONArray toOpenAiToolsJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (ToolMetadata tool : tools.values()) {
            array.put(tool.toOpenAiTool());
        }
        return array;
    }

    @NonNull
    public JSONObject toResponseJson() throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("count", tools.size());
        data.put("tools", toInternalJson());
        data.put("openAiTools", toOpenAiToolsJson());
        return data;
    }

    /**
     * Writes internal schemas and OpenAI-compatible tools to {@code ~/.launcherctl/tools.json}
     * for debugging and CLI/MCP consumers.
     */
    public void writeDebugToolsJson() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("generatedAtMs", System.currentTimeMillis());
            payload.put("count", tools.size());
            payload.put("tools", toInternalJson());
            payload.put("openAiTools", toOpenAiToolsJson());
            writeTextFile(LauncherCtlStorage.getToolsJsonFile(), payload.toString(2));
        } catch (Exception e) {
            android.util.Log.w("LauncherToolRegistry", "Failed to write tools.json: " + e.getMessage());
        }
    }

    private static void add(
        @NonNull Map<String, ToolMetadata> map,
        @NonNull String name,
        @NonNull String description,
        @NonNull JSONObject schema,
        @NonNull ToolRisk risk,
        boolean requiresConfirmation,
        @NonNull ToolExecutor executor
    ) {
        map.put(name, new ToolMetadata(name, description, schema, risk, requiresConfirmation, executor));
    }

    private static JSONObject schemaEmpty() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", new JSONObject());
            schema.put("additionalProperties", false);
        } catch (JSONException ignored) {
        }
        return schema;
    }

    private static SchemaBuilder schemaObject() {
        return new SchemaBuilder();
    }

    private static final class SchemaBuilder {
        private final JSONObject properties = new JSONObject();
        private final JSONArray required = new JSONArray();

        SchemaBuilder withString(@NonNull String name, @NonNull String description, boolean required) {
            return withString(name, description, required, null);
        }

        SchemaBuilder withString(@NonNull String name, @NonNull String description, boolean required, @Nullable String defaultValue) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "string");
                prop.put("description", description);
                if (defaultValue != null) {
                    prop.put("default", defaultValue);
                }
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        SchemaBuilder withInteger(@NonNull String name, @NonNull String description, boolean required) {
            return withInteger(name, description, 0, Integer.MAX_VALUE, 0, required);
        }

        SchemaBuilder withInteger(@NonNull String name, int minimum, int maximum, int defaultValue, boolean required) {
            return withInteger(name, "", minimum, maximum, defaultValue, required);
        }

        SchemaBuilder withInteger(@NonNull String name, @NonNull String description, int minimum, int maximum, int defaultValue, boolean required) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "integer");
                prop.put("minimum", minimum);
                prop.put("maximum", maximum);
                prop.put("default", defaultValue);
                if (!description.isEmpty()) {
                    prop.put("description", description);
                }
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        SchemaBuilder withLong(@NonNull String name, @NonNull String description, long minimum, long maximum, long defaultValue, boolean required) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "integer");
                prop.put("minimum", minimum);
                prop.put("maximum", maximum);
                prop.put("default", defaultValue);
                if (!description.isEmpty()) {
                    prop.put("description", description);
                }
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        SchemaBuilder withObject(@NonNull String name, @NonNull String description, boolean required) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "object");
                prop.put("description", description);
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        SchemaBuilder withEnum(@NonNull String name, @NonNull String[] values, boolean required, @NonNull String defaultValue) {
            try {
                JSONObject prop = new JSONObject();
                prop.put("type", "string");
                JSONArray enumArray = new JSONArray();
                for (String value : values) {
                    enumArray.put(value);
                }
                prop.put("enum", enumArray);
                prop.put("default", defaultValue);
                properties.put(name, prop);
                if (required) {
                    this.required.put(name);
                }
            } catch (JSONException ignored) {
            }
            return this;
        }

        JSONObject build() {
            JSONObject schema = new JSONObject();
            try {
                schema.put("type", "object");
                schema.put("properties", properties);
                if (required.length() > 0) {
                    schema.put("required", required);
                }
                schema.put("additionalProperties", false);
            } catch (JSONException ignored) {
            }
            return schema;
        }
    }

    private static void writeTextFile(@NonNull File file, @NonNull String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create dir for " + file.getAbsolutePath());
        }
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
