package com.termux.launcherctl;

import org.junit.After;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LauncherCtlStorageTest {

    @After
    public void tearDown() {
        File base = LauncherCtlStorage.getHomeDir();
        LauncherCtlStorage.clearTestBaseDir();
        deleteRecursively(base);
    }

    @Test
    public void storagePaths_resolveUnderLauncherCtlDir() throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "launcherctl-storage-test-" + System.nanoTime());
        LauncherCtlStorage.setBaseDirForTesting(tempDir);

        assertEquals(new File(tempDir, ".launcherctl"), LauncherCtlStorage.getLauncherCtlDir());
        assertEquals(new File(tempDir, ".launcherctl/launcher.db"), LauncherCtlStorage.getDatabaseFile());
        assertEquals(new File(tempDir, ".launcherctl/notifications.jsonl"), LauncherCtlStorage.getNotificationsJsonlFile());
        assertEquals(new File(tempDir, ".launcherctl/events.jsonl"), LauncherCtlStorage.getEventsJsonlFile());
        assertEquals(new File(tempDir, ".launcherctl/agent-runs.jsonl"), LauncherCtlStorage.getAgentRunsJsonlFile());
        assertEquals(new File(tempDir, ".launcherctl/tools.json"), LauncherCtlStorage.getToolsJsonFile());
        assertEquals(new File(tempDir, ".launcherctl/capabilities.json"), LauncherCtlStorage.getCapabilitiesJsonFile());
        assertEquals(new File(tempDir, ".launcherctl/config.json"), LauncherCtlStorage.getConfigJsonFile());
    }

    @Test
    public void ensureLauncherCtlDir_createsDirectory() throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "launcherctl-storage-test-" + System.nanoTime());
        LauncherCtlStorage.setBaseDirForTesting(tempDir);

        File dir = LauncherCtlStorage.ensureLauncherCtlDir();

        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
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
