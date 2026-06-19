package com.termux.launcherctl;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class LauncherCtlMemoryStoreTest {

    private Context context;
    private File tempDir;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        tempDir = new File(context.getFilesDir(), "memory-store-test-" + System.nanoTime());
        LauncherCtlStorage.setBaseDirForTesting(tempDir);
        LauncherCtlMemoryStore.resetForTesting();
    }

    @After
    public void tearDown() {
        LauncherCtlMemoryStore.resetForTesting();
        LauncherCtlStorage.clearTestBaseDir();
        deleteRecursively(tempDir);
    }

    @Test
    public void writeAndGet_roundTrip() {
        LauncherCtlMemoryStore store = LauncherCtlMemoryStore.getInstance();
        store.write("agent", "note1", "value1");

        LauncherCtlMemoryStore.MemoryEntry entry = store.get("agent", "note1");
        assertNotNull(entry);
        assertEquals("agent", entry.namespace);
        assertEquals("note1", entry.key);
        assertEquals("value1", entry.value);
        assertTrue(entry.createdAt > 0);
        assertTrue(entry.updatedAt > 0);
    }

    @Test
    public void write_overwritesExistingKey() {
        LauncherCtlMemoryStore store = LauncherCtlMemoryStore.getInstance();
        store.write("agent", "note1", "value1");
        store.write("agent", "note1", "value2");

        LauncherCtlMemoryStore.MemoryEntry entry = store.get("agent", "note1");
        assertNotNull(entry);
        assertEquals("value2", entry.value);
    }

    @Test
    public void search_matchesKeyOrValue() {
        LauncherCtlMemoryStore store = LauncherCtlMemoryStore.getInstance();
        store.write("agent", "alpha", "hello world");
        store.write("agent", "beta", "goodbye");
        store.write("other", "gamma", "hello");

        List<LauncherCtlMemoryStore.MemoryEntry> results = store.search("agent", "hello", 10);
        assertEquals(1, results.size());
        assertEquals("alpha", results.get(0).key);

        List<LauncherCtlMemoryStore.MemoryEntry> valueResults = store.search("agent", "goodbye", 10);
        assertEquals(1, valueResults.size());
        assertEquals("beta", valueResults.get(0).key);
    }

    @Test
    public void search_respectsLimit() {
        LauncherCtlMemoryStore store = LauncherCtlMemoryStore.getInstance();
        store.write("agent", "a", "x");
        store.write("agent", "b", "x");
        store.write("agent", "c", "x");

        List<LauncherCtlMemoryStore.MemoryEntry> results = store.search("agent", "x", 2);
        assertEquals(2, results.size());
    }

    @Test
    public void get_missingNamespaceOrKey_returnsNull() {
        LauncherCtlMemoryStore store = LauncherCtlMemoryStore.getInstance();
        assertNull(store.get("", "key"));
        assertNull(store.get("agent", ""));
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
