package com.DataLaburo.web.embedding;

import org.springframework.stereotype.Component;

@Component
public class BgeM3EmbeddingGenerator {
    private final BgeM3EmbeddingClient embeddingClient;

    public BgeM3EmbeddingGenerator(BgeM3EmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    public String model() {
        return DocumentEmbedding.DEFAULT_EMBEDDING_MODEL;
    }

    public int dimensions() {
        return DocumentEmbedding.DEFAULT_EMBEDDING_DIMENSIONS;
    }

    public EmbeddingGenerationResult generate(String normalizedText) {
        return embeddingClient.createEmbedding(normalizedText);
    }
}
