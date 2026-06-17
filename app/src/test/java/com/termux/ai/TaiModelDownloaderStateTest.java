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

    private void run(String modelId, String url, File output, List<String> states) {
        run(modelId, url, output, states, transfer -> { });
    }

    private void run(String modelId, String url, File output, List<String> states,
                     TaiModelDownloader.ProgressCallback extraCallback) {
        TaiModelDownloader downloader = new TaiModelDownloader(context, store);
        downloader.runDownload("download-" + modelId, modelId, url, output,
            modelId, "license", capabilities(), TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM, "", "", 4096, 0, "", null,
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
