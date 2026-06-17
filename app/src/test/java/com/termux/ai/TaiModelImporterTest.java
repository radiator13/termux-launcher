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
        assertTrue(TaiModelImporter.isSupportedFileName("phi-3-mini.mlc.json"));
        assertTrue(TaiModelImporter.isSupportedFileName("phi-3-mini-mlc.json"));
        assertFalse(TaiModelImporter.isSupportedFileName("model.bin"));
        assertFalse(TaiModelImporter.isSupportedFileName("model.zip"));
    }

    @Test
    public void unsupportedRawWeightsAndNativeLibraries_reportStableCodes() {
        TaiModelImporter.ValidationResult safetensors = TaiModelImporter.validateSupportedImportFileName("model.safetensors");
        assertFalse(safetensors.supported);
        assertEquals(TaiModelImporter.ERROR_RAW_WEIGHTS_FORBIDDEN, safetensors.errorCode);

        TaiModelImporter.ValidationResult nativeLibrary = TaiModelImporter.validateSupportedImportFileName("libmlc_llm.so");
        assertFalse(nativeLibrary.supported);
        assertEquals(TaiModelImporter.ERROR_NATIVE_LIBRARY_FORBIDDEN, nativeLibrary.errorCode);

        TaiModelImporter.ValidationResult zip = TaiModelImporter.validateSupportedImportFileName("model.zip");
        assertFalse(zip.supported);
        assertEquals(TaiModelImporter.ERROR_UNSUPPORTED_MODEL_FILE, zip.errorCode);
    }

    @Test
    public void mlcManifestNames_areRecognizedAsManifestImports() {
        TaiModelImporter.ValidationResult dotManifest = TaiModelImporter.validateSupportedImportFileName("gemma.mlc.json");
        assertTrue(dotManifest.supported);
        assertTrue(dotManifest.mlcManifest);

        TaiModelImporter.ValidationResult dashManifest = TaiModelImporter.validateSupportedImportFileName("gemma-mlc.json");
        assertTrue(dashManifest.supported);
        assertTrue(dashManifest.mlcManifest);
    }

    @Test
    public void backendValidation_acceptsOnlyBackendSpecificSafFiles() {
        assertTrue(TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_LITERT_LM, "assistant.litertlm").supported);
        assertTrue(TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_LITERT_LM, "actions.task").supported);
        assertFalse(TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_LITERT_LM, "phi.mlc.json").supported);

        TaiModelImporter.ValidationResult mlc = TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_MLC_LLM, "phi.mlc.json");
        assertTrue(mlc.supported);
        assertTrue(mlc.mlcManifest);
        assertFalse(TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_MLC_LLM, "assistant.litertlm").supported);
    }

    @Test
    public void backendValidation_rejectsRawWeightsAndNativeLibrariesWithStableCodes() {
        TaiModelImporter.ValidationResult raw = TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_MLC_LLM, "model.gguf");
        assertFalse(raw.supported);
        assertEquals(TaiModelImporter.ERROR_RAW_WEIGHTS_FORBIDDEN, raw.errorCode);

        TaiModelImporter.ValidationResult nativeLibrary = TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_MLC_LLM, "libmodel.so");
        assertFalse(nativeLibrary.supported);
        assertEquals(TaiModelImporter.ERROR_NATIVE_LIBRARY_FORBIDDEN, nativeLibrary.errorCode);
    }

    @Test
    public void huggingFaceUrlValidation_requiresHttpsAndBackendSpecificPackage() {
        TaiModelImporter.ValidationResult insecure = TaiModelImporter.validateHuggingFaceImportUrl(
            TaiModelSpec.BACKEND_LITERT_LM, "http://huggingface.co/user/model/resolve/main/model.litertlm");
        assertFalse(insecure.supported);
        assertEquals(TaiModelImporter.ERROR_INSECURE_URL, insecure.errorCode);

        assertTrue(TaiModelImporter.validateHuggingFaceImportUrl(
            TaiModelSpec.BACKEND_LITERT_LM, "https://huggingface.co/user/model/resolve/main/model.task?download=1").supported);
        assertTrue(TaiModelImporter.validateHuggingFaceImportUrl(
            TaiModelSpec.BACKEND_MLC_LLM, "https://huggingface.co/user/model/resolve/main/model.mlc.json").supported);
        assertFalse(TaiModelImporter.validateHuggingFaceImportUrl(
            TaiModelSpec.BACKEND_MLC_LLM, "https://huggingface.co/user/model/resolve/main/model.safetensors").supported);
    }

    @Test
    public void modelId_defaultsFromSelectedFileName() {
        assertEquals("Gemma-4-E2B-it",
            TaiModelImporter.sanitizeModelId(
                TaiModelImporter.stripModelExtension("Gemma 4 E2B it.litertlm")));
        assertEquals("mobile_actions", TaiModelImporter.stripModelExtension("mobile_actions.task"));
        assertEquals("phi-3-mini", TaiModelImporter.stripModelExtension("phi-3-mini.mlc.json"));
    }
}
