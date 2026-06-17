package com.termux.app.fragments.settings.termux;

import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiModelCatalogPreferencesFragmentTest {

    @Test
    public void filterEntries_filtersByBackend() {
        List<TaiModelCatalog.CatalogEntry> liteRt = TaiModelCatalogPreferencesFragment.filterEntries(
            TaiModelCatalog.entries().values(), TaiModelCatalogPreferencesFragment.BackendFilter.LITERT, "");
        List<TaiModelCatalog.CatalogEntry> mlc = TaiModelCatalogPreferencesFragment.filterEntries(
            TaiModelCatalog.entries().values(), TaiModelCatalogPreferencesFragment.BackendFilter.MLC, "");

        assertEquals(5, liteRt.size());
        assertEquals(11, mlc.size());
        for (TaiModelCatalog.CatalogEntry entry : liteRt) {
            assertEquals(TaiModelSpec.BACKEND_LITERT_LM, entry.backend);
        }
        for (TaiModelCatalog.CatalogEntry entry : mlc) {
            assertEquals(TaiModelSpec.BACKEND_MLC_LLM, entry.backend);
        }
    }

    @Test
    public void filterEntries_searchesNameIdBackendJobGroupAndCapabilityTags() {
        assertTrue(containsModel(search("Gemma 4 E2B"), TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
        assertTrue(containsModel(search("qwen2.5-coder-1.5b"), "qwen2.5-coder-1.5b-instruct-q4f16_1-mlc"));
        assertTrue(search("mlc_llm").size() >= 11);
        assertTrue(containsModel(search("general_multimodal"), TaiModelRegistry.MODEL_GEMMA_4_E2B_IT));
        assertTrue(containsModel(search("Vision"), "qwen2.5-vl-3b-instruct-q4f16_1-mlc"));
    }

    @Test
    public void groupByJobGroup_preservesCatalogJobGroups() {
        Map<String, List<TaiModelCatalog.CatalogEntry>> groups = TaiModelCatalogPreferencesFragment.groupByJobGroup(
            TaiModelCatalogPreferencesFragment.filterEntries(TaiModelCatalog.entries().values(),
                TaiModelCatalogPreferencesFragment.BackendFilter.ALL, ""));

        assertTrue(groups.containsKey("general_multimodal"));
        assertTrue(groups.containsKey("coding"));
        assertTrue(groups.containsKey("reasoning"));
        assertEquals("general_multimodal", groups.get("general_multimodal").get(0).jobGroup);
    }

    @Test
    public void displayTags_limitsCatalogTagsToSupportedUiSet() {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);

        assertTrue(TaiModelCatalogPreferencesFragment.displayTags(entry).contains("Text"));
        assertTrue(TaiModelCatalogPreferencesFragment.displayTags(entry).contains("Vision"));
        assertTrue(TaiModelCatalogPreferencesFragment.displayTags(entry).contains("Audio"));
        assertFalse(TaiModelCatalogPreferencesFragment.displayTags(entry).contains("tool_use"));
    }

    @Test
    public void actionStateFor_returnsRequiredStatefulActions() throws Exception {
        TaiModelCatalog.CatalogEntry installable = TaiModelCatalog.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        TaiModelCatalog.CatalogEntry importOnly = TaiModelCatalog.get("qwen2.5-1.5b-instruct-litert-lm");
        TaiModelCatalog.CatalogEntry mlc = TaiModelCatalog.get("qwen2.5-coder-1.5b-instruct-q4f16_1-mlc");
        JSONObject downloading = new JSONObject()
            .put("status", TaiModelStore.STATE_DOWNLOADING)
            .put("bytesRead", 25L)
            .put("totalBytes", 100L);

        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.INSTALL,
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, false, null, "other", true, null).type);
        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.DOWNLOADING,
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, false, downloading, "other", true, null).type);
        assertEquals("25%",
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, false, downloading, "other", true, null).progressLabel);
        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.INSTALLED,
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, true, null, "other", true, null).type);
        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.ACTIVE,
            TaiModelCatalogPreferencesFragment.actionStateFor(installable, true, null, installable.modelId, true, null).type);
        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.IMPORT_ONLY,
            TaiModelCatalogPreferencesFragment.actionStateFor(importOnly, false, null, "other", true, null).type);

        TaiModelCatalogPreferencesFragment.CatalogActionState blocked = TaiModelCatalogPreferencesFragment.actionStateFor(
            mlc, false, null, "other", false, "unsupported ABI");
        assertEquals(TaiModelCatalogPreferencesFragment.CatalogActionType.MLC_UNSUPPORTED, blocked.type);
        assertFalse(blocked.enabled);
        assertEquals("unsupported ABI", blocked.disabledReason);
    }

    private List<TaiModelCatalog.CatalogEntry> search(String query) {
        return TaiModelCatalogPreferencesFragment.filterEntries(TaiModelCatalog.entries().values(),
            TaiModelCatalogPreferencesFragment.BackendFilter.ALL, query);
    }

    private boolean containsModel(List<TaiModelCatalog.CatalogEntry> entries, String modelId) {
        for (TaiModelCatalog.CatalogEntry entry : entries) {
            if (modelId.equals(entry.modelId)) return true;
        }
        return false;
    }
}
