package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MlcTaiRuntimeTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void unsupportedAbi_returns501MlcRuntimeUnavailable() throws Exception {
        MlcTaiRuntime runtime = new MlcTaiRuntime(context);
        runtime.setSupportedAbisForTest(new String[]{"x86"});

        JSONObject result = runtime.load(model("phi-3-mini-mlc"), options());

        assertFalse(result.getBoolean("ok"));
        assertEquals("mlc_runtime_unavailable", result.getString("error"));
        assertEquals(501, result.getInt("_statusCode"));
    }

    @Test
    public void missingBundledLibrary_returns501() throws Exception {
        MlcTaiRuntime runtime = new MlcTaiRuntime(context, tempDir());
        runtime.setSupportedAbisForTest(new String[]{MlcBundledLibraryRegistry.ABI_ARM64_V8A});
        // tempDir is empty -> libraries missing

        JSONObject result = runtime.load(model("phi-3-mini-mlc"), options());

        assertFalse(result.getBoolean("ok"));
        assertEquals("mlc_runtime_unavailable", result.getString("error"));
        assertEquals(501, result.getInt("_statusCode"));
        String msg = result.getString("message");
        assertTrue("Expected missing library message, got: " + msg, msg.contains("missing"));
    }

    @Test
    public void customSoPath_rejectedWith501() throws Exception {
        File nativeDir = tempDir();
        touch(new File(nativeDir, "libtvm4j_runtime_packed.so"));
        touch(new File(nativeDir, "libmlc_runtime.so"));
        touch(new File(nativeDir, "libmodel_android.so"));

        MlcTaiRuntime runtime = new MlcTaiRuntime(context, nativeDir);
        runtime.setSupportedAbisForTest(new String[]{MlcBundledLibraryRegistry.ABI_ARM64_V8A});

        TaiModelSpec badModel = new TaiModelSpec(
            "phi-3-mini-mlc",
            "phi-3-mini-mlc",
            "test",
            "test",
            "/sdcard/download/libevil.so",
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

        JSONObject result = runtime.load(badModel, options());

        assertFalse(result.getBoolean("ok"));
        assertEquals("mlc_runtime_unavailable", result.getString("error"));
        assertEquals(501, result.getInt("_statusCode"));
        String msg = result.getString("message");
        assertTrue("Expected custom .so rejection, got: " + msg, msg.contains("custom .so"));
    }

    @Test
    public void validLoad_transitionsStateCorrectly() throws Exception {
        File nativeDir = tempDir();
        touch(new File(nativeDir, "libtvm4j_runtime_packed.so"));
        touch(new File(nativeDir, "libmlc_runtime.so"));
        touch(new File(nativeDir, "libmodel_android.so"));

        MlcTaiRuntime runtime = new MlcTaiRuntime(context, nativeDir);
        runtime.setSupportedAbisForTest(new String[]{MlcBundledLibraryRegistry.ABI_ARM64_V8A});

        // Initial state
        TaiRuntimeState state = runtime.getState();
        assertEquals("unloaded", state.state);
        assertFalse(state.loaded);
        assertNull(state.loadedModelId);

        // Load
        JSONObject loadResult = runtime.load(model("phi-3-mini-mlc"), options());
        assertTrue(loadResult.getBoolean("ok"));
        assertEquals("phi-3-mini-mlc", loadResult.getString("loadedModelId"));

        state = runtime.getState();
        assertEquals("loaded", state.state);
        assertTrue(state.loaded);
        assertEquals("phi-3-mini-mlc", state.loadedModelId);

        // Unload
        JSONObject unloadResult = runtime.unload();
        assertTrue(unloadResult.getBoolean("ok"));

        state = runtime.getState();
        assertEquals("unloaded", state.state);
        assertFalse(state.loaded);
        assertNull(state.loadedModelId);
    }

    @Test
    public void keepWarm_transitionsToIdleWarm() throws Exception {
        File nativeDir = tempDir();
        touch(new File(nativeDir, "libtvm4j_runtime_packed.so"));
        touch(new File(nativeDir, "libmlc_runtime.so"));
        touch(new File(nativeDir, "libmodel_android.so"));

        MlcTaiRuntime runtime = new MlcTaiRuntime(context, nativeDir);
        runtime.setSupportedAbisForTest(new String[]{MlcBundledLibraryRegistry.ABI_ARM64_V8A});

        runtime.load(model("phi-3-mini-mlc"), options());
        JSONObject warm = runtime.keepWarm(model("phi-3-mini-mlc"), options(), 5);

        assertTrue(warm.getBoolean("ok"));
        assertTrue(warm.getBoolean("keepWarm"));
        assertEquals(5, warm.getInt("keepWarmMinutes"));

        TaiRuntimeState state = runtime.getState();
        assertEquals("idle-warm", state.state);
        assertTrue(state.keepWarmUntilMs > System.currentTimeMillis());
    }

    @Test
    public void cancel_duringLoad_returnsCancelled() throws Exception {
        File nativeDir = tempDir();
        touch(new File(nativeDir, "libtvm4j_runtime_packed.so"));
        touch(new File(nativeDir, "libmlc_runtime.so"));
        touch(new File(nativeDir, "libmodel_android.so"));

        MlcTaiRuntime runtime = new MlcTaiRuntime(context, nativeDir);
        runtime.setSupportedAbisForTest(new String[]{MlcBundledLibraryRegistry.ABI_ARM64_V8A});

        // Start load in a background thread so we can cancel it mid-flight.
        final JSONObject[] loadResult = new JSONObject[1];
        Thread loader = new Thread(() -> {
            try {
                loadResult[0] = runtime.load(model("phi-3-mini-mlc"), options());
            } catch (Exception ignored) {
            }
        });
        loader.start();
        // Give the load thread time to enter the loading state.
        Thread.sleep(50);

        JSONObject cancelResult = runtime.cancel();
        assertTrue(cancelResult.getBoolean("ok"));
        assertTrue(cancelResult.getBoolean("cancelled"));
        assertTrue(cancelResult.getBoolean("loadCancellationRequested"));

        loader.join(2000);

        // After cancellation the load should finish and report cancellation.
        assertNotNull(loadResult[0]);
        assertFalse(loadResult[0].getBoolean("ok"));
        assertEquals("model_load_cancelled", loadResult[0].getString("error"));
        assertEquals(499, loadResult[0].getInt("_statusCode"));

        TaiRuntimeState state = runtime.getState();
        assertEquals("unloaded", state.state);
    }

    @Test
    public void generationApis_return501WhenLoaded() throws Exception {
        File nativeDir = tempDir();
        touch(new File(nativeDir, "libtvm4j_runtime_packed.so"));
        touch(new File(nativeDir, "libmlc_runtime.so"));
        touch(new File(nativeDir, "libmodel_android.so"));

        MlcTaiRuntime runtime = new MlcTaiRuntime(context, nativeDir);
        runtime.setSupportedAbisForTest(new String[]{MlcBundledLibraryRegistry.ABI_ARM64_V8A});

        runtime.load(model("phi-3-mini-mlc"), options());

        JSONObject chat = runtime.chat("phi-3-mini-mlc", "sys", "user", options());
        assertFalse(chat.getBoolean("ok"));
        assertEquals("unsupported_operation", chat.getString("error"));
        assertEquals(501, chat.getInt("_statusCode"));

        JSONObject complete = runtime.complete("phi-3-mini-mlc", "prompt", options());
        assertFalse(complete.getBoolean("ok"));
        assertEquals("unsupported_operation", complete.getString("error"));
        assertEquals(501, complete.getInt("_statusCode"));
    }

    @Test
    public void generationApis_return409WhenNotLoaded() throws Exception {
        MlcTaiRuntime runtime = new MlcTaiRuntime(context);
        runtime.setSupportedAbisForTest(new String[]{MlcBundledLibraryRegistry.ABI_ARM64_V8A});

        JSONObject chat = runtime.chat("phi-3-mini-mlc", "sys", "user", options());
        assertFalse(chat.getBoolean("ok"));
        assertEquals("model_not_loaded", chat.getString("error"));
        assertEquals(409, chat.getInt("_statusCode"));
    }

    @Test
    public void pathTraversalInLocalPath_rejected() throws Exception {
        File nativeDir = tempDir();
        touch(new File(nativeDir, "libtvm4j_runtime_packed.so"));
        touch(new File(nativeDir, "libmlc_runtime.so"));
        touch(new File(nativeDir, "libmodel_android.so"));

        MlcTaiRuntime runtime = new MlcTaiRuntime(context, nativeDir);
        runtime.setSupportedAbisForTest(new String[]{MlcBundledLibraryRegistry.ABI_ARM64_V8A});

        TaiModelSpec badModel = new TaiModelSpec(
            "phi-3-mini-mlc",
            "phi-3-mini-mlc",
            "test",
            "test",
            "/data/data/../evil",
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

        JSONObject result = runtime.load(badModel, options());
        assertFalse(result.getBoolean("ok"));
        assertEquals("mlc_runtime_unavailable", result.getString("error"));
        String msg = result.getString("message");
        assertTrue("Expected path traversal rejection, got: " + msg, msg.contains("Path traversal"));
    }

    private static TaiModelSpec model(String id) {
        return new TaiModelSpec(
            id,
            id,
            "test",
            "test",
            "/models/" + id + "/model.mlc",
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
    }

    private static TaiRuntimeOptions options() {
        return new TaiRuntimeOptions(null, null, null, null, null, null, null, null);
    }

    private static File tempDir() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "mlc_test_" + System.currentTimeMillis());
        dir.mkdirs();
        return dir;
    }

    private static void touch(File file) throws Exception {
        file.getParentFile().mkdirs();
        file.createNewFile();
    }
}
