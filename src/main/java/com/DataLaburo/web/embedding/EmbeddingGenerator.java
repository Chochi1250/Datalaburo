package com.DataLaburo.web.embedding;

public interface EmbeddingGenerator {
    String provider();

    String model();

    int dimensions();

    EmbeddingGenerationResult generate(String input);
}
