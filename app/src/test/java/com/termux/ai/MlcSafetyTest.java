package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests proving MLC safety boundaries.
 *
 * <p>All tests use fakes/mocks/stubs and do not require a real MLC native runtime.
 */
@RunWith(RobolectricTestRunner.class)
public class MlcSafetyTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @After
    public void tearDown() {
        TaiDeviceCapabilities.clearDebugMlcUnsupportedReason();
    }

    @Test
    public void mlcSchema_parsesBackendAndCapabilities() throws Exception {
        JSONObject json = baseModelJson("mlc-chat", "/models/mlc-chat/model.mlc")
            .put("backend", TaiModelSpec.BACKEND_MLC_LLM)
            .put("format", TaiModelSpec.FORMAT_MLC)
            .put("capabilities", new JSONArray()
                .put(TaiModelSpec.CAPABILITY_TEXT_CHAT)
                .put(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));

        TaiModelSpec spec = TaiModelSpec.fromJson(json);

        assertEquals(TaiModelSpec.BACKEND_MLC_LLM, spec.backend);
        assertEquals(TaiModelSpec.FORMAT_MLC, spec.format);
        assertTrue(spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertTrue(spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
    }

    @Test
    public void mlcUnsupportedBackend_rejected() {
        try {
            new TaiModelSpec(
                "bad", "bad", "test", "test", null, "test", 0L,
                Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT),
                false, null, "unknown-backend", TaiModelSpec.FORMAT_MLC,
                null, null, 4096, 0, null);
            assertTrue("Expected IllegalArgumentException for unsupported backend", false);
        } catch (IllegalArgumentException e) {
            assertEquals("unsupported_backend", e.getMessage());
        }
    }

    @Test
    public void mlcBackendRouting_routesToMlcRuntime() throws Exception {
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(context);
        TaiModelSpec mlc = model("mlc-model", TaiModelSpec.BACKEND_MLC_LLM, TaiModelSpec.FORMAT_MLC);

        Object target = runtimeForModel(runtime, mlc);
        Object mlcField = field(runtime, "mlc");

        assertSame(mlcField, target);
    }

    @Test
    public void mlcPackageValidator_rejectsCustomSo() {
        TaiMlcPackageValidator.ValidationResult result =
            TaiMlcPackageValidator.validatePackagePath("phi-3-mini-mlc", "/sdcard/download/libevil.so");
        assertEquals(TaiMlcPackageValidator.ValidationResult.CUSTOM_SO_FORBIDDEN, result);
    }

    @Test
    public void mlcPackageValidator_rejectsPathTraversal() {
        TaiMlcPackageValidator.ValidationResult result =
            TaiMlcPackageValidator.validatePackagePath("phi-3-mini-mlc", "../etc/passwd");
        assertEquals(TaiMlcPackageValidator.ValidationResult.PATH_TRAVERSAL_DETECTED, result);
    }

    @Test
    public void mlcPackageValidator_rejectsRawWeights() {
        assertEquals(TaiMlcPackageValidator.ValidationResult.RAW_WEIGHTS_FORBIDDEN,
            TaiMlcPackageValidator.validatePackagePath("phi-3-mini-mlc", "model.safetensors"));
        assertEquals(TaiMlcPackageValidator.ValidationResult.RAW_WEIGHTS_FORBIDDEN,
            TaiMlcPackageValidator.validatePackagePath("phi-3-mini-mlc", "model.gguf"));
        assertEquals(TaiMlcPackageValidator.ValidationResult.RAW_WEIGHTS_FORBIDDEN,
            TaiMlcPackageValidator.validatePackagePath("phi-3-mini-mlc", "model.bin"));
        assertEquals(TaiMlcPackageValidator.ValidationResult.RAW_WEIGHTS_FORBIDDEN,
            TaiMlcPackageValidator.validatePackagePath("phi-3-mini-mlc", "model.pt"));
        assertEquals(TaiMlcPackageValidator.ValidationResult.RAW_WEIGHTS_FORBIDDEN,
            TaiMlcPackageValidator.validatePackagePath("phi-3-mini-mlc", "model.onnx"));
    }

    @Test
    public void mlcPackageValidator_rejectsHttpUrl() {
        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-http")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("sourceUrl", "http://example.com/manifest.json")
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "config.json")
                    .put("size", 1)
                    .put("sha256", "a")))
            .toString();

        TaiMlcPackageInstaller installer = new TaiMlcPackageInstaller();
        TaiModelStore store = new TaiModelStore(context);
        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_INSECURE_URL, result.errorCode);
    }

    @Test
    public void mlcPackageValidator_rejectsMissingHash() {
        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-missing-hash")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "config.json")
                    .put("size", 1)))
            .toString();

        TaiMlcPackageInstaller installer = new TaiMlcPackageInstaller();
        TaiModelStore store = new TaiModelStore(context);
        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_HASH_MISMATCH, result.errorCode);
    }

    @Test
    public void mlcPackageValidator_rejectsUnknownModelLibraryId() {
        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-unknown-lib")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "nonexistent-lib-12345")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "config.json")
                    .put("size", 1)
                    .put("sha256", "a")))
            .toString();

        TaiMlcPackageInstaller installer = new TaiMlcPackageInstaller();
        TaiModelStore store = new TaiModelStore(context);
        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_UNKNOWN_MODEL_LIBRARY, result.errorCode);
    }

    @Test
    public void mlcDeviceGating_unsupportedAbiReturnsReason() {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.createForTest(
            "Emulator", "Google", "", 34,
            Arrays.asList("x86"),
            4L * 1024L * 1024L * 1024L, "totalMem", false);

        assertFalse(device.mlcSupported);
        assertNotNull(device.mlcUnsupportedReason);
        assertTrue("Expected ABI reason, got: " + device.mlcUnsupportedReason,
            device.mlcUnsupportedReason.contains("not available for this device ABI"));
        assertTrue("Expected supported ABI list, got: " + device.mlcUnsupportedReason,
            device.mlcUnsupportedReason.contains("arm64-v8a"));
    }

    @Test
    public void mlcRuntime_unsupportedAbiReturns501() throws Exception {
        MlcTaiRuntime runtime = new MlcTaiRuntime(context);
        runtime.setSupportedAbisForTest(new String[]{"x86"});

        JSONObject result = runtime.load(model("phi-3-mini-mlc"), options());

        assertFalse(result.getBoolean("ok"));
        assertEquals("mlc_runtime_unavailable", result.getString("error"));
        assertEquals(501, result.getInt("_statusCode"));
    }

    private static TaiModelSpec model(String id, String backend, String format) {
        return new TaiModelSpec(
            id,
            id,
            "Test model",
            "test",
            "/models/" + id + "/model." + format,
            "test",
            123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false,
            null,
            backend,
            format,
            null,
            null,
            4096,
            0,
            null
        );
    }

    private static TaiModelSpec model(String id) {
        return model(id, TaiModelSpec.BACKEND_MLC_LLM, TaiModelSpec.FORMAT_MLC);
    }

    private static TaiRuntimeOptions options() {
        return new TaiRuntimeOptions(null, null, null, null, null, null, null, null);
    }

    private static Object runtimeForModel(MultiBackendTaiRuntime runtime, TaiModelSpec model) throws Exception {
        Method method = MultiBackendTaiRuntime.class.getDeclaredMethod("runtimeForModel", TaiModelSpec.class);
        method.setAccessible(true);
        return method.invoke(runtime, model);
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
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

    private static File tempDir() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "mlc_safety_test_" + System.currentTimeMillis());
        dir.mkdirs();
        return dir;
    }
}
