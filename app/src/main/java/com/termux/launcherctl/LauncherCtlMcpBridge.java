package com.termux.launcherctl;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class LauncherCtlMcpBridge {
    private static final long CACHE_TTL_MS = 60_000L;
    private static final int DEFAULT_RESULT_LIMIT = 5;
    private static final int MAX_RESULT_LIMIT = 8;
    private static final int MAX_SNIPPET_CHARS = 280;

    private static LauncherCtlMcpBridge instance;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Object lock = new Object();
    private Map<String, McpTool> cachedTools = new LinkedHashMap<>();
    private long cachedAtMs = 0L;
    private long configLastModified = -1L;
    @Nullable private Context appContext;

    private LauncherCtlMcpBridge() {
    }

    @NonNull
    public static synchronized LauncherCtlMcpBridge getInstance() {
        if (instance == null) {
            instance = new LauncherCtlMcpBridge();
        }
        return instance;
    }

    static synchronized void resetForTesting() {
        if (instance != null) {
            instance.executor.shutdownNow();
        }
        instance = null;
    }

    public void setContext(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    public void refresh() {
        synchronized (lock) {
            cachedAtMs = 0L;
            configLastModified = -2L;
            cachedTools = new LinkedHashMap<>();
        }
    }

    @NonNull
    public List<LauncherToolRegistry.ToolMetadata> getToolMetadata() {
        List<LauncherToolRegistry.ToolMetadata> out = new ArrayList<>();
        for (McpTool tool : discoverTools(false).values()) {
            out.add(tool.metadata);
        }
        return out;
    }

    @Nullable
    public LauncherToolRegistry.ToolMetadata findTool(@Nullable String name) {
        if (name == null) return null;
        McpTool tool = discoverTools(false).get(name);
        if (tool == null) {
            tool = discoverTools(false).get(LauncherToolRegistry.openAiNameToInternalName(name));
        }
        return tool == null ? null : tool.metadata;
    }

    @NonNull
    public LauncherToolRegistry.ToolExecutionResult executeTool(@NonNull String name,
                                                                @NonNull JSONObject arguments) {
        McpTool tool = discoverTools(false).get(name);
        if (tool == null) {
            tool = discoverTools(false).get(LauncherToolRegistry.openAiNameToInternalName(name));
        }
        if (tool == null) {
            return LauncherToolRegistry.ToolExecutionResult.error(404, "not_found", "Unknown MCP tool: " + name);
        }
        final McpTool selected = tool;
        Future<JSONObject> future = executor.submit(new Callable<JSONObject>() {
            @Override
            public JSONObject call() throws Exception {
                JSONObject boundedArgs = boundArguments(selected.metadata.name, arguments);
                return callMcpTool(selected.server, selected.remoteName, boundedArgs);
            }
        });
        try {
            JSONObject result = future.get(selected.server.timeoutMs, TimeUnit.MILLISECONDS);
            return LauncherToolRegistry.ToolExecutionResult.success(result);
        } catch (Exception e) {
            future.cancel(true);
            return LauncherToolRegistry.ToolExecutionResult.error(504, "mcp_timeout",
                "MCP tool timed out or failed: " + messageOf(e));
        }
    }

    @NonNull
    private Map<String, McpTool> discoverTools(boolean force) {
        File configFile = LauncherCtlStorage.getMcpConfigJsonFile();
        long modified = configFile.exists() ? configFile.lastModified() : -1L;
        long now = System.currentTimeMillis();
        synchronized (lock) {
            if (!force && modified == configLastModified && now - cachedAtMs < CACHE_TTL_MS) {
                return new LinkedHashMap<>(cachedTools);
            }
        }
        LauncherCtlMcpConfig config = LauncherCtlMcpConfig.load(configFile);
        Map<String, McpTool> tools = new LinkedHashMap<>();
        for (LauncherCtlMcpConfig.Server server : config.servers.values()) {
            tools.putAll(discoverServerTools(server));
        }
        synchronized (lock) {
            cachedTools = tools;
            cachedAtMs = now;
            configLastModified = modified;
            return new LinkedHashMap<>(cachedTools);
        }
    }

    @NonNull
    private Map<String, McpTool> discoverServerTools(@NonNull LauncherCtlMcpConfig.Server server) {
        Map<String, McpTool> out = new LinkedHashMap<>();
        try {
            Future<JSONObject> future = executor.submit(() -> withSession(server, session -> {
                session.request("initialize", new JSONObject()
                    .put("protocolVersion", "2024-11-05")
                    .put("capabilities", new JSONObject())
                    .put("clientInfo", new JSONObject()
                        .put("name", "termux-launcher")
                        .put("version", "1")));
                session.notify("notifications/initialized", new JSONObject());
                return session.request("tools/list", new JSONObject());
            }));
            JSONObject response = future.get(server.timeoutMs, TimeUnit.MILLISECONDS);
            JSONObject result = response.optJSONObject("result");
            JSONArray tools = result != null ? result.optJSONArray("tools") : null;
            if (tools == null) return out;
            for (int i = 0; i < tools.length(); i++) {
                JSONObject tool = tools.optJSONObject(i);
                if (tool == null) continue;
                McpTool mapped = normalizeTool(server, tool);
                if (mapped != null) out.put(mapped.metadata.name, mapped);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    @Nullable
    private McpTool normalizeTool(@NonNull LauncherCtlMcpConfig.Server server, @NonNull JSONObject remote) {
        String remoteName = remote.optString("name", "").trim();
        if (remoteName.isEmpty()) {
            return null;
        }
        String exposedName = remoteName.contains(".") ? remoteName : server.name + "." + remoteName;
        if (server.deny.contains(remoteName) || server.deny.contains(exposedName)) {
            return null;
        }
        if (!server.allow.isEmpty()
            && !server.allow.contains(remoteName)
            && !server.allow.contains(exposedName)) {
            return null;
        }
        JSONObject schema = normalizeSchema(remote.optJSONObject("inputSchema"));
        String description = compact(remote.optString("description",
            "MCP tool provided by " + server.name), 240);
        boolean lowRisk = isLowRiskName(exposedName);
        LauncherToolRegistry.ToolMetadata metadata = new LauncherToolRegistry.ToolMetadata(
            exposedName,
            description,
            schema,
            lowRisk ? LauncherToolRegistry.ToolRisk.LOW : LauncherToolRegistry.ToolRisk.MEDIUM,
            !lowRisk,
            LauncherToolRegistry.ToolExecutor.MCP
        );
        return new McpTool(server, remoteName, metadata);
    }

    @NonNull
    static JSONObject normalizeSchema(@Nullable JSONObject inputSchema) {
        JSONObject schema;
        try {
            schema = inputSchema == null ? new JSONObject() : new JSONObject(inputSchema.toString());
        } catch (Exception e) {
            schema = new JSONObject();
        }
        try {
            if (!"object".equals(schema.optString("type", ""))) {
                schema.put("type", "object");
            }
            if (schema.optJSONObject("properties") == null) {
                schema.put("properties", new JSONObject());
            }
            schema.put("additionalProperties", false);
            JSONObject properties = schema.optJSONObject("properties");
            if (properties != null) {
                JSONObject limit = properties.optJSONObject("limit");
                if (limit != null) {
                    limit.put("type", "integer");
                    limit.put("minimum", 1);
                    limit.put("maximum", MAX_RESULT_LIMIT);
                    if (!limit.has("default")) limit.put("default", DEFAULT_RESULT_LIMIT);
                }
            }
        } catch (Exception ignored) {
        }
        return schema;
    }

    @NonNull
    private static JSONObject boundArguments(@NonNull String toolName, @NonNull JSONObject arguments) {
        JSONObject out;
        try {
            out = new JSONObject(arguments.toString());
            if (out.has("limit")) {
                int limit = out.optInt("limit", DEFAULT_RESULT_LIMIT);
                if (limit < 1) limit = 1;
                if (limit > MAX_RESULT_LIMIT) limit = MAX_RESULT_LIMIT;
                out.put("limit", limit);
            } else if (toolName.toLowerCase(Locale.US).contains("search")) {
                out.put("limit", DEFAULT_RESULT_LIMIT);
            }
        } catch (Exception ignored) {
            out = new JSONObject();
        }
        return out;
    }

    @NonNull
    private JSONObject callMcpTool(@NonNull LauncherCtlMcpConfig.Server server,
                                   @NonNull String remoteName,
                                   @NonNull JSONObject arguments) throws Exception {
        JSONObject response = withSession(server, session -> {
            session.request("initialize", new JSONObject()
                .put("protocolVersion", "2024-11-05")
                .put("capabilities", new JSONObject())
                .put("clientInfo", new JSONObject()
                    .put("name", "termux-launcher")
                    .put("version", "1")));
            session.notify("notifications/initialized", new JSONObject());
            return session.request("tools/call", new JSONObject()
                .put("name", remoteName)
                .put("arguments", arguments));
        });
        JSONObject error = response.optJSONObject("error");
        if (error != null) {
            return new JSONObject()
                .put("ok", false)
                .put("error", "mcp_error")
                .put("message", compact(error.optString("message", "MCP tool failed"), 240))
                .put("_statusCode", 502);
        }
        JSONObject result = response.optJSONObject("result");
        return compactToolResult(result);
    }

    @NonNull
    static JSONObject compactToolResult(@Nullable JSONObject result) throws Exception {
        JSONObject out = new JSONObject();
        out.put("ok", true);
        JSONArray results = new JSONArray();
        if (result != null) {
            JSONObject structuredContent = result.optJSONObject("structuredContent");
            if (structuredContent != null) {
                collectStructured(results, structuredContent);
            }
            JSONArray content = result.optJSONArray("content");
            if (content != null) {
                for (int i = 0; i < content.length() && results.length() < MAX_RESULT_LIMIT; i++) {
                    Object item = content.opt(i);
                    collectResultItem(results, item);
                }
            } else {
                collectResultItem(results, result);
            }
        }
        out.put("results", results);
        return out;
    }

    private static void collectResultItem(@NonNull JSONArray results, @Nullable Object item) throws Exception {
        if (item == null || item == JSONObject.NULL || results.length() >= MAX_RESULT_LIMIT) return;
        if (item instanceof JSONObject) {
            JSONObject object = (JSONObject) item;
            JSONObject structured = object.optJSONObject("json");
            if (structured == null) structured = object.optJSONObject("data");
            if (structured != null) {
                collectStructured(results, structured);
                return;
            }
            String text = object.optString("text", "");
            if (!text.isEmpty()) {
                collectText(results, text);
            }
            return;
        }
        collectText(results, String.valueOf(item));
    }

    private static void collectStructured(@NonNull JSONArray results, @NonNull JSONObject object) throws Exception {
        JSONArray array = object.optJSONArray("results");
        if (array == null) array = object.optJSONArray("items");
        if (array != null) {
            for (int i = 0; i < array.length() && results.length() < MAX_RESULT_LIMIT; i++) {
                JSONObject row = array.optJSONObject(i);
                if (row != null) results.put(compactResultRow(row));
            }
            return;
        }
        results.put(compactResultRow(object));
    }

    private static void collectText(@NonNull JSONArray results, @NonNull String text) throws Exception {
        String[] lines = text.split("\\r?\\n");
        StringBuilder snippet = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) continue;
            if (snippet.length() > 0) snippet.append(' ');
            snippet.append(line.trim());
            if (snippet.length() >= MAX_SNIPPET_CHARS) break;
        }
        if (snippet.length() > 0) {
            results.put(new JSONObject().put("snippet", compact(snippet.toString(), MAX_SNIPPET_CHARS)));
        }
    }

    @NonNull
    private static JSONObject compactResultRow(@NonNull JSONObject row) throws Exception {
        JSONObject out = new JSONObject();
        String title = first(row, "title", "name");
        String url = first(row, "url", "link", "source");
        String snippet = first(row, "snippet", "text", "content", "description");
        if (!title.isEmpty()) out.put("title", compact(title, 120));
        if (!url.isEmpty()) out.put("url", compact(url, 300));
        if (!snippet.isEmpty()) out.put("snippet", compact(snippet, MAX_SNIPPET_CHARS));
        if (out.length() == 0) out.put("snippet", compact(row.toString(), MAX_SNIPPET_CHARS));
        return out;
    }

    @NonNull
    private static String first(@NonNull JSONObject row, @NonNull String... keys) {
        for (String key : keys) {
            String value = row.optString(key, "").trim();
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    @NonNull
    private static String compact(@Nullable String value, int maxChars) {
        if (value == null) return "";
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxChars) return normalized;
        return normalized.substring(0, Math.max(0, maxChars - 1)).trim();
    }

    private static boolean isLowRiskName(@NonNull String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.contains("delete") || lower.contains("write") || lower.contains("create")
            || lower.contains("update") || lower.contains("send") || lower.contains("post")
            || lower.contains("run") || lower.contains("exec") || lower.contains("shell")) {
            return false;
        }
        return lower.contains("search") || lower.contains("read") || lower.contains("fetch")
            || lower.contains("lookup") || lower.contains("get");
    }

    @NonNull
    private static String messageOf(@NonNull Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private interface SessionCallback {
        JSONObject run(McpSession session) throws Exception;
    }

    @NonNull
    private static JSONObject withSession(@NonNull LauncherCtlMcpConfig.Server server,
                                          @NonNull SessionCallback callback) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(server.command);
        command.addAll(server.args);
        ProcessBuilder builder = new ProcessBuilder(command);
        Map<String, String> env = builder.environment();
        Context context = LauncherCtlMcpBridge.getInstance().appContext;
        Map<String, String> appEnv = context != null
            ? LauncherCtlMcpPreferences.buildMcpEnvironment(context)
            : new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : server.env.entrySet()) {
            env.put(entry.getKey(), resolveConfiguredEnv(entry.getValue(), appEnv));
        }
        if (context != null) {
            env.putAll(appEnv);
        }
        builder.directory(new File(LauncherCtlStorage.getHomeDir().getAbsolutePath()));
        java.lang.Process process = builder.start();
        try {
            McpSession session = new McpSession(process);
            return callback.run(session);
        } finally {
            process.destroy();
        }
    }

    @NonNull
    private static String resolveConfiguredEnv(@Nullable String value, @NonNull Map<String, String> appEnv) {
        if (value == null || value.isEmpty()) return "";
        if (value.startsWith("$") && value.indexOf('/', 1) < 0) {
            String key = value.substring(1);
            String appValue = appEnv.get(key);
            if (appValue != null) return appValue;
            String systemValue = System.getenv(key);
            return systemValue == null ? "" : systemValue;
        }
        return value;
    }

    private static final class McpTool {
        final LauncherCtlMcpConfig.Server server;
        final String remoteName;
        final LauncherToolRegistry.ToolMetadata metadata;

        McpTool(@NonNull LauncherCtlMcpConfig.Server server, @NonNull String remoteName,
                @NonNull LauncherToolRegistry.ToolMetadata metadata) {
            this.server = server;
            this.remoteName = remoteName;
            this.metadata = metadata;
        }
    }

    private static final class McpSession {
        private final BufferedInputStream input;
        private final BufferedOutputStream output;
        private int nextId = 1;

        McpSession(@NonNull java.lang.Process process) {
            input = new BufferedInputStream(process.getInputStream());
            output = new BufferedOutputStream(process.getOutputStream());
        }

        JSONObject request(@NonNull String method, @NonNull JSONObject params) throws Exception {
            int id = nextId++;
            write(new JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
                .put("params", params));
            while (true) {
                JSONObject message = read();
                if (message.optInt("id", -1) == id) {
                    return message;
                }
            }
        }

        void notify(@NonNull String method, @NonNull JSONObject params) throws Exception {
            write(new JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("params", params));
        }

        private void write(@NonNull JSONObject message) throws Exception {
            byte[] body = message.toString().getBytes(StandardCharsets.UTF_8);
            output.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        }

        private JSONObject read() throws Exception {
            Map<String, String> headers = new HashMap<>();
            while (true) {
                String line = readAsciiLine();
                if (line.isEmpty()) break;
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US),
                        line.substring(colon + 1).trim());
                }
            }
            int length = Integer.parseInt(headers.get("content-length"));
            byte[] body = new byte[length];
            int offset = 0;
            while (offset < length) {
                int read = input.read(body, offset, length - offset);
                if (read < 0) throw new IllegalStateException("MCP server closed stdout");
                offset += read;
            }
            return new JSONObject(new String(body, StandardCharsets.UTF_8));
        }

        private String readAsciiLine() throws Exception {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int b;
            while ((b = input.read()) != -1) {
                if (b == '\n') break;
                if (b != '\r') line.write(b);
            }
            if (b == -1 && line.size() == 0) throw new IllegalStateException("MCP server closed stdout");
            return line.toString(StandardCharsets.US_ASCII.name());
        }
    }
}
