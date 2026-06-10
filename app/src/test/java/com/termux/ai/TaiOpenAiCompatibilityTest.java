package com.termux.ai;

import com.google.ai.edge.litertlm.ToolCall;
import com.google.ai.edge.litertlm.ToolProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiOpenAiCompatibilityTest {

    @Test
    public void toolProviders_acceptOpenAiFunctionSchema() throws Exception {
        JSONObject function = new JSONObject();
        function.put("name", "show_map");
        function.put("description", "Shows a location on the map.");
        function.put("parameters", new JSONObject()
            .put("type", "object")
            .put("properties", new JSONObject().put("query", new JSONObject().put("type", "string")))
            .put("required", new JSONArray().put("query")));
        JSONArray tools = new JSONArray().put(new JSONObject()
            .put("type", "function")
            .put("function", function));

        List<ToolProvider> providers = TaiManager.toolProviders(tools, "auto");

        assertEquals(1, providers.size());
    }

    @Test
    public void assistantToolCalls_roundTripIntoLiteRtHistory() throws Exception {
        JSONObject assistant = new JSONObject();
        assistant.put("role", "assistant");
        assistant.put("tool_calls", new JSONArray().put(new JSONObject()
            .put("id", "call_123")
            .put("type", "function")
            .put("function", new JSONObject()
                .put("name", "create_calendar_event")
                .put("arguments", "{\"title\":\"Lunch\",\"details\":{\"hour\":13}}"))));
        Map<String, String> names = new LinkedHashMap<>();

        List<ToolCall> calls = TaiManager.toolCallsFromAssistant(assistant, names);

        assertEquals(1, calls.size());
        assertEquals("create_calendar_event", calls.get(0).getName());
        assertEquals("Lunch", calls.get(0).getArguments().get("title"));
        assertTrue(calls.get(0).getArguments().get("details") instanceof Map);
        assertEquals("create_calendar_event", names.get("call_123"));
    }

    @Test
    public void oneShotRequests_doNotReuseConversationHistory() {
        TaiChatRequest request = TaiChatRequest.oneShot("system", "hello");

        assertFalse(request.reusableConversation);
        assertTrue(request.initialMessages.isEmpty());
        assertEquals("hello", request.message.toString());
    }

    @Test
    public void openAiModels_returnsStrictListShape() throws Exception {
        JSONObject taiModels = new JSONObject()
            .put("ok", true)
            .put("models", new JSONArray()
                .put(new JSONObject().put("id", "qwen").put("backend", "llama-cpp"))
                .put(new JSONObject().put("id", "qwen").put("backend", "mlc"))
                .put(new JSONObject().put("id", "gemma")));

        JSONObject response = TaiManager.openAiModelsFromTaiModels(taiModels);

        assertEquals("list", response.getString("object"));
        assertEquals(2, response.getJSONArray("data").length());
        assertEquals("qwen", response.getJSONArray("data").getJSONObject(0).getString("id"));
        assertEquals("model", response.getJSONArray("data").getJSONObject(0).getString("object"));
        assertFalse(response.has("tai"));
        assertFalse(response.getJSONArray("data").getJSONObject(0).has("tai"));
    }

    @Test
    public void cancellationDetection_acceptsLiteRtJniCancellationMessage() {
        RuntimeException error = new RuntimeException(
            "Failed to call nativeSendMessage: CANCELLED: Process cancelled.");

        assertTrue(LiteRtTaiRuntime.isCancellation(error));
        assertFalse(LiteRtTaiRuntime.isCancellation(new RuntimeException("GPU initialization failed")));
    }
}
