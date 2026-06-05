package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TaiModelRegistryTest {

    @Test
    public void registry_containsInitialRecommendedModels() {
        TaiModelRegistry registry = new TaiModelRegistry();

        assertNotNull(registry.getModel(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
        assertNotNull(registry.getModel(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT));
        assertEquals(2, registry.getBuiltInModels().size());
    }

    @Test
    public void modelJson_exposesCapabilitiesWithoutSamplingDefaults() throws Exception {
        TaiModelSpec spec = new TaiModelRegistry().getModel(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        JSONObject json = spec.toJson();
        JSONArray capabilities = json.getJSONArray("capabilities");

        assertEquals("Gemma-4-E2B-it", json.getString("id"));
        assertTrue(capabilities.toString().contains("text_chat"));
        assertTrue(capabilities.toString().contains("image_input"));
        assertTrue(json.isNull("localPath"));
        assertTrue(!json.has("temperature"));
        assertTrue(!json.has("topK"));
        assertTrue(!json.has("topP"));
        assertTrue(!json.has("maxTokens"));
    }

    @Test
    public void modelSpec_roundTripsImportedModelMetadata() throws Exception {
        TaiModelSpec spec = new TaiModelSpec(
            "local-test",
            "Local Test",
            "Imported local model",
            "imported",
            "/tmp/local-test.task",
            "User accepted license externally",
            1234L,
            new java.util.LinkedHashSet<>(java.util.Arrays.asList("text_chat", "tool_use")),
            false
        );

        TaiModelSpec roundTrip = TaiModelSpec.fromJson(spec.toJson());

        assertEquals("local-test", roundTrip.id);
        assertEquals("Local Test", roundTrip.displayName);
        assertEquals("imported", roundTrip.source);
        assertEquals("/tmp/local-test.task", roundTrip.localPath);
        assertEquals(1234L, roundTrip.sizeBytes);
        assertTrue(!roundTrip.builtInCatalogEntry);
        assertTrue(roundTrip.capabilities.contains("text_chat"));
        assertTrue(roundTrip.capabilities.contains("tool_use"));
    }

    @Test
    public void runtimeOptions_exposeIdleUnloadAndRequestOverrides() throws Exception {
        TaiRuntimeOptions options = new TaiRuntimeOptions(
            null,
            64,
            0.95d,
            null,
            null,
            null,
            true,
            10
        ).withGenerationOverrides(512, null, null, 0.7d, "cpu", null, null);

        JSONObject json = options.toJson();

        assertEquals(512, json.getInt("maxTokens"));
        assertEquals(64, json.getInt("topK"));
        assertEquals(0.95d, json.getDouble("topP"), 0.001d);
        assertEquals(0.7d, json.getDouble("temperature"), 0.001d);
        assertEquals("cpu", json.getString("accelerator"));
        assertTrue(json.getBoolean("speculativeDecodingEnabled"));
        assertEquals(10, json.getInt("idleUnloadMinutes"));
    }

    @Test
    public void runtimeStateJson_exposesLifecycleAndTimers() throws Exception {
        TaiRuntimeState state = new TaiRuntimeState(
            true,
            "local-test",
            "litert-lm",
            "idle-warm",
            "Model loaded.",
            "gpu",
            "Auto selected GPU first.",
            "/tmp/local-test.litertlm",
            false,
            null,
            0L,
            System.currentTimeMillis() + 60_000L,
            System.currentTimeMillis() + 120_000L,
            100L,
            200L
        );

        JSONObject json = state.toJson();

        assertTrue(json.getBoolean("loaded"));
        assertEquals("local-test", json.getString("loadedModelId"));
        assertEquals("idle-warm", json.getString("state"));
        assertEquals("gpu", json.getString("backend"));
        assertTrue(json.getLong("keepWarmRemainingMs") > 0L);
        assertTrue(json.getLong("idleUnloadRemainingMs") > 0L);
    }
}
