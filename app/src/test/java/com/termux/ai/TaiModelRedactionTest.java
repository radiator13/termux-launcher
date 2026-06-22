package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiModelRedactionTest {

    @Test
    public void redactToken_returnsEmptyForNullOrEmpty() {
        assertEquals("", TaiModelSpec.redactToken(null));
        assertEquals("", TaiModelSpec.redactToken(""));
        assertEquals("", TaiModelSpec.redactToken("   "));
    }

    @Test
    public void redactToken_prefixSuffixForLongTokens() {
        String token = "abcdefghijklmno";
        String redacted = TaiModelSpec.redactToken(token);
        assertTrue("prefix should be visible", redacted.startsWith("abcdef"));
        assertTrue("suffix should be visible", redacted.endsWith("lmno"));
        assertFalse("inner token must not leak", redacted.contains("ghijklm"));
        assertTrue(redacted.contains("..."));
    }

    @Test
    public void redactToken_shortTokensMaskAllButSuffix() {
        String token = "abc123";
        String redacted = TaiModelSpec.redactToken(token);
        assertEquals("...c123", redacted);
        assertFalse(redacted.contains("ab"));
    }

    @Test
    public void redactToken_preservesPrefixAndSuffixLength() {
        String token = "tok_abcdef1234567";
        String redacted = TaiModelSpec.redactToken(token);
        assertEquals("tok_ab...4567", redacted);
    }

    @Test
    public void redactToken_isIdempotentSafe() {
        String token = "sk-abcdefghij12345";
        String redacted = TaiModelSpec.redactToken(token);
        assertFalse(TaiModelSpec.redactToken(redacted).contains("ghij1234"));
    }
}