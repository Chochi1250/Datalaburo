package com.DataLaburo.web.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "spring.datasource.driver-class-name",
        havingValue = "org.h2.Driver"
)
public class UnsupportedDocumentEmbeddingVectorWriter implements DocumentEmbeddingVectorWriter {
    @Override
    public boolean writeReady(DocumentEmbedding documentEmbedding, EmbeddingGenerationResult generationResult) {
        throw new IllegalStateException("Writing pgvector embeddings requires PostgreSQL with pgvector enabled");
    }
}
