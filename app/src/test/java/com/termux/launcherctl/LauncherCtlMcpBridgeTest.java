package com.termux.launcherctl;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LauncherCtlMcpBridgeTest {

    @Test
    public void configParse_loadsStdioServersWithPolicy() throws Exception {
        JSONObject root = new JSONObject()
            .put("servers", new JSONObject()
                .put("web", new JSONObject()
                    .put("transport", "stdio")
                    .put("command", "npx")
                    .put("args", new JSONArray().put("-y").put("some-web-search-mcp"))
                    .put("env", new JSONObject().put("SEARCH_API_KEY", "$LAUNCHERCTL_SEARCH_API_KEY"))
                    .put("tools", new JSONObject()
                        .put("allow", new JSONArray().put("web.search"))
                        .put("deny", new JSONArray()))
                    .put("timeout_ms", 10_000)));

        LauncherCtlMcpConfig config = LauncherCtlMcpConfig.parse(root);

        assertEquals(1, config.servers.size());
        LauncherCtlMcpConfig.Server server = config.servers.get("web");
        assertEquals("npx", server.command);
        assertEquals(2, server.args.size());
        assertEquals("$LAUNCHERCTL_SEARCH_API_KEY", server.env.get("SEARCH_API_KEY"));
        assertTrue(server.allows("web.search"));
        assertFalse(server.allows("web.fetch"));
    }

    @Test
    public void normalizeSchema_boundsLimitAndDisallowsAdditionalProperties() throws Exception {
        JSONObject schema = LauncherCtlMcpBridge.normalizeSchema(new JSONObject()
            .put("type", "object")
            .put("properties", new JSONObject()
                .put("query", new JSONObject().put("type", "string"))
                .put("limit", new JSONObject().put("type", "integer"))));

        assertEquals("object", schema.getString("type"));
        assertEquals(false, schema.getBoolean("additionalProperties"));
        JSONObject limit = schema.getJSONObject("properties").getJSONObject("limit");
        assertEquals(1, limit.getInt("minimum"));
        assertEquals(8, limit.getInt("maximum"));
        assertEquals(5, limit.getInt("default"));
    }

    @Test
    public void compactToolResult_returnsShortSearchRows() throws Exception {
        JSONObject result = new JSONObject()
            .put("content", new JSONArray()
                .put(new JSONObject()
                    .put("json", new JSONObject()
                        .put("results", new JSONArray()
                            .put(new JSONObject()
                                .put("title", "Example")
                                .put("url", "https://example.com")
                                .put("snippet", "A concise grounded snippet."))))));

        JSONObject compact = LauncherCtlMcpBridge.compactToolResult(result);

        assertTrue(compact.getBoolean("ok"));
        assertEquals(1, compact.getJSONArray("results").length());
        JSONObject row = compact.getJSONArray("results").getJSONObject(0);
        assertEquals("Example", row.getString("title"));
        assertEquals("https://example.com", row.getString("url"));
        assertEquals("A concise grounded snippet.", row.getString("snippet"));
    }
}
