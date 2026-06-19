package com.termux.launcherctl;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LauncherCtlEventStoreTest {

    private Context context;
    private File tempDir;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        tempDir = new File(context.getFilesDir(), "event-store-test-" + System.nanoTime());
        LauncherCtlStorage.setBaseDirForTesting(tempDir);
        LauncherCtlEventStore.resetForTesting();
    }

    @After
    public void tearDown() {
        LauncherCtlEventStore.resetForTesting();
        LauncherCtlStorage.clearTestBaseDir();
        deleteRecursively(tempDir);
    }

    @Test
    public void appendEvent_andTailEvents_roundTrip() throws Exception {
        LauncherCtlEventStore store = LauncherCtlEventStore.getInstance();
        JSONObject payload = new JSONObject();
        payload.put("tool", "apps.launch");
        store.appendEvent("agent.route", payload);
        store.awaitWritesForTesting(2_000);

        List<JSONObject> events = store.tailEvents(10, null);
        assertEquals(1, events.size());
        assertEquals("agent.route", events.get(0).getString("type"));
        assertEquals("apps.launch", events.get(0).getJSONObject("payload").getString("tool"));
        assertTrue(events.get(0).getLong("timestampMs") > 0);
    }

    @Test
    public void appendAgentRun_writesToAgentRunsJsonl() throws Exception {
        LauncherCtlEventStore store = LauncherCtlEventStore.getInstance();
        JSONObject payload = new JSONObject();
        payload.put("tool", "system.resources");
        store.appendAgentRun("agent.execute", payload);
        store.awaitWritesForTesting(2_000);

        List<JSONObject> runs = store.tailAgentRuns(10, null);
        assertEquals(1, runs.size());
        assertEquals("agent.execute", runs.get(0).getString("type"));
    }

    @Test
    public void tailEvents_sinceFilter_ignoresOlderEvents() throws Exception {
        long before = System.currentTimeMillis();
        LauncherCtlEventStore store = LauncherCtlEventStore.getInstance();
        store.appendEventSync("old", new JSONObject(), LauncherCtlStorage.getEventsJsonlFile());
        Thread.sleep(10);
        long after = System.currentTimeMillis();
        store.appendEventSync("new", new JSONObject(), LauncherCtlStorage.getEventsJsonlFile());

        List<JSONObject> events = store.tailEvents(10, after);
        assertEquals(1, events.size());
        assertEquals("new", events.get(0).getString("type"));

        List<JSONObject> all = store.tailEvents(10, before);
        assertEquals(2, all.size());
    }

    @Test
    public void tailEvents_emptyStore_returnsEmptyList() {
        List<JSONObject> events = LauncherCtlEventStore.getInstance().tailEvents(10, null);
        assertTrue(events.isEmpty());
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
