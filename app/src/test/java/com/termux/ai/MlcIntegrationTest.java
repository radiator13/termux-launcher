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
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end integration test for the MLC model lifecycle using fakes.
 *
 * <p>Proves the full flow: manifest validation, package install, model spec
 * persistence, and load/unload state transitions without requiring a real
 * MLC native runtime.
 */
@RunWith(RobolectricTestRunner.class)
public class MlcIntegrationTest {
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
        for (String modelId : store.getUserModels().keySet()) {
            store.deleteUserModel(modelId);
        }
    }

    @Test
    public void fullFlow_mlcModelDownloadInstallLoad() throws Exception {
        // 1. Prepare a fake downloaded file with a valid SHA-256
        File downloadDir = tempDir();
        File configFile = writeFile(downloadDir, "config.json", "{\"param\": 1}");
        String hash = sha256(configFile);

        // 2. Build a valid MLC manifest
        String manifestJson = new JSONObject()
            .put("schemaVersion", "1.0")
            .put("modelId", "integration-phi-3")
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

        // 3. Install from manifest
        TaiMlcPackageInstaller.InstallResult installResult = installer.installFromManifest(manifestJson, downloadDir, store);
        assertTrue("Expected install success, got: " + installResult.message, installResult.success);
        assertNotNull(installResult.installedSpec);
        assertEquals("integration-phi-3", installResult.installedSpec.id);
        assertEquals(TaiModelSpec.BACKEND_MLC_LLM, installResult.installedSpec.backend);
        assertEquals(TaiModelSpec.FORMAT_MLC, installResult.installedSpec.format);

        // 4. Verify the model is persisted in the store
        java.util.Map<String, TaiModelSpec> userModels = store.getUserModels();
        assertTrue(userModels.containsKey("integration-phi-3"));
        TaiModelSpec persisted = userModels.get("integration-phi-3");
        assertEquals(TaiModelSpec.BACKEND_MLC_LLM, persisted.backend);
        assertEquals(TaiModelSpec.FORMAT_MLC, persisted.format);

        // 5. Verify model files exist in app-private storage
        File modelDir = new File(store.getModelsDirectory(), "integration-phi-3");
        assertTrue(new File(modelDir, "config.json").isFile());

        // 6. Attempt to load the model through MlcTaiRuntime with fake native libs
        File nativeDir = tempDir();
        touch(new File(nativeDir, "libtvm4j_runtime_packed.so"));
        touch(new File(nativeDir, "libmlc_runtime.so"));
        touch(new File(nativeDir, "libmodel_android.so"));

        MlcTaiRuntime runtime = new MlcTaiRuntime(context, nativeDir);
        runtime.setSupportedAbisForTest(new String[]{MlcBundledLibraryRegistry.ABI_ARM64_V8A});

        JSONObject loadResult = runtime.load(persisted, options());
        assertTrue(loadResult.getBoolean("ok"));
        assertEquals("integration-phi-3", loadResult.getString("loadedModelId"));

        // 7. Verify runtime state reflects loaded model
        TaiRuntimeState state = runtime.getState();
        assertTrue(state.loaded);
        assertEquals("loaded", state.state);
        assertEquals("integration-phi-3", state.loadedModelId);

        // 8. Verify keep-warm transitions to idle-warm
        JSONObject warm = runtime.keepWarm(persisted, options(), 5);
        assertTrue(warm.getBoolean("ok"));
        assertTrue(warm.getBoolean("keepWarm"));

        state = runtime.getState();
        assertEquals("idle-warm", state.state);

        // 9. Verify unload transitions back to unloaded
        JSONObject unloadResult = runtime.unload();
        assertTrue(unloadResult.getBoolean("ok"));

        state = runtime.getState();
        assertFalse(state.loaded);
        assertEquals("unloaded", state.state);
    }

    private static TaiRuntimeOptions options() {
        return new TaiRuntimeOptions(null, null, null, null, null, null, null, null);
    }

    private static File tempDir() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "mlc_integration_test_" + System.currentTimeMillis());
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

    private static void touch(File file) throws Exception {
        file.getParentFile().mkdirs();
        file.createNewFile();
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream input = new java.io.FileInputStream(file)) {
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
