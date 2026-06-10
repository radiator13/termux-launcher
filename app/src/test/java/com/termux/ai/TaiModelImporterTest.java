package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiModelImporterTest {

    @Test
    public void supportedFileNames_acceptLiteRtLmPackages() {
        assertTrue(TaiModelImporter.isSupportedFileName("model.litertlm"));
        assertTrue(TaiModelImporter.isSupportedFileName("MODEL.TASK"));
        assertTrue(TaiModelImporter.isSupportedFileName("model.gguf"));
        assertFalse(TaiModelImporter.isSupportedFileName("model.zip"));
    }

    @Test
    public void modelId_defaultsFromSelectedFileName() {
        assertEquals("Gemma-4-E2B-it",
            TaiModelImporter.sanitizeModelId(
                TaiModelImporter.stripModelExtension("Gemma 4 E2B it.litertlm")));
        assertEquals("mobile_actions", TaiModelImporter.stripModelExtension("mobile_actions.task"));
        assertEquals("qwen-coder", TaiModelImporter.stripModelExtension("qwen-coder.gguf"));
    }

    @Test
    public void modelSpec_infersMlcPackageFormat() {
        assertEquals(TaiModelSpec.FORMAT_MLC_PACKAGE, TaiModelSpec.inferFormat("/models/qwen.mlc-package"));
        assertEquals(TaiModelSpec.BACKEND_MLC, TaiModelSpec.inferBackend("/models/qwen.mlc-package"));
    }
}
