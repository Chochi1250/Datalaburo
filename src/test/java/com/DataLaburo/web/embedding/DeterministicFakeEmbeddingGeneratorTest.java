package com.DataLaburo.web.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicFakeEmbeddingGeneratorTest {
    private final DeterministicFakeEmbeddingGenerator generator = new DeterministicFakeEmbeddingGenerator();

    @Test
    void sameInputProducesSameVector() {
        float[] first = generator.generate("same-source-hash").vector();
        float[] second = generator.generate("same-source-hash").vector();

        assertArrayEquals(first, second);
    }

    @Test
    void generatesExpectedModelAndDimensions() {
        EmbeddingGenerationResult result = generator.generate("source-hash");

        assertEquals("local-fake", result.provider());
        assertEquals(DocumentEmbedding.FAKE_DETERMINISTIC_EMBEDDING_MODEL, result.model());
        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS, result.dimensions());
        assertEquals(DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS, result.vector().length);
    }

    @Test
    void generatedVectorHasNoNaNOrInfiniteValues() {
        float[] vector = generator.generate("source-hash").vector();

        for (float value : vector) {
            assertTrue(Float.isFinite(value));
        }
    }
}
