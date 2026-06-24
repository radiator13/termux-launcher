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
        assertFalse(TaiModelImporter.isSupportedFileName("phi-3-mini.mnn.json"));
        assertFalse(TaiModelImporter.isSupportedFileName("phi-3-mini-mnn.json"));
        assertFalse(TaiModelImporter.isSupportedFileName("model.bin"));
        assertFalse(TaiModelImporter.isSupportedFileName("model.zip"));
    }

    @Test
    public void huggingFaceImportUrl_acceptsBareRepoAndResolveUrls() {
        // Bare repo URL: the downloader resolves the entry file, so it's accepted for either backend.
        assertTrue(TaiModelImporter.validateHuggingFaceImportUrl(TaiModelSpec.BACKEND_MNN_LLM,
            "https://huggingface.co/taobao-mnn/Qwen2.5-VL-3B-Instruct-MNN").supported);
        assertTrue(TaiModelImporter.validateHuggingFaceImportUrl(TaiModelSpec.BACKEND_LITERT_LM,
            "https://huggingface.co/litert-community/Gemma3-1B-IT").supported);
        // Direct resolve URLs still validate by file name per backend.
        assertTrue(TaiModelImporter.validateHuggingFaceImportUrl(TaiModelSpec.BACKEND_MNN_LLM,
            "https://huggingface.co/taobao-mnn/Foo-MNN/resolve/main/config.json").supported);
        assertFalse(TaiModelImporter.validateHuggingFaceImportUrl(TaiModelSpec.BACKEND_LITERT_LM,
            "https://huggingface.co/foo/bar/resolve/main/model.gguf").supported);
        // Not a repo / not https.
        assertFalse(TaiModelImporter.validateHuggingFaceImportUrl(TaiModelSpec.BACKEND_MNN_LLM,
            "https://huggingface.co/taobao-mnn").supported);
        assertFalse(TaiModelImporter.validateHuggingFaceImportUrl(TaiModelSpec.BACKEND_MNN_LLM,
            "http://huggingface.co/a/b").supported);
    }

    @Test
    public void unsupportedRawWeightsAndNativeLibraries_reportStableCodes() {
        TaiModelImporter.ValidationResult safetensors = TaiModelImporter.validateSupportedImportFileName("model.safetensors");
        assertFalse(safetensors.supported);
        assertEquals(TaiModelImporter.ERROR_RAW_WEIGHTS_FORBIDDEN, safetensors.errorCode);

        TaiModelImporter.ValidationResult nativeLibrary = TaiModelImporter.validateSupportedImportFileName("libmnn_llm.so");
        assertFalse(nativeLibrary.supported);
        assertEquals(TaiModelImporter.ERROR_NATIVE_LIBRARY_FORBIDDEN, nativeLibrary.errorCode);

        TaiModelImporter.ValidationResult zip = TaiModelImporter.validateSupportedImportFileName("model.zip");
        assertFalse(zip.supported);
        assertEquals(TaiModelImporter.ERROR_UNSUPPORTED_MODEL_FILE, zip.errorCode);
    }

    @Test
    public void mnnConfigUrl_isRecognizedForBackendDownload() {
        TaiModelImporter.ValidationResult config = TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_MNN_LLM, "config.json");
        assertTrue(config.supported);
        assertFalse(config.packageManifest);
    }

    @Test
    public void backendValidation_acceptsOnlyBackendSpecificSafFiles() {
        assertTrue(TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_LITERT_LM, "assistant.litertlm").supported);
        assertTrue(TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_LITERT_LM, "actions.task").supported);
        assertFalse(TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_LITERT_LM, "phi.mnn.json").supported);

        TaiModelImporter.ValidationResult mnn = TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_MNN_LLM, "config.json");
        assertTrue(mnn.supported);
        assertFalse(mnn.packageManifest);
        assertFalse(TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_MNN_LLM, "assistant.litertlm").supported);
    }

    @Test
    public void backendValidation_rejectsRawWeightsAndNativeLibrariesWithStableCodes() {
        TaiModelImporter.ValidationResult raw = TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_MNN_LLM, "model.gguf");
        assertFalse(raw.supported);
        assertEquals(TaiModelImporter.ERROR_RAW_WEIGHTS_FORBIDDEN, raw.errorCode);

        TaiModelImporter.ValidationResult nativeLibrary = TaiModelImporter.validateImportFileNameForBackend(
            TaiModelSpec.BACKEND_MNN_LLM, "libmodel.so");
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
            TaiModelSpec.BACKEND_MNN_LLM, "https://huggingface.co/user/model/resolve/main/config.json").supported);
        assertFalse(TaiModelImporter.validateHuggingFaceImportUrl(
            TaiModelSpec.BACKEND_MNN_LLM, "https://huggingface.co/user/model/resolve/main/model.safetensors").supported);
    }

    @Test
    public void modelId_defaultsFromSelectedFileName() {
        assertEquals("Gemma-4-E2B-it",
            TaiModelImporter.sanitizeModelId(
                TaiModelImporter.stripModelExtension("Gemma 4 E2B it.litertlm")));
        assertEquals("mobile_actions", TaiModelImporter.stripModelExtension("mobile_actions.task"));
        assertEquals("config.json", TaiModelImporter.stripModelExtension("config.json"));
    }
}
