package com.termux.ai;

import org.junit.Test;

import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;

public class TaiModelDownloaderUrlTest {

    @Test
    public void repoId_parsedFromBareAndDecoratedRepoUrls() {
        assertEquals("taobao-mnn/Qwen2.5-VL-3B-Instruct-MNN",
            TaiModelDownloader.huggingFaceRepoIdFromRepoUrl("https://huggingface.co/taobao-mnn/Qwen2.5-VL-3B-Instruct-MNN"));
        assertEquals("taobao-mnn/Qwen2.5-VL-3B-Instruct-MNN",
            TaiModelDownloader.huggingFaceRepoIdFromRepoUrl("https://huggingface.co/taobao-mnn/Qwen2.5-VL-3B-Instruct-MNN/tree/main"));
        assertEquals("org/model",
            TaiModelDownloader.huggingFaceRepoIdFromRepoUrl("https://huggingface.co/org/model/?x=1"));
        assertEquals("", TaiModelDownloader.huggingFaceRepoIdFromRepoUrl("https://huggingface.co/onlyorg"));
        assertEquals("", TaiModelDownloader.huggingFaceRepoIdFromRepoUrl("https://example.com/a/b"));
    }

    @Test
    public void entryFile_autoDetectedFromFileList() {
        // MNN package: config.json alongside a .mnn weight.
        LinkedHashSet<String> mnn = new LinkedHashSet<>();
        mnn.add("config.json");
        mnn.add("llm.mnn");
        mnn.add("llm.mnn.weight");
        assertEquals("config.json", TaiModelDownloader.chooseEntryFile(mnn));

        // LiteRT package wins even if a config.json is also present.
        LinkedHashSet<String> litert = new LinkedHashSet<>();
        litert.add("README.md");
        litert.add("config.json");
        litert.add("gemma3-1b-it.litertlm");
        assertEquals("gemma3-1b-it.litertlm", TaiModelDownloader.chooseEntryFile(litert));

        LinkedHashSet<String> taskOnly = new LinkedHashSet<>();
        taskOnly.add("model.task");
        assertEquals("model.task", TaiModelDownloader.chooseEntryFile(taskOnly));

        // config.json with no .mnn weight is not a usable MNN package -> empty.
        LinkedHashSet<String> bareConfig = new LinkedHashSet<>();
        bareConfig.add("config.json");
        bareConfig.add("tokenizer.json");
        assertEquals("", TaiModelDownloader.chooseEntryFile(bareConfig));

        assertEquals("", TaiModelDownloader.chooseEntryFile(new LinkedHashSet<>()));
    }
}
