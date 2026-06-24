package com.termux.launcherctl;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiRuntimeState;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Handles agent route and execute logic for the LauncherCtl API.
 *
 * <p>Routing is deterministic and never executes. It maps obvious user intents to
 * registered tools using keyword matching. If FunctionGemma is already loaded in
 * the TAI runtime, route may optionally call it through the OpenAI-compatible
 * chat completions endpoint with compact OpenAI tool schemas.
 *
 * <p>Execution enforces confirmation gating for tools that require it or carry
 * medium/high/critical risk, then delegates to a {@link LauncherToolRegistry.ToolExecutionHandler}.
 */
public final class LauncherCtlAgentHandler {

    private final Context context;
    private final LauncherToolRegistry.ToolExecutionHandler executionHandler;

    public LauncherCtlAgentHandler(
        @NonNull Context context,
        @NonNull LauncherToolRegistry.ToolExecutionHandler executionHandler
    ) {
        this.context = context.getApplicationContext();
        this.executionHandler = executionHandler;
    }

    /**
     * Routes a user request to the most appropriate tool without executing it.
     *
     * <p>Accepts either a simple text request ({"request": "open maps"}) or an
     * OpenAI-style conversation ({"messages": [...]}).
     */
    @NonNull
    public JSONObject route(@Nullable String body) throws JSONException {
        JSONObject request = parseJsonBody(body);
        String textRequest = request.optString("request", "").trim();
        JSONArray messages = request.optJSONArray("messages");

        String userText;
        if (!textRequest.isEmpty()) {
            userText = textRequest;
        } else if (messages != null && messages.length() > 0) {
            userText = extractLastUserText(messages);
        } else {
            userText = "";
        }

        LauncherToolRegistry registry = LauncherToolRegistry.getInstance();

        // Prefer deterministic keyword routing first.
        RouteMatch match = deterministicRoute(userText.toLowerCase(Locale.US), registry);

        // Fall back to FunctionGemma only when it is already loaded. We never
        // auto-download or auto-load models here.
        if (match == null && isFunctionGemmaLoaded()) {
            match = functionGemmaRoute(userText, registry);
        }

        if (match == null) {
            match = new RouteMatch(
                registry.getTool(LauncherToolRegistry.TOOL_CAPABILITIES_GET),
                new JSONObject(),
                "No obvious intent matched; defaulting to capabilities.get."
            );
        }

        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("routed", true);
        result.put("tool", match.tool.name);
        result.put("openAiName", match.tool.openAiName());
        result.put("arguments", match.arguments);
        result.put("rationale", match.rationale);
        result.put("requiresConfirmation", match.tool.requiresConfirmation || isElevatedRisk(match.tool.risk));
        result.put("risk", match.tool.risk.label);
        result.put("executor", match.tool.executor.label);
        return result;
    }

    /**
     * Executes a routed tool call after enforcing confirmation gating.
     *
     * <p>Accepts payloads shaped like {"tool": "apps.launch", "arguments": {...}, "confirm": true}
     * or {"name": "apps.launch", "arguments": {...}, "confirm": true}.
     */
    @NonNull
    public JSONObject execute(@Nullable String body) throws JSONException {
        JSONObject request = parseJsonBody(body);
        String toolName = coalesce(request.optString("tool", ""), request.optString("name", "")).trim();
        if (toolName.isEmpty()) {
            return errorResponse(400, "bad_request", "Missing tool name");
        }

        LauncherToolRegistry registry = LauncherToolRegistry.getInstance();
        LauncherToolRegistry.ToolMetadata tool = registry.getTool(toolName);
        if (tool == null) {
            tool = registry.getToolByOpenAiName(toolName);
        }
        if (tool == null) {
            return errorResponse(404, "not_found", "Unknown tool: " + toolName);
        }

        JSONObject arguments = request.optJSONObject("arguments");
        if (arguments == null) {
            arguments = new JSONObject();
        }

        boolean confirmationRequired = tool.requiresConfirmation || isElevatedRisk(tool.risk);
        if (confirmationRequired && !request.optBoolean("confirm", false)) {
            JSONObject result = new JSONObject();
            result.put("ok", false);
            result.put("error", "confirmation_required");
            result.put("message", "Tool '" + tool.name + "' requires confirmation");
            result.put("tool", tool.name);
            result.put("risk", tool.risk.label);
            result.put("requiresConfirmation", true);
            result.put("_statusCode", 403);
            return result;
        }

        try {
            LauncherToolRegistry.ToolExecutionResult execResult = executionHandler.execute(tool, arguments);
            return execResult.toJson();
        } catch (Exception e) {
            return errorResponse(500, "execution_failed",
                e.getMessage() != null ? e.getMessage() : "Tool execution failed");
        }
    }

    @Nullable
    private RouteMatch deterministicRoute(@NonNull String text, @NonNull LauncherToolRegistry registry) {
        // App launch: launch/open/start <query>
        if (startsWithAny(text, "launch ", "open ", "start ")) {
            String query = text.substring(text.indexOf(' ') + 1).trim();
            if (!query.isEmpty()) {
                JSONObject args = new JSONObject();
                try {
                    args.put("query", query);
                } catch (JSONException ignored) {
                }
                return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_APPS_LAUNCH), args,
                    "User asked to open or launch an app.");
            }
        }

        // App search
        if (text.contains("search app") || text.contains("find app") || text.contains("look for app")) {
            String query = extractQueryAfter(text, "search app", "find app", "look for app");
            if (!query.isEmpty()) {
                JSONObject args = new JSONObject();
                try {
                    args.put("query", query);
                } catch (JSONException ignored) {
                }
                return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_APPS_SEARCH), args,
                    "User asked to search installed apps.");
            }
        }

        // Notifications
        if (text.contains("notification")) {
            if (text.contains("since")) {
                Long since = extractTimestamp(text);
                JSONObject args = new JSONObject();
                try {
                    args.put("since", since != null ? since.longValue() : (System.currentTimeMillis() - 3600_000L));
                } catch (JSONException ignored) {
                }
                return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_NOTIFICATIONS_SINCE), args,
                    "User asked for notifications since a time.");
            }
            if (text.contains("search") || text.contains("find")) {
                String query = extractTrailingQuery(text);
                if (!query.isEmpty()) {
                    JSONObject args = new JSONObject();
                    try {
                        args.put("query", query);
                    } catch (JSONException ignored) {
                    }
                    return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_NOTIFICATIONS_SEARCH), args,
                        "User asked to search notifications.");
                }
            }
            if (text.contains("stat") || text.contains("count")) {
                return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_NOTIFICATIONS_STATS), new JSONObject(),
                    "User asked for notification statistics.");
            }
            return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_NOTIFICATIONS_RECENT), new JSONObject(),
                "User asked for recent notifications.");
        }

        // Media
        if (text.contains("now playing") || text.contains("currently playing")
            || text.contains("what is playing") || text.contains("playing now")
            || text.contains("media") || text.contains("music")) {
            return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_MEDIA_NOW_PLAYING), new JSONObject(),
                "User asked for now playing / media information.");
        }

        // System resources
        if (text.contains("resources") || text.contains("cpu") || text.contains("memory")
            || text.contains("battery") || text.contains("storage") || text.contains("ram")) {
            return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_SYSTEM_RESOURCES), new JSONObject(),
                "User asked for system resources.");
        }

        // Capabilities / tools
        if (text.contains("capabilit") || text.contains("what can you do") || text.contains("tools")) {
            if (text.contains("tool")) {
                return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_CAPABILITIES_GET), new JSONObject(),
                    "User asked about available tools.");
            }
            return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_CAPABILITIES_GET), new JSONObject(),
                "User asked for launcher capabilities.");
        }

        // Memory
        if (text.contains("memory")) {
            if (text.contains("write") || text.contains("save") || text.contains("remember") || text.contains("store")) {
                MemoryParse parse = parseMemoryWrite(text);
                if (parse != null) {
                    return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_MEMORY_WRITE), parse.args,
                        "User asked to write to memory.");
                }
            }
            return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_MEMORY_SEARCH), new JSONObject(),
                "User asked to search memory.");
        }

        // Events / logs
        if (text.contains("event") || text.contains("log") || text.contains("tail")) {
            return new RouteMatch(registry.getTool(LauncherToolRegistry.TOOL_EVENTS_TAIL), new JSONObject(),
                "User asked for recent events.");
        }

        return null;
    }

    @Nullable
    private RouteMatch functionGemmaRoute(@NonNull String userText, @NonNull LauncherToolRegistry registry) {
        try {
            JSONObject request = new JSONObject();
            request.put("model", TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M);
            request.put("tool_choice", "auto");
            request.put("max_tokens", 128);

            JSONArray messages = new JSONArray();
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", "Pick one launcher tool for the user request. Return a tool call only.");
            messages.put(system);
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", userText);
            messages.put(user);
            request.put("messages", messages);
            request.put("tools", compactFunctionGemmaTools(registry));

            JSONObject response = TaiManager.getInstance(context).openAiChatCompletions(request.toString());
            JSONObject message = response.optJSONArray("choices") != null
                ? response.optJSONArray("choices").optJSONObject(0)
                : null;
            if (message == null) return null;
            message = message.optJSONObject("message");
            if (message == null) return null;

            JSONArray toolCalls = message.optJSONArray("tool_calls");
            if (toolCalls == null || toolCalls.length() == 0) return null;

            JSONObject call = toolCalls.optJSONObject(0);
            JSONObject function = call != null ? call.optJSONObject("function") : null;
            if (function == null) return null;

            String openAiName = function.optString("name", "");
            if (openAiName.isEmpty()) return null;

            LauncherToolRegistry.ToolMetadata tool = registry.getToolByOpenAiName(openAiName);
            if (tool == null) return null;

            JSONObject arguments;
            Object argsValue = function.opt("arguments");
            if (argsValue instanceof JSONObject) {
                arguments = (JSONObject) argsValue;
            } else {
                String argsText = argsValue == null ? "{}" : String.valueOf(argsValue);
                arguments = argsText.trim().isEmpty() ? new JSONObject() : new JSONObject(argsText);
            }

            return new RouteMatch(tool, arguments,
                "FunctionGemma selected tool '" + tool.name + "'.");
        } catch (Exception e) {
            // FunctionGemma routing is best-effort; fall back to deterministic behavior.
            return null;
        }
    }

    @NonNull
    private JSONArray compactFunctionGemmaTools(@NonNull LauncherToolRegistry registry) throws JSONException {
        JSONArray tools = new JSONArray();
        String[] names = {
            LauncherToolRegistry.TOOL_APPS_LAUNCH,
            LauncherToolRegistry.TOOL_APPS_SEARCH,
            LauncherToolRegistry.TOOL_INTENT_OPEN,
            LauncherToolRegistry.TOOL_NOTIFICATIONS_RECENT,
            LauncherToolRegistry.TOOL_NOTIFICATIONS_SEARCH,
            LauncherToolRegistry.TOOL_NOTIFICATIONS_STATS,
            LauncherToolRegistry.TOOL_MEDIA_NOW_PLAYING,
            LauncherToolRegistry.TOOL_SYSTEM_RESOURCES,
            LauncherToolRegistry.TOOL_CAPABILITIES_GET
        };
        for (String name : names) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            if (tool != null) {
                tools.put(compactTool(tool));
            }
        }
        return tools;
    }

    @NonNull
    private JSONObject compactTool(@NonNull LauncherToolRegistry.ToolMetadata tool) throws JSONException {
        JSONObject function = new JSONObject();
        function.put("name", tool.openAiName());
        function.put("description", compactDescription(tool.name));
        function.put("parameters", compactSchema(tool.name));
        JSONObject wrapper = new JSONObject();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        return wrapper;
    }

    @NonNull
    private String compactDescription(@NonNull String toolName) {
        switch (toolName) {
            case LauncherToolRegistry.TOOL_APPS_LAUNCH:
                return "Launch app by name.";
            case LauncherToolRegistry.TOOL_APPS_SEARCH:
                return "Search apps.";
            case LauncherToolRegistry.TOOL_INTENT_OPEN:
                return "Open Android URI intent.";
            case LauncherToolRegistry.TOOL_NOTIFICATIONS_RECENT:
                return "Recent notifications.";
            case LauncherToolRegistry.TOOL_NOTIFICATIONS_SEARCH:
                return "Search notifications.";
            case LauncherToolRegistry.TOOL_NOTIFICATIONS_STATS:
                return "Notification stats.";
            case LauncherToolRegistry.TOOL_MEDIA_NOW_PLAYING:
                return "Now playing media.";
            case LauncherToolRegistry.TOOL_SYSTEM_RESOURCES:
                return "Device resources.";
            case LauncherToolRegistry.TOOL_CAPABILITIES_GET:
                return "Launcher capabilities.";
            default:
                return toolName;
        }
    }

    @NonNull
    private JSONObject compactSchema(@NonNull String toolName) throws JSONException {
        JSONObject schema = new JSONObject();
        JSONObject properties = new JSONObject();
        JSONArray required = new JSONArray();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);

        if (LauncherToolRegistry.TOOL_APPS_LAUNCH.equals(toolName)
            || LauncherToolRegistry.TOOL_APPS_SEARCH.equals(toolName)
            || LauncherToolRegistry.TOOL_NOTIFICATIONS_SEARCH.equals(toolName)) {
            properties.put("query", new JSONObject().put("type", "string"));
            required.put("query");
        } else if (LauncherToolRegistry.TOOL_INTENT_OPEN.equals(toolName)) {
            properties.put("data", new JSONObject().put("type", "string"));
            properties.put("action", new JSONObject().put("type", "string"));
            required.put("data");
        }

        if (required.length() > 0) {
            schema.put("required", required);
        }
        return schema;
    }

    private boolean isFunctionGemmaLoaded() {
        try {
            TaiRuntimeState state = TaiManager.getInstance(context).getRuntimeState();
            return state.loaded && TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M.equals(state.loadedModelId);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isElevatedRisk(LauncherToolRegistry.ToolRisk risk) {
        return risk == LauncherToolRegistry.ToolRisk.MEDIUM
            || risk == LauncherToolRegistry.ToolRisk.HIGH
            || risk == LauncherToolRegistry.ToolRisk.CRITICAL;
    }

    @NonNull
    private String extractLastUserText(@NonNull JSONArray messages) {
        for (int i = messages.length() - 1; i >= 0; i--) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) continue;
            if ("user".equals(message.optString("role", ""))) {
                Object content = message.opt("content");
                if (content instanceof String) {
                    return ((String) content).trim();
                }
            }
        }
        return "";
    }

    private boolean startsWithAny(@NonNull String text, @NonNull String... prefixes) {
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) return true;
        }
        return false;
    }

    @NonNull
    private String extractQueryAfter(@NonNull String text, @NonNull String... markers) {
        for (String marker : markers) {
            int idx = text.indexOf(marker);
            if (idx >= 0) {
                String after = text.substring(idx + marker.length()).trim();
                // Strip leading filler words.
                after = after.replaceFirst("^(called|named|for|with|about|that|which|the)\\s+", "");
                return after;
            }
        }
        return "";
    }

    @NonNull
    private String extractTrailingQuery(@NonNull String text) {
        for (String marker : new String[]{"search for", "search", "find", "containing"}) {
            int idx = text.indexOf(marker);
            if (idx >= 0) {
                String after = text.substring(idx + marker.length()).trim();
                after = after.replaceFirst("^(for|with|about|the)\\s+", "");
                return after;
            }
        }
        return "";
    }

    @Nullable
    private Long extractTimestamp(@NonNull String text) {
        // Very lightweight heuristic: if the text mentions a relative hour/minute, convert it.
        // Otherwise return null so the caller can default to one hour ago.
        return null;
    }

    @Nullable
    private MemoryParse parseMemoryWrite(@NonNull String text) {
        // Best-effort parse: "remember X is Y" or "write X = Y" or "save X as Y".
        String[][] patterns = {
            {"remember ", " is "},
            {"write ", " = "},
            {"save ", " as "},
            {"store ", " as "},
        };
        for (String[] pattern : patterns) {
            int keyStart = text.indexOf(pattern[0]);
            if (keyStart < 0) continue;
            String afterPrefix = text.substring(keyStart + pattern[0].length());
            int split = afterPrefix.indexOf(pattern[1]);
            if (split < 0) continue;
            String key = afterPrefix.substring(0, split).trim();
            String value = afterPrefix.substring(split + pattern[1].length()).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                try {
                    JSONObject args = new JSONObject();
                    args.put("key", key);
                    args.put("value", value);
                    return new MemoryParse(args);
                } catch (JSONException ignored) {
                }
            }
        }
        return null;
    }

    @NonNull
    private String coalesce(@NonNull String first, @NonNull String second) {
        return !first.isEmpty() ? first : second;
    }

    @NonNull
    private JSONObject parseJsonBody(@Nullable String body) {
        if (body == null || body.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    @NonNull
    private JSONObject errorResponse(int statusCode, @NonNull String errorCode, @NonNull String message) {
        JSONObject error = new JSONObject();
        try {
            error.put("ok", false);
            error.put("error", errorCode);
            error.put("message", message);
            error.put("_statusCode", statusCode);
        } catch (JSONException ignored) {
        }
        return error;
    }

    private static final class RouteMatch {
        final LauncherToolRegistry.ToolMetadata tool;
        final JSONObject arguments;
        final String rationale;

        RouteMatch(LauncherToolRegistry.ToolMetadata tool, JSONObject arguments, String rationale) {
            this.tool = tool;
            this.arguments = arguments != null ? arguments : new JSONObject();
            this.rationale = rationale;
        }
    }

    private static final class MemoryParse {
        final JSONObject args;

        MemoryParse(JSONObject args) {
            this.args = args;
        }
    }
}
