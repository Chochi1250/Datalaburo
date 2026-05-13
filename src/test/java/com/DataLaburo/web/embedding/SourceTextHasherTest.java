package com.DataLaburo.web.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceTextHasherTest {
    private final SourceTextHasher hasher = new SourceTextHasher();

    @Test
    void producesStableSha256HexUsingUtf8() {
        String hash = hasher.sha256Hex("embedding-text-v1\nDiseñar APIs");

        assertEquals(64, hash.length());
        assertEquals("eb8c0e70cac985765ba8e7995e2466a6670971ccfdcfb7e72743b6f9feb26f04", hash);
    }

    @Test
    void nullTextHashesAsEmptyString() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hasher.sha256Hex(null));
    }
}
