package com.DataLaburo.web.embedding;

public interface DocumentEmbeddingVectorWriter {
    boolean writeReady(DocumentEmbedding documentEmbedding, EmbeddingGenerationResult generationResult);
}
