package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TaiModelVariantsTest {

    private static final String BASE_ID = "gemma-4-e4b-it-litert-lm";

    private TaiModelSpec multimodal(String id) {
        Set<String> caps = new LinkedHashSet<>(Arrays.asList(
            TaiModelSpec.CAPABILITY_TEXT_CHAT,
            TaiModelSpec.CAPABILITY_IMAGE_INPUT,
            TaiModelSpec.CAPABILITY_AUDIO_INPUT,
            TaiModelSpec.CAPABILITY_LLM_THINKING));
        return new TaiModelSpec(
            id, "Gemma 4 E4B IT", "role", "catalog",
            "/data/user/0/com.termux/files/tai/models/Gemma-4-E4B-it/gemma-4-E4B-it.litertlm",
            "Apache-2.0", 3_659_530_240L, caps, true, null,
            TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "gemma", null,
            4096, 32768, 4000, 12, null, null, null);
    }

    private TaiModelSpec textOnly(String id) {
        Set<String> caps = new LinkedHashSet<>(Arrays.asList(
            TaiModelSpec.CAPABILITY_TEXT_CHAT, TaiModelSpec.CAPABILITY_TOOL_USE));
        return new TaiModelSpec(
            id, "Coder", "role", "catalog", "/data/coder.litertlm",
            "Apache-2.0", 100L, caps, true, null,
            TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "qwen", null,
            4096, 4096, 4096, 6, null, null, null);
    }

    @Test
    public void expand_multimodalProducesChatVisionAudio() {
        List<TaiModelSpec> variants = TaiModelVariants.expand(multimodal(BASE_ID));
        assertEquals(3, variants.size());

        TaiModelSpec chat = variants.get(0);
        assertEquals(BASE_ID, chat.id);
        assertTrue(chat.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertFalse(chat.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertFalse(chat.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertFalse(chat.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertFalse(chat.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));

        TaiModelSpec vision = variants.get(1);
        assertEquals(BASE_ID + "-vision", vision.id);
        assertTrue(vision.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertFalse(vision.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));

        TaiModelSpec audio = variants.get(2);
        assertEquals(BASE_ID + "-audio", audio.id);
        assertTrue(audio.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertFalse(audio.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
    }

    @Test
    public void expand_variantsShareThePhysicalFileAndProfileInputs() {
        TaiModelSpec base = multimodal(BASE_ID);
        for (TaiModelSpec variant : TaiModelVariants.expand(base)) {
            assertEquals(base.localPath, variant.localPath);
            assertEquals(base.sizeBytes, variant.sizeBytes);
            assertEquals(base.recommendedRamGb, variant.recommendedRamGb);
            assertEquals(base.defaultMaxOutputTokens, variant.defaultMaxOutputTokens);
        }
    }

    @Test
    public void expand_textOnlyModelIsUnchanged() {
        List<TaiModelSpec> variants = TaiModelVariants.expand(textOnly("coder"));
        assertEquals(1, variants.size());
        assertEquals("coder", variants.get(0).id);
        assertTrue(variants.get(0).capabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE));
    }

    @Test
    public void chatScopedOrSelf_stripsModalitiesForMultimodalAndPassesThroughOthers() {
        TaiModelSpec chat = TaiModelVariants.chatScopedOrSelf(multimodal(BASE_ID));
        assertNotNull(chat);
        assertEquals(BASE_ID, chat.id);
        assertFalse(chat.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertFalse(chat.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));

        TaiModelSpec coder = textOnly("coder");
        assertEquals(coder, TaiModelVariants.chatScopedOrSelf(coder));
    }

    @Test
    public void resolve_suffixesMapToScopedSpecs() {
        TaiModelVariants.Lookup lookup = id -> BASE_ID.equals(id) ? multimodal(BASE_ID) : null;

        TaiModelSpec vision = TaiModelVariants.resolve(BASE_ID + "-vision", lookup);
        assertNotNull(vision);
        assertEquals(BASE_ID + "-vision", vision.id);
        assertTrue(vision.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertFalse(vision.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));

        TaiModelSpec audio = TaiModelVariants.resolve(BASE_ID + "-audio", lookup);
        assertNotNull(audio);
        assertTrue(audio.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertFalse(audio.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
    }

    @Test
    public void resolve_unknownOrNonVariantReturnsNull() {
        TaiModelVariants.Lookup lookup = id -> BASE_ID.equals(id) ? multimodal(BASE_ID) : null;
        assertNull(TaiModelVariants.resolve("does-not-exist-vision", lookup));
        assertNull(TaiModelVariants.resolve(BASE_ID, lookup));
        // A text-only base cannot produce a vision variant.
        TaiModelVariants.Lookup textLookup = id -> "coder".equals(id) ? textOnly("coder") : null;
        assertNull(TaiModelVariants.resolve("coder-vision", textLookup));
    }
}
