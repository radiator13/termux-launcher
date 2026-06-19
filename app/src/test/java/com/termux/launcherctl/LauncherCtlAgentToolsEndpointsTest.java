package com.termux.launcherctl;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.TaiManager;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LauncherCtlAgentToolsEndpointsTest {

    private Context context;
    private LauncherCtlApiServer server;
    private int port;
    private String token;
    private File tempDir;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();

        tempDir = new File(context.getFilesDir(), "agent-tools-test-" + System.nanoTime());
        LauncherCtlStorage.setBaseDirForTesting(tempDir);
        LauncherCtlNotificationStore.resetForTesting();

        resetSingleton(TaiManager.class, "instance");
        resetSingleton(LauncherCtlApiServer.class, "instance");
        LauncherToolRegistry.resetForTesting();

        server = LauncherCtlApiServer.getInstance();
        server.start(context);
        Thread.sleep(150);

        JSONObject endpoint = server.endpointSettings(context);
        port = endpoint.getInt("activePort");
        token = endpoint.getString("token");
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
        resetSingleton(TaiManager.class, "instance");
        resetSingleton(LauncherCtlApiServer.class, "instance");
        LauncherCtlNotificationStore.resetForTesting();
        LauncherToolRegistry.resetForTesting();
        LauncherCtlStorage.clearTestBaseDir();
        deleteRecursively(tempDir);
    }

    @Test
    public void getLauncherCapabilities_returnsExpectedShape() throws Exception {
        HttpURLConnection conn = get("/v1/launcher/capabilities");
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertTrue(response.getBoolean("ok"));
        assertEquals("v1", response.getString("apiVersion"));
        assertTrue(response.has("timestampMs"));
        assertTrue(response.has("device"));
        assertTrue(response.has("notifications"));
        assertTrue(response.has("tai"));
        assertTrue(response.has("functionGemma"));
        assertTrue(response.has("availableTools"));
        assertTrue(response.has("warnings"));
        assertTrue(response.has("blockingReasons"));

        JSONObject notifications = response.getJSONObject("notifications");
        assertFalse(notifications.getBoolean("listenerConnected"));
        assertFalse(notifications.getBoolean("accessEnabled"));

        JSONArray tools = response.getJSONArray("availableTools");
        assertTrue(tools.length() >= 14);

        JSONObject functionGemma = response.getJSONObject("functionGemma");
        assertEquals("functiongemma-270m-mobile-actions-litert-lm", functionGemma.getString("modelId"));
        assertTrue(functionGemma.has("catalog"));
        assertTrue(functionGemma.has("backendSupported"));
        assertTrue(functionGemma.has("modelAvailable"));
        assertTrue(functionGemma.has("modelLoaded"));
        assertTrue(functionGemma.has("usable"));
    }

    @Test
    public void getAgentTools_returnsInternalAndOpenAiFormats() throws Exception {
        HttpURLConnection conn = get("/v1/agent/tools");
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertTrue(response.getBoolean("ok"));
        assertTrue(response.has("count"));
        assertTrue(response.has("tools"));
        assertTrue(response.has("openAiTools"));

        JSONArray internal = response.getJSONArray("tools");
        JSONArray openAi = response.getJSONArray("openAiTools");
        assertEquals(internal.length(), openAi.length());
        assertTrue(internal.length() >= 14);

        JSONObject firstOpenAi = openAi.getJSONObject(0);
        assertEquals("function", firstOpenAi.getString("type"));
        assertTrue(firstOpenAi.has("function"));

        File toolsJson = LauncherCtlStorage.getToolsJsonFile();
        assertTrue("tools.json debug snapshot should be written", toolsJson.exists());
        JSONObject snapshot = new JSONObject(new String(java.nio.file.Files.readAllBytes(toolsJson.toPath()), StandardCharsets.UTF_8));
        assertTrue(snapshot.has("tools"));
        assertTrue(snapshot.has("openAiTools"));
    }

    @Test
    public void getAgentTools_toolSchemasAreValid() throws Exception {
        HttpURLConnection conn = get("/v1/agent/tools");
        JSONObject response = new JSONObject(readBody(conn));
        JSONArray tools = response.getJSONArray("tools");
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.getJSONObject(i);
            assertTrue(tool.has("name"));
            assertTrue(tool.has("description"));
            assertTrue(tool.has("schema"));
            assertTrue(tool.has("risk"));
            assertTrue(tool.has("requiresConfirmation"));
            assertTrue(tool.has("executor"));
            JSONObject schema = tool.getJSONObject("schema");
            assertEquals("object", schema.getString("type"));
            assertTrue(schema.has("properties"));
        }
    }

    @Test
    public void existingOpenAiEndpoints_remainCompatible() throws Exception {
        HttpURLConnection conn = get("/v1/models");
        assertEquals(200, conn.getResponseCode());
        JSONObject response = new JSONObject(readBody(conn));
        assertEquals("list", response.getString("object"));
        assertTrue(response.has("data"));
    }

    private HttpURLConnection get(String path) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        return conn;
    }

    private String readBody(HttpURLConnection conn) throws Exception {
        InputStream stream = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = stream.read(b)) != -1) buf.write(b, 0, n);
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    private static void resetSingleton(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, null);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
