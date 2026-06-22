package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TaiModelDownloaderStateTest {
    private Context context;
    private TaiModelStore store;
    private HttpServer server;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        store = new TaiModelStore(context);
    }

    @After
    public void tearDown() {
        if (server != null) server.stop(0);
        TaiModelDownloadService.clearCancellation("state-test");
        TaiModelDownloadService.clearCancellation("cancel-test");
        TaiModelDownloadService.clearCancellation("retry-test");
        store.deleteUserModel("state-test");
        store.deleteUserModel("cancel-test");
        store.deleteUserModel("retry-test");
        store.deleteUserModel(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        store.deleteUserModel(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);
        store.deleteUserModel("Qwen2.5-Coder-1.5B-GGUF");
        store.deleteUserModel("resolvable-test");
    }

    @Test
    public void startDownload_rejectsHttpUrls() throws Exception {
        TaiModelDownloader downloader = new TaiModelDownloader(context, store);

        JSONObject result = downloader.startDownload("http-test", "http://example.com/model.litertlm",
            "HTTP Test", "license", capabilities(), null);

        assertFalse(result.getBoolean("ok"));
        assertEquals("insecure_url", result.getString("error"));
    }

    @Test
    public void runDownload_transitionsDownloadingVerifyingInstalled() throws Exception {
        byte[] model = modelBytes('a');
        String url = serve(new FixedBytesHandler(model));
        List<String> states = new ArrayList<>();
        File output = output("state-test", "model.litertlm");

        run("state-test", url, output, states);

        assertTrue(states.contains(TaiModelStore.STATE_DOWNLOADING));
        assertTrue(states.contains(TaiModelStore.STATE_VERIFYING));
        assertEquals(TaiModelStore.STATE_INSTALLED, states.get(states.size() - 1));
        assertTrue(output.isFile());
        assertFalse(new File(output.getAbsolutePath() + ".part").exists());
    }

    @Test
    public void runDownload_cancelledKeepsPartForCleanup() throws Exception {
        byte[] model = modelBytes('b');
        String url = serve(new FixedBytesHandler(model));
        List<String> states = new ArrayList<>();
        File output = output("cancel-test", "model.litertlm");

        run("cancel-test", url, output, states, transfer -> {
            if (TaiModelStore.STATE_DOWNLOADING.equals(transfer.optString("status"))) {
                TaiModelDownloadService.requestCancel("cancel-test");
            }
        });

        assertEquals(TaiModelStore.STATE_CANCELLED, states.get(states.size() - 1));
        assertFalse(output.exists());
        assertTrue(new File(output.getAbsolutePath() + ".part").exists());
    }

    @Test
    public void runDownload_failedValidationCanRetryFromPart() throws Exception {
        byte[] html = modelBytes('<');
        html[0] = '<';
        html[1] = 'h';
        html[2] = 't';
        html[3] = 'm';
        html[4] = 'l';
        byte[] valid = modelBytes('c');
        String url = serve(new SequenceHandler(html, valid));
        File output = output("retry-test", "model.litertlm");
        List<String> firstStates = new ArrayList<>();

        run("retry-test", url, output, firstStates);

        assertEquals(TaiModelStore.STATE_FAILED, firstStates.get(firstStates.size() - 1));
        assertFalse(output.exists());
        assertTrue(new File(output.getAbsolutePath() + ".part").exists());

        List<String> retryStates = new ArrayList<>();
        run("retry-test", url, output, retryStates);

        assertEquals(TaiModelStore.STATE_INSTALLED, retryStates.get(retryStates.size() - 1));
        assertTrue(output.isFile());
        assertFalse(new File(output.getAbsolutePath() + ".part").exists());
    }

    @Test
    public void completedDownloadRecordAdvertisesReadableModel() throws Exception {
        File output = output(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, "gemma-4-E2B-it.litertlm");
        assertTrue(output.getParentFile().mkdirs() || output.getParentFile().isDirectory());
        java.nio.file.Files.write(output.toPath(), modelBytes('g'));
        store.upsertDownload(new JSONObject()
            .put("id", "legacy-gemma-download")
            .put("modelId", TaiModelRegistry.MODEL_GEMMA_4_E2B_IT)
            .put("path", output.getAbsolutePath())
            .put("status", "complete")
            .put("bytesRead", output.length())
            .put("totalBytes", output.length()));

        TaiModelSpec advertised = store.getDownloadedReadableModels().get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);

        assertNotNull(advertised);
        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, advertised.backend);
        assertTrue(advertised.capabilities.contains("image_input"));
        assertTrue(advertised.capabilities.contains("audio_input"));
        assertFalse(advertised.capabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertTrue(advertised.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertEquals(4096, advertised.endpointContextWindow);
        assertEquals(32768, advertised.sourceContextWindow);
        assertEquals(4000, advertised.defaultMaxOutputTokens);
        assertEquals(output.length(), advertised.sizeBytes);
    }

    @Test
    public void completedGgufDownloadRecordIsNotEndpointVisible() throws Exception {
        File output = output("Qwen2.5-Coder-1.5B-GGUF", "qwen2.5-coder-1.5b.gguf");
        assertTrue(output.getParentFile().mkdirs() || output.getParentFile().isDirectory());
        java.nio.file.Files.write(output.toPath(), modelBytes('q'));
        store.upsertDownload(new JSONObject()
            .put("id", "legacy-gguf-download")
            .put("modelId", "Qwen2.5-Coder-1.5B-GGUF")
            .put("path", output.getAbsolutePath())
            .put("status", "complete")
            .put("bytesRead", output.length())
            .put("totalBytes", output.length()));

        assertFalse(store.getDownloadedReadableModels().containsKey("Qwen2.5-Coder-1.5B-GGUF"));
    }

    @Test
    public void installedAndDownloadedReadableModels_areResolvableBySameId() throws Exception {
        byte[] model = modelBytes('r');
        String url = serve(new FixedBytesHandler(model));
        File output = output("resolvable-test", "model.litertlm");
        run("resolvable-test", url, output, new ArrayList<>());

        // After a real install, both the installed-user-model store and the download record
        // must resolve the same id, so /v1/models listings can never 404 on resolveModel.
        assertNotNull("installed download must be resolvable via getUserModel", store.getUserModel("resolvable-test"));
        assertTrue("installed download must be in getDownloadedReadableModels",
            store.getDownloadedReadableModels().containsKey("resolvable-test"));
        java.util.Map<String, TaiModelSpec> readable = store.getDownloadedReadableModels();
        for (String id : readable.keySet()) {
            assertTrue("every /v1/models item must be a supported backend/format or excluded",
                TaiModelSpec.isSupportedBackendFormat(readable.get(id).backend, readable.get(id).format));
        }
        store.deleteUserModel("resolvable-test");
    }

    @Test
    public void staleInstalledE4bMetadata_isRebuiltFromCatalogFacts() throws Exception {
        File output = output(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT, "gemma-4-E4B-it.litertlm");
        assertTrue(output.getParentFile().mkdirs() || output.getParentFile().isDirectory());
        byte[] bytes = modelBytes('e');
        java.nio.file.Files.write(output.toPath(), bytes);

        // Stale installed record that drops audio and never declared llm_thinking.
        JSONObject stale = new JSONObject()
            .put("id", TaiModelRegistry.MODEL_GEMMA_4_E4B_IT)
            .put("displayName", "Old E4B Name")
            .put("roleHint", "old role")
            .put("source", "imported")
            .put("localPath", output.getAbsolutePath())
            .put("license", "stale")
            .put("sizeBytes", 999L)
            .put("builtInCatalogEntry", false)
            .put("backend", TaiModelSpec.BACKEND_LITERT_LM)
            .put("format", TaiModelSpec.FORMAT_LITERTLM)
            .put("endpointContextWindow", 2048)
            .put("sourceContextWindow", 2048)
            .put("defaultMaxOutputTokens", 512)
            .put("recommendedRamGb", 0)
            .put("sourceCapabilities", new org.json.JSONArray()
                .put(TaiModelSpec.CAPABILITY_TEXT_CHAT)
                .put(TaiModelSpec.CAPABILITY_IMAGE_INPUT))
            .put("endpointCapabilities", new org.json.JSONArray()
                .put(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        android.content.SharedPreferences prefs =
            context.getSharedPreferences(TaiSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE);
        prefs.edit().putString("tai_user_models_json", new org.json.JSONArray().put(stale).toString()).apply();

        TaiModelSpec rebuilt = store.getUserModels().get(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);

        assertNotNull(rebuilt);
        assertTrue("stale installed metadata must not suppress catalog audio",
            rebuilt.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertTrue(rebuilt.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertFalse(rebuilt.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertTrue(rebuilt.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertEquals(4096, rebuilt.endpointContextWindow);
        assertEquals(32768, rebuilt.sourceContextWindow);
        assertEquals(4000, rebuilt.defaultMaxOutputTokens);
        assertEquals(output.length(), rebuilt.sizeBytes);
        store.deleteUserModel(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);
    }

    private void run(String modelId, String url, File output, List<String> states) {
        run(modelId, url, output, states, transfer -> { });
    }

    private void run(String modelId, String url, File output, List<String> states,
                     TaiModelDownloader.ProgressCallback extraCallback) {
        TaiModelDownloader downloader = new TaiModelDownloader(context, store);
        downloader.runDownload("download-" + modelId, modelId, url, output,
            modelId, "license", capabilities(), TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, "", "", 4096, 0, "", 0L, null,
            transfer -> {
                states.add(transfer.optString("status"));
                extraCallback.onProgress(transfer);
            });
    }

    private String serve(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model.litertlm", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/model.litertlm";
    }

    private File output(String modelId, String name) {
        return new File(new File(store.getModelsDirectory(), modelId), name);
    }

    private static LinkedHashSet<String> capabilities() {
        return new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT));
    }

    private static byte[] modelBytes(char fill) {
        byte[] bytes = new byte[1024 * 1024 + 16];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) fill;
        bytes[0] = 'T';
        bytes[1] = 'A';
        bytes[2] = 'I';
        return bytes;
    }

    private static class FixedBytesHandler implements HttpHandler {
        private final byte[] bytes;

        FixedBytesHandler(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    private static final class SequenceHandler implements HttpHandler {
        private final byte[] first;
        private final byte[] second;
        private int calls;

        SequenceHandler(byte[] first, byte[] second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public synchronized void handle(HttpExchange exchange) throws IOException {
            byte[] bytes = calls++ == 0 ? first : second;
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }
}
