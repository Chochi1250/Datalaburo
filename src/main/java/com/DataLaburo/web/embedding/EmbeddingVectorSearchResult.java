package com.DataLaburo.web.embedding;

public record EmbeddingVectorSearchResult(
        Long jobId,
        Long jobEmbeddingId,
        double distance,
        double similarity,
        String embeddingModel,
        boolean semanticMeaning
) {
}
