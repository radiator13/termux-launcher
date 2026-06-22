package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class MnnTaiRuntimeConfigTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void mergedConfig_preservesMnnConfigDefaultsWhenOptionsAreAuto() throws Exception {
        File dir = new File(context.getCacheDir(), "mnn-config-preserve");
        dir.mkdirs();
        File config = configFile(dir);
        touch(new File(dir, "llm.mnn"));
        touch(new File(dir, "llm.mnn.weight"));
        touch(new File(dir, "tokenizer.mtok"));

        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        TaiRuntimeOptions options = new TaiRuntimeOptions(null, null, null, null,
            null, null, null, null, null, null, null, null);
        JSONObject merged = new JSONObject((String) invokeMergedConfig(runtime, config, model(config), options));

        assertEquals("cpu", merged.getString("backend_type"));
        assertEquals(4, merged.getInt("thread_num"));
        assertEquals("low", merged.getString("precision"));
        assertEquals("low", merged.getString("memory"));
        assertEquals(0.8d, merged.getDouble("temperature"), 0.0d);
        assertEquals(40, merged.getInt("top_k"));
        assertEquals(0.9d, merged.getDouble("top_p"), 0.0d);
        assertEquals("mixed", merged.getString("sampler_type"));
        assertEquals(0.05d, merged.getDouble("min_p"), 0.0d);
        assertEquals("tokenizer.mtok", merged.getString("tokenizer_file"));
        assertFalse(merged.has("system_prompt"));
    }

    @Test
    public void loadMnnModel_reportsMissingTokenizerSidecar() throws Exception {
        File dir = new File(context.getCacheDir(), "mnn-config-missing-tokenizer");
        dir.mkdirs();
        File config = configFile(dir);
        touch(new File(dir, "llm.mnn"));
        touch(new File(dir, "llm.mnn.weight"));

        JSONObject result = new MnnTaiRuntime(context).load(model(config),
            new TaiRuntimeOptions(null, null, null, null, null, null, null, null));

        assertFalse(result.getBoolean("ok"));
        assertEquals("model_file_not_readable", result.getString("error"));
        assertEquals("tokenizer.mtok", result.getString("missingFilename"));
    }

    @Test
    public void mergedConfig_injectsMaxContextAndMaxNewTokenDefaultsFromModelSpec() throws Exception {
        File dir = new File(context.getCacheDir(), "mnn-config-defaults");
        dir.mkdirs();
        File config = new File(dir, "config.json");
        // Minimal config: no max_context_len, no max_new_tokens, only model paths + chat template.
        write(config, "{\"llm_model\":\"llm.mnn\",\"llm_weight\":\"llm.mnn.weight\","
            + "\"tokenizer_file\":\"tokenizer.mtok\",\"jinja\":{\"chat_template\":\"template\"}}");
        touch(new File(dir, "llm.mnn"));
        touch(new File(dir, "llm.mnn.weight"));
        touch(new File(dir, "tokenizer.mtok"));

        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        TaiRuntimeOptions options = new TaiRuntimeOptions(null, null, null, null,
            null, null, null, null, null, null, null, null);
        TaiModelSpec spec = model(config);
        JSONObject merged = new JSONObject((String) invokeMergedConfig(runtime, config, spec, options));

        assertEquals("cpu", merged.getString("backend_type"));
        assertEquals(4, merged.getInt("thread_num"));
        assertEquals("low", merged.getString("precision"));
        assertEquals("low", merged.getString("memory"));
        assertEquals(spec.endpointContextWindow, merged.getInt("max_context_len"));
        assertEquals(spec.defaultMaxOutputTokens, merged.getInt("max_new_tokens"));
        assertEquals(0.8d, merged.getDouble("temperature"), 0.0d);
        assertEquals(40, merged.getInt("top_k"));
        assertEquals(0.9d, merged.getDouble("top_p"), 0.0d);
    }

    @Test
    public void mergedConfig_clampsConfigMaxContextToEndpointContextWindow() throws Exception {
        File dir = new File(context.getCacheDir(), "mnn-config-clamp");
        dir.mkdirs();
        File config = new File(dir, "config.json");
        // Upstream config advertises a larger context than the endpoint cap.
        write(config, "{\"llm_model\":\"llm.mnn\",\"llm_weight\":\"llm.mnn.weight\","
            + "\"tokenizer_file\":\"tokenizer.mtok\",\"max_context_len\":32768,"
            + "\"max_new_tokens\":8192,\"jinja\":{\"chat_template\":\"template\"}}");
        touch(new File(dir, "llm.mnn"));
        touch(new File(dir, "llm.mnn.weight"));
        touch(new File(dir, "tokenizer.mtok"));

        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        TaiRuntimeOptions options = new TaiRuntimeOptions(null, null, null, null,
            null, null, null, null, null, null, null, null);
        TaiModelSpec spec = model(config);
        JSONObject merged = new JSONObject((String) invokeMergedConfig(runtime, config, spec, options));

        assertEquals(spec.endpointContextWindow, merged.getInt("max_context_len"));
        assertTrue(merged.getInt("max_context_len") <= spec.endpointContextWindow);
        assertEquals(8192, merged.getInt("max_new_tokens"));
    }

    @Test
    public void settingsAutoLeavesMnnConfigValuesNull() {
        TaiRuntimeOptions options = new TaiSettings(context).getRuntimeOptions(TaiModelSpec.BACKEND_MNN_LLM, "mnn-auto");

        assertNull(options.accelerator);
        assertNull(options.threadCount);
        assertNull(options.precision);
        assertNull(options.memoryMode);
        assertNull(options.temperature);
        assertNull(options.topK);
        assertNull(options.topP);
    }

    @Test
    public void parseToolCalls_convertsMnnToolBlocksToOpenAiShape() throws Exception {
        MnnTaiRuntime runtime = new MnnTaiRuntime(context);
        Method method = MnnTaiRuntime.class.getDeclaredMethod("parseToolCalls", String.class, String.class);
        method.setAccessible(true);

        org.json.JSONArray calls = (org.json.JSONArray) method.invoke(runtime,
            "<tool_call>\n{\"name\":\"lookup\",\"arguments\":{\"query\":\"termux\"}}\n</tool_call>",
            "gen-1");

        assertEquals(1, calls.length());
        assertEquals("gen-1-call-1", calls.getJSONObject(0).getString("id"));
        assertEquals("function", calls.getJSONObject(0).getString("type"));
        assertEquals("lookup", calls.getJSONObject(0).getJSONObject("function").getString("name"));
        assertEquals("{\"query\":\"termux\"}", calls.getJSONObject(0).getJSONObject("function").getString("arguments"));
    }

    private static Object invokeMergedConfig(MnnTaiRuntime runtime, File config, TaiModelSpec model, TaiRuntimeOptions options) throws Exception {
        Method method = MnnTaiRuntime.class.getDeclaredMethod("mergedConfigJson", File.class, TaiModelSpec.class, TaiRuntimeOptions.class);
        method.setAccessible(true);
        return method.invoke(runtime, config, model, options);
    }

    private static File configFile(File dir) throws Exception {
        File config = new File(dir, "config.json");
        String json = "{\"llm_model\":\"llm.mnn\",\"llm_weight\":\"llm.mnn.weight\",\"backend_type\":\"cpu\","
            + "\"thread_num\":4,\"precision\":\"low\",\"memory\":\"low\",\"sampler_type\":\"mixed\","
            + "\"temperature\":0.8,\"top_k\":40,\"top_p\":0.9,\"min_p\":0.05,"
            + "\"tokenizer_file\":\"tokenizer.mtok\",\"jinja\":{\"chat_template\":\"template\"}}";
        write(config, json);
        return config;
    }

    private static void touch(File file) throws Exception {
        write(file, "x");
    }

    private static void write(File file, String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static TaiModelSpec model(File config) {
        return new TaiModelSpec(
            "mnn-test",
            "MNN Test",
            "test",
            "test",
            config.getAbsolutePath(),
            "test",
            1L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false,
            null,
            TaiModelSpec.BACKEND_MNN_LLM,
            TaiModelSpec.FORMAT_MNN,
            "qwen2.5",
            "int4",
            4096,
            0,
            null
        );
    }
}
