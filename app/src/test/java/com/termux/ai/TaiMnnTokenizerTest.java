package com.termux.ai;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TaiMnnTokenizerTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void declaredTokenizerFileWins() throws Exception {
        File dir = tmp.newFolder("m");
        JSONObject config = new JSONObject().put("tokenizer_file", "tok.special");
        assertEquals("tok.special", TaiModelStore.mnnTokenizerFile(dir, config));
    }

    @Test
    public void fallsBackToConventionalTokenizerWhenConfigOmitsIt() throws Exception {
        // Qwen3-VL/eagle case: config.json has no tokenizer_file, tokenizer.txt is on disk.
        File dir = tmp.newFolder("m");
        assertTrue(new File(dir, "tokenizer.txt").createNewFile());
        assertEquals("tokenizer.txt", TaiModelStore.mnnTokenizerFile(dir, new JSONObject()));
    }

    @Test
    public void prefersMtokWhenOnlyMtokPresent() throws Exception {
        File dir = tmp.newFolder("m");
        assertTrue(new File(dir, "tokenizer.mtok").createNewFile());
        assertEquals("tokenizer.mtok", TaiModelStore.mnnTokenizerFile(dir, new JSONObject()));
    }

    @Test
    public void defaultsToTokenizerTxtWhenNothingPresent() throws Exception {
        File dir = tmp.newFolder("m");
        assertEquals("tokenizer.txt", TaiModelStore.mnnTokenizerFile(dir, new JSONObject()));
    }
}
