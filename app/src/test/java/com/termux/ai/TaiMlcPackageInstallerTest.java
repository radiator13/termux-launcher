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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TaiMlcPackageInstallerTest {

    private Context context;
    private TaiModelStore store;
    private TaiMlcPackageInstaller installer;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        store = new TaiModelStore(context);
        installer = new TaiMlcPackageInstaller();
    }

    @After
    public void tearDown() {
        // Clean up any test models left behind
        for (String modelId : store.getUserModels().keySet()) {
            store.deleteUserModel(modelId);
        }
    }

    @Test
    public void validManifest_installsSuccessfully() throws Exception {
        File downloadDir = tempDir();
        File configFile = writeFile(downloadDir, "config.json", "{\"param\": 1}");
        String hash = sha256(configFile);

        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-phi-3-valid")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "config.json")
                    .put("size", configFile.length())
                    .put("sha256", hash)))
            .toString();

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, downloadDir, store);

        assertTrue("Expected success, got: " + result.message, result.success);
        assertNotNull(result.installedSpec);
        assertEquals("test-phi-3-valid", result.installedSpec.id);
        assertEquals(TaiModelSpec.BACKEND_MLC_LLM, result.installedSpec.backend);
        assertEquals(TaiModelSpec.FORMAT_MLC, result.installedSpec.format);
        assertEquals("downloaded", result.installedSpec.source);

        File modelDir = new File(store.getModelsDirectory(), "test-phi-3-valid");
        assertTrue(new File(modelDir, "config.json").isFile());
    }

    @Test
    public void httpUrl_rejected() {
        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-http-url")
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

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_INSECURE_URL, result.errorCode);
    }

    @Test
    public void missingSha256_rejected() {
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

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_HASH_MISMATCH, result.errorCode);
    }

    @Test
    public void soFile_rejected() {
        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-so-file")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "libevil.so")
                    .put("size", 1)
                    .put("sha256", "a")))
            .toString();

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_NATIVE_ARTIFACT_FORBIDDEN, result.errorCode);
    }

    @Test
    public void rawWeight_rejected() {
        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-raw-weight")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "model.safetensors")
                    .put("size", 1)
                    .put("sha256", "a")))
            .toString();

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_RAW_WEIGHTS_FORBIDDEN, result.errorCode);
    }

    @Test
    public void pathTraversal_rejected() {
        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-traversal")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "../etc/passwd")
                    .put("size", 1)
                    .put("sha256", "a")))
            .toString();

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_PATH_TRAVERSAL, result.errorCode);
    }

    @Test
    public void unknownModelLibraryId_rejected() {
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

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_UNKNOWN_MODEL_LIBRARY, result.errorCode);
    }

    @Test
    public void duplicateModelId_rejected() throws Exception {
        // Pre-insert a model with the same ID
        TaiModelSpec existing = new TaiModelSpec(
            "test-duplicate-id",
            "test-duplicate-id",
            "test",
            "test",
            "/tmp/test",
            "test",
            0L,
            new LinkedHashSet<>(Collections.singletonList(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false,
            null,
            TaiModelSpec.BACKEND_MLC_LLM,
            TaiModelSpec.FORMAT_MLC,
            null,
            null,
            4096,
            0,
            null
        );
        store.upsertUserModel(existing);

        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-duplicate-id")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "config.json")
                    .put("size", 1)
                    .put("sha256", "a")))
            .toString();

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_DUPLICATE_MODEL, result.errorCode);
    }

    @Test
    public void hashMismatch_rejected() throws Exception {
        File downloadDir = tempDir();
        writeFile(downloadDir, "config.json", "data");

        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-hash-mismatch")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "config.json")
                    .put("size", 4)
                    .put("sha256", "0000000000000000000000000000000000000000000000000000000000000000")))
            .toString();

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, downloadDir, store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_HASH_MISMATCH, result.errorCode);
    }

    @Test
    public void htmlResponse_rejected() {
        String html = "<html><body>Please log in</body></html>";
        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(html, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_INVALID_MANIFEST, result.errorCode);
    }

    @Test
    public void fileMissing_rejected() {
        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-missing-file")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "nonexistent.json")
                    .put("size", 1)
                    .put("sha256", "a")))
            .toString();

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_FILE_MISSING, result.errorCode);
    }

    @Test
    public void unsupportedSchema_rejected() {
        String manifestJson = new JSONObject()
            .put("schemaVersion", "2.0")
            .put("modelId", "test-bad-schema")
            .put("backend", "mlc-llm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "config.json")
                    .put("size", 1)
                    .put("sha256", "a")))
            .toString();

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_UNSUPPORTED_SCHEMA, result.errorCode);
    }

    @Test
    public void invalidBackend_rejected() {
        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "test-bad-backend")
            .put("backend", "litert-lm")
            .put("format", "mlc")
            .put("modelLibraryId", "phi-3-mini-mlc")
            .put("capabilities", new JSONArray().put("text_chat"))
            .put("files", new JSONArray()
                .put(new JSONObject()
                    .put("path", "config.json")
                    .put("size", 1)
                    .put("sha256", "a")))
            .toString();

        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest(manifestJson, tempDir(), store);

        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_INVALID_MANIFEST, result.errorCode);
    }

    @Test
    public void emptyManifest_rejected() {
        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest("", tempDir(), store);
        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_INVALID_MANIFEST, result.errorCode);
    }

    @Test
    public void nonJsonManifest_rejected() {
        TaiMlcPackageInstaller.InstallResult result = installer.installFromManifest("not json at all", tempDir(), store);
        assertFalse(result.success);
        assertEquals(TaiMlcPackageInstaller.ERROR_INVALID_MANIFEST, result.errorCode);
    }

    private static File tempDir() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "mlc_test_" + System.currentTimeMillis());
        dir.mkdirs();
        return dir;
    }

    private static File writeFile(File dir, String name, String content) throws Exception {
        File file = new File(dir, name);
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return file;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (byte value : digest.digest()) {
            builder.append(String.format(Locale.US, "%02x", value));
        }
        return builder.toString();
    }
}
