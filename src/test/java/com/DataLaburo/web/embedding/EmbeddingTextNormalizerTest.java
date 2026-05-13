package com.DataLaburo.web.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddingTextNormalizerTest {
    private final EmbeddingTextNormalizer normalizer = new EmbeddingTextNormalizer();

    @Test
    void exposesStableVersion() {
        assertEquals("embedding-text-v1", EmbeddingTextNormalizer.VERSION);
    }

    @Test
    void producesStableNormalizedOutput() {
        String input = "  Titulo:\r\n  Desarrollador   Java\tSenior  \n\n\n"
                + "Descripcion:\u0007\nDiseñar APIs, mantener integración y documentación.  ";

        String normalized = normalizer.normalize(input);

        assertEquals("""
                Titulo:
                Desarrollador Java Senior

                Descripcion:
                Diseñar APIs, mantener integración y documentación.""", normalized);
    }

    @Test
    void nullAndBlankInputsReturnEmptyString() {
        assertEquals("", normalizer.normalize(null));
        assertEquals("", normalizer.normalize(" \t \n "));
    }
}
