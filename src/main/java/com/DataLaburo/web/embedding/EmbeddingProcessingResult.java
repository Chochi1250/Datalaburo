package com.DataLaburo.web.embedding;

public record EmbeddingProcessingResult(
        EmbeddingProcessingAction action,
        Long documentEmbeddingId,
        String embeddingModel,
        Integer embeddingDimensions,
        String reason
) {
}
