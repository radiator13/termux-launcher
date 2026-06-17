package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class TaiModelSchemaTest {

    @Test
    public void oldLiteRtJson_deserializesWithExistingBackendAndFormat() throws Exception {
        JSONObject json = baseModelJson("old-litert", "/models/old-litert/model.litertlm");

        TaiModelSpec spec = TaiModelSpec.fromJson(json);

        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, spec.backend);
        assertEquals(TaiModelSpec.FORMAT_LITERTLM, spec.format);
        assertTrue(spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
    }

    @Test
    public void mnnJson_deserializesAndSerializesBackendFormatAndCapabilities() throws Exception {
        JSONObject json = baseModelJson("mnn-chat", "/models/mnn-chat/model.mnn");
        json.put("backend", TaiModelSpec.BACKEND_MNN_LLM);
        json.put("format", TaiModelSpec.FORMAT_MNN);
        json.put("capabilities", new JSONArray()
            .put(TaiModelSpec.CAPABILITY_TEXT_CHAT)
            .put(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));

        TaiModelSpec spec = TaiModelSpec.fromJson(json);
        JSONObject roundTrip = spec.toJson();

        assertEquals(TaiModelSpec.BACKEND_MNN_LLM, spec.backend);
        assertEquals(TaiModelSpec.FORMAT_MNN, spec.format);
        assertTrue(spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertTrue(spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
        assertEquals(TaiModelSpec.BACKEND_MNN_LLM, roundTrip.getString("backend"));
        assertEquals(TaiModelSpec.FORMAT_MNN, roundTrip.getString("format"));
        assertEquals(2, roundTrip.getJSONArray("capabilities").length());
    }

    @Test
    public void inferBackendAndFormat_detectMnnPathHints() {
        assertEquals(TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.inferBackend("/models/chat/model.mnn"));
        assertEquals(TaiModelSpec.FORMAT_MNN, TaiModelSpec.inferFormat("https://huggingface.co/mnn-ai/test-model/config.json"));
        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.inferBackend("/models/chat/model.litertlm"));
        assertEquals(TaiModelSpec.FORMAT_LITERTLM, TaiModelSpec.inferFormat("/models/chat/model.litertlm"));
    }

    @Test
    public void duplicateModelIds_lastRecordWins() throws Exception {
        JSONObject payload = new JSONObject().put("entries", new JSONArray()
            .put(catalogEntry("duplicate-model", "First", TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM))
            .put(catalogEntry("duplicate-model", "Second", TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN)));

        TaiModelCatalog.applyRemotePayload(payload, true);

        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get("duplicate-model");
        assertEquals("Second", entry.displayName);
        assertEquals(TaiModelSpec.BACKEND_MNN_LLM, entry.backend);
    }

    @Test
    public void unsupportedBackendAndFormat_throwDeterministicErrors() throws Exception {
        assertIllegalArgument("unsupported_backend",
            baseModelJson("bad-backend", "/models/bad/model.litertlm").put("backend", "custom-backend"));
        assertIllegalArgument("unsupported_format",
            baseModelJson("bad-format", "/models/bad/model.litertlm").put("format", "custom-format"));
    }

    @Test
    public void catalogMerge_preservesLiteRtAndMnnEntries() throws Exception {
        JSONObject payload = new JSONObject().put("entries", new JSONArray()
            .put(catalogEntry("remote-litert", "Remote LiteRT", TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM))
            .put(catalogEntry("remote-mnn", "Remote MNN", TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN))
            .put(catalogEntry("remote-bad", "Remote Bad", "custom-backend", TaiModelSpec.FORMAT_LITERTLM)));

        TaiModelCatalog.applyRemotePayload(payload, true);

        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, TaiModelCatalog.get("remote-litert").backend);
        assertEquals(TaiModelSpec.BACKEND_MNN_LLM, TaiModelCatalog.get("remote-mnn").backend);
        assertEquals(null, TaiModelCatalog.get("remote-bad"));
        assertTrue(TaiModelCatalog.entries().containsKey(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
    }

    @Test
    public void userModelStore_persistsAndReloadsMnnModels() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        TaiModelStore store = new TaiModelStore(context);
        TaiModelSpec mnn = TaiModelSpec.fromJson(baseModelJson("user-mnn", "/models/user-mnn/model.mnn")
            .put("backend", TaiModelSpec.BACKEND_MNN_LLM)
            .put("format", TaiModelSpec.FORMAT_MNN)
            .put("capabilities", new JSONArray()
                .put(TaiModelSpec.CAPABILITY_TEXT_CHAT)
                .put(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)));

        store.upsertUserModel(mnn);
        Map<String, TaiModelSpec> reloaded = new TaiModelStore(context).getUserModels();

        assertEquals(1, reloaded.size());
        assertEquals(TaiModelSpec.BACKEND_MNN_LLM, reloaded.get("user-mnn").backend);
        assertEquals(TaiModelSpec.FORMAT_MNN, reloaded.get("user-mnn").format);
        assertTrue(reloaded.get("user-mnn").capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
    }

    private static JSONObject baseModelJson(String id, String localPath) throws Exception {
        return new JSONObject()
            .put("id", id)
            .put("displayName", id)
            .put("roleHint", "Test model")
            .put("source", "test")
            .put("localPath", localPath)
            .put("license", "test")
            .put("sizeBytes", 123L)
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_CHAT));
    }

    private static JSONObject catalogEntry(String id, String displayName, String backend, String format) throws Exception {
        return new JSONObject()
            .put("modelId", id)
            .put("displayName", displayName)
            .put("roleHint", "Catalog model")
            .put("repositoryId", "example/" + id)
            .put("revision", "main")
            .put("artifactPath", id + ".bin")
            .put("license", "test")
            .put("sizeBytes", 456L)
            .put("backend", backend)
            .put("format", format)
            .put("architecture", "gemma")
            .put("contextWindow", 4096)
            .put("recommendedRamGb", 6)
            .put("capabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_CHAT));
    }

    private static void assertIllegalArgument(String expectedMessage, JSONObject json) {
        try {
            TaiModelSpec.fromJson(json);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }
}
