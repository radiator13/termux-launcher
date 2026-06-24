package com.termux.launcherctl;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LauncherToolRegistryTest {

    private LauncherToolRegistry registry;

    @Before
    public void setUp() {
        LauncherToolRegistry.resetForTesting();
        registry = LauncherToolRegistry.getInstance();
    }

    @Test
    public void registry_containsExpectedTools() {
        List<LauncherToolRegistry.ToolMetadata> tools = registry.getTools();
        assertEquals(14, tools.size());
        assertNotNull(registry.getTool("capabilities.get"));
        assertNotNull(registry.getTool("apps.search"));
        assertNotNull(registry.getTool("apps.launch"));
        assertNotNull(registry.getTool("notifications.recent"));
        assertNotNull(registry.getTool("notifications.since"));
        assertNotNull(registry.getTool("notifications.search"));
        assertNotNull(registry.getTool("notifications.stats"));
        assertNotNull(registry.getTool("media.now_playing"));
        assertNotNull(registry.getTool("system.resources"));
        assertNotNull(registry.getTool("intent.open"));
        assertNotNull(registry.getTool("memory.write"));
        assertNotNull(registry.getTool("memory.search"));
        assertNotNull(registry.getTool("events.tail"));
        assertNotNull(registry.getTool("user.confirm"));
        assertNull(registry.getTool("unknown.tool"));
    }

    @Test
    public void launchTool_requiresConfirmationAndHasSchema() {
        LauncherToolRegistry.ToolMetadata tool = registry.getTool("apps.launch");
        assertNotNull(tool);
        assertEquals("apps.launch", tool.name);
        assertEquals(LauncherToolRegistry.ToolRisk.MEDIUM, tool.risk);
        assertTrue(tool.requiresConfirmation);
        assertEquals("launcher", tool.executor.label);
        JSONObject schema = tool.schema;
        assertEquals("object", schema.optString("type"));
        assertTrue(schema.optJSONObject("properties").has("query"));
        assertTrue(schema.optJSONArray("required").toString().contains("query"));
    }

    @Test
    public void readOnlyTools_areLowRiskAndDoNotRequireConfirmation() {
        String[] names = {"capabilities.get", "apps.search", "notifications.recent", "notifications.since",
            "notifications.search", "notifications.stats", "media.now_playing", "system.resources", "events.tail"};
        for (String name : names) {
            LauncherToolRegistry.ToolMetadata tool = registry.getTool(name);
            assertNotNull(name, tool);
            assertEquals(name, LauncherToolRegistry.ToolRisk.LOW, tool.risk);
            assertFalse(name, tool.requiresConfirmation);
        }
    }

    @Test
    public void userConfirmTool_isCritical() {
        LauncherToolRegistry.ToolMetadata tool = registry.getTool("user.confirm");
        assertNotNull(tool);
        assertEquals(LauncherToolRegistry.ToolRisk.CRITICAL, tool.risk);
        assertTrue(tool.requiresConfirmation);
    }

    @Test
    public void toOpenAiToolsJson_producesFunctionTools() throws Exception {
        JSONArray openAiTools = registry.toOpenAiToolsJson();
        assertEquals(14, openAiTools.length());
        for (int i = 0; i < openAiTools.length(); i++) {
            JSONObject item = openAiTools.getJSONObject(i);
            assertEquals("function", item.getString("type"));
            JSONObject function = item.getJSONObject("function");
            assertTrue(function.getString("name").length() > 0);
            assertFalse(function.getString("name").contains("."));
            assertTrue(function.has("description"));
            assertTrue(function.has("parameters"));
        }
    }

    @Test
    public void toInternalJson_includesSchemaAndRisk() throws Exception {
        JSONArray internal = registry.toInternalJson();
        assertEquals(14, internal.length());
        JSONObject first = internal.getJSONObject(0);
        assertTrue(first.has("name"));
        assertTrue(first.has("description"));
        assertTrue(first.has("schema"));
        assertTrue(first.has("risk"));
        assertTrue(first.has("requiresConfirmation"));
        assertTrue(first.has("executor"));
    }

    @Test
    public void responseJson_containsBothFormats() throws Exception {
        JSONObject response = registry.toResponseJson();
        assertTrue(response.getBoolean("ok"));
        assertEquals(14, response.getInt("count"));
        assertEquals(14, response.getJSONArray("tools").length());
        assertEquals(14, response.getJSONArray("openAiTools").length());
    }

    @Test
    public void intentOpenSchema_hasExpectedFields() {
        JSONObject schema = registry.getTool("intent.open").schema;
        JSONObject properties = schema.optJSONObject("properties");
        assertNotNull(properties);
        assertTrue(properties.has("action"));
        assertTrue(properties.has("data"));
        assertTrue(properties.has("package"));
        assertTrue(properties.has("component"));
        assertTrue(properties.has("extras"));
        assertEquals("android.intent.action.VIEW", properties.optJSONObject("action").optString("default"));
    }

    @Test
    public void notificationsSinceSchema_marksSinceRequired() {
        JSONObject schema = registry.getTool("notifications.since").schema;
        JSONArray required = schema.optJSONArray("required");
        assertNotNull(required);
        assertTrue(required.toString().contains("since"));
    }

    @Test
    public void epochMillisSchemas_allowCurrentTimestamps() throws Exception {
        long now = System.currentTimeMillis();
        assertTrue(registry.getTool("notifications.since").schema
            .getJSONObject("properties").getJSONObject("since").getLong("maximum") > now);
        assertTrue(registry.getTool("notifications.stats").schema
            .getJSONObject("properties").getJSONObject("since").getLong("maximum") > now);
        assertTrue(registry.getTool("events.tail").schema
            .getJSONObject("properties").getJSONObject("since").getLong("maximum") > now);
    }

    @Test
    public void openAiNameToInternalName_mapsUnderscoresToDots() {
        assertEquals("apps.launch", LauncherToolRegistry.openAiNameToInternalName("apps_launch"));
        assertEquals("notifications.recent", LauncherToolRegistry.openAiNameToInternalName("notifications_recent"));
        assertEquals("media.now_playing", LauncherToolRegistry.openAiNameToInternalName("media_now_playing"));
    }

    @Test
    public void getToolByOpenAiName_findsTool() {
        LauncherToolRegistry.ToolMetadata tool = registry.getToolByOpenAiName("apps_launch");
        assertNotNull(tool);
        assertEquals("apps.launch", tool.name);
        LauncherToolRegistry.ToolMetadata mediaTool = registry.getToolByOpenAiName("media_now_playing");
        assertNotNull(mediaTool);
        assertEquals("media.now_playing", mediaTool.name);
        assertNull(registry.getToolByOpenAiName("unknown_tool"));
    }

    @Test
    public void toolExecutionResult_successHasOkAndStatusCode() throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("value", 1);
        LauncherToolRegistry.ToolExecutionResult result = LauncherToolRegistry.ToolExecutionResult.success(payload);
        assertTrue(result.ok);
        assertEquals(200, result.statusCode);
        assertEquals(1, result.result.getInt("value"));
        JSONObject json = result.toJson();
        assertTrue(json.getBoolean("ok"));
    }

    @Test
    public void toolExecutionResult_errorHasErrorAndStatusCode() throws Exception {
        LauncherToolRegistry.ToolExecutionResult result =
            LauncherToolRegistry.ToolExecutionResult.error(403, "confirmation_required", "Need confirm");
        assertFalse(result.ok);
        assertEquals(403, result.statusCode);
        assertEquals("confirmation_required", result.errorCode);
        JSONObject json = result.toJson();
        assertEquals("confirmation_required", json.getString("error"));
        assertEquals(403, json.getInt("_statusCode"));
    }
}
